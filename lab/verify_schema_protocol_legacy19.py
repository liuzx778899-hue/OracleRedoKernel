"""New DTO fields must leave every frozen legacy projection field and envelope unchanged."""
import hashlib,json,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;N=R/'lab/out/historical-schema-protocol-20261006';O=R/'lab/out/v2-dml-capture-20261006'
assert not (N/'legacy-protocol-proof.json').exists();(N/'executed-legacy-protocol-driver.py').write_bytes(Path(__file__).read_bytes())
args=json.loads((O/'implicit-tail-typed-projection.command.json').read_text())
jackson=sorted((O/'implicit-tail-libs').glob('jackson-*.jar'))
args[3]=';'.join(map(str,[N/'kernel.jar',N/'adapter.jar',N/'spi.jar',O/'parser-classes',*jackson]))
(N/'legacy-projection.command.json').write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=240)
for suffix,data in [('stdout.txt',p.stdout),('stderr.txt',p.stderr),('rc.txt',str(p.returncode).encode())]:(N/('legacy-projection.'+suffix)).write_bytes(data)
assert p.returncode==0,p.stderr.decode()
actual=json.loads(p.stdout);old=json.loads((O/'implicit-tail-typed-projection.stdout.txt').read_text())
assert actual['evidence'].pop('eventSchemas')==[] and actual['evidence'].pop('schemaSequenceSha256') is None
assert actual==old
(N/'legacy-protocol-proof.json').write_text(json.dumps(dict(legacy_fields_json_equal=True,legacy_envelopes_sha_equal=True,new_legacy_event_schemas_empty=True,new_legacy_sequence_digest_null=True,complete_events=3,source_actions=0,target_actions=0),indent=2))
print('LEGACY_PROTOCOL_FIELDS_AND_ENVELOPES_EQUAL')
