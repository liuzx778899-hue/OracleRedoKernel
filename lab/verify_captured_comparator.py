"""Validate strict full native comparator from already frozen captures, no source actions."""
import json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OLD=ROOT/'lab/out/capture-records-20261005';OUT=ROOT/'lab/out/capture-comparator-20261005'
OUT.mkdir(parents=True,exist_ok=False)

def run(name,dump,rows,expected):
    args=[sys.executable,str(ROOT/'lab/compare_captured_dump.py'),str(dump),str(rows)]
    (OUT/(name+'.command.json')).write_text(json.dumps(args));result=subprocess.run(args,capture_output=True)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if result.returncode!=expected:raise RuntimeError(name+': unexpected comparator result; preserve')

for sequence in (40,41,42):run('positive-'+str(sequence),OLD/('dump-'+str(sequence)+'.trc'),OLD/('capture-records-'+str(sequence)+'.stdout.txt'),0)
rows=(OLD/'capture-records-41.stdout.txt').read_text().splitlines();assert len(rows)>2
wrong=rows.copy();fields=wrong[-1].split('|');fields[4]=str(int(fields[4])+1);wrong[-1]='|'.join(fields)
unordered=rows.copy();unordered[0],unordered[1]=unordered[1],unordered[0]
for name,changed in [('missing',rows[:-1]),('duplicate',rows+[rows[-1]]),('wrong-scn',wrong),('unordered',unordered)]:
    path=OUT/(name+'.records.txt');path.write_text('\n'.join(changed)+'\n');run(name,OLD/'dump-41.trc',path,1)
spanning={}
for sequence in (40,41,42):
    records=[line.split('|') for line in (OLD/('capture-records-'+str(sequence)+'.stdout.txt')).read_text().splitlines()]
    spanning[str(sequence)]=sum(int(row[2])>512-int(row[1]) for row in records)
(OUT/'proof.json').write_text(json.dumps({'strict_positive_sequences':[40,41,42],'comparator_refusals':4,'records_crossing_block_boundary':spanning},indent=2));print((OUT/'proof.json').read_text())
