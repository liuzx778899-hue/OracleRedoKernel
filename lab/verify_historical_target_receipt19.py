"""Tamper only the owned new commit token, prove refusal, restore exactly."""
import json, subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-target-candidate-20261006'
assert (N/'historical-target-proof.json').exists()
assert not (N/'receipt-poison.command.json').exists()
(N/'executed-receipt-driver.py').write_bytes(Path(__file__).read_bytes())
Q=json.loads((R/'lab/out/historical-schema-protocol-20261006/actual-schema-evidence.stdout.txt').read_text())['evidence']

def run(name,args,data=None,rc=0):
    (N/(name+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,input=data,capture_output=True,timeout=300)
    for suffix,value in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (N/(name+'.'+suffix)).write_bytes(value)
    assert p.returncode==rc,(name,p.returncode)
    return p

def replace(name,before,after):
    script=("whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\n"
        "alter session set container=ORKPDB;\nbegin\nupdate ORKTGT_CMT_20261005.HS6_COMMITS set SCHEMA_SHA='"+after+
        "' where BATCH_SHA='"+Q['batchSha256']+"' and SCHEMA_SHA='"+before+
        "';\nif SQL%ROWCOUNT!=1 then raise_application_error(-20001,'COMMIT_TOKEN_SCOPE_MISMATCH');end if;\ncommit;\nend;\n/\nexit success\n")
    (N/(name+'.sql')).write_text(script)
    run(name,['docker','exec','-i','oraredo-19c-lab-20261004','sqlplus','-s','/','as','sysdba'],script.encode())

args=json.loads((N/'verify.command.json').read_text());args[args.index('--name')+1]='ork-history-receipt-poison-20261006'
original=Q['schemaSequenceSha256']
replace('receipt-poison',original,'0'*64)
try:
    p=run('receipt-poison-refused',args,rc=1)
    assert not p.stdout and b'ORACLE_TARGET_COMMIT_CONFLICT' in p.stderr
finally:
    replace('receipt-exact-restore','0'*64,original)
args[args.index('--name')+1]='ork-history-receipt-restored-20261006'
run('receipt-restored-verify',args)
(N/'receipt-integrity-proof.json').write_text(json.dumps(dict(tampered_sequence_refused=True,reason='ORACLE_TARGET_COMMIT_CONFLICT',stdout_events=0,exact_commit_token_restored=True,restored_independent_verifier_passed=True)))
print('HISTORICAL_TARGET_RECEIPT_REFUSAL_AND_EXACT_RESTORE_PASSED')
