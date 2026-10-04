"""Only independent read-only target comparison; accepted kill/DML rounds never rerun."""
import json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OUT=ROOT/'lab/out/oracle-target-evidence-20261005'
GOOD=ROOT/'lab/out/oracle-target-20261005';OLD=ROOT/'lab/out/closed-follow-20261005'
REF=ROOT/'lab/out/captured-batch-20261005/actual.stdout.txt';SOURCE='oraredo-19c-lab-20261004'
OUT.mkdir(parents=True,exist_ok=False)


def run(name,args,rc=0,input_data=None):
    args=list(map(str,args));(OUT/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=input_data,capture_output=True,timeout=60)
    for ext,data in [('stdout',p.stdout),('stderr',p.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==rc,name
    return p


for prefix in ['PRE','POST','ACKD']:
    t='ORKTGT_CMT_20261005.'+prefix
    text=(GOOD/(prefix+'-final.sql.redacted')).read_bytes()
    run(prefix+'-fresh-read',['docker','exec','-i',SOURCE,'sqlplus','-s','/','as','sysdba'],input_data=text)
    result=run(prefix+'-compare',[sys.executable,ROOT/'lab/compare_oracle_target19.py',OUT/(prefix+'-fresh-read.stdout.txt'),prefix,OLD,REF,GOOD/(prefix+'.ack')])
    assert json.loads(result.stdout)['mismatches']==0
run('normal-committed-snapshot-compare',[sys.executable,ROOT/'lab/compare_oracle_target19.py',GOOD/'normal-state.stdout.txt','NORM',OLD,REF,GOOD/'NORM.ack'])
run('actual-poisoned-target-compare',[sys.executable,ROOT/'lab/compare_oracle_target19.py',GOOD/'target-proof-conflict-state.stdout.txt','NORM',OLD,REF,GOOD/'NORM.ack'],1)
healthy=(OUT/'POST-fresh-read.stdout.txt').read_text().splitlines()
for name in ['missing-member','wrong-after','wrong-ack']:
    changed=healthy.copy();ack=OUT/(name+'.ack');ack.write_bytes((GOOD/'POST.ack').read_bytes())
    if name=='missing-member':
        index=next(i for i,line in enumerate(changed) if line.startswith('MEMBER|'));del changed[index]
    elif name=='wrong-after':
        index=next(i for i,line in enumerate(changed) if line.startswith('APPLIED|'))
        fields=changed[index].split('|');fields[5]='ID=NUMBER:11;TXT=VARCHAR2:eA;NOTE=VARCHAR2:bmV3LW5vdGU';changed[index]='|'.join(fields)
    else:ack.write_bytes(ack.read_bytes().replace(b'|3\n',b'|4\n'))
    path=OUT/(name+'.truth.txt');path.write_text('\n'.join(changed)+'\n')
    run(name,[sys.executable,ROOT/'lab/compare_oracle_target19.py',path,'POST',OLD,REF,ack],1)
run('lgwr-still-running',['docker','exec',SOURCE,'cat','/proc/5601/comm'])
assert (OUT/'lgwr-still-running.stdout.txt').read_text().strip()=='ora_lgwr_ork19'
run('no-live-owned-client',['docker','ps','--filter','label=scope=oracle-target-commit','--format','{{.Names}}'])
assert not (OUT/'no-live-owned-client.stdout.txt').read_text().strip()
proof={'fresh_real_oracle_target_comparisons':3,'normal_committed_snapshot_comparison':1,'original_source_commits':3,
       'independent_comparator_refusals':4,'same_lgwr_pid':5601,'source_dml':0,'target_dml':0,'client_kill_rounds_rerun':False}
(OUT/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
