"""Physical SYS clustered patches against native and selected ASOF fields. No parent/history authority."""
import hashlib,json,re,subprocess,sys,traceback,zipfile
from pathlib import Path
R=Path(__file__).resolve().parent.parent;H=R/'lab/out/v2-historical-product-20261006';S=R/'lab/out/sys-cluster-row-research-20261006';O=R/'lab/out/sys-cluster-row-candidate-20261006'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def fail(kind,value,tb):
    (O/'driver-failure-r1.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=fail
assert not (O/'execution-pin.json').exists();(O/'executed-driver.py').write_bytes(Path(__file__).read_bytes())
kernel=O/'kernel.jar';assert sha(kernel)=='9288afbcbd0e53999ed38631121078731e8fd265fb350f26960f998b9ef24557'
old=R/'lab/out/sys-control-candidate-20261006/kernel.jar'
with zipfile.ZipFile(old) as a,zipfile.ZipFile(kernel) as b:
    unchanged=[n for n in a.namelist() if n.endswith('.class')]
    assert all(a.read(n)==b.read(n) for n in unchanged)
    added=[n for n in b.namelist() if n.endswith('.class') and n not in unchanged]
    assert sorted(added)==['com/streamhouse/oracle/ClusterRowPatch$Patch.class','com/streamhouse/oracle/ClusterRowPatch.class']
baseline=H/'snapshots/100644812901937152.snapshot';receipt='e16e6275651894614dc9a79e42381338ddbf0771aa68c971df66f35abd5c0e27'
assert sha(baseline)==receipt
pins={str(p.relative_to(R)):sha(p) for p in [kernel,old,baseline,R/'lab/ClusterRowsProbe.java',Path(__file__),S/'row-shapes.json',S/'independent-sys-layout-history.stdout.txt',H/'archive-66.dbf',H/'archive-67.dbf']}
(O/'execution-pin.json').write_text(json.dumps(pins,indent=2))
actual=[]
for seq,xid in [(66,'10.18.2272'),(66,'3.30.705'),(67,'3.26.705')]:
    name=f'rows-{seq}-{xid}';args=['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(kernel)+';'+str(O/'classes'),'ClusterRowsProbe',str(baseline),receipt,str(H/f'archive-{seq}.dbf'),xid]
    (O/(name+'.command.json')).write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=180)
    for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(O/(name+'.'+suffix)).write_bytes(data)
    assert p.returncode==0,p.stderr.decode()
    for line in p.stdout.decode().splitlines():
        q=line.split('|')
        if q[0]=='ROW':r=dict(xid=xid,position=list(map(int,q[1:5])),role=q[5],side=q[6],kind=q[7],slot=int(q[8]),table=int(q[9]),key_index=None if q[10]=='null' else int(q[10]),columns=[]);actual.append(r)
        else:assert q[0]=='COLUMN' and int(q[1])==len(r['columns']);r['columns'].append(dict(state=q[2],hex=q[3]))
expected=json.loads((S/'row-shapes.json').read_text());assert len(actual)==len(expected)==16
compared=0
for a,e in zip(actual,expected):
    assert (a['position'],a['side'],a['slot'],a['table'])==(e['position'],e['side'],e['slot'],e['cluster_tab'])
    native={}
    blocks=re.split(r'(?m)(?=^col\s+\d+:)',e['native_columns_text'])[1:]
    for block in blocks:
        h=re.match(r'col\s+(\d+):\s*(?:\[\s*(\d+)\]|(\*NULL\*))',block);assert h
        index=int(h[1]);body=block[h.end():]
        data='' if h[3] else ''.join(re.findall(r'(?<!\S)([0-9a-f]{2})(?!\S)',body))
        if h[2]:assert len(data)//2==int(h[2]),(a['position'],index,data,h[2])
        native[index]=dict(state='NULL' if h[3] else 'VALUE',hex=data)
    assert {i:c for i,c in enumerate(a['columns']) if c['state']!='ABSENT'}==native,(a['position'],native)
    compared+=len(native)
truth=(S/'independent-sys-layout-history.stdout.txt').read_text().splitlines();layouts={}
for line in truth:
    if line.startswith('LAYOUT|'):
        q=line.split('|');layouts.setdefault(int(q[1]),{})[q[5]]=int(q[3])-2
assert layouts[21]['NAME']==4 and layouts[21]['INTCOL#']==13 and layouts[21]['PROPERTY']==14
assert layouts[21]['ACDRRESCOL#']==12 and layouts[21]['DEFAULT$']==35
assert len(layouts[4])==45 and len(layouts[21])==37
selected_names=['COL#','SEGCOL#','SEGCOLLENGTH','OFFSET','NAME','TYPE#','LENGTH','NULL$','INTCOL#','PROPERTY','CHARSETID','CHARSETFORM']
images={};state=None
for a in actual:
    if a['role']!='COL$' or a['slot']!=51:continue
    if a['side']=='inverse':
        if a['kind']=='DELETE':assert state is None
        elif a['kind']=='INSERT':assert state is not None and a['columns']==state
        elif a['kind']=='UPDATE':
            assert state is not None
            assert all(c==state[i] for i,c in enumerate(a['columns']) if c['state']!='ABSENT')
    else:
        if a['kind']=='INSERT':state=a['columns'].copy()
        elif a['kind']=='UPDATE':
            for i,c in enumerate(a['columns']):
                if c['state']!='ABSENT':state[i]=c
        elif a['kind']=='DELETE':state=None
        images[a['xid']]=None if state is None else state.copy()
matched=0
for scn,xid in [(2522904,'10.18.2272'),(2522911,'3.30.705'),(2522937,'3.26.705')]:
    rows=[l.split('|') for l in truth if l.startswith(f'COL_TRUTH|{scn}|75185|51|')]
    image=images[xid]
    if image is None:assert not rows;continue
    assert len(rows)==1
    for name,dump in zip(selected_names,rows[0][4:]):
        m=re.match(r'Typ=\d+ Len=(\d+): (.*)',dump);assert m
        data=''.join(f'{int(x,16):02x}' for x in m[2].split(','));assert len(data)//2==int(m[1])
        assert image[layouts[21][name]]==dict(state='VALUE',hex=data),(scn,name)
        matched+=1
assert pins=={p:sha(R/p) for p in pins}
proof=dict(kernel_sha256=sha(kernel),unchanged_prior_class_files=len(unchanged),added_classes=added,physical_row_sides=16,native_column_comparisons=compared,selected_ASOF_values=matched,drop_absence_verified=True,system_layout_physical_internal_reordering_verified=True,source_actions=0,events=0,patches=actual,parent_object_binding_verified=False,SYS_transaction_row_pairs_verified=False,complete_SYS_row_images_verified=False,historical_dictionary_authorized=False,product_rebuilt=False)
(O/'proof.json').write_text(json.dumps(proof,indent=2));print(json.dumps({k:v for k,v in proof.items() if k not in ['patches']}))
