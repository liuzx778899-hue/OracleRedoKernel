"""Independently compare the real product V3 ledger/file with same-SCN SYS DUMP values."""
import hashlib,json,re,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/v3-product-runtime-20261006';S='oraredo-19c-lab-20261004'
assert not (N/'baseline-truth-proof.json').exists()
resume=sys.argv[1:]==['--resume-metadata']
if resume:assert (N/'first-truth-driver.failure-from-tool.txt').exists()
(N/('executed-baseline-metadata-repair-driver.py' if resume else 'executed-baseline-truth-driver.py')).write_bytes(Path(__file__).read_bytes())
row=json.loads((N/'acquire.stdout.txt').read_text())[1]['data'];identifier=str(row['id']);snapshot=N/'snapshots'/(identifier+'.snapshot')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(snapshot)==row['snapshotSha256'];frames=[line.split('|') for line in snapshot.read_text().splitlines()]
assert frames[0]==['FORMAT','ORK_HEAP_SNAPSHOT_V3'];scn=int(row['snapshotScn']);assert next(f[1] for f in frames if f[0]=='SNAPSHOT_SCN')==str(scn)
(N/'baseline-reference.json').write_text(json.dumps(dict(acquisition_id=identifier,path=str(snapshot),sha256=row['snapshotSha256'],scn=scn)))
reader=R/'lab/out/oracle-snapshot-acquisition-20261005/source.secret'
shutil.copyfile(reader,N/'source.secret');shutil.copyfile(R/'lab/out/oracle-target-20261005/ojdbc8.jar',N/'ojdbc8.jar')
classes=N/'truth-classes';classes.mkdir(exist_ok=resume)

def run(name,args,data=None):
    args=list(map(str,args))
    if resume:
        assert json.loads((N/(name+'.command.json')).read_text())==args and (N/(name+'.rc.txt')).read_text()=='0'
        return (N/(name+'.stdout.txt')).read_text()
    assert not (N/(name+'.command.json')).exists();(N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=data,capture_output=True,timeout=240)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==0 and b'SP2-' not in p.stdout,(name,p.returncode)
    return p.stdout.decode()

run('compile-product-baseline-truth',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-d',classes,R/'lab/SysDictionaryBaselineResearchProbe.java'])
heap=next(f for f in frames if f[:2]==['HEAP','BEFORE']);index=next(f for f in frames if f[:2]==['INDEX','BEFORE'])
text=run('independent-product-baseline-sys',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=v3-product-baseline-truth','--network','container:'+S,'--mount','type=bind,source='+str(N)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/truth-classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,heap[4],index[4],'/evidence'])

def decode(dump):
    if dump=='NULL' or not dump:return 'NULL'
    m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',dump);assert m,dump
    raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2
    return raw

rows=[];values={};layouts=[]
for line in text.splitlines():
    f=line.split('|')
    if f[0]=='SYS_ROW':rows.append(f[1:])
    elif f[0] in ['OBJ$','TAB$','COL$'] and f[1]=='LAYOUT':layouts.append([f[0],f[3],f[2],*f[4:]])
    elif f[0]=='CELL':values.setdefault(tuple(f[1:4]),[]).append(decode(f[8]))
actual=[f[1:] for f in frames if f[0]=='DICT_ROW'];assert len(actual)==len(rows)==6
for actual_row,ruler in zip(actual,rows):assert actual_row[:6]==ruler and actual_row[6:]==values[tuple([ruler[0],ruler[1],ruler[5]])]
assert [f[1:] for f in frames if f[0]=='DICT_LAYOUT_COLUMN']==layouts
sql=f"""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 feedback off heading off trimspool on
alter session set container=ORKPDB;
select 'INDEX_TRUTH|'||o.name||'|'||o.obj#||'|'||o.dataobj#||'|'||(i.file#*4194304+i.block#) from SYS.OBJ$ as of scn {scn} o join SYS.IND$ as of scn {scn} i on o.obj#=i.obj# where o.name in ('I_COL1','I_COL2','I_COL3') order by o.name;
select 'HEAP_TRUTH|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||dump(ID,16)||'|'||dump(TXT,16)||'|'||dump(NOTE,16) from ORKLAB.T_V2_HIST_20261006 as of scn {scn} order by rowid;
select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;
select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
exit success
"""
(N/'baseline-index-heap.sql').write_text(sql);text=run('product-baseline-index-heap',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],sql.encode())
lines=[line.strip().split('|') for line in text.splitlines() if line.strip()]
assert [f[1:] for f in lines if f[0]=='INDEX_TRUTH']==[f[2:] for f in frames if f[:2]==['DICT_INDEX','BEFORE']]
heap_truth=next(f for f in lines if f[0]=='HEAP_TRUTH');baseline_row=next(f for f in frames if f[0]=='ROW')
assert heap_truth[1:5]==baseline_row[1:5] and [decode(v) for v in heap_truth[5:]]==baseline_row[5:]
assert ['OLD','11','blocking-closed','new-note'] in lines and ['LOGGING','NO','NO'] in lines
metadata=json.loads(row['metadataJson'])
def role(frame):return dict(owner=frame[2],name=frame[3],logicalObject=int(frame[4]),dataObject=int(frame[5]))
assert metadata['heap']==role(heap) and metadata['index']==role(index) and int(metadata['scn'])==scn and metadata['rowCount']==1
roles={f[3]:role(f) for f in frames if f[:2]==['DICT_ROLE','BEFORE']}
expected_layouts={}
for f in frames:
    if f[0]=='DICT_LAYOUT':expected_layouts[f[1]]=dict(role=roles[f[1]],tableNumber=int(f[2]),columns=[])
    elif f[0]=='DICT_LAYOUT_COLUMN':expected_layouts[f[1]]['columns'].append(dict(segment=int(f[2]),internal=int(f[3]),name=f[4],type=int(f[5]),required=f[6]=='1'))
expected_rows=[]
for f in actual:
    expected_rows.append(dict(role=f[0],target=int(f[1]),key=dict(dataObjectId=int(f[2]),dba=(int(f[3])<<22)|int(f[4]),slot=int(f[5])),
        values=[dict(state='NULL' if value=='NULL' else 'VALUE',hex='' if value=='NULL' else value.lower()) for value in f[6:]]))
expected_indexes=[dict(role=dict(owner='SYS',name=f[2],logicalObject=int(f[3]),dataObject=int(f[4])),indexDba=int(f[5])) for f in frames if f[:2]==['DICT_INDEX','BEFORE']]
assert metadata['dictionaryBaseline']==dict(layouts=expected_layouts,rows=expected_rows,indexes=expected_indexes)
proof=dict(acquisition_id=identifier,scn=scn,snapshot_sha256=row['snapshotSha256'],full_sys_rows=6,sys_cells=sum(len(v) for v in values.values()),
    indexes=3,baseline_rows=1,differences=0,complete_product_metadata_matches_independent_frames=True,state=row['state'],source_dml=0,target_actions=0,historical_publication=False)
(N/'baseline-truth-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
