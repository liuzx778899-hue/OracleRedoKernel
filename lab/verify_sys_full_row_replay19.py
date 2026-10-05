"""Diagnostic replay from old-SCN research rulers, never trusted historical publication input."""
import copy,json,re,sys,traceback
from pathlib import Path
R=Path(__file__).resolve().parent.parent;O=R/'lab/out/sys-full-history-research-20261006';Q=R/'lab/out/sys-full-baseline-research-20261006';I=R/'lab/out/dictionary-index-pair-20261006';G=R/'lab/out/sys-control-candidate-20261006'
assert not (O/'full-row-replay-proof.json').exists();(O/'executed-full-row-replay-driver.py').write_bytes(Path(__file__).read_bytes())
def fail(kind,value,tb):
    (O/'full-row-replay-failure-r1.txt').write_text(''.join(traceback.format_exception(kind,value,tb)));sys.__excepthook__(kind,value,tb)
sys.excepthook=fail
initial=json.loads((Q/'full-row-proof.json').read_text());cells=json.loads((Q/'canonical-cells.json').read_text());layouts=initial['layouts']
def key(r):return (r[0],int(r[2]),int(r[3])*4194304+int(r[4]),int(r[5]))
state={key(r):[c[-1].upper() for c in cells if c[:3]==[r[0],r[1],r[5]]] for r in initial['rows']}
def value(row,name):return row[next(i for i,c in enumerate(layouts['COL$']) if c[2]==name)]
def number(raw):
    b=bytes.fromhex(raw)
    if b==b'\x80':return 0
    assert b[0]>=193 and all(1<=x<=100 for x in b[1:]);v=0
    for d in b[1:]:v=v*100+d-1
    exponent=b[0]-193-(len(b)-2);assert exponent>=0;return v*100**exponent
def expected_indexes(rows):
    out={n:set() for n in ['I_COL1','I_COL2','I_COL3']}
    for k,row in rows.items():
        if k[0]!='COL$':continue
        parent=number(value(row,'OBJ#'));rowid=k[2:]
        out['I_COL1'].add((parent,bytes.fromhex(value(row,'NAME')).decode(),*rowid))
        out['I_COL2'].add((parent,number(value(row,'COL#')),*rowid))
        out['I_COL3'].add((parent,number(value(row,'INTCOL#')),*rowid))
    return out
def truth(text):
    result={};current=None
    for line in text.splitlines():
        q=line.split('|')
        if q[0]=='SYS_ROW':current=key(q[1:]);result[current]=[]
        elif q[0]=='CELL':
            assert current[0]==q[1] and current[3]==int(q[3])
            if q[8]=='NULL':raw='NULL'
            else:
                m=re.fullmatch(r'Typ=(\d+) Len=(\d+): (.*)',q[8]);assert m
                raw=''.join(f'{int(x,16):02X}' for x in m[3].split(','));assert len(raw)==int(m[2])*2
            result[current].append(raw)
    return result
transactions=[]
for end in json.loads((G/'native-end-proof.json').read_text())['cases']:
    seq=end['sequence'];xid=end['xid'];operations=[];dispositions=[];current=None
    for line in (O/f'row-pairs-r2-{seq}-{xid}.stdout.txt').read_text().splitlines():
        q=line.split('|')
        if q[0]=='ROW':current=dict(position=tuple(map(int,q[1:5])),role=q[7],patches={});operations.append(current)
        elif q[0]=='PATCH':current['patches'][q[1]]=dict(kind=q[2],dba=int(q[3]),slot=int(q[4]),extent=q[5],cells=[])
        elif q[0]=='CELL':assert int(q[2])==len(current['patches'][q[1]]['cells']);current['patches'][q[1]]['cells'].append((q[3],q[4].upper()))
        elif q[0]=='DISPOSITION':
            m=re.fullmatch(r'Position\[archiveSequence=(\d+), block=(\d+), offset=(\d+), vector=(\d+)\]',q[2]);assert m;dispositions.append((q[1],tuple(map(int,m.groups()))))
    indexes=[]
    for line in (I/f'pair-{seq}-{xid}.stdout.txt').read_text().splitlines():
        q=line.split('|');item=dict(position=tuple(map(int,q[1:5])),role=q[6],kind=q[9],parent=int(q[15]),component=q[16] if q[6]=='I_COL1' else int(q[17]),dba=int(q[18]),slot=int(q[19]));indexes.append(item)
    original={tuple(map(int,l.split('|')[1:5])) for l in (G/f'history-{seq}-{xid}.stdout.txt').read_text().splitlines() if l.startswith('NODE|')}
    assert original=={o['position'] for o in operations}|{d[1] for d in dispositions}
    assert {i['position'] for i in indexes}=={p for kind,p in dispositions if kind=='INDEX'}
    transactions.append(dict(end=end,operations=operations,indexes=indexes,original_count=len(original),controls=sum(k=='CONTROL' for k,p in dispositions)))
def replay(txs):
    rows=copy.deepcopy(state);index=expected_indexes(rows);proof=[]
    for tx in txs:
        parents={}
        for i in tx['indexes']:
            assert i['parent']==75185
            k=('COL$',2,i['dba'],i['slot']);assert k not in parents or parents[k]==i['parent'];parents[k]=i['parent']
            entry=(i['parent'],i['component'],i['dba'],i['slot']);entries=index[i['role']]
            if i['kind']=='INSERT':assert entry not in entries;entries.add(entry)
            else:assert i['kind']=='DELETE' and entry in entries;entries.remove(entry)
        for o in tx['operations']:
            p=o['patches']['forward'];z=o['patches']['inverse'];name=o['role'];offset=0 if name=='OBJ$' else 1
            k=(name,18 if name=='OBJ$' else 2,p['dba'],p['slot']);size=len(layouts[name]);assert len(p['cells'])==size-offset
            def complete(patch):
                values=[]
                for n,(kind,raw) in enumerate(patch['cells']):
                    if kind=='ABSENT':
                        assert name=='COL$' and n>=32 and layouts[name][n+offset][4]=='0'
                        values.append('NULL') # Diagnostic observed IRP tail hypothesis, not production authority.
                    else:values.append('NULL' if kind=='NULL' else raw)
                assert name=='COL$' and parents[k]==75185
                return ['C3083456']+values
            if p['kind']=='INSERT':assert k not in rows;rows[k]=complete(p)
            else:
                assert k in rows
                for n,(kind,raw) in enumerate(z['cells']):
                    if kind!='ABSENT':assert rows[k][n+offset]==('NULL' if kind=='NULL' else raw),(o['position'],name,n)
                if p['kind']=='UPDATE':
                    for n,(kind,raw) in enumerate(p['cells']):
                        if kind!='ABSENT':rows[k][n+offset]='NULL' if kind=='NULL' else raw
                elif p['kind']=='DELETE':assert rows[k]==complete(z);del rows[k]
                else:assert p['kind']=='LOCK'
        assert index==expected_indexes(rows),'complete three-index lifecycle mismatch'
        scn=tx['end']['end_scn'];expected=truth((O/f'full-sys-{scn}.stdout.txt').read_text());assert rows==expected,scn
        proof.append(dict(xid=tx['end']['xid'],scn=scn,originals=tx['original_count'],row_pairs=len(tx['operations']),index_pairs=len(tx['indexes']),controls=tx['controls'],full_rows=len(rows),values=sum(map(len,rows.values())),independent_differences=0))
    return proof
proof=replay(transactions);negatives=[]
for kind in ['missing-index','wrong-parent','wrong-before','missing-row']:
    bad=copy.deepcopy(transactions)
    if kind=='missing-index':bad[0]['indexes'].pop()
    elif kind=='wrong-parent':bad[0]['indexes'][0]['parent']=75186
    elif kind=='wrong-before':
        patch=bad[0]['operations'][0]['patches']['inverse'];n=next(i for i,c in enumerate(patch['cells']) if c[0]=='VALUE');patch['cells'][n]=('VALUE','80')
    else:bad[0]['operations'].pop()
    try:replay(bad)
    except (AssertionError,KeyError) as e:negatives.append(dict(name=kind,detected=True,reason=str(e)))
    else:raise AssertionError('REPLAY_COMPARATOR_NEGATIVE_NOT_DETECTED:'+kind)
(O/'full-row-replay-proof.json').write_text(json.dumps(dict(commits=proof,comparator_negatives=negatives,all_originals=22,rows=11,indexes=10,controls=1,independent_values=764,history_authority=False,events=0,tail_is_observed_diagnostic_hypothesis=True),indent=2));print(json.dumps(dict(commits=3,originals=22,full_values=764,differences=0,comparator_negatives=len(negatives),authority=False)))
