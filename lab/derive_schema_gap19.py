"""DERIVED marker erasure/unknown-opcode evidence. No source or target actions."""
import hashlib,json,struct,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;OLD=R/'lab/out/schema-barrier-20261005';O=R/'lab/out/schema-coverage-gap-20261005';O.mkdir(exist_ok=False);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(OLD/'fixed-kernel.jar')=='9275ab9062413ae7fa88ac3a147337fbe1cc1d9b1c7add834f374c401a9570c1'
(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes());(O/'execution-pin.json').write_text(json.dumps(dict(script_sha256=sha(Path(__file__)),kernel_sha256=sha(OLD/'fixed-kernel.jar'),derived_only=True,source_actions=0,target_actions=0)))
def run(name,args,expected=None):
 (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),capture_output=True,timeout=120)
 for suffix,value in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(name+'.'+suffix+'.txt')).write_bytes(value)
 (O/(name+'.rc.txt')).write_text(str(p.returncode));assert expected is None or p.returncode==expected,(name,p.stderr[-1200:]);return p
classes=O/'classes';classes.mkdir();cp=str(OLD/'fixed-kernel.jar')+';'+str(classes)
run('compile-structure',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/CapturedRecordsProbe.java'],0)
inputs={}
for seq,block,offset in [(56,118,96),(57,9,208)]:
 original=(OLD/'capture'/('sequence-'+str(seq)+'.oracap')).read_bytes();frames=[];blocks=[];at=0
 while at<len(original):
  m,n=struct.unpack_from('<II',original,at+8);assert n==512
  frames.append(json.loads(original[at+16:at+16+m]));blocks.append(bytearray(original[at+16+m:at+16+m+n]));at+=16+m+n+32
 virtual=b''.join(bytes(b[16:]) for b in blocks[2:]);start=(block-2)*496+offset-16;length=struct.unpack_from('<I',virtual,start)[0];body=virtual[start:start+length];header=68 if body[4]&4 else 24;assert body[header:header+2]==bytes([24,1])
 for case,opcode in [('erased',(0,0)),('unknown-family',(24,0))]:
  changed=[bytearray(b) for b in blocks];positions=[]
  for step,value in enumerate(opcode):
   p=start+header+step;bi=2+p//496;bo=16+p%496;positions.append(dict(block=bi,offset=bo,old=changed[bi][bo],new=value));changed[bi][bo]=value
  data=bytearray()
  for meta,payload in zip(frames,changed):
   struct.pack_into('<H',payload,14,0);checksum=0
   for word in struct.unpack('<256H',payload):checksum^=word
   struct.pack_into('<H',payload,14,checksum);meta=dict(meta);meta['sha256']=hashlib.sha256(payload).hexdigest();encoded=json.dumps(meta,sort_keys=True,separators=(',',':')).encode();data+=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest()
  path=O/(case+'-'+str(seq)+'.oracap');path.write_bytes(data);inputs.setdefault(case,[]).extend([str(path),sha(path)])
  (O/(case+'-'+str(seq)+'.mutation.json')).write_text(json.dumps(dict(derived=True,original_sha256=hashlib.sha256(original).hexdigest(),result_sha256=sha(path),record=dict(sequence=seq,block=block,offset=offset,length=length),changes=positions,other_raw_bytes_unchanged_except_block_checksums=True),indent=2))
  assert all(all(a==b for i,(a,b) in enumerate(zip(originalblock,modified)) if i not in [14,15]+[p['offset'] for p in positions if p['block']==bi]) for bi,(originalblock,modified) in enumerate(zip(blocks,changed)))
  run(case+'-'+str(seq)+'-structure',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',cp,'CapturedRecordsProbe',path,sha(path),'records'],0)
actual=json.loads((OLD/'old-schema-batch.command.json').read_text());i=actual.index('-cp')+1;actual[i]=str(OLD/'fixed-kernel.jar')+';'+actual[i];split=actual.index('CapturedBatchProbe')+4;results=[]
for case,paths in inputs.items():
 p=run(case+'-semantic',actual[:split]+paths);events=sum(line.startswith(b'EVENT|') for line in p.stdout.splitlines());results.append(dict(case=case,rc=p.returncode,events=events,stdout_bytes=len(p.stdout),structural_verified=True,accepted_semantically=p.returncode==0))
(O/'gap-proof.json').write_text(json.dumps(dict(derived_only=True,not_real_source_history=True,results=results,raw_dictionary_and_DML_bytes_preserved=True,source_actions=0,target_actions=0,historical_coverage_accepted=False),indent=2));print(json.dumps(results))
