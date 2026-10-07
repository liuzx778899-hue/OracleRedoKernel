"""Compare committed product facts before/after ACK-only recovery."""
import hashlib, json
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/recovery-failures-20261007'
prefixes = ('TARGET|', 'ROW|', 'EVENT|', 'COMMIT|', 'BATCH|', 'CP|', 'WINDOW|', 'LEDGER|', 'GATES|')

def facts(name):
    return [v.strip() for v in (N / (name + '.stdout.txt')).read_text(encoding='utf-8').splitlines()
            if v.strip().startswith(prefixes)]

assert facts('ack-blocked-state') == facts('ack-recovered-state')
assert (N / 'ack-blocked-forensic.stdout.txt').read_bytes() == (N / 'ack-recovered-forensic.stdout.txt').read_bytes()
out = N / 'ack-recovery-no-rewrite-proof.json'
assert not out.exists()
proof = dict(target_ledger_members_commit_rows_checkpoint_batches_exact_unchanged=True,
             failure_file_references_and_original_reasons_exact_unchanged=True,
             new_batches=0, source_actions=0,
             files=[dict(path=f.relative_to(N).as_posix(), sha256=hashlib.sha256(f.read_bytes()).hexdigest())
                    for f in sorted((N / 'failures').rglob('*.applyfail.txt'))])
assert len(proof['files']) == 2
out.write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
