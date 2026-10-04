"""Bounded fixed-member observation journal. No complete-record cursor, CDC, resume or ACK."""
import hashlib,json,os,struct,sys,time

source,directory=sys.argv[1:3]
blocks,polls,interval=int(sys.argv[3]),int(sys.argv[4]),float(sys.argv[5])
if blocks<3 or polls<1 or interval<0:raise ValueError('invalid bounded observation')
os.mkdir(directory)  # Never overwrite an earlier capture.
def identity(b):
    if len(b)!=512 or struct.unpack_from('<H',b)[0]!=0x2201:raise RuntimeError('unreadable log header')
    return {name:struct.unpack_from(fmt,b,offset)[0] for name,fmt,offset in [
        ('sequence','<I',8),('compatibility','<I',20),('dbid','<I',24),('activation','<I',52),
        ('thread','<H',176),('resetlogs','<I',160)]}
def checksum(b):
    result=0
    for word in struct.unpack('<256H',b):result^=word
    return result==0
seen=set();records=0;states={};generations=set()
with open(source,'rb',buffering=0) as reader,open(os.path.join(directory,'observations.oracap'),'xb') as journal:
    file_id=os.fstat(reader.fileno());source_binding={'path':source,'device':file_id.st_dev,'inode':file_id.st_ino,'bytes':file_id.st_size}
    with open(os.path.join(directory,'source.json'),'x') as f:json.dump(source_binding,f,sort_keys=True);f.flush();os.fsync(f.fileno())
    for sample in range(polls):
        reader.seek(512);before=identity(reader.read(512))
        reader.seek(0);first=reader.read(blocks*512)
        reader.seek(0);second=reader.read(blocks*512)
        reader.seek(512);after=identity(reader.read(512));now=os.fstat(reader.fileno())
        named=os.stat(source)
        if (now.st_dev,now.st_ino,now.st_size)!=(file_id.st_dev,file_id.st_ino,file_id.st_size) or (named.st_dev,named.st_ino)!=(now.st_dev,now.st_ino):raise RuntimeError('source file identity changed')
        if len(first)!=blocks*512 or len(second)!=blocks*512:raise RuntimeError('short bounded read')
        generation=tuple(sorted(before.items()));generations.add(generation)
        for block in range(blocks):
            a=first[block*512:(block+1)*512];b=second[block*512:(block+1)*512]
            if block>0 and (struct.unpack_from('<H',b)[0]!=0x2201 or struct.unpack_from('<I',b,8)[0]!=before['sequence']):continue
            state='OBSERVED_STABLE'
            if before!=after:state='GENERATION_CHANGED'
            elif a!=b:state='OBSERVED_CHANGED'
            elif not checksum(b):state='CHECKSUM_INVALID'
            elif block>0 and struct.unpack_from('<I',b,4)[0]!=block:state='BLOCK_INDEX_INVALID'
            digest=hashlib.sha256(b).hexdigest();key=(generation,block,digest,state)
            if key in seen:continue
            metadata={'sample':sample,'observed_unix_seconds':time.time(),'source':before,'block':block,'sha256':digest,'state':state}
            encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode()
            journal.write(b'ORACAP01'+struct.pack('<II',len(encoded),len(b))+encoded+b+hashlib.sha256(encoded+b).digest())
            seen.add(key);records+=1;states[state]=states.get(state,0)+1
        journal.flush();os.fsync(journal.fileno())
        print(json.dumps({'sample':sample,'durable_observations':records,'sequence':before['sequence'],'states':states}),flush=True)
        if sample+1<polls:time.sleep(interval)
    summary={'polls':polls,'bounded_blocks':blocks,'observations':records,'states':states,'generations':len(generations),'capture_complete':False,'cdc_events':0}
    with open(os.path.join(directory,'summary.json'),'x') as f:json.dump(summary,f,sort_keys=True);f.flush();os.fsync(f.fileno())
print(json.dumps(summary),flush=True)
