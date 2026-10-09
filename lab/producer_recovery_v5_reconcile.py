"""Offline itemized verification of retained r109 results; never replay product actions."""
from pathlib import Path
from decimal import Decimal
import json,hashlib,base64
R=Path('D:/work/vibecode/OracleRedoKernel');N=R/'lab/out/d3-stream-runtime-20261009/producer-product-r109'
source=(N/'executed.py').read_text()
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
proof=json.loads((N/'product-proof.json').read_text());projection=json.loads((N/'projection.stdout').read_text(),parse_float=Decimal)
e=projection['evidence'];SOURCE='oracle-d2-blocks';TARGET='oracle-recovery-RPROD5'
receipt=json.loads((N/'checkpoint.stdout').read_text())['data']
state=N/'prefix-state'/Path(receipt['checkpoint']['path']).name
frozen=R/'lab/out/d3-stream-runtime-20261009/producer-recovery-r107/business.state'
assert state.read_bytes()==frozen.read_bytes() and sha(state)==receipt['checkpoint']['sha256']
actual=(N/'actual.stdout').read_text()
exec('def image(values):'+source.split('def image(values):',1)[1].split('def enroll(prefix):',1)[0])
assert not (N/'candidate-normal-published.json').exists()
tail="assert 'COUNTS|46|46|1|46|1'"+source.split(" assert 'COUNTS|46|46|1|46|1'",1)[1].split('\nfinally:',1)[0]
exec(tail.replace('\n ','\n'))
assert (N/'source-stop.rc').read_text().strip()=='0' and (N/'helper-remove.rc').read_text().strip()=='0'
print('EVENTS=46;MISMATCHES=0;V5_FROZEN_STATE_BYTES_EQUAL=true;CHECKPOINT_ACK_BYTES_EQUAL=true;PRODUCT_RERUN=false')
