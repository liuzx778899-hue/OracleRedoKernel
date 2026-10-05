"""Independent native/SQL comparison for frozen V2 NULL-tail experiment, no source writes."""
import hashlib,json,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/v2-dml-capture-20261006'
reference=(R/'lab/compare_mixed_capture_rows.py').read_text()
reference=reference.replace("(O/'baseline/snapshot.stdout.txt').read_text()","Path(json.loads((O/'baseline-reference.json').read_text())['path']).read_text()")
reference=reference.replace("if 'TRUTH|'+name+'|0|mixed-seed|anchor' not in t or 'TRUTH_COUNT|'+name+'|1' not in t:errors.append(name+'_final')","if [l for l in t.splitlines() if l.startswith('TRUTH|'+name+'|')] != ['TRUTH|'+name+'|0|v2-seed|anchor']:errors.append(name+'_final')")
reference=reference.replace("bytes.fromhex(image[1].split(':')[1]).decode(),bytes.fromhex(image[2].split(':')[1]).decode()","('<NULL>' if image[1]=='NULL:' else bytes.fromhex(image[1].split(':')[1]).decode()),('<NULL>' if image[2]=='NULL:' else bytes.fromhex(image[2].split(':')[1]).decode())")
reference=reference.replace("v=values(c);assert set(v)=={0,1,2}","v=fullvalues(c);assert set(v)=={0,1,2}").replace("values(inverse)==dict(enumerate(current))","fullvalues(inverse)==dict(enumerate(current))")
helper=r'''
def fullvalues(text):
    v=values(text);n=int(re.search(r'cc:\s*(\d+)',text)[1]);assert 1<=n<=3 and set(v)==set(range(n))
    for i in range(n,3):
        assert re.search(r'^COLUMN\|BEFORE\|'+str(i+1)+r'\|'+str(i+1)+r'\|[^|]+\|VARCHAR2\|Y\|',baseline,re.M)
        v[i]='NULL:'
    return v
'''
reference=reference.replace('for record in re.split',helper+'\nfor record in re.split',1)
reference=reference.replace('old,new=values(inverse),values(c);assert set(old)==set(new)',r'''old,new=values(inverse),values(c)
            assert not old and set(new)=={1} and current[1:]==['NULL:','NULL:']
            assert re.search(r'ncol: 1 nnew: 0 size: -9\b',inverse)
            assert re.search(r'ncol: 2 nnew: 1 size: 9\b',c)
            assert len(bytes.fromhex(new[1].split(':')[1]))==8''')
comparator=O/'implicit-tail-independent-rows.py';assert not comparator.exists();comparator.write_text(reference)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
(O/'implicit-tail-rows-pin.json').write_text(json.dumps(dict(driver=sha(Path(__file__)),reference=sha(R/'lab/compare_mixed_capture_rows.py'),generated=sha(comparator)),indent=2))
def run(name,path,rc):
    args=[sys.executable,str(comparator),str(O),str(path)];assert not (O/(name+'.command.json')).exists()
    (O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=120)
    (O/(name+'.stdout.txt')).write_bytes(p.stdout);(O/(name+'.stderr.txt')).write_bytes(p.stderr);(O/(name+'.rc.txt')).write_text(str(p.returncode))
    assert p.returncode==rc,(name,p.stdout[-1500:],p.stderr[-1500:])
original=O/'implicit-tail-complete-batch.stdout.txt';run('implicit-tail-native-rows',original,0)
for suffix,alter in [('wrong-before',lambda s:s.replace('VALUE:76322d6166746572','VALUE:6261642d76616c7565')),('missing-delete',lambda s:'\n'.join(l for l in s.splitlines() if '|DELETE|' not in l))]:
    text=original.read_text();bad=alter(text);assert bad!=text;p=O/('implicit-tail-'+suffix+'.rows.txt');p.write_text(bad);run('implicit-tail-compare-'+suffix,p,1)
print('NATIVE_THREE_COMPLETE_IMAGES_SQL_AND_TWO_COUNTEREXAMPLES_VERIFIED')
