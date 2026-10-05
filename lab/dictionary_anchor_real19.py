"""Owned lab-only permissions and new fixture, actual fixed-SCN V2 acquisition and independent truth."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-anchor-real-20261005'
A=R/'lab/out/dictionary-acquisition-20261005';S='oraredo-19c-lab-20261004'
TABLE='T_DICT_ANCHOR_20261005';INDEX='PK_DICT_ANCHOR_20261005'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert subprocess.check_output(['docker','inspect',S,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
O.mkdir(exist_ok=False)
(O/'executed-prepare.py').write_bytes(Path(__file__).read_bytes())
def run(name,args,body=None):
    assert not (O/(name+'.command.json')).exists()
    (O/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    p=subprocess.run(list(map(str,args)),input=body,capture_output=True,timeout=120)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr)
    (O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,name
    return p.stdout.decode()
def sql(name,body):
    body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\n'+body+'\nexit success\n'
    (O/(name+'.sql')).write_text(body)
    out=run(name,['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode())
    assert 'SP2-' not in out;return out
proof=json.loads((R/'docs/workorders/evidence/DELIVERY-dictionary-anchor-candidate-20261005.json').read_text())
for n in ['kernel','adapter']:
    assert sha(A/(n+'.jar'))==proof[n+'_sha256']
for n in ['kernel','adapter','spi','ojdbc8']:shutil.copyfile(A/(n+'.jar'),O/(n+'.jar'))
shutil.copyfile(A/'source.secret',O/'source.secret')
reader=(O/'source.secret').read_text().splitlines()[0];assert reader=='ORKACQ_20261005'
classes=O/'classes';classes.mkdir()
cp=';'.join(str(O/(n+'.jar')) for n in ['kernel','adapter','spi','ojdbc8'])
run('compile-probe',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-cp',cp,'-d',classes,R/'lab/OracleSnapshotAcquisitionProbe.java'])
(O/'prepare-execution-pins.json').write_text(json.dumps({str(p.relative_to(R)):sha(p) for p in [Path(__file__),R/'lab/OracleSnapshotAcquisitionProbe.java',*classes.glob('*'),*O.glob('*.jar')]},indent=2))
out=sql('preflight',"declare n number;begin select count(*) into n from v$database where dbid=1276726821 and activation#=1276717861 and log_mode='ARCHIVELOG' and supplemental_log_data_min='NO' and supplemental_log_data_all='NO';if n!=1 then raise_application_error(-20001,'source mismatch');end if;end;\n/\nalter session set container=ORKPDB;\nselect 'OBJECTS|'||count(*) from dba_objects where owner='ORKLAB' and object_name in ('"+TABLE+"','"+INDEX+"');\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
assert 'OBJECTS|0' in out and 'OLD|11|blocking-closed|new-note' in out
sql('lab-reader-grants-once','alter session set container=ORKPDB;\n'+''.join('grant select,flashback on SYS.'+t+' to '+reader+';\n' for t in ['OBJ$','TAB$','COL$']))
sql('fixture-once',"alter system archive log current;\nalter session set container=ORKPDB;\ncreate table ORKLAB."+TABLE+"(ID number not null,TXT varchar2(128),NOTE varchar2(128) invisible,constraint "+INDEX+" primary key(ID));\ninsert into ORKLAB."+TABLE+"(ID,TXT,NOTE) values(0,'dictionary-seed','anchor');\ncommit;\ngrant select,flashback on ORKLAB."+TABLE+' to '+reader+';')
out=run('actual-v2-acquisition',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=dictionary-anchor-real','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/classes:/evidence/adapter.jar:/evidence/kernel.jar:/evidence/spi.jar:/evidence/ojdbc8.jar','OracleSnapshotAcquisitionProbe','/evidence/source.secret','initial','ORKLAB',TABLE,INDEX,'dictionary-normal','/evidence/initial.snapshot.txt'])
f=out.strip().split('|');assert f[0]=='ACQUIRED' and f[4:]==['3','1','1'];assert sha(O/'initial.snapshot.txt')==f[3]
scn=int(f[2]);lines=(O/'initial.snapshot.txt').read_text().splitlines();heap=int(next(l for l in lines if l.startswith('HEAP|BEFORE')).split('|')[4]);index=int(next(l for l in lines if l.startswith('INDEX|BEFORE')).split('|')[4])
(O/'baseline-reference.json').write_text(json.dumps({'path':str(O/'initial.snapshot.txt'),'sha256':f[3],'scn':scn,'heap':heap,'index':index},indent=2))
body='alter session set container=ORKPDB;\n'
for t in ['OBJ$','TAB$','COL$']:
    body+="select 'DICT_ANCHOR|"+t+"|'||obj#||'|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from SYS."+t+' as of scn '+str(scn)+' where obj#'+(' in ('+str(heap)+','+str(index)+')' if t=='OBJ$' else '='+str(heap))+' order by obj#,rowid;\n'
body+="select 'DICT_COLUMN|'||intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$||'|'||property from SYS.COL$ as of scn "+str(scn)+' where obj#='+str(heap)+' order by segcol#;\n'
truth=sql('independent-same-scn',body)
actual=[l.strip() for l in truth.splitlines() if l.strip().startswith(('DICT_ANCHOR|','DICT_COLUMN|'))]
wanted=[l for l in lines if l.startswith(('DICT_ANCHOR|','DICT_COLUMN|'))];assert actual==wanted
sql('post-source',"select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nalter session set container=ORKPDB;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;")
(O/'prepare-proof.json').write_text(json.dumps({'snapshot_scn':scn,'snapshot_sha256':f[3],'dictionary_anchors':6,'independent_dictionary_differences':0,'permission_changes':'owned lab SELECT/FLASHBACK only; not production zero-configuration proof','source_DDL_DML':'new unique fixture seed only; no old source replay','kernel_sha256':sha(O/'kernel.jar'),'adapter_sha256':sha(O/'adapter.jar'),'P0_closed':False},indent=2))
print('REAL_V2_ACQUISITION_VERIFIED|'+str(scn)+'|6_ANCHORS|0_DIFFERENCES')
