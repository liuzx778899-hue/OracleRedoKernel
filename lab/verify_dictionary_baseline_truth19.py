"""Independent fixed-SCN DUMP ruler for actual fresh V3 rows/layout/indexes; no event production input."""
import hashlib,json,re,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/dictionary-baseline-candidate-20261006';S='oraredo-19c-lab-20261004'
assert not (O/'independent-truth-proof.json').exists();b=json.loads((O/'actual-acquisition-r2-proof.json').read_text());snapshot=R/b['snapshot']
assert hashlib.sha256(snapshot.read_bytes()).hexdigest()==b['sha256'];scn=b['scn'];frames=[line.split('|') for line in snapshot.read_text().splitlines()]
shutil.copyfile(Path(__file__),O/'executed-truth-driver.py');shutil.copyfile(R/'lab/SysDictionaryBaselineResearchProbe.java',O/'executed-truth-probe.java')
(O/'truth-classes').mkdir()
def run(name,args,body=None):
    args=list(map(str,args));(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode();return p.stdout.decode()
run('compile-truth',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-encoding','UTF-8','-d',O/'truth-classes',R/'lab/SysDictionaryBaselineResearchProbe.java'])
text=run('independent-sys-rows',['docker','run','--rm','--label','owner=OracleRedoKernel','--label','scope=dictionary-baseline-truth','--network','container:'+S,'--mount','type=bind,source='+str(O)+',target=/evidence','--entrypoint','java','flink:1.18-java17','-cp','/evidence/truth-classes:/evidence/ojdbc8.jar','SysDictionaryBaselineResearchProbe','/evidence/source.secret',scn,75185,75186,'/evidence'])
rows=[];values={};layouts=[]
for line in text.splitlines():
    q=line.split('|')
    if q[0]=='SYS_ROW':rows.append(q[1:])
    elif q[0] in ['OBJ$','TAB$','COL$'] and q[1]=='LAYOUT':layouts.append([q[0],q[3],q[2],*q[4:]])
    elif q[0]=='CELL':
        if q[8]=='NULL':value='NULL'
        else:
            m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m and m[1]==q[7]
            value=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(value)==int(m[2])*2
        key=tuple(q[1:4]);values.setdefault(key,[]).append(value)
actual=[q[1:] for q in frames if q[0]=='DICT_ROW'];assert len(actual)==len(rows)==6
for q,r in zip(actual,rows):assert q[:6]==r and q[6:]==values[tuple([r[0],r[1],r[5]])]
assert [q[1:] for q in frames if q[0]=='DICT_LAYOUT_COLUMN']==layouts
body="whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\nalter session set container=ORKPDB;\n"
body+=f"select 'INDEX_TRUTH|'||o.name||'|'||o.obj#||'|'||o.dataobj#||'|'||(i.file#*4194304+i.block#) from SYS.OBJ$ as of scn {scn} o join SYS.IND$ as of scn {scn} i on o.obj#=i.obj# where o.name in ('I_COL1','I_COL2','I_COL3') order by o.name;\n"
body+=f"select 'HEAP_TRUTH|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||dump(ID,16)||'|'||dump(TXT,16)||'|'||dump(NOTE,16) from ORKLAB.T_V2_HIST_20261006 as of scn {scn} order by rowid;\n"
body+="select 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nexit success\n"
(O/'independent-index-heap.sql').write_text(body);truth=run('independent-index-heap',['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba'],body.encode());assert 'SP2-' not in truth
q=[line.strip().split('|') for line in truth.splitlines() if line.strip()];assert [f[1:] for f in q if f[0]=='INDEX_TRUTH']==[f[2:] for f in frames if f[:2]==['DICT_INDEX','BEFORE']]
heap=next(f for f in q if f[0]=='HEAP_TRUTH');row=next(f for f in frames if f[0]=='ROW');assert heap[1:5]==row[1:5]
for dump,expected in zip(heap[5:],row[5:]):
    if not dump:assert expected=='NULL'
    else:
        m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',dump);assert m
        assert ''.join(f'{int(x,16):02X}' for x in m[3].split(','))==expected
assert ['OLD','11','blocking-closed','new-note'] in q and ['LOGGING','NO','NO'] in q
proof=dict(scn=scn,snapshot_sha256=b['sha256'],full_sys_rows=6,sys_cells=230,indexes=3,baseline_rows=1,differences=0,business_ddl_dml=0,target_actions=0,history_dml_accepted=False)
(O/'independent-truth-proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
