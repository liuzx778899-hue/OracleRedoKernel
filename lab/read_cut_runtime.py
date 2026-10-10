"""Owned test: seal existing commits, reclaim a copied central prefix, restart without old raw. No source DML."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time,zipfile

R=Path(__file__).resolve().parent.parent
N=R/'lab/out/read-cut-20261010';O=R/'lab/out/d3-online-product-20261010'
P=R/'lab/out/streamhouse-oracle-gate-worktree';S='oraredo-19c-lab-20261004';HELPER='ork-product-runtime'
SOURCE='oracle-online-normal';U='ORKONLINECTL_20261010';PORT=37301
label=sys.argv[1];assert label.replace('-','').isalnum()
assert not (N/(label+'.executed.py')).exists(),'do not replay an executed action'
shutil.copyfile(__file__,N/(label+'.executed.py'))
props=dict(line.split('=',1) for line in (O/'runtime.secret.env').read_text().splitlines())
token=(O/'machine.secret.env').read_text().strip().split('=',1)[1]
secrets=[token.encode()]+[v.encode() for k,v in props.items() if 'PASSWORD' in k and v]
def sha(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for data in iter(lambda:f.read(1048576),b''):h.update(data)
    return h.hexdigest()
def run(name,args,data=None,timeout=180):
    name=label+'-'+name;assert not (N/(name+'.rc')).exists(),name
    p=subprocess.run(list(map(str,args)),input=data,capture_output=True,timeout=timeout)
    for suffix,content in [('stdout',p.stdout),('stderr',p.stderr)]:
        for secret in secrets:content=content.replace(secret,b'<redacted-secret>')
        (N/(name+'.'+suffix)).write_bytes(content)
    (N/(name+'.rc')).write_text(str(p.returncode));assert p.returncode==0,(name,p.returncode)
    return p.stdout.decode('utf8')
def sql(name,body):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 heading off feedback off lines 32767 long 300000 longchunksize 300000 trimspool on\nalter session set container=ORKPDB;\n'+body+'\nexit success\n'
    (N/(label+'-'+name+'.sql')).write_text(text)
    out=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],text.encode());assert 'SP2-' not in out;return out
def request(name,path,body=None,machine=False,success=True,timeout=180):
    login=json.loads((R/'lab/out/product-active-20261007/admin.secret.json').read_text())
    code="""import json,urllib.request,urllib.error
def req(path,body,headers):
 request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
 try:
  with urllib.request.urlopen(request,timeout=TIMEOUT) as response:return json.loads(response.read())
 except urllib.error.HTTPError as ex:return json.loads(ex.read())
headers={'Content-Type':'application/json'}
if MACHINE:headers['X-SH-Agent-Token']=TOKEN
else:
 auth=req('/auth/login',LOGIN,headers);assert auth['code']==0;headers['Authorization']='Bearer '+auth['data']['token']
print(json.dumps(req(PATH,BODY,headers)))
"""
    for key,value in [('BASE',f'http://127.0.0.1:{PORT}/api/v1'),('MACHINE',machine),('TOKEN',token),('LOGIN',login),('PATH',path),('BODY',body),('TIMEOUT',timeout)]:code=code.replace(key,repr(value))
    value=json.loads(run(name,['docker','exec','-i',S,'python3','-'],code.encode(),timeout=timeout+30))
    assert (value['code']==0)==success,(name,value)
    return value.get('data') if success else value
def counts(name):
    out=sql(name,f"select 'COUNTS|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_APPLIED)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_MEMBERS)||'|'||(select count(*) from ORKTGT_CMT_20261005.RONL1_COMMITS)||'|'||(select count(*) from {U}.SH_EVENT_LEDGER) from dual;")
    assert 'COUNTS|2|2|2|2' in out;return [2,2,2,2]
helper=False;started=False
def start_app(name):
    global helper
    run(name+'-start',['docker','run','-d','--name',HELPER,'--label','owner=OracleRedoKernel','--label','scope=oracle-read-cut','--user','0','--network','container:'+S,'--env-file',N/'runtime.secret.env',
        '--mount','type=bind,source='+str(O)+',target=/evidence,readonly','--mount','type=bind,source='+str(N)+',target=/read-cut',
        '--mount','type=bind,source='+str(N/'central-raw')+',target=/evidence/raw-ready','--mount','type=bind,source='+str(N/'acks')+',target=/evidence/acks',
        '--mount','type=bind,source='+str(N/'prefix-state')+',target=/evidence/prefix-state','--mount','type=bind,source='+str(N/'failures')+',target=/evidence/failures',
        '--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/read-cut/'+globals().get('applicationFile','application.jar')])
    helper=True
    for i in range(40):
        try:request(name+'-health-'+str(i),'/system/info');break
        except (AssertionError,ValueError):
            if i==39:raise
            time.sleep(3)
    assert run(name+'-sha',['docker','exec',HELPER,'sha256sum','/read-cut/'+globals().get('applicationFile','application.jar')]).split()[0]==candidate['applicationSha256']
def stop_app(name):
    global helper
    run(name+'-logs',['docker','logs',HELPER]);run(name+'-stop',['docker','stop','-t','20',HELPER]);run(name+'-remove',['docker','rm',HELPER]);helper=False
try:
    assert (N/'product-build.rc').read_text().strip()=='0'
    assert not run('dirty',['git','-C',P,'status','--porcelain']).strip()
    jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar'
    assert not (N/'application.jar').exists();shutil.copyfile(jar,N/'application.jar')
    with zipfile.ZipFile(jar) as z:
        entry='BOOT-INF/lib/oracle-redo-kernel-0.1.0-readcut-6368cd5e0f00.jar'
        assert hashlib.sha256(z.read(entry)).hexdigest()=='6368cd5e0f006657760fe6f1b4cf6f14dc3e2c13767a13e724e32a9db38cfd61'
    candidate=dict(productCommit=run('commit',['git','-C',P,'rev-parse','HEAD']).strip(),applicationSha256=sha(jar),kernelSha256='6368cd5e0f006657760fe6f1b4cf6f14dc3e2c13767a13e724e32a9db38cfd61')
    (N/'candidate.json').write_text(json.dumps(candidate,indent=2))
    # All deletion tests use an independent copied central directory. Original evidence is mounted read-only.
    shutil.copytree(O/'raw-ready',N/'central-raw');shutil.copytree(O/'acks',N/'acks');shutil.copytree(O/'prefix-state',N/'prefix-state')
    (N/'failures').mkdir()
    props.update(SH_ORACLE_RAW_DISCOVERY_ENABLED='false',SH_PIPELINE_AUTO_CONSUME_ENABLED='false',SH_GATEWAY_STAGING_DIR='/read-cut/staging')
    (N/'runtime.secret.env').write_bytes(''.join(k+'='+v+'\n' for k,v in props.items()).encode())
    original={p.name:sha(p) for p in (O/'acks').glob('*.ack.json')}
    assert HELPER not in run('containers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    assert run('source-state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    run('source-start',['docker','start',S]);started=True
    for i in range(30):
        try:
            if 'READY|1' in sql('ready-'+str(i),"select 'READY|'||count(*) from ORKLAB.T_D2_BLOCKS_20261008 where ID=0 and NOTE='online-next';"):break
        except AssertionError:
            if i==29:raise
        time.sleep(3)
    else:raise AssertionError('SOURCE_NOT_READY')
    start_app('initial');counts('before')
    request('client-path-refused','/transfer/oracle/read-cut',{'path':'/foreign'},True,False)
    cut=request('prepare','/transfer/oracle/read-cut',{},True)
    assert (int(cut['firstSequence']),int(cut['scn']))==(101,3090136)
    (N/'cut.json').write_text(json.dumps(cut,indent=2))
    ack=next(p for p in (N/'acks').glob('*.ack.json') if json.loads(p.read_text())['lastWindowKey']=='raw-stream-21001-2')
    held=ack.with_suffix('.held');ack.rename(held)
    try:request('missing-ack-refused','/transfer/oracle/read-cut/reclaim',{},True,False)
    finally:held.rename(ack)
    first=N/'central-raw/raw-members'/SOURCE/'f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820.raw'
    assert first.exists()
    reclaimed=request('reclaim','/transfer/oracle/read-cut/reclaim',{},True)
    assert (reclaimed['removed'],int(reclaimed['bytes']),int(reclaimed['requiredRawFirstSequence']))==(1,209715712,101)
    assert not first.exists();counts('after-reclaim')
    stop_app('initial');start_app('restart')
    floor=request('restart-floor','/transfer/oracle/retention',{},True)
    assert (int(floor['requiredRawFirstSequence']),int(floor['decodedThroughSequence']))==(101,101)
    discovered=request('restart-discover','/oracle/raw-plan/discover',{})
    assert int(discovered['candidate']['firstSequence'])==101 and [int(x['sequence']) for x in discovered['members']]==[101]
    repeated=request('idempotent-reclaim','/transfer/oracle/read-cut/reclaim',{},True);assert repeated['removed']==0
    counts('after-restart')
    assert {p.name:sha(p) for p in (N/'acks').glob('*.ack.json')}==original=={p.name:sha(p) for p in (O/'acks').glob('*.ack.json')}
    for digest in ['f2ba9de4e018da2a4e4753a047e70439d3de4e98fd444cd0d2249aa6c99b1820','3112c82f72e267174eef1e4cd27748c661e530df4472470e2c8573dc9994a8d5']:
        assert sha(O/'raw-ready/raw-members'/SOURCE/(digest+'.raw'))==digest
    (N/'reclaim-proof.json').write_text(json.dumps(dict(candidate=candidate,cut=cut,reclaimed=reclaimed,retention=floor,discovery=discovered,newSourceDml=0,newEvents=0,targetAndLedger=[2,2,2,2],originalRawAndAckUnchanged=True,restartWithoutSequence100=True),indent=2))
finally:
    try:
        if helper:stop_app('cleanup')
    finally:
        if started:run('source-stop',['docker','stop','-t','60',S])
