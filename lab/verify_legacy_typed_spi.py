"""Actual old bytecode links against the new binary SPI; no decoder/database actions."""
import json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/oracle-typed-evidence-20261005';B=R/'lab/out/release-oracle-typed-evidence-20261005'
resume=sys.argv[1:]==['--resume-comparison']
classes=O/'legacy-classes'
if not resume:classes.mkdir()
def run(n,args):
    args=list(map(str,args));(O/(n+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=60)
    for ext,v in [('stdout',p.stdout),('stderr',p.stderr)]:(O/(n+'.'+ext+'.txt')).write_bytes(v)
    (O/(n+'.rc.txt')).write_text(str(p.returncode));assert p.returncode==0,n
    return p.stdout
if not resume:
    run('compile-against-old-spi',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',R/'lab/out/release-oracle-null-sides-20261005/spi.jar','-d',classes,R/'lab/OracleLegacyDecodeResultProbe.java'])
    output=run('run-against-new-spi',['D:/work/Java/jdk-17.0.7/bin/java.exe','-cp',str(classes)+';'+str(B/'spi.jar'),'OracleLegacyDecodeResultProbe'])
else:
    assert (O/'compile-against-old-spi.rc.txt').read_text()==(O/'run-against-new-spi.rc.txt').read_text()=='0'
    output=(O/'run-against-new-spi.stdout.txt').read_bytes()
assert output.splitlines()==[b'LEGACY_CONSTRUCTORS_LINKED|4|5']
(O/'legacy-proof.json').write_text(json.dumps({'old_4_and_5_argument_bytecode_linked':True,'legacy_evidence_is_null':True,'windows_line_ending_comparison_repaired':resume}))
print(output.decode().strip())
