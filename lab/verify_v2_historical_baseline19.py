"""Read-only independent fixed-SCN dictionary and baseline bytes for the new V2 capture."""
import hashlib,json,re,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-historical-product-20261006';S='oraredo-19c-lab-20261004'
b=json.loads((O/'baseline-reference.json').read_text());snapshot=Path(b['path']);assert hashlib.sha256(snapshot.read_bytes()).hexdigest()==b['sha256']
lines=[s.split('|') for s in snapshot.read_text().splitlines()];scn=b['scn'];heap=75185;index=75186
body='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 32767 feedback off heading off trimspool on\nalter session set container=ORKPDB;\n'
for t in ['OBJ$','TAB$','COL$']:
    body+="select 'DICT_ANCHOR|"+t+"|'||obj#||'|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from SYS."+t+' as of scn '+str(scn)+' where obj#'+(' in (75185,75186)' if t=='OBJ$' else '=75185')+' order by obj#,rowid;\n'
body+="select 'DICT_COLUMN|'||intcol#||'|'||segcol#||'|'||name||'|'||type#||'|'||null$||'|'||property from SYS.COL$ as of scn "+str(scn)+' where obj#=75185 order by segcol#;\n'
body+="select 'DICT_ROLE|BEFORE|SYS|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='SYS' and object_name in ('OBJ$','TAB$','COL$') and object_type='TABLE';\n"
body+="select 'DUMP|'||dbms_rowid.rowid_object(rowid)||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid)||'|'||dump(ID,16)||'|'||dump(TXT,16)||'|'||dump(NOTE,16) from ORKLAB.T_V2_HIST_20261006 as of scn "+str(scn)+' order by rowid;\n'
body+="select 'FINAL|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_V2_HIST_20261006 order by ID;\nselect 'OLD|'||ID||'|'||TXT||'|'||NOTE from ORKLAB.T_BASELINE_20261004;\nselect 'LOGGING|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;\nexit success\n"
name='independent-v2-historical-baseline';assert not (O/(name+'.command.json')).exists()
(O/(name+'.sql')).write_text(body);args=['docker','exec','-i',S,'sqlplus','-s','/','as','sysdba']
(O/(name+'.execution-pin.json')).write_text(json.dumps(dict(driver_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),sql_sha256=hashlib.sha256(body.encode()).hexdigest(),baseline_sha256=b['sha256'])))
(O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,input=body.encode(),capture_output=True,timeout=60)
(O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0 and b'SP2-' not in p.stdout
truth=[s.strip().split('|') for s in p.stdout.decode().splitlines() if s.strip()]
for role in ['DICT_ANCHOR','DICT_COLUMN']:
    assert [s for s in truth if s[0]==role]==[s for s in lines if s[0]==role],role
assert sorted(s for s in truth if s[0]=='DICT_ROLE')==sorted(s for s in lines if s[:2]==['DICT_ROLE','BEFORE'])
rows=[s for s in lines if s[0]=='ROW'];dumps=[s for s in truth if s[0]=='DUMP'];assert len(rows)==len(dumps)==1
for row,dump in zip(rows,dumps):
    assert row[1:5]==dump[1:5]
    for expected,actual in zip(row[5:],dump[5:]):
        if not actual:assert expected=='NULL'
        else:
            m=re.fullmatch(r'Typ=([0-9]+) Len=([0-9]+): (.*)',actual);assert m
            raw=''.join(format(int(x.strip(),16),'02X') for x in m[3].split(','));assert len(raw)==2*int(m[2]) and raw==expected
assert [s for s in truth if s[0]=='FINAL']==[['FINAL','0','dictionary-seed','anchor']]
assert ['OLD','11','blocking-closed','new-note'] in truth and ['LOGGING','NO','NO'] in truth
proof=dict(acquisition_id=b['acquisition_id'],scn=scn,baseline_sha256=b['sha256'],anchors=6,columns=3,rows=1,differences=0,source_actions=0,post_source_seed_and_old_row_preserved=True,logging='NO/NO',historical_dictionary_accepted=False)
(O/(name+'-proof.json')).write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
