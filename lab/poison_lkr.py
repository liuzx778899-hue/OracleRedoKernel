"""Resealed real LKR pair and late row before-image counterexamples; no source actions."""
import hashlib,json,struct,sys
from pathlib import Path
r=Path(__file__).resolve().parent.parent;raw=(r/'lab/out/oracle19-blocking-rows-r2-20261005/archive-1-39.dbf').read_bytes();assert hashlib.sha256(raw).hexdigest()=='337db6af29d6798df632f55d0f54da01501a36657387d3a9760eb2260ff3fe89'
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=False)
t=(r/'lab/poison_rollback_only.py').read_text();ns={'raw':raw,'struct':struct};exec(t[t.index('def vectors'):t.index('regular=vectors')],ns);vs=ns['vectors'](6,16);f=vs[0];u=vs[4];late=vs[5];assert f[0]==(11,4) and u[0]==late[0]==(5,1)
assert bytes(raw[i] for i in late[2][5])==b'blocking-a-final'
cases={'wrong_lock_to':[(f[2][1][19],0)],'wrong_inverse_lock':[(u[2][3][19],1)],'wrong_lock_slot':[(f[2][1][16],1)],'missing_lock_forward':[(f[1][1],7)],'unsupported_lock_xtype':[(f[2][1][11],2)],'late_before':[(late[2][5][0],120)]};result=[]
for name,changes in cases.items():
 bad=bytearray(raw)
 for at,value in changes:bad[at]=value
 for block in {at//512 for at,_ in changes}:
  start=block*512;struct.pack_into('<H',bad,start+14,0);checksum=0
  for word in struct.unpack('<256H',bad[start:start+512]):checksum^=word
  struct.pack_into('<H',bad,start+14,checksum)
 path=out/(name+'.dbf');path.write_bytes(bad);result.append({'case':name,'changes':changes,'sha256':hashlib.sha256(bad).hexdigest()})
(out/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
