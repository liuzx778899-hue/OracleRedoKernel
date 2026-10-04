"""Native closed undo graphs determine surviving row identities; compare their ordered complete images and SQL states."""
import json,re,subprocess,sys
from pathlib import Path
dump,baseline,truth,actual=map(Path,sys.argv[1:5]);base=Path(__file__).parent;objects=[];live=set()
for line in baseline.read_text().splitlines():
 p=line.split('|')
 if p[0] in ('HEAP','INDEX') and p[1]=='BEFORE':objects.extend(p[4:6])
def native(script,args):
 p=subprocess.run([sys.executable,str(base/script),*args],capture_output=True)
 if p.returncode:raise RuntimeError('NATIVE_READER_FAILED:'+p.stderr.decode())
 return json.loads(p.stdout)
found=native('compare_object_transactions.py',[','.join(objects),'--native-only',str(dump)])
for row in found['native_discovery']:
 if row[0]!='DISCOVER':continue
 graph=native('compare_mixed_undo.py',['--concurrent','--objects',','.join(objects),row[1],'--native-only',str(dump)])
 for entry in graph['native_graph']:
  if entry[0]=='LIVE':live.add(tuple(map(int,entry[1].split(':'))))
def changed(c):
 result={}
 for line in c.splitlines():
  m=re.search(r'col\s+(\d+):\s*\[\s*(\d+)\]\s*(.*)',line);n=re.search(r'col\s+(\d+):\s*\*NULL\*',line)
  if m:
   raw=''.join(m[3].split()).lower()
   if len(raw)!=int(m[2])*2:raise RuntimeError('NATIVE_PARTIAL_UPDATE_BYTE_LENGTH')
   result[int(m[1])]='VALUE:'+raw
  elif n:result[int(n[1])]='NULL:'
 return result
source=(base/'compare_insert_delete_rows.py').read_text()
def replace(old,new):
 global source
 assert source.count(old)==1,old;source=source.replace(old,new)
replace("h[3] in ('11.2','11.3')", "h[3] in ('11.2','11.3','11.5') and 'xtype: XA' in c")
replace("  _,number,inverse=matched[0];slot=", "  if uba not in live:continue\n  _,number,inverse=matched[0];slot=")
replace("  else:\n   if key not in current", """  elif op=='11.5':
   if key not in current:raise RuntimeError('NATIVE_LIVE_UPDATE_MISSING_ROW')
   before=current[key].copy();prior=changed(inverse);values=changed(c)
   if set(prior)!=set(values) or int(re.search(r'ncol: (\\d+)',c)[1])!=columns:raise RuntimeError('NATIVE_LIVE_UPDATE_COLUMNS')
   for column,value in prior.items():
    if current[key][column]!=value:raise RuntimeError('NATIVE_LIVE_UPDATE_BEFORE')
   for column,value in values.items():current[key][column]=value
   after=current[key].copy();kind='UPDATE'
  else:
   after_insert={k:v.copy() for k,v in current.items()}
   if key not in current""")
replace("('AFTER_INSERT',after_insert)","('AFTER_PARTIAL_COMMIT',after_insert)")
replace("['INSERT_TX','DELETE_TX']","['PARTIAL_TX','DELETE_TX']")
replace('if seen!=txs:', 'txs=[txs[0],txs[0],txs[1]]\nif seen!=txs:')
replace("'scope':'fixed schema complete INSERT/DELETE native and SQL images; general DML and CDC unaccepted'","'scope':'native closed live identities and complete partial rollback row images; general DML and CDC unaccepted'")
exec(compile(source,str(base/'compare_insert_delete_rows.py')+'+native-live-partial','exec'))
