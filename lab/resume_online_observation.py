"""Linux fixed-member journal resume. No group follow, record cursor or consumer ACK."""
import fcntl,hashlib,json,math,os,struct,sys,time

STATES=('OBSERVED_STABLE','OBSERVED_CHANGED','GENERATION_CHANGED','CHECKSUM_INVALID','BLOCK_INDEX_INVALID')
FIELDS=(('sequence','<I',8),('compatibility','<I',20),('dbid','<I',24),('activation','<I',52),('thread','<H',176),('resetlogs','<I',160))

def identity(block):
    if len(block)!=512 or struct.unpack_from('<H',block)[0]!=0x2201:raise RuntimeError('CAPTURE_SOURCE_HEADER_INVALID')
    return {name:struct.unpack_from(fmt,block,offset)[0] for name,fmt,offset in FIELDS}

def checksum(block):
    value=0
    for word in struct.unpack('<256H',block):value^=word
    return value==0

def durable_json(directory,name,value):
    temporary=os.path.join(directory,name+'.pending')
    with open(temporary,'w') as out:
        json.dump(value,out,sort_keys=True);out.flush();os.fsync(out.fileno())
    os.replace(temporary,os.path.join(directory,name))
    descriptor=os.open(directory,os.O_RDONLY|os.O_DIRECTORY)
    try:os.fsync(descriptor)
    finally:os.close(descriptor)

def audit(directory):
    with open(os.path.join(directory,'source.json')) as stream:binding=json.load(stream)
    with open(os.path.join(directory,'receipt.json')) as stream:receipt=json.load(stream)
    with open(os.path.join(directory,'observations.oracap'),'rb') as stream:data=stream.read()
    if set(receipt)!={'bytes','sha256','observations'} or any(type(receipt[k]) is not int or receipt[k]<0 for k in ('bytes','observations')):
        raise RuntimeError('CAPTURE_RECEIPT_INVALID')
    if receipt['bytes']>len(data) or hashlib.sha256(data[:receipt['bytes']]).hexdigest()!=receipt['sha256']:
        raise RuntimeError('CAPTURE_DURABLE_PREFIX_MISMATCH')
    offset=0;seen=set();count=0;states={};samples=[];receipt_found=receipt['bytes']==0 and receipt['observations']==0
    while offset<len(data):
        if len(data)-offset<16 or data[offset:offset+8]!=b'ORACAP01':raise RuntimeError('CAPTURE_TRUNCATED_OR_INVALID_HEADER')
        m,n=struct.unpack_from('<II',data,offset+8);offset+=16
        if m<2 or m>4096 or n!=512 or len(data)-offset<m+n+32:raise RuntimeError('CAPTURE_TRUNCATED_OR_INVALID_RECORD')
        encoded=data[offset:offset+m];block=data[offset+m:offset+m+n];digest=data[offset+m+n:offset+m+n+32];offset+=m+n+32
        if hashlib.sha256(encoded+block).digest()!=digest:raise RuntimeError('CAPTURE_ENVELOPE_DIGEST_MISMATCH')
        meta=json.loads(encoded)
        if set(meta)!={'sample','observed_unix_seconds','source','block','sha256','state'} or type(meta['block']) is not int or not 0<=meta['block']<binding['bounded_blocks'] or type(meta['sample']) is not int or meta['sample']<0 or type(meta['observed_unix_seconds']) not in (int,float) or not math.isfinite(meta['observed_unix_seconds']) or meta['state'] not in STATES:
            raise RuntimeError('CAPTURE_INVALID_OBSERVATION_METADATA')
        if meta['source']!=binding['generation']:raise RuntimeError('CAPTURE_SOURCE_GENERATION_MISMATCH')
        if hashlib.sha256(block).hexdigest()!=meta['sha256']:raise RuntimeError('CAPTURE_BLOCK_DIGEST_MISMATCH')
        if samples and meta['sample']<samples[-1]:raise RuntimeError('CAPTURE_SAMPLE_ORDER_MISMATCH')
        if meta['state']=='OBSERVED_STABLE':
            if not checksum(block):raise RuntimeError('CAPTURE_STABLE_BLOCK_CHECKSUM_MISMATCH')
            if meta['block']>0 and (struct.unpack_from('<H',block)[0]!=0x2201 or struct.unpack_from('<I',block,4)[0]!=meta['block'] or struct.unpack_from('<I',block,8)[0]!=meta['source']['sequence']):raise RuntimeError('CAPTURE_STABLE_BLOCK_IDENTITY_MISMATCH')
            if meta['block']==1 and identity(block)!=binding['generation']:raise RuntimeError('CAPTURE_SOURCE_HEADER_MISMATCH')
        key=(meta['block'],meta['sha256'],meta['state'])
        if key in seen:raise RuntimeError('CAPTURE_DUPLICATE_OBSERVATION')
        seen.add(key);count+=1;samples.append(meta['sample']);states[meta['state']]=states.get(meta['state'],0)+1
        if offset==receipt['bytes']:
            if count!=receipt['observations']:raise RuntimeError('CAPTURE_RECEIPT_COUNT_MISMATCH')
            receipt_found=True
    if not receipt_found:raise RuntimeError('CAPTURE_RECEIPT_NOT_FRAME_BOUNDARY')
    return binding,data,seen,count,states,max(samples,default=-1)+1

def observe(source,directory,blocks,polls,interval,resume):
    if blocks<3 or polls<1 or not math.isfinite(interval) or interval<0:raise ValueError('invalid bounded observation')
    if not resume:os.mkdir(directory)
    with open(os.path.join(directory,'writer.lock'),'a') as lock:
        fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        with open(source,'rb',buffering=0) as reader:
            file_id=os.fstat(reader.fileno());reader.seek(512);generation=identity(reader.read(512))
            binding={'path':source,'device':file_id.st_dev,'inode':file_id.st_ino,'bytes':file_id.st_size,'generation':generation,'bounded_blocks':blocks}
            if resume:
                previous,data,seen,count,states,sample=audit(directory)
                if previous!=binding:raise RuntimeError('CAPTURE_RESUME_SOURCE_BINDING_MISMATCH')
            else:
                durable_json(directory,'source.json',binding)
                with open(os.path.join(directory,'observations.oracap'),'xb') as out:out.flush();os.fsync(out.fileno())
                data=b'';seen=set();count=0;states={};sample=0
                durable_json(directory,'receipt.json',{'bytes':0,'sha256':hashlib.sha256(data).hexdigest(),'observations':0})
            recovered=len(data)
            with open(os.path.join(directory,'observations.oracap'),'ab') as journal:
                digest=hashlib.sha256(data)
                for current in range(sample,sample+polls):
                    reader.seek(512);before=identity(reader.read(512))
                    reader.seek(0);first=reader.read(blocks*512)
                    reader.seek(0);second=reader.read(blocks*512)
                    reader.seek(512);after=identity(reader.read(512));now=os.fstat(reader.fileno());named=os.stat(source)
                    if (now.st_dev,now.st_ino,now.st_size)!=(file_id.st_dev,file_id.st_ino,file_id.st_size) or (named.st_dev,named.st_ino)!=(now.st_dev,now.st_ino):raise RuntimeError('CAPTURE_SOURCE_FILE_CHANGED')
                    if before!=generation or after!=generation:raise RuntimeError('CAPTURE_SOURCE_GENERATION_CHANGED')
                    if len(first)!=blocks*512 or len(second)!=blocks*512:raise RuntimeError('CAPTURE_SHORT_READ')
                    for index in range(blocks):
                        a=first[index*512:(index+1)*512];b=second[index*512:(index+1)*512]
                        if index>0 and (struct.unpack_from('<H',b)[0]!=0x2201 or struct.unpack_from('<I',b,8)[0]!=generation['sequence']):continue
                        state='OBSERVED_CHANGED' if a!=b else 'CHECKSUM_INVALID' if not checksum(b) else 'BLOCK_INDEX_INVALID' if index>0 and struct.unpack_from('<I',b,4)[0]!=index else 'OBSERVED_STABLE'
                        sha=hashlib.sha256(b).hexdigest();key=(index,sha,state)
                        if key in seen:continue
                        encoded=json.dumps({'sample':current,'observed_unix_seconds':time.time(),'source':generation,'block':index,'sha256':sha,'state':state},sort_keys=True,separators=(',',':')).encode()
                        frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+b+hashlib.sha256(encoded+b).digest()
                        journal.write(frame);digest.update(frame);seen.add(key);count+=1;states[state]=states.get(state,0)+1
                    journal.flush();os.fsync(journal.fileno())
                    durable_json(directory,'receipt.json',{'bytes':journal.tell(),'sha256':digest.hexdigest(),'observations':count})
                    print(json.dumps({'sample':current,'durable_observations':count,'sequence':generation['sequence'],'resumed':resume,'recovered_bytes':recovered,'capture_complete':False,'cdc_events':0}),flush=True)
                    if current+1<sample+polls:time.sleep(interval)

if __name__=='__main__':
    if len(sys.argv)==3 and sys.argv[1]=='--verify':
        binding,data,seen,count,states,sample=audit(sys.argv[2])
        print(json.dumps({'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'observations':count,'next_observed_sample':sample,'states':states,'capture_complete':False,'consumer_ack':False}))
    else:
        if len(sys.argv) not in (6,7) or (len(sys.argv)==7 and sys.argv[6]!='--resume'):raise ValueError('source directory blocks polls interval [--resume] or --verify directory')
        observe(sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]),float(sys.argv[5]),sys.argv[6:]==['--resume'])
