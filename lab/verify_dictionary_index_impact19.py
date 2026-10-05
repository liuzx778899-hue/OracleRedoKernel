"""Changed ordinary index code: frozen complete DML and historical refusal impact only."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
O=Path(sys.argv[1]) if len(sys.argv)>1 else R/'lab/out/dictionary-index-pair-20261006'
assert not (O/'impact-proof.json').exists()
(O/'executed-impact-driver.py').write_bytes(Path(__file__).read_bytes())
cases=[]
for folder,name,positive in [('v2-dml-capture-20261006','implicit-tail-complete-batch',True),('v2-historical-product-20261006','actual-v2-batch',False),('v2-historical-product-20261006','erased-v2-batch',False)]:
    source=R/'lab/out'/folder
    args=json.loads((source/(name+'.command.json')).read_text())
    args[3]=str(O/'kernel.jar')+';'+str(source/'parser-classes')
    stem='impact-'+name
    (O/(stem+'.command.json')).write_text(json.dumps(args))
    p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:
        (O/(stem+'.'+suffix)).write_bytes(data)
    if positive:
        assert p.returncode==0 and p.stdout==(source/(name+'.stdout.txt')).read_bytes()
        cases.append(dict(name=name,rc=0,complete_events=3,output_byte_equal=True))
    else:
        assert p.returncode==1 and not p.stdout
        reason=p.stderr.decode().splitlines()[0]
        assert reason==(source/(name+'.stderr.txt')).read_text().splitlines()[0]
        cases.append(dict(name=name,rc=1,events=0,reason=reason))
(O/'impact-proof.json').write_text(json.dumps(dict(cases=cases,source_actions=0,target_actions=0,product_rebuilt=False),indent=2))
print(json.dumps(cases))
