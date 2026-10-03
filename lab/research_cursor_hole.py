"""Frozen-archive counterexample to experimental cursor constraints; no Oracle source actions."""
import hashlib,json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'lab/out/research-cursor-hole-20261004'
OUT.mkdir(parents=True,exist_ok=True)
if (OUT/'commands.json').exists():raise RuntimeError('research output already sealed; do not overwrite')
JAVA=Path('D:/work/Java/jdk-17.0.7/bin')
JAR=ROOT/'lab/out/release-cross-compensation/build/target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
if sha(JAR)!='931840b788e41def334f91a4f2e222edf28ffe63162d2f883bdbcaaaba8d40c9':raise RuntimeError('sealed core artifact differs')
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
sealed=OUT/'source';sealed.mkdir(exist_ok=False)
for name in ('UndoCursorProbe.java','compare_cursor_fields.py','research_cursor_hole.py'):
    (sealed/name).write_bytes(subprocess.check_output(['git','show',commit+':lab/'+name],cwd=ROOT))
commands=[]
def run(name,args,expected=0):
    result=subprocess.run(list(map(str,args)),cwd=ROOT,capture_output=True)
    (OUT/(name+'.stdout.txt')).write_bytes(result.stdout)
    (OUT/(name+'.stderr.txt')).write_bytes(result.stderr)
    commands.append({'name':name,'args':list(map(str,args)),'rc':result.returncode,'expected_rc':expected})
    (OUT/'commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8')
    if result.returncode!=expected:raise RuntimeError(name+': unexpected rc '+str(result.returncode))
run('compile',[JAVA/'javac.exe','-cp',JAR,'-d',OUT,sealed/'UndoCursorProbe.java'])
cp=str(JAR)+';'+str(OUT)
reports=[]
for seq,directory,xid in ((10,'oracle19-cross-undo-20261004-r2','1.1.604'),(11,'oracle19-multi-undo-20261004','10.23.1156'),(12,'oracle19-multiblock-undo-20261004','3.26.669')):
    source=ROOT/'lab/out'/directory;archive=source/f'archive-1-{seq}.dbf';dump=source/f'dump-{seq}.trc'
    manifest=json.loads((source/'manifest.json').read_text())
    if sha(archive)!=next(f['sha256'] for f in manifest['files'] if f['sequence']==seq):raise RuntimeError('archive binding differs')
    prefix=str(seq)
    run(prefix+'-positive',[JAVA/'java.exe','-cp',cp,'UndoCursorProbe',archive,xid])
    run(prefix+'-positive-parity',[sys.executable,sealed/'compare_cursor_fields.py',dump,OUT/(prefix+'-positive.stdout.txt'),xid])
    run(prefix+'-omitted',[JAVA/'java.exe','-cp',cp,'UndoCursorProbe',archive,xid,'--omit-cross-predecessor'])
    run(prefix+'-omitted-parity',[sys.executable,sealed/'compare_cursor_fields.py',dump,OUT/(prefix+'-omitted.stdout.txt'),xid],1)
    report=json.loads((OUT/(prefix+'-omitted-parity.stdout.txt')).read_text())
    if report['mismatches']!=2 or not report['native_end_tail_matches'] or {e['reason'] for e in report['errors']}!={'DIRECT_FIELD_DIFF','COVERAGE_OR_END_TAIL_DIFF'}:raise RuntimeError('counterexample disposition differs')
    omitted=(OUT/(prefix+'-omitted.stderr.txt')).read_text().splitlines()[0]
    reports.append({'sequence':seq,'xid':xid,'archive_sha256':sha(archive),'native_dump_sha256':sha(dump),
                    'omission':omitted,'local_cursor_rc':0,'visible_nodes':119,'native_full_coverage_rc':1,'native_report':report})
evidence={'research_source_commit':commit,'production_source_commit':'0445b4328f24ef6fbe2cb85ac0c9a4e26caa0446',
          'jar_sha256':sha(JAR),'samples':reports,'cdc_events':0,
          'scope':'selector-loss counterexample: original archives unchanged; fixture node-count assertion deliberately adjusted to 119; local cursor constraints alone insufficient; complete cross-block graph remains unaccepted',
          'files':[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted([*OUT.glob('*.txt'),OUT/'commands.json',*sealed.iterdir()])]}
(OUT/'RESEARCH-cursor-hole.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'samples':len(reports),'local_checks_accept_incomplete_graph':len(reports),'independent_full_coverage_refusals':len(reports),'cdc_events':0}))
