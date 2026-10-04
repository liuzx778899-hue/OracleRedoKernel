"""Bounded Linux regular-file closed-generation collector. No header normalization, records or ACK."""
import fcntl,hashlib,json,math,os,re,stat,struct,sys,time
from resume_online_observation import identity,checksum,durable_json

def scn(block,offset):return struct.unpack_from('<I',block,offset)[0]|((struct.unpack_from('<H',block,offset+4)[0]&0x7fff)<<32)

def audit_generation(directory,receipt,source,bindings,sequence):
    if set(receipt)!={'sequence','journal','sha256','blocks','source_path','first_scn','next_scn','raw_closed_header_sha256','capture_complete','consumer_ack'} or receipt['sequence']!=sequence or receipt['journal']!='sequence-'+str(sequence)+'.oracap' or receipt['capture_complete'] is not False or receipt['consumer_ack'] is not False:
        raise RuntimeError('CAPTURE_RESUME_GENERATION_RECEIPT_INVALID')
    binding=next((b for b in bindings if b['path']==receipt['source_path']),None)
    if binding is None:raise RuntimeError('CAPTURE_RESUME_MEMBER_BINDING_MISMATCH')
    with open(os.path.join(directory,receipt['journal']),'rb') as journal:data=journal.read()
    if hashlib.sha256(data).hexdigest()!=receipt['sha256']:raise RuntimeError('CAPTURE_RESUME_JOURNAL_DIGEST_MISMATCH')
    offset=0;blocks=[];generation=dict(source,sequence=sequence);previous_sample=-1
    while offset<len(data):
        if len(data)-offset<16 or data[offset:offset+8]!=b'ORACAP01':raise RuntimeError('CAPTURE_RESUME_FRAME_HEADER_INVALID')
        m,n=struct.unpack_from('<II',data,offset+8);offset+=16
        if not 2<=m<=4096 or n!=512 or len(data)-offset<m+544:raise RuntimeError('CAPTURE_RESUME_FRAME_TRUNCATED')
        encoded=data[offset:offset+m];raw=data[offset+m:offset+m+512];digest=data[offset+m+512:offset+m+544];offset+=m+544
        if hashlib.sha256(encoded+raw).digest()!=digest:raise RuntimeError('CAPTURE_RESUME_FRAME_DIGEST_MISMATCH')
        meta=json.loads(encoded)
        if set(meta)!={'block','observed_unix_seconds','sample','sha256','source','state'} or type(meta['block']) is not int or meta['block']!=len(blocks) or meta['state']!='OBSERVED_STABLE' or meta['source']!=generation or type(meta['sample']) is not int or meta['sample']<previous_sample or type(meta['observed_unix_seconds']) not in (int,float) or not math.isfinite(meta['observed_unix_seconds']):raise RuntimeError('CAPTURE_RESUME_FRAME_METADATA_INVALID')
        previous_sample=meta['sample']
        if hashlib.sha256(raw).hexdigest()!=meta['sha256'] or not checksum(raw):raise RuntimeError('CAPTURE_RESUME_RAW_DIGEST_OR_CHECKSUM_MISMATCH')
        if blocks and (struct.unpack_from('<H',raw)[0]!=0x2201 or struct.unpack_from('<I',raw,4)[0]!=len(blocks) or struct.unpack_from('<I',raw,8)[0]!=sequence):raise RuntimeError('CAPTURE_RESUME_BLOCK_IDENTITY_MISMATCH')
        blocks.append(raw)
    if len(blocks)<3 or type(receipt['blocks']) is not int or len(blocks)!=receipt['blocks']:raise RuntimeError('CAPTURE_RESUME_CLOSED_RANGE_MISMATCH')
    file,header=blocks[:2]
    if struct.unpack_from('<H',file)[0]!=0x2200 or struct.unpack_from('<I',file,20)[0]!=512 or struct.unpack_from('<I',file,28)[0]!=0x7a7b7c7d or (struct.unpack_from('<I',file,24)[0]+1)*512!=binding['bytes']:raise RuntimeError('CAPTURE_RESUME_FILE_HEADER_INVALID')
    if identity(header)!=generation or struct.unpack_from('<I',header,156)[0]!=len(blocks) or len(blocks)*512>binding['bytes'] or not scn(header,180)<scn(header,192)<0x7fffffffffff or (scn(header,180),scn(header,192))!=(receipt['first_scn'],receipt['next_scn']) or hashlib.sha256(header).hexdigest()!=receipt['raw_closed_header_sha256']:raise RuntimeError('CAPTURE_RESUME_CLOSED_HEADER_MISMATCH')

def resume_receipts(directory,start,bindings,source,expected_sha):
    with open(os.path.join(directory,'source.json')) as stream:saved=json.load(stream)
    if saved!={'source':source,'start_sequence':start,'members':bindings}:raise RuntimeError('CAPTURE_RESUME_SOURCE_BINDING_MISMATCH')
    with open(os.path.join(directory,'receipt.json'),'rb') as stream:data=stream.read()
    if hashlib.sha256(data).hexdigest()!=expected_sha:raise RuntimeError('CAPTURE_RESUME_RECEIPT_DIGEST_MISMATCH')
    receipt=json.loads(data)
    if set(receipt)!={'start_sequence','next_sequence','captured','capture_complete'} or receipt['start_sequence']!=start or receipt['capture_complete'] is not False or type(receipt['captured']) is not list or receipt['next_sequence']!=start+len(receipt['captured']):raise RuntimeError('CAPTURE_RESUME_LEDGER_INVALID')
    captured=receipt['captured'];expected=start
    for item in captured:
        audit_generation(directory,item,source,bindings,expected)
        if expected>start and captured[expected-start-1]['next_scn']!=item['first_scn']:raise RuntimeError('CAPTURE_RESUME_SCN_CHAIN_MISMATCH')
        expected+=1
    # Per-generation receipts are durable before the aggregate ledger; recover only a complete checked tail.
    while os.path.exists(os.path.join(directory,'sequence-'+str(expected)+'.oracap')):
        sidecar=os.path.join(directory,'sequence-'+str(expected)+'.receipt.json')
        if not os.path.exists(sidecar):raise RuntimeError('CAPTURE_UNCOMMITTED_GENERATION')
        with open(sidecar) as stream:item=json.load(stream)
        audit_generation(directory,item,source,bindings,expected)
        if captured and captured[-1]['next_scn']!=item['first_scn']:raise RuntimeError('CAPTURE_RESUME_SCN_CHAIN_MISMATCH')
        captured.append(item);expected+=1
    allowed={item['journal'] for item in captured}
    for name in os.listdir(directory):
        if name.startswith('sequence-') and name.endswith('.oracap') and name not in allowed:raise RuntimeError('CAPTURE_RESUME_ORPHAN_GENERATION_GAP')
        if name.startswith('sequence-') and name.endswith('.receipt.json'):
            if name[:-13]+'.oracap' not in allowed:raise RuntimeError('CAPTURE_RESUME_SIDECAR_WITHOUT_JOURNAL')
    for item in captured:
        sidecar='sequence-'+str(item['sequence'])+'.receipt.json';path=os.path.join(directory,sidecar)
        if os.path.exists(path):
            with open(path) as stream:saved=json.load(stream)
            if saved!=item:raise RuntimeError('CAPTURE_RESUME_SIDECAR_DISAGREEMENT')
        else:durable_json(directory,sidecar,item)  # Upgrade an already fully verified legacy ledger.
    durable_json(directory,'receipt.json',{'start_sequence':start,'next_sequence':expected,'captured':captured,'capture_complete':False})
    return expected,captured

def follow(directory,start,polls,interval,members,resume_sha=None):
    if start<1 or polls<1 or not math.isfinite(interval) or interval<0 or len(set(members))!=len(members) or not members:raise ValueError('invalid bounded follow')
    if resume_sha is not None and re.fullmatch('[a-f0-9]{64}',resume_sha) is None:raise ValueError('trusted receipt SHA required')
    if resume_sha is None:os.mkdir(directory)
    with open(os.path.join(directory,'writer.lock'),'a') as lock:
        fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        _follow_locked(directory,start,polls,interval,members,resume_sha)

def _follow_locked(directory,start,polls,interval,members,resume_sha):
    readers=[];bindings=[];source=None;expected=start;captured=[]
    try:
        for path in members:
            reader=open(path,'rb',buffering=0);readers.append(reader);status=os.fstat(reader.fileno())
            if not stat.S_ISREG(status.st_mode) or status.st_size<1536 or status.st_size%512:raise RuntimeError('CAPTURE_MEMBER_LAYOUT_INVALID')
            reader.seek(512);header=reader.read(512);generation=identity(header)
            key={k:v for k,v in generation.items() if k!='sequence'}
            if source is None:source=key
            if key!=source or not checksum(header) or generation['thread']<1 or generation['compatibility'] not in (0x13000000,0x15000000):raise RuntimeError('CAPTURE_MEMBER_SOURCE_MISMATCH')
            bindings.append({'path':path,'device':status.st_dev,'inode':status.st_ino,'bytes':status.st_size})
        if resume_sha is None:
            durable_json(directory,'source.json',{'source':source,'start_sequence':start,'members':bindings})
            durable_json(directory,'receipt.json',{'start_sequence':start,'next_sequence':start,'captured':[],'capture_complete':False})
        else:expected,captured=resume_receipts(directory,start,bindings,source,resume_sha)
        print(json.dumps({'ready':True,'start_sequence':start,'next_sequence':expected,'resumed':resume_sha is not None,'members':len(members),'capture_complete':False}),flush=True)
        for sample in range(polls):
            closed={};headers=[]
            for reader,binding in zip(readers,bindings):
                now=os.fstat(reader.fileno());named=os.stat(binding['path'])
                if (now.st_dev,now.st_ino,now.st_size)!=(binding['device'],binding['inode'],binding['bytes']) or (named.st_dev,named.st_ino)!=(now.st_dev,now.st_ino):raise RuntimeError('CAPTURE_MEMBER_FILE_CHANGED')
                reader.seek(512);header=reader.read(512);generation=identity(header)
                if {k:v for k,v in generation.items() if k!='sequence'}!=source or not checksum(header):raise RuntimeError('CAPTURE_MEMBER_HEADER_CHANGED_OR_INVALID')
                sequence=generation['sequence'];headers.append(sequence);nab=struct.unpack_from('<I',header,156)[0]
                if sequence>=expected and nab!=0xffffffff:
                    if sequence in closed:raise RuntimeError('CAPTURE_DUPLICATE_SEQUENCE_MEMBER')
                    if not 3<=nab<=binding['bytes']//512 or not scn(header,180)<scn(header,192)<0x7fffffffffff:raise RuntimeError('CAPTURE_CLOSED_BOUNDARY_INVALID')
                    closed[sequence]=(reader,binding,header,generation,nab)
            while expected in closed:
                reader,binding,header,generation,nab=closed.pop(expected)
                reader.seek(0);first=reader.read(nab*512);reader.seek(0);second=reader.read(nab*512)
                reader.seek(512);after=reader.read(512)
                if len(first)!=nab*512 or first!=second or header!=after or second[512:1024]!=header:raise RuntimeError('CAPTURE_CLOSED_INPUT_CHANGED')
                now=os.fstat(reader.fileno());named=os.stat(binding['path'])
                if (now.st_dev,now.st_ino,now.st_size)!=(binding['device'],binding['inode'],binding['bytes']) or (named.st_dev,named.st_ino)!=(now.st_dev,now.st_ino):raise RuntimeError('CAPTURE_MEMBER_FILE_CHANGED')
                if struct.unpack_from('<H',second)[0]!=0x2200 or struct.unpack_from('<I',second,20)[0]!=512 or struct.unpack_from('<I',second,28)[0]!=0x7a7b7c7d or (struct.unpack_from('<I',second,24)[0]+1)*512!=binding['bytes']:raise RuntimeError('CAPTURE_FILE_HEADER_INVALID')
                # Validate the WHOLE source range before publishing any generation receipt.
                for block in range(nab):
                    raw=second[block*512:(block+1)*512]
                    if not checksum(raw):raise RuntimeError('CAPTURE_CLOSED_BLOCK_CHECKSUM_MISMATCH')
                    if block>0 and (struct.unpack_from('<H',raw)[0]!=0x2201 or struct.unpack_from('<I',raw,4)[0]!=block or struct.unpack_from('<I',raw,8)[0]!=expected):raise RuntimeError('CAPTURE_CLOSED_BLOCK_IDENTITY_MISMATCH')
                if captured and captured[-1]['next_scn']!=scn(header,180):raise RuntimeError('CAPTURE_CLOSED_SCN_GAP')
                journal_name='sequence-'+str(expected)+'.oracap';digest=hashlib.sha256()
                with open(os.path.join(directory,journal_name),'xb') as journal:
                    for block in range(nab):
                        raw=second[block*512:(block+1)*512]
                        encoded=json.dumps({'sample':sample,'observed_unix_seconds':time.time(),'source':generation,'block':block,'sha256':hashlib.sha256(raw).hexdigest(),'state':'OBSERVED_STABLE'},sort_keys=True,separators=(',',':')).encode()
                        frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+raw+hashlib.sha256(encoded+raw).digest();journal.write(frame);digest.update(frame)
                    journal.flush();os.fsync(journal.fileno())
                receipt={'sequence':expected,'journal':journal_name,'sha256':digest.hexdigest(),'blocks':nab,'source_path':binding['path'],'first_scn':scn(header,180),'next_scn':scn(header,192),'raw_closed_header_sha256':hashlib.sha256(header).hexdigest(),'capture_complete':False,'consumer_ack':False}
                durable_json(directory,'sequence-'+str(expected)+'.receipt.json',receipt)
                captured.append(receipt);durable_json(directory,'receipt.json',{'start_sequence':start,'next_sequence':expected+1,'captured':captured,'capture_complete':False})
                print(json.dumps({'captured_sequence':expected,'blocks':nab,'next_sequence':expected+1,'capture_complete':False}),flush=True);expected+=1
            if closed or min(headers)>expected:raise RuntimeError('CAPTURE_MISSING_GENERATION')
            if sample+1<polls:time.sleep(interval)
        print(json.dumps({'terminal':True,'captured_generations':len(captured),'next_sequence':expected,'capture_complete':False,'consumer_ack':False,'cdc_events':0}),flush=True)
    finally:
        for reader in readers:reader.close()

if __name__=='__main__':
    resume=len(sys.argv)>6 and sys.argv[-2]=='--resume'
    follow(sys.argv[1],int(sys.argv[2]),int(sys.argv[3]),float(sys.argv[4]),sys.argv[5:-2] if resume else sys.argv[5:],sys.argv[-1] if resume else None)
