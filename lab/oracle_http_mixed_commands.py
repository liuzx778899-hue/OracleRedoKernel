"""Owned HTTP mixed fixture commands; credentials go only to private files or stdin."""
import json,subprocess,sys,shutil
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-http-mixed-20261005';S='oraredo-19c-lab-20261004';APP='ork-oracle-http-mixed-20261005'
def record(name,args,body=None):
    assert not (O/(name+'.rc.txt')).exists(),name
    (O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body,capture_output=True,timeout=90)
    for suffix,data in [('.stdout.txt',p.stdout),('.stderr.txt',p.stderr)]: (O/(name+suffix)).write_bytes(data)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout,(name,p.returncode)
    return p.stdout.decode()
def sql(name,text):
    body='whenever sqlerror exit failure rollback\nset pages 0 feedback off heading off lines 32767 long 200000 longchunksize 200000 trimspool on\nalter session set container=ORKPDB;\n'+text+'\nexit success\n';(O/(name+'.sql')).write_text(body)
    return record(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
def http(name,path,data=None):
    remote="""import json,urllib.request
base='http://127.0.0.1:37216/api/v1'
def request(path,data=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 with urllib.request.urlopen(urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=h),timeout=60) as r:return json.loads(r.read())
l=request('/auth/login',LOGIN);assert l['code']==0
o=request(PATH,DATA,l['data']['token']);print(json.dumps(o));assert o['code']==0
""".replace('LOGIN',repr(json.loads((O/'admin.secret.json').read_text()))).replace('PATH',repr(path)).replace('DATA',repr(data))
    return json.loads(record(name,['docker','exec','-i',S,'python3','-'],remote.encode()))
def owned(name):
    p=json.loads(subprocess.check_output(['docker','inspect',name]))[0]
    assert p['Config']['Labels'].get('owner')=='OracleRedoKernel' and p['Config']['Labels'].get('scope') in ['oracle-http-mixed','oracle-owned-row-lock']
    return p
if __name__=='__main__':
    action=sys.argv[1];name=sys.argv[2]
    if action=='setup':
        a=json.loads((O/'approved-binding.json').read_text());s=json.dumps(a,separators=(',',':'));chunks=[s[i:i+2000] for i in range(0,len(s),2000)];expr='||\n'.join("to_clob('"+c.replace("'","''")+"')" for c in chunks)
        shas='\n'.join(Path(p).name+' '+c['journalSha256'] for p,c in zip(a['capturePaths'],a['evidence']['captures']))
        text="insert into ORKHTTP_20261005.SH_ORACLE_INPUT_BINDING values(7001,'oracle-http-SMIX','http-SMIX',1,1,'http-consumer-SMIX','"+a['evidence']['schemaSha256']+"','APPROVED_BOUNDED',"+expr+",systimestamp,'owned-http-fixture');\n"
        text+="insert into ORKHTTP_20261005.SH_CAP_WINDOW(ID,SOURCE_ID,ENGINE_TYPE,WINDOW_KEY,ISSUE_SEQ_START,ISSUE_SEQ_END,HEAD_PATH,FROZEN,FILE_SHA256,TOTAL_SIZE,DROPPED_SEGMENTS,STATUS,REGISTERED_AT) values(7002,'oracle-http-SMIX','oracle','http-SMIX',1,1,'"+a['capturePaths'][0]+"',1,'"+shas+"',"+str(sum(c['journalBytes'] for c in a['evidence']['captures']))+",0,'PENDING',systimestamp);\ncommit;"
        sql(name,text)
    elif action=='http':
        print(json.dumps(http(name,sys.argv[3],json.loads(sys.argv[4]) if len(sys.argv)>4 else None)))
    elif action=='observe':
        print(sql(name,"select 'TARGET|'||(select count(*) from ORKTGT_CMT_20261005.HTMIX_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.HTMIX_COMMITS) from dual;\nselect 'WINDOW|'||STATUS from ORKHTTP_20261005.SH_CAP_WINDOW;\nselect 'TASK|'||to_char(ID,'FM99999999999999999999')||'|'||STATE||'|'||nvl(CHECKPOINT_JSON,'NULL') from ORKHTTP_20261005.SH_TASK;\nselect 'LEDGER|'||count(*) from ORKHTTP_20261005.SH_EVENT_LEDGER;\nselect 'BLOCK|'||s.sid||'|'||s.username||'|'||s.blocking_session||'|'||s.event||'|'||q.sql_text from v$session s left join v$sql q on s.sql_id=q.sql_id and s.sql_child_number=q.child_number where s.username in ('ORKHTTP_20261005','ORKTGT_CMT_20261005') and s.blocking_session is not null;"))
    elif action=='lock':
        mode=sys.argv[3];target=mode=='target';secret=R/'lab/out/oracle-target-20261005/target.secret' if target else O/'control.secret';
        if secret != O/(mode+'.secret'):shutil.copyfile(secret,O/(mode+'.secret'))
        shutil.copyfile(R/'lab/out/oracle-bounded-runtime-20261005/runtime-libs/ojdbc11-21.9.0.0.jar',O/'ojdbc.jar')
        args=['docker','run','-d','--name',name,'--label','owner=OracleRedoKernel','--label','scope=oracle-owned-row-lock','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence,readonly','--entrypoint','java','flink:1.18-java17','-cp','/evidence/lock-classes:/evidence/ojdbc.jar','OracleOwnedRowLocker','/evidence/'+mode+'.secret','ORKTGT_CMT_20261005.HTMIX_ROWS' if target else 'ORKHTTP_20261005.SH_CAP_WINDOW','1' if target else '7002'];record('start-'+name,args)
    elif action=='kill':
        p=owned(name);assert p['State']['Running'];(O/(name+'-epoch-'+str(p['RestartCount'])+'.json')).write_text(json.dumps({k:p['State'][k] for k in ['Pid','StartedAt','Status']}))
        print(record(sys.argv[3],['docker','kill','--signal','KILL',name]));state=owned(name)['State'];assert state['ExitCode']==137; (O/(sys.argv[3]+'.state.json')).write_text(json.dumps(state))
    elif action=='start':
        p=owned(name);assert not p['State']['Running'];print(record(sys.argv[3],['docker','start',name]))
