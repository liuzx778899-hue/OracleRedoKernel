"""Independent real Oracle audit/membership/receipt and original source SQL images."""
import base64,json,sys
from pathlib import Path

actual,prefix,actions,reference,ack=sys.argv[1:6];actual=Path(actual);actions=Path(actions);reference=Path(reference);ack=Path(ack)
lines=[line.strip().split('|') for line in actual.read_text().splitlines() if line.strip()]
expected_meta=[line.split('|') for line in reference.read_text().splitlines() if line.startswith('EVENT|')]
assert len(expected_meta)==3
batch=next(line.split('|')[1] for line in reference.read_text().splitlines() if line.startswith('BATCH|'))
schema=next(line.split('|')[4] for line in reference.read_text().splitlines() if line.startswith('BATCH|'))


def image(txt,note):
    b64=lambda text:base64.urlsafe_b64encode(text.encode('utf-8')).decode().rstrip('=')
    return 'ID=NUMBER:11;TXT=VARCHAR2:'+b64(txt)+';NOTE=VARCHAR2:'+b64(note)


expected=[];before=image('blocking-closed','new-note')
for sequence,meta in zip((40,41,42),expected_meta):
    truth=[line.strip().split('|') for line in (actions/('step-'+str(sequence)+'.stdout.txt')).read_text().splitlines() if line.strip().startswith('TRUTH|')]
    assert len(truth)==1 and truth[0][1:3]==[str(sequence),'11']
    after=image(truth[0][3],truth[0][4]);expected.append(['APPLIED',prefix,'oracle1:'+meta[1],meta[2],before,after,'UPDATE']);before=after
applications=[line for line in lines if line[0]=='APPLIED'];members=[line for line in lines if line[0]=='MEMBER'];receipts=[line for line in lines if line[0]=='RECEIPT']
target='oracle-target-'+prefix+'-20261005';errors=[]
if sorted(applications)!=sorted(expected):errors.append('SOURCE_SQL_COMPLETE_AUDIT_IMAGES')
expected_members=[['MEMBER',prefix,batch,str(i),e[2]] for i,e in enumerate(expected)]
if members!=expected_members:errors.append('TARGET_BATCH_MEMBERS')
if receipts!=[['RECEIPT',prefix,batch,target,schema,'3']]:errors.append('TARGET_COMMIT_RECEIPT')
if [line for line in lines if line[0]=='STATE']!=[['STATE',prefix,'3','3','1']]:errors.append('TARGET_COUNTS')
if [line for line in lines if line[0]=='ROW']!=[['ROW',prefix,'11','blocking-closed','new-note']]:errors.append('FINAL_TARGET_ROW')
expected_ack=('ORK_ORACLE_TARGET_ACK_V1|'+target+'|'+batch+'|'+schema+'|3\n').encode()
if ack.read_bytes()!=expected_ack:errors.append('ACK_COMMIT_RECEIPT')
print(json.dumps({'actual_oracle_target':prefix,'original_source_commits':3,'complete_audit_images':6,
                  'event_members':len(members),'target_commit_receipts':len(receipts),'mismatches':len(errors),'errors':errors,
                  'scope':'owned ID11 three UPDATE target fixture; not general DML/site acceptance'}))
sys.exit(bool(errors))
