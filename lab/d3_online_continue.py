"""Resume the same online proof before collector start; reuse acquisition and target, no replay."""
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/d3-online-product-20261010'
assert (N/'runtime-r5.rc').read_text().strip()=='1' and (N/'source-0.started').exists() and not (N/'source-1.started').exists()
script=(R/'lab/d3_online_product.py').read_text()
prefix=script.split('try:\n assert HELPER')[0]
prefix=prefix.replace("'executed-runtime.py'","'executed-runtime-r6.py'").replace("'online-product-20261010'","'online-product-20261010-r6'")
prefix='\n'.join(line for line in prefix.splitlines() if not line.startswith("run('probe-compile'"))
prefix=prefix.replace("(N/name).mkdir()","(N/name).mkdir(exist_ok=True)")
exec(prefix)
prior_run=run
def run(name,args,data=None,private=False):return prior_run('r6-'+name,args,data,private)
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
acquired=json.loads((N/'acquire.stdout').read_text())['data'];ACQ=int(acquired['id']);baseline=N/'snapshots'/f'{ACQ}.snapshot'
assert sha(baseline)==acquired['snapshotSha256'];plan=json.loads(props['SH_ORACLE_RAW_PLAN_PLAN_JSON']);first=plan['firstSequence']
config=(N/'collector.ini').read_text();members=[line.split('=',1)[1] for line in config.splitlines() if line.startswith(('member_1=','member_2=','member_3='))]
token=(N/'machine.secret.env').read_text().strip().split('=',1)[1];secrets.append(token.encode())
prior_projection=projection
def projection(label,inputs):
 if label!='first-projection':return prior_projection(label,inputs)
 assert (N/'frozen-first-projection.rc').read_text().strip()=='0'
 p=json.loads((N/'frozen-first-projection.log').read_text());assert p['evidence']['captures'][0]['journalSha256']==sha(inputs[0]);return p
try:
 assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
 assert run('source-owner',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
 run('source-start',['docker','start',S]);started=True
 for attempt in range(30):
  try:
   pre=sql('ready-'+str(attempt),f"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'SEED|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where NOTE='anchor';\nselect 'TARGET|'||count(*) from {T}_ROWS where NOTE='anchor';")
   if 'SEED|19' in pre and 'TARGET|20' in pre:break
  except AssertionError:
   if attempt==29:raise
  time.sleep(5)
 assert f'CURRENT|{first+1}' in pre and 'LOGGING|NO|NO' in pre and 'SEED|19' in pre and 'TARGET|20' in pre
 start_app('online-app')
 (N/'run-collector.sh').write_bytes((N/'run-collector.sh').read_bytes().replace(b'\r\n',b'\n'))
 run('copy-script',['docker','cp',N/'run-collector.sh',S+':'+REMOTE+'/run-collector.sh'])
 (N/'machine.secret.env').write_bytes(('SH_AGENT_MACHINE_TOKEN='+token+'\n').encode())
 run('copy-token',['docker','cp',N/'machine.secret.env',S+':'+REMOTE+'/machine.secret.env'])
 run('collector-owner',['docker','exec','--user','0',S,'chown','oracle:oinstall',*[REMOTE+'/'+name for name in ['sh_agent','collector.ini','machine.secret.env','run-collector.sh']]])
 run('collector-permissions',['docker','exec',S,'chmod','700',REMOTE+'/sh_agent',REMOTE+'/run-collector.sh',REMOTE+'/machine.secret.env'])
 for suffix in ['stdout','stderr']:(N/('collector.'+suffix)).rename(N/('collector-r5.'+suffix))
 # Execute the unchanged once-only source/normal proof and its resource cleanup.
 tail=script.split(" cout=(N/'collector.stdout')",1)[1].split('\nfinally:',1)[0]
 start=tail.index(' # Before any new business DML');end=tail.index(' proofs=[];raws=[]');tail=tail[:start]+tail[end:]
 begin=tail.index("  prior='anchor'");end=tail.index("  key='raw-stream-",begin)
 actions=tail[begin:end];tail=tail[:begin]+"  if ordinal==0:assert (N/'source-0.started').exists()\n  else:\n"+''.join(' '+line+'\n' for line in actions.splitlines())+tail[end:]
 exec("cout=(N/'collector.stdout')"+tail.replace('\n ', '\n'))
finally:
 cleanup=script.split('\nfinally:',1)[1]
 exec('\n'.join(line[1:] if line.startswith(' ') else line for line in cleanup.splitlines()))
