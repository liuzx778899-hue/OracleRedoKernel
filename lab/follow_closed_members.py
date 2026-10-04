"""Bounded Linux regular-file closed-generation collector. No header normalization, records or ACK."""
import hashlib,json,math,os,stat,struct,sys,time
from resume_online_observation import identity,checksum,durable_json

def scn(block,offset):return struct.unpack_from('<I',block,offset)[0]|((struct.unpack_from('<H',block,offset+4)[0]&0x7fff)<<32)

def follow(directory,start,polls,interval,members):
    if start<1 or polls<1 or not math.isfinite(interval) or interval<0 or len(set(members))!=len(members) or not members:raise ValueError('invalid bounded follow')
    os.mkdir(directory);readers=[];bindings=[];source=None;expected=start;captured=[]
    try:
        for path in members:
            reader=open(path,'rb',buffering=0);readers.append(reader);status=os.fstat(reader.fileno())
            if not stat.S_ISREG(status.st_mode) or status.st_size<1536 or status.st_size%512:raise RuntimeError('CAPTURE_MEMBER_LAYOUT_INVALID')
            reader.seek(512);header=reader.read(512);generation=identity(header)
            key={k:v for k,v in generation.items() if k!='sequence'}
            if source is None:source=key
            if key!=source or not checksum(header) or generation['thread']<1 or generation['compatibility'] not in (0x13000000,0x15000000):raise RuntimeError('CAPTURE_MEMBER_SOURCE_MISMATCH')
            bindings.append({'path':path,'device':status.st_dev,'inode':status.st_ino,'bytes':status.st_size})
        durable_json(directory,'source.json',{'source':source,'start_sequence':start,'members':bindings})
        print(json.dumps({'ready':True,'start_sequence':start,'members':len(members),'capture_complete':False}),flush=True)
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
                journal_name='sequence-'+str(expected)+'.oracap';digest=hashlib.sha256()
                with open(os.path.join(directory,journal_name),'xb') as journal:
                    for block in range(nab):
                        raw=second[block*512:(block+1)*512]
                        encoded=json.dumps({'sample':sample,'observed_unix_seconds':time.time(),'source':generation,'block':block,'sha256':hashlib.sha256(raw).hexdigest(),'state':'OBSERVED_STABLE'},sort_keys=True,separators=(',',':')).encode()
                        frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+raw+hashlib.sha256(encoded+raw).digest();journal.write(frame);digest.update(frame)
                    journal.flush();os.fsync(journal.fileno())
                receipt={'sequence':expected,'journal':journal_name,'sha256':digest.hexdigest(),'blocks':nab,'source_path':binding['path'],'first_scn':scn(header,180),'next_scn':scn(header,192),'raw_closed_header_sha256':hashlib.sha256(header).hexdigest(),'capture_complete':False,'consumer_ack':False}
                if captured and captured[-1]['next_scn']!=receipt['first_scn']:raise RuntimeError('CAPTURE_CLOSED_SCN_GAP')
                captured.append(receipt);durable_json(directory,'receipt.json',{'start_sequence':start,'next_sequence':expected+1,'captured':captured,'capture_complete':False})
                print(json.dumps({'captured_sequence':expected,'blocks':nab,'next_sequence':expected+1,'capture_complete':False}),flush=True);expected+=1
            if closed or min(headers)>expected:raise RuntimeError('CAPTURE_MISSING_GENERATION')
            if sample+1<polls:time.sleep(interval)
        print(json.dumps({'terminal':True,'captured_generations':len(captured),'next_sequence':expected,'capture_complete':False,'consumer_ack':False,'cdc_events':0}),flush=True)
    finally:
        for reader in readers:reader.close()

if __name__=='__main__':follow(sys.argv[1],int(sys.argv[2]),int(sys.argv[3]),float(sys.argv[4]),sys.argv[5:])
