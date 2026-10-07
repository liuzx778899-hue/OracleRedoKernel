"""Changed-kernel impact checks on earlier full DML/history and explicit V2 historical refusals."""
import hashlib,json,subprocess,sys,zipfile
from types import SimpleNamespace
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/split-history-candidate-20261007';A=N/sys.argv[1];O=R/'lab/out/historical-row-schema-candidate-20261006'
V=N/'regression';V.mkdir(exist_ok=True);frozen=V/'executed-regression-driver-r2.py';assert not frozen.exists();frozen.write_bytes(Path(__file__).read_bytes());proof=[]
for name in ['impact-implicit-tail-complete-batch','actual-historical-batch-r2','impact-actual-v2-batch','impact-erased-v2-batch']:
    args=json.loads((O/(name+'.command.json')).read_text());at=args.index('-cp')+1;parts=args[at].split(';');parts[0]=str(A/'kernel.jar');args[at]=';'.join(parts)
    assert json.loads((V/(name+'.command.json')).read_text())==args
    p=SimpleNamespace(returncode=int((V/(name+'.rc.txt')).read_text()),stdout=(V/(name+'.stdout.txt')).read_bytes(),stderr=(V/(name+'.stderr.txt')).read_bytes())
    expected=int((O/(name+'.rc.txt')).read_text());assert p.returncode==expected
    if expected:
        assert not p.stdout and p.stderr.decode().splitlines()[0]==(O/(name+'.stderr.txt')).read_text().splitlines()[0]
    else:assert p.stdout==(O/(name+'.stdout.txt')).read_bytes()
    proof.append(dict(name=name,rc=p.returncode,stdout_byte_equal=True,stdout_sha256=hashlib.sha256(p.stdout).hexdigest()))
    print(name,p.returncode,flush=True)
with zipfile.ZipFile(R/'lab/out/dictionary-split-candidate-20261006/kernel-r3.jar') as old,zipfile.ZipFile(A/'kernel.jar') as new:
    before={n:old.read(n) for n in old.namelist() if n.endswith('.class')};after={n:new.read(n) for n in new.namelist() if n.endswith('.class')}
    assert before.keys()<=after.keys();changed=sorted(n for n in before if before[n]!=after[n]);added=sorted(after.keys()-before.keys())
    assert changed==sorted(['com/streamhouse/oracle/'+n+'.class' for n in ['ArchivedDictionaryHistory','ArchivedDictionaryIndexSplit$Closed','ArchivedDictionaryIndexSplit$Context','ArchivedDictionaryIndexSplit','DictionaryHistoryRows','HeapRowPatch','IndexUndoPair','TransactionStart']]),changed
    assert added==['com/streamhouse/oracle/ArchivedDictionaryIndexRestores.class'],added
(V/'regression-proof.json').write_text(json.dumps(dict(cases=proof,previous_classes=len(before),unchanged_classes=len(before)-len(changed),changed_classes=changed,added_classes=added,source_actions=0,target_actions=0),indent=2))
