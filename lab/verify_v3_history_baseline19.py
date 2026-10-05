"""Independent fresh-V3 complete SYS/index/business ruler, before source fixture actions."""
import hashlib,json,re,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v3-historical-raw-20261006';S='oraredo-19c-lab-20261004'
assert not (O/'baseline-truth-proof.json').exists();b=json.loads((O/'baseline-reference.json').read_text());snapshot=Path(b['path']);scn=b['scn']
assert hashlib.sha256(snapshot.read_bytes()).hexdigest()==b['sha256'];frames=[l.split('|') for l in snapshot.read_text().splitlines()]
shutil.copyfile(Path(__file__),O/'executed-baseline-truth-driver.py');(O/'truth-classes').mkdir()
def run(name,args,body=None):
    args=list(map(str,args));assert not (O/(name+'.command.json')).exists();(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
run('compile-baseline-truth',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-d',O/'truth-classes',R/'lab/SysDictionaryBaselineResearchProbe.java'])
text=run('independent-baseline-sys',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=v3-historical-baseline-truth','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/truth-classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,75185,75186,'/evidence'])
def decode(dump):
    if dump=='NULL' or not dump:return 'NULL'
    m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',dump);assert m
    raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2;return raw
rows=[];values={};layouts=[]
for line in text.splitlines():
    q=line.split('|')
    if q[0]=='SYS_ROW':rows.append(q[1:])
    elif q[0] in ['OBJ$','TAB$','COL$'] and q[1]=='LAYOUT':layouts.append([q[0],q[3],q[2],*q[4:]])
    elif q[0]=='CELL':values.setdefault(tuple(q[1:4]),[]).append(decode(q[8]))
actual=[q[1:] for q in frames if q[0]=='DICT_ROW'];assert len(actual)==len(rows)==6
for q,r in zip(actual,rows):assert q[:6]==r and q[6:]==values[tuple([r[0],r[1],r[5]])]
assert [q[1:] for q in frames if q[0]=='DICT_LAYOUT_COLUMN']==layouts
body=f"""whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 feedback off heading off trimspool on
alter session set container=ORKPDB;
select 'INDEX_TRUTH|'||o.name||'|'||o.obj#||'|'||o.dataobj#||'|'||(i.file#*4194304+i.block#) from SYS.OBJ$ as of scn {scn} o join SYS.IND$ as of scn {scn} i on o.obj#=i.obj# where o.name in ('I_COL1','I_COL2','I_COL3') order by o.name;
select 'HEAP_TRUTH|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||dump(ID,16)||'|'||dump(TXT,16)||'|'||dump(NOTE,16) from ORKLAB.T_V2_HIST_20261006 as of scn {scn} order by rowid;
select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;
select 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
exit success
"""
(O/'baseline-index-heap.sql').write_text(body);text=run('baseline-index-heap',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert 'SP2-' not in text
q=[l.strip().split('|') for l in text.splitlines() if l.strip()]
assert [f[1:] for f in q if f[0]=='INDEX_TRUTH']==[f[2:] for f in frames if f[:2]==['DICT_INDEX','BEFORE']]
heap=next(f for f in q if f[0]=='HEAP_TRUTH');row=next(f for f in frames if f[0]=='ROW');assert heap[1:5]==row[1:5] and [decode(v) for v in heap[5:]]==row[5:]
assert ['OLD','11','blocking-closed','new-note'] in q and ['LOGGING','NO','NO'] in q
proof=dict(scn=scn,snapshot_sha256=b['sha256'],full_sys_rows=6,sys_cells=230,indexes=3,baseline_rows=1,differences=0,source_actions=0,target_actions=0,events=0)
(O/'baseline-truth-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
