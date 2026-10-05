"""Retain accepted inspections; reject the uncovered standalone rollback suffix."""
import json
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-dml-target-20261005';configs=[]
for prefix,seed,events,expected in [('DNON','dml-seed',2,'baseline-physical-scope-20261005/actual-r2.stdout.txt'),('DNUL','null-seed',2,'nullable-tail-20261005/actual.stdout.txt'),('DMIX','mixed-seed',3,'mixed-dml-capture-20261005/raw-complete-batch.stdout.txt')]:
    n=prefix+'-inspect';assert (O/(n+'.rc.txt')).read_text()=='0'
    a=json.loads((O/(n+'.command.json')).read_text())[4:]
    windows=[s.split('|')[1:] for s in (O/(n+'.stdout.txt')).read_text().splitlines() if s.startswith('WINDOW|')]
    assert int(windows[-1][3])==events
    configs.append({'prefix':prefix,'seed':seed,'baseline_sha256':a[5],'leaf':a[6],'captures':a[8:],'expected_raw_output':expected,'expected_events':events,'windows':windows})
assert b'BASELINE_SCN_OUTSIDE_INPUT_COVERAGE' in (O/'DRBK-inspect-r2.stderr.txt').read_bytes()
(O/'config.json').write_text(json.dumps(configs,indent=2));print('CONFIGURED|3|uncovered_suffix_refused')
