"""Independent native full-XID header associations; not physical-row/head authorization."""
import json,re,sys
from pathlib import Path
target=tuple(map(int,sys.argv[1].split('.'))); records=[]; originals=[]; windows={}; markers=[]
def meta(c):
    m=re.search(r'ktub[ul] redo: slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci: (\d+) opc: (\d+)\.(\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]',c)
    if m is None:raise RuntimeError('NATIVE_METADATA_MISSING')
    return tuple(int(m[i],16 if i in (3,4) else 10) for i in range(1,11))
for filename in sys.argv[3:]:
    for r in re.split(r'(?=REDO RECORD - Thread:)',Path(filename).read_text()):
        if not r.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in r.splitlines()[0]:continue
        at=tuple(int(x,16) for x in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',r).groups()); changes=[]
        for c in re.split(r'(?=CHANGE #\d+)',r)[1:]:
            h=re.search(r'^CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+)[^\n]*OP:(\d+\.\d+)\b',c)
            if h:changes.append((int(h[1]),int(h[2],16),h[3],c))
        records.append((at,changes))
        for n,dba,op,c in changes:
            if op!='5.1':continue
            m=meta(c);identity=re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',c)
            xid=tuple(int(x,16) for x in identity.groups());address=re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)',c)
            uba=(dba,int(address[1],16),int(address[2],16));begin='\nBegin trans' in c;segment=None
            if begin:
                controls=[]
                for _,seg,cop,control in changes:
                    pointer=re.search(r'uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)',control)
                    slot=re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',control)
                    if cop=='5.2' and pointer and slot and tuple(int(x,16) for x in pointer.groups())==uba and tuple(int(x,16) for x in slot.groups())==xid[1:]:controls.append(seg)
                if len(controls)!=1:raise RuntimeError('NATIVE_BEGIN_BINDING')
                segment=controls[0]
                if xid in windows:raise RuntimeError('NATIVE_DUPLICATE_FULL_XID')
                windows[xid]={'first':at,'segment':segment,'last':None}
            originals.append({'at':at+(n,),'xid':xid,'uba':uba,'meta':m,'begin':begin,'segment':segment})
        for n,dba,op,c in changes:
            if op=='5.4':
                end=re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)',c)
                if end:
                    for xid,w in windows.items():
                        if w['last'] is None and w['segment']==dba and tuple(int(x,16) for x in end.groups())==xid[1:]:w['last']=at
            if op in ('5.6','5.11'):markers.append({'at':at+(n,),'dba':dba,'meta':meta(c),'begin':'\nBegin trans' in c})
if target not in windows or windows[target]['last'] is None:raise RuntimeError('NATIVE_TARGET_WINDOW_INCOMPLETE')
window=windows[target];expected=[]
for c in markers:
    if not window['first']<=c['at'][:3]<=window['last']:continue
    matches=[];m=c['meta']
    for u in originals:
        n=u['meta'];w=windows.get(u['xid'])
        if u['at'][:3]>=c['at'][:3] or u['begin']!=c['begin'] or n[:2]!=m[:2] or n[3:]!=m[3:]:continue
        if c['begin']:
            if u['segment']!=c['dba']:continue
        elif u['uba'][0]!=c['dba'] or u['xid'][2]&0xffff!=m[1]:continue
        if w is None or w['last'] is None:raise RuntimeError('NATIVE_OWNER_WINDOW_INCOMPLETE')
        if w['first']<=c['at'][:3]<=w['last']:matches.append(u)
    if len(matches)!=1:raise RuntimeError('NATIVE_ASSOCIATION_NOT_UNIQUE')
    u=matches[0];expected.append(['ASSOC',':'.join(map(str,c['at'])),'.'.join(map(str,u['xid'])),':'.join(map(str,u['at'])),':'.join(map(str,u['uba'])),str(u['meta'][7]),str(u['meta'][8]),str(u['begin']).lower()])
actual=[line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()];errors=[]
if actual!=expected:errors.append({'expected':expected,'actual':actual})
print(json.dumps({'target':'.'.join(map(str,target)),'native_associations':len(expected),'foreign_associations':sum(e[2]!='.'.join(map(str,target)) for e in expected),'mismatches':len(errors),'errors':errors,'scope':'source/full-XID/original header association only; head, physical match and full DML unaccepted','cdc_events':0},indent=2));sys.exit(bool(errors))
