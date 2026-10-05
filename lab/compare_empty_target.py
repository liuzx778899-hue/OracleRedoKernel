"""Independent empty SQL receipt and complete checkpoint/ACK comparator, using frozen raw metadata."""
import base64,hashlib,json,sys
from pathlib import Path
F,sql,checkpoint,ack=map(Path,sys.argv[1:5])
raw=(F/'complete-batch-r2.stdout.txt').read_text().splitlines();batch=next(s.split('|') for s in raw if s.startswith('BATCH|'));input=next(s.split('|') for s in raw if s.startswith('INPUT|'));assert not any(s.startswith('EVENT|') or s.startswith('ROW|') for s in raw)
_,batch_sha,baseline_sha,baseline_scn,schema,*_=batch
seq,capture_sha=input[1:3];next_scn=input[5];leaf=(F/'raw-leaf.stdout.txt').read_text().strip().split('|')[1]
blocks=str(next(json.loads(s)['blocks'] for s in (F/'collector.stdout.txt').read_text().splitlines() if json.loads(s).get('captured_sequence')==int(seq)))
lines=[s.strip() for s in sql.read_text().splitlines() if s.strip()]
assert lines==['STATE|0|0|0|1','RECEIPT|'+batch_sha+'|oracle-dml-EMPT-20261005|'+schema+'|0'],'EMPTY_TARGET_RECEIPT_OR_COUNTS_DIFFER'
target=base64.urlsafe_b64encode(b'ORKTGT_CMT_20261005.EMPT|oracle-dml-EMPT-20261005').decode().rstrip('=')
body='ORK_ORACLE_CONSUMER_V1\nTARGET|'+target+'\nFRONTIER|'+batch_sha+'\n'+'|'.join(['BATCH',batch_sha,schema,baseline_sha,baseline_scn,leaf,'1276726821:1276717861:318767104:1:1245693159:1920977:677737149:3',seq,blocks,next_scn,'0',seq+':'+capture_sha])+'\n'
assert checkpoint.read_bytes()==(body+'SHA256|'+hashlib.sha256(body.encode()).hexdigest()+'\n').encode(),'EMPTY_CHECKPOINT_FIELDS_DIFFER'
assert ack.read_bytes()==('ORK_ORACLE_TARGET_ACK_V1|oracle-dml-EMPT-20261005|'+batch_sha+'|'+schema+'|0\n').encode(),'EMPTY_ACK_FIELDS_DIFFER'
print(json.dumps({'empty_target_receipt_counts_checkpoint_ack_zero_differences':True,'raw_metadata_sha256':hashlib.sha256((F/'complete-batch-r2.stdout.txt').read_bytes()).hexdigest()}))
