"""Independent native evidence for one concurrent slot relocation; no DML acceptance."""
import json, re, sys
from pathlib import Path

directory=Path(sys.argv[1])
fields=Path(sys.argv[2]) if len(sys.argv)>2 else directory/'fields.stdout.txt'
raw={}
for line in fields.read_text(encoding='utf-8-sig').splitlines():
    p=line.split('|');pos=tuple(map(int,p[:4]))
    if pos in raw:raise RuntimeError('DUPLICATE_RAW_POSITION')
    raw[pos]=(p[4],int(p[6]),[bytes.fromhex(x) for x in p[7:]])
native={}
native_original_uba=None
for record in re.split(r'(?=REDO RECORD - Thread:)',(directory/'dump.trc').read_text()):
    if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:continue
    rba=re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',record)
    scn=int(re.search(r'\nSCN: 0x([0-9a-f]+)',record)[1],16)
    changes=re.split(r'(?=CHANGE #\d+)',record)[1:]
    for change in changes:
        h=re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x0300013b OBJ:73062[^\n]*OP:(10\.\d+)\b',change)
        if not h:continue
        role=None
        if h[2]=='10.4' and re.search(r'xid:  +0x000a\.017\.000004c4',record):role='A_DELETE'
        if h[2]=='10.2' and re.search(r'xid:  +0x0004\.002\.0000024b',record):role='B_INSERT'
        if h[2]=='10.5' and re.search(r'slt: 23 wrp: 1220 flg: 0x0010',record):role='A_RESTORE'
        if role is None:continue
        if role=='A_DELETE':
            originals=[]
            for undo in re.split(r'(?=CHANGE #\d+)',record)[1:]:
                uh=re.search(r'CHANGE #\d+ CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:5\.1\b',undo)
                address=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',undo)
                if uh and address and 'opc: 10.22 [objn: 73062 objd: 73062' in undo and re.search(r'\n\s+xid: +0x000a\.017\.000004c4',undo):
                    originals.append((int(uh[1],16),int(address[1],16),int(address[2],16)))
            if len(originals)!=1:raise RuntimeError('NATIVE_ORIGINAL_INDEX_UNDO_COUNT')
            native_original_uba=originals[0]
        if role in native:raise RuntimeError('AMBIGUOUS_NATIVE_ROLE')
        leaf=re.search(r'itl: (\d+), sno: (\d+), row size (\d+)',change)
        pos=tuple(int(x,16) for x in rba.groups())+(int(h[1]),)
        native[role]={'position':pos,'scn':scn,'opcode':h[2],'itl':int(leaf[1]),'slot':int(leaf[2]),'size':int(leaf[3])}
errors=[]
if set(native)!={'A_DELETE','B_INSERT','A_RESTORE'}:raise RuntimeError('NATIVE_ROLES_MISSING')
for role,n in native.items():
    a=raw.get(n['position'])
    if a is None:errors.append(role+':MISSING_RAW_POSITION');continue
    op,scn,f=a
    if len(f)<2 or len(f[1])!=6:errors.append(role+':LEAF_LAYOUT');continue
    b=f[1];leaf=(b[0],int.from_bytes(b[2:4],'little'),int.from_bytes(b[4:6],'little'))
    if (op,scn,leaf)!=(n['opcode'],n['scn'],(n['itl'],n['slot'],n['size'])):errors.append(role+':RAW_NATIVE_DIFFERENCE')
    if b[1]!=(1 if role=='A_RESTORE' else 0):errors.append(role+':WRONG_MODE')
a,b,c=(native[k] for k in ('A_DELETE','B_INSERT','A_RESTORE'))
if not a['position']<b['position']<c['position'] or not a['scn']<b['scn']<c['scn']:errors.append('NATIVE_ORDER')
if (a['slot'],b['slot'],c['slot'])!=(1,1,2) or a['itl']!=c['itl'] or a['size']!=c['size']:errors.append('EXPECTED_RELOCATION_NOT_OBSERVED')
before=(directory/'session-a.stdout.txt').read_text()
middle=(directory/'session-b.stdout.txt').read_text()
sa=int(re.search(r'A_DELETED_SCN\|(\d+)',before)[1]);sb=int(re.search(r'B_COMMITTED_SCN\|(\d+)',middle)[1]);sc=int(re.search(r'A_BEFORE_ROLLBACK_SCN\|(\d+)',before)[1])
if not sa<sb<sc or not json.loads((directory/'b-finished-a-live.json').read_text())['a_live']:errors.append('SOURCE_COORDINATION_NOT_PROVEN')
truth=[line.split('|')[1:3] for line in (directory/'truth-capture.stdout.txt').read_text().splitlines() if line.startswith('FINAL_TRUTH|')]
if truth!=[['1','one'],['2','two'],['3','three']]:errors.append('FINAL_TRUTH_MISMATCH')
if (directory/'match.stdout.txt').stat().st_size or (directory/'match.rc.txt').read_text().strip()!='1' or 'MISSING_INDEX_COMPENSATION_ORIGINAL' not in (directory/'match.stderr.txt').read_text():errors.append('EXISTING_MATCHER_REFUSAL_NOT_PROVEN')
if len(sys.argv)>3:
    expected=['MATCH',':'.join(map(str,c['position'])),'10.23.1220',':'.join(map(str,native_original_uba)),'RESTORE',str(a['slot']),str(c['slot'])]
    actual=[line.split('|') for line in Path(sys.argv[3]).read_text(encoding='utf-8-sig').splitlines()]
    if actual!=[expected]:errors.append('POSITION_MATCH_OUTPUT_NATIVE_DIFFERENCE')
print(json.dumps({'native':native,'native_original_uba':native_original_uba,'source_scns':[sa,sb,sc],'errors':errors,'mismatches':len(errors),'slot_immutable_assumption':'FALSIFIED','existing_matcher':'SAFE_REFUSAL_SUPPORT_GAP','position_match_compared':len(sys.argv)>3,'cdc_events':0,'scope':'one leaf concurrent insert shifts deleted key; no complete transaction acceptance'},indent=2))
sys.exit(bool(errors))
