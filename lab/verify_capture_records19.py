"""Frozen capture/archived/native parity and whole-batch refusals; no DML/config/log switch."""
import hashlib,json,re,shutil,struct,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent;OLD=ROOT/'lab/out/closed-follow-20261005';OUT=ROOT/'lab/out/capture-records-20261005';CONTAINER='oraredo-19c-lab-20261004'
JAVA='D:/work/Java/jdk-17.0.7/bin/java.exe';JAVAC='D:/work/Java/jdk-17.0.7/bin/javac.exe'
OUT.mkdir(parents=True,exist_ok=False);jar=OUT/'kernel.jar';shutil.copyfile(ROOT/'target/oracle-redo-kernel-0.1.0-SNAPSHOT.jar',jar)

def run(name,args,expected=0,input_data=None):
    (OUT/(name+'.command.json')).write_text(json.dumps(list(map(str,args))))
    result=subprocess.run(list(map(str,args)),input=input_data,capture_output=True,timeout=240)
    for ext,data in [('stdout',result.stdout),('stderr',result.stderr)]:(OUT/(name+'.'+ext+'.txt')).write_bytes(data)
    (OUT/(name+'.rc.txt')).write_text(str(result.returncode))
    if (result.returncode==0)!=(expected==0) or expected and result.stdout:raise RuntimeError(name+': unexpected result; preserve')
    return result.stdout.decode()

run('freeze-tree',['git','archive','--format=zip','--output='+str(OUT/'source-tree.zip'),'HEAD'])
classes=OUT/'classes';classes.mkdir();run('compile-probe',[JAVAC,'-cp',jar,'-d',classes,ROOT/'lab/CapturedRecordsProbe.java'])
java=[JAVA,'-cp',str(jar)+';'+str(classes),'CapturedRecordsProbe']
binding=run('binding',['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}|{{.Config.Image}}|{{.State.Status}}']).strip()
assert binding=='OracleRedoKernel|container-registry.oracle.com/database/enterprise:19.3.0.0|running'
receipt=json.loads((OLD/'capture/receipt.json').read_text())
for item in receipt['captured']:
    sequence=item['sequence'];journal=OLD/'capture'/item['journal'];archive=OLD/('archive-'+str(sequence)+'.dbf')
    for mode in ('records','vectors'):
        run('capture-'+mode+'-'+str(sequence),java+[journal,item['sha256'],mode])
        run('archive-'+mode+'-'+str(sequence),[JAVA,'-jar',jar,mode,archive])
        assert (OUT/('capture-'+mode+'-'+str(sequence)+'.stdout.txt')).read_bytes()==(OUT/('archive-'+mode+'-'+str(sequence)+'.stdout.txt')).read_bytes()
    paths=[line.strip().split('|')[2] for line in (OLD/('step-'+str(sequence)+'.stdout.txt')).read_text().splitlines() if line.strip().startswith('ARCHIVE|')];assert len(paths)==1
    sql="whenever sqlerror exit failure rollback\nset pages 0 lines 32767 feedback off heading off\nalter session set tracefile_identifier='ORK_CAPTURE_RECORDS_"+str(sequence)+"';\nalter system dump logfile '"+paths[0]+"';\nselect value from v$diag_info where name='Default Trace File';\nexit success\n"
    (OUT/('dump-'+str(sequence)+'.sql')).write_text(sql)
    text=run('dump-'+str(sequence),['docker','exec','-i',CONTAINER,'sqlplus','-s','/','as','sysdba'],input_data=sql.encode())
    traces=[line.strip() for line in text.splitlines() if line.strip().startswith('/opt/oracle/') and line.strip().endswith('.trc')];assert len(traces)==1
    dump=OUT/('dump-'+str(sequence)+'.trc');run('export-dump-'+str(sequence),['docker','cp',CONTAINER+':'+traces[0],dump])
    run('native-'+str(sequence),[sys.executable,ROOT/'lab/compare_captured_dump.py',dump,OUT/('capture-records-'+str(sequence)+'.stdout.txt')])

original=(OLD/'capture/sequence-41.oracap').read_bytes();frames=[];offset=0
while offset<len(original):
    m,n=struct.unpack_from('<II',original,offset+8);end=offset+16+m+n+32;frames.append((offset,end,m));offset=end

def refusal(name,data):
    path=OUT/(name+'.oracap');path.write_bytes(data);run(name,java+[path,hashlib.sha256(data).hexdigest(),'records'],1)

refusal('truncated-frame',original[:-1]);refusal('missing-last-frame',original[:frames[-1][0]])
refusal('duplicate-frame',original+original[:frames[0][1]])
refusal('wrong-envelope',original[:20]+bytes([original[20]^1])+original[21:])
start,end,m=frames[2];metadata=json.loads(original[start+16:start+16+m]);payload=bytearray(original[start+16+m:end-32])
def replace_frame(metadata,payload):
    metadata['sha256']=hashlib.sha256(payload).hexdigest();encoded=json.dumps(metadata,sort_keys=True,separators=(',',':')).encode()
    frame=b'ORACAP01'+struct.pack('<II',len(encoded),512)+encoded+payload+hashlib.sha256(encoded+payload).digest();return original[:start]+frame+original[end:]
metadata['state']='OBSERVED_CHANGED';refusal('unstable-frame',replace_frame(metadata,payload))
metadata['state']='OBSERVED_STABLE';payload[40]^=1;refusal('rehashed-bad-checksum',replace_frame(metadata,payload))
# Corrupt the last actual record after earlier valid records; rehash frame, raw CRC and receipt.
last=(OUT/'capture-records-41.stdout.txt').read_text().splitlines()[-1].split('|');block,position=int(last[0]),int(last[1]);start,end,m=frames[block]
metadata=json.loads(original[start+16:start+16+m]);payload=bytearray(original[start+16+m:end-32]);payload[position+4]=0
struct.pack_into('<H',payload,14,0);value=0
for word in struct.unpack('<256H',payload):value^=word
struct.pack_into('<H',payload,14,value);refusal('late-record-validity',replace_frame(metadata,payload))
run('untrusted-receipt',java+[OLD/'capture/sequence-41.oracap','0'*64,'records'],1)
run('events-still-refused',[JAVA,'-jar',jar,'events',OLD/'archive-41.dbf'],1)
reports=[json.loads((OUT/('native-'+str(n)+'.stdout.txt')).read_text()) for n in (40,41,42)]
(OUT/'proof.json').write_text(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD']).decode().strip(),'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'native_records':sum(r['native_records'] for r in reports),'native_vectors':sum(r['vectors'] for r in reports),'whole_batch_capture_refusals':8,'cdc_events':0,'consumer_ack':False},indent=2))
print((OUT/'proof.json').read_text())
