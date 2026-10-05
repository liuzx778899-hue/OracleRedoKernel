"""Only derived comparator counterexamples; never modify accepted SQL evidence or consumer state."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;F=R/'lab/out/empty-dml-capture-20261005';O=R/'lab/out/oracle-empty-target-20261005';C=O/'consumer';proof=json.loads((O/'proof.json').read_text());ack=C/(proof['target_batch']+'.ack');cp=C/'checkpoint';sql=O/'final-state.stdout.txt'
def run(n,files,rc):
    assert not (O/(n+'.rc.txt')).exists();args=[sys.executable,R/'lab/compare_empty_target.py',F]+files;(O/(n+'.command.json')).write_text(json.dumps(list(map(str,args))));p=subprocess.run(list(map(str,args)),capture_output=True,timeout=60)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc and (rc==0 or not p.stdout)
run('independent-empty-target',[sql,cp,ack],0)
p=O/'wrong-empty-count.sql-output';p.write_text(sql.read_text().replace('|0\n','|1\n'));run('wrong-empty-count',[p,cp,ack],1)
parts=cp.read_text().splitlines();parts[2]='FRONTIER|'+'0'*64;body='\n'.join(parts[:-1])+'\n';p=O/'wrong-empty-frontier.checkpoint';p.write_text(body+'SHA256|'+hashlib.sha256(body.encode()).hexdigest()+'\n');run('wrong-empty-frontier',[sql,p,ack],1)
p=O/'wrong-empty-ack';p.write_bytes(ack.read_bytes().replace(b'|0\n',b'|1\n'));run('wrong-empty-ack',[sql,cp,p],1)
(O/'comparator-proof.json').write_text(json.dumps({'positive':1,'refusals':3,'accepted_state_modified':False},indent=2));print((O/'comparator-proof.json').read_text())
