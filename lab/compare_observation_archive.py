"""Verify observation envelopes and archived block byte coverage. Not an online completeness certificate."""
import hashlib,json,struct,sys
from pathlib import Path
data=Path(sys.argv[1]).read_bytes();archive=Path(sys.argv[2]).read_bytes();offset=0;stable={};observations=0
sequence=struct.unpack_from('<I',archive,520)[0]
while offset<len(data):
    if len(data)-offset<16 or data[offset:offset+8]!=b'ORACAP01':raise RuntimeError('CAPTURE_TRUNCATED_OR_INVALID_HEADER')
    m,n=struct.unpack_from('<II',data,offset+8);offset+=16
    if m>4096 or n!=512 or len(data)-offset<m+n+32:raise RuntimeError('CAPTURE_TRUNCATED_OR_INVALID_RECORD')
    encoded=data[offset:offset+m];block=data[offset+m:offset+m+n];digest=data[offset+m+n:offset+m+n+32];offset+=m+n+32
    if hashlib.sha256(encoded+block).digest()!=digest:raise RuntimeError('CAPTURE_ENVELOPE_DIGEST_MISMATCH')
    metadata=json.loads(encoded)
    if not isinstance(metadata['block'],int) or metadata['block']<0 or metadata['state'] not in ('OBSERVED_STABLE','OBSERVED_CHANGED','GENERATION_CHANGED','CHECKSUM_INVALID','BLOCK_INDEX_INVALID'):
        raise RuntimeError('CAPTURE_INVALID_OBSERVATION_METADATA')
    if hashlib.sha256(block).hexdigest()!=metadata['sha256']:raise RuntimeError('CAPTURE_BLOCK_DIGEST_MISMATCH')
    source=metadata['source'];observations+=1
    expected={'dbid':struct.unpack_from('<I',archive,536)[0],'activation':struct.unpack_from('<I',archive,564)[0],
        'resetlogs':struct.unpack_from('<I',archive,672)[0],'thread':struct.unpack_from('<H',archive,688)[0],
        'compatibility':struct.unpack_from('<I',archive,532)[0],'sequence':sequence}
    if metadata['state']=='OBSERVED_STABLE':
        checksum=0
        for word in struct.unpack('<256H',block):checksum^=word
        if checksum:raise RuntimeError('CAPTURE_STABLE_BLOCK_CHECKSUM_MISMATCH')
        if metadata['block']>0 and (struct.unpack_from('<H',block)[0]!=0x2201 or struct.unpack_from('<I',block,4)[0]!=metadata['block'] or struct.unpack_from('<I',block,8)[0]!=source['sequence']):raise RuntimeError('CAPTURE_STABLE_BLOCK_IDENTITY_MISMATCH')
        if source==expected:stable.setdefault(metadata['block'],set()).add(block)
missing=[];different=[]
# Closed archive headers can differ from active-file headers. Never normalize them into equality.
for index in range(2,len(archive)//512):
    wanted=archive[index*512:(index+1)*512]
    if index not in stable:missing.append(index)
    elif wanted not in stable[index]:different.append(index)
print(json.dumps({'sequence':sequence,'observations_verified':observations,'archive_data_blocks':len(archive)//512-2,
    'missing_blocks':missing,'different_blocks':different,'header_equality_claimed':False,
    'capture_complete':False,'scope':'finite archived data-block byte coverage only; source online headers retained, not normalized'},indent=2))
sys.exit(bool(missing or different))
