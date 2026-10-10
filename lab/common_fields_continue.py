"""Continue only the rolled-back mixed SecureFile transaction; BasicFile is never replayed."""
from pathlib import Path
import hashlib,json,re,shutil,subprocess,sys,time
R=Path(__file__).resolve().parent.parent
OLD=R/'lab/out/common-fields-20261010/source'
retry=sys.argv[1:]==['--execute','resume-r2'];N=OLD/('resume-r2' if retry else 'resume-r1')
S='oraredo-19c-lab-20261004';H='ork-product-runtime'
assert retry or sys.argv[1:]==['--execute']
if retry:
    assert (OLD/'resume-r1/stop.rc').read_text().strip()=='0'
    assert not (OLD/'resume-r1/quota.rc').exists() and not (OLD/'resume-r1/secure-new-transaction.started').exists()
assert (OLD/'stop.rc').read_text().strip()=='0'
assert (OLD/'T_D4_MIX_B_20261010-actions.rc').read_text().strip()=='0'
assert (OLD/'T_D4_MIX_S_20261010-actions.rc').read_text().strip()=='1'
assert 'ORA-01536' in (OLD/'T_D4_MIX_S_20261010-actions.stdout').read_text()
N.mkdir(exist_ok=False);shutil.copyfile(__file__,N/'executed.py')
common=(R/'lab/common_fields_source.py').read_text()
exec(common[common.index('reader='):common.index('libs=N/')])
started=False
try:
    assert run('state',['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).strip()=='OracleRedoKernel|exited'
    assert H not in run('helpers',['docker','ps','-a','--format','{{.Names}}']).splitlines()
    run('start',['docker','start',S]);started=True
    for attempt in range(30):
        try:
            pre=sql('ready-'+str(attempt),"""select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
alter session set container=ORKPDB;
select 'QUOTA|'||bytes||'|'||max_bytes from dba_ts_quotas where username='ORKLAB' and tablespace_name='USERS';
select 'FREE|'||sum(bytes) from dba_free_space where tablespace_name='USERS';
select 'BASIC_DONE|'||count(*) from ORKLAB.T_D4_MIX_B_20261010 where ID=1 and TXT='mixed-text' and dbms_lob.getlength(B)=10000 and dbms_lob.getlength(C)=5000 and dbms_lob.getlength(J)=5012 and dbms_lob.getlength(X)=5007;
select 'SECURE_ROLLED_BACK|'||count(*) from ORKLAB.T_D4_MIX_S_20261010 where ID=1 and TXT='seed' and rawtohex(BIN)='00FF' and dbms_lob.getlength(B)=3 and dbms_lob.getlength(C)=9 and dbms_lob.getlength(J)=16 and dbms_lob.getlength(X)=30;
""")
            if 'QUOTA|' in pre:break
        except AssertionError:
            if attempt==29:raise
        time.sleep(3)
    assert 'LOGGING|NO|NO' in pre and 'BASIC_DONE|1' in pre and 'SECURE_ROLLED_BACK|1' in pre
    q=next(l.split('|') for l in pre.splitlines() if l.startswith('QUOTA|'))
    used,maximum=map(int,q[1:]);free=int(next(l.split('|')[1] for l in pre.splitlines() if l.startswith('FREE|')))
    assert maximum>0 and used<=maximum and free>=8*1024*1024
    new=maximum+8*1024*1024;assert new<=256*1024*1024
    (N/'quota-before.json').write_text(json.dumps(dict(used=used,maximum=maximum,free=free,newMaximum=new,ownedFixtureOnly=True),indent=2))
    sql('quota',f"alter session set container=ORKPDB;\nalter user ORKLAB quota {new} on USERS;")
    table='T_D4_MIX_S_20261010'
    (N/'secure-new-transaction.started').write_text('prior transaction rolled back; BasicFile not replayed\n')
    sql('secure-new-transaction',(OLD/(table+'-actions.sql')).read_text())
    for col in ['B','C','J','X']:
        sql('secure-truth-'+col,(OLD/('T_D4_MIX_B_20261010-truth-'+col+'.sql')).read_text().replace('T_D4_MIX_B_20261010',table))
    first=int(next(l.split('|')[1] for p in sorted(OLD.glob('ready-*.stdout')) for l in p.read_text().splitlines() if l.startswith('CURRENT|')))
    out=sql('close',"select 'CURRENT|'||sequence# from v$log where status='CURRENT';\nalter system archive log current;")
    end=int(next(l.split('|')[1] for l in out.splitlines() if l.startswith('CURRENT|')))
    archives=[]
    for seq in range(first,end+1):
        out=sql('archive-'+str(seq),f"select 'ARCHIVE|'||name from v$archived_log where sequence#={seq} and archived='YES' and deleted='NO' and name is not null order by recid;")
        paths=set(l.split('|')[1] for l in out.splitlines() if l.startswith('ARCHIVE|'));assert len(paths)==1
        path=next(iter(paths));local=N/(str(seq)+'.raw');run('copy-'+str(seq),['docker','cp',S+':'+path,local])
        assert run('sha-'+str(seq),['docker','exec',S,'sha256sum',path]).split()[0]==sha(local)
        archives.append(dict(sequence=seq,path=str(local),bytes=local.stat().st_size,sha256=sha(local)))
    (N/'archives.json').write_text(json.dumps(archives,indent=2))
    print('MIXED_SOURCE_CONTINUATION_FROZEN',flush=True)
finally:
    if started:run('stop',['docker','stop','-t','60',S])
