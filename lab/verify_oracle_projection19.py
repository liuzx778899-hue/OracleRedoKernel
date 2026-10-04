"""Real raw-capture -> typed kernel -> product SPI projection, no target/source action."""
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'lab/out/oracle-projection-20261005'
BUILD = ROOT / 'lab/out/release-oracle-projection-20261005'
KERNEL = ROOT / 'lab/out/captured-batch-20261005'
OLD = ROOT / 'lab/out/closed-follow-20261005'
FAULTS = ROOT / 'lab/out/capture-row-faults-20261005'
JAVA = 'D:/work/Java/jdk-17.0.7/bin/java.exe'
JAVAC = 'D:/work/Java/jdk-17.0.7/bin/javac.exe'
OUT.mkdir(parents=True, exist_ok=False)
build = json.loads((BUILD / 'proof.json').read_text())
paths = []
for file, expected in [(BUILD / 'streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar', build['adapter_jar_sha256']),
                       (BUILD / 'streamhouse-spi-0.1.0-SNAPSHOT.jar', build['spi_contract_jar_sha256']),
                       (KERNEL / 'kernel.jar', build['kernel_sha256'])]:
    assert hashlib.sha256(file.read_bytes()).hexdigest() == expected
    target = OUT / file.name; shutil.copyfile(file, target); paths.append(target)


def run(name, args, rc=0, empty=False, reason=None):
    args = list(map(str, args)); (OUT / (name + '.command.json')).write_text(json.dumps(args))
    result = subprocess.run(args, capture_output=True, timeout=180)
    for ext, data in [('stdout', result.stdout), ('stderr', result.stderr)]:
        (OUT / (name + '.' + ext + '.txt')).write_bytes(data)
    (OUT / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == rc and (not empty or not result.stdout), name
    assert reason is None or reason.encode() in result.stderr, name
    return result


classes = OUT / 'classes'; classes.mkdir()
classpath = ';'.join(map(str, paths))
run('compile-probe', [JAVAC, '-encoding', 'UTF-8', '-cp', classpath, '-d', classes, ROOT / 'lab/OracleProjectionProbe.java'])
base = [JAVA, '-cp', classpath + ';' + str(classes), 'OracleProjectionProbe']
inputs = json.loads((KERNEL / 'actual.command.json').read_text())[4:]
run('actual', base + ['project'] + inputs)
run('repeat-adapter', base + ['project'] + inputs)
assert (OUT / 'actual.stdout.txt').read_bytes() == (OUT / 'repeat-adapter.stdout.txt').read_bytes()
compare = [sys.executable, ROOT / 'lab/compare_oracle_projection19.py', OUT / 'actual.stdout.txt', OLD, KERNEL / 'actual.stdout.txt']
run('independent-sql-values', compare)
run('public-gate', base + ['public', OLD / 'capture/sequence-42.oracap'], 2, empty=True,
    reason='ORACLE_DICTIONARY_PUBLICATION_GATES_NOT_ACCEPTED')
for name in ('missing-sequence', 'late-before-rows', 'missing-begin-control', 'untrusted-baseline'):
    old = json.loads((FAULTS / (name + '.command.json')).read_text())
    run(name, base + ['project'] + old[5:], 1, empty=True)

# Last actual forward value gets invalid AL32UTF8. Keep exact layout, undo/before,
# complete raw checksum and envelope integrity so the kernel byte stage succeeds.
data = Path(inputs[-2]).read_bytes(); frames = []; offset = 0; matches = 0
while offset < len(data):
    m, n = struct.unpack_from('<II', data, offset + 8); assert n == 512
    metadata = json.loads(data[offset + 16:offset + 16 + m]); payload = bytearray(data[offset + 16 + m:offset + 16 + m + n])
    index = payload.find(b'blocking-closed')
    if index >= 0:
        assert payload.find(b'blocking-closed', index + 1) == -1
        payload[index] = 0x80; matches += 1
    frames.append((metadata, payload)); offset += 16 + m + n + 32
assert matches == 1 and offset == len(data)
poison = bytearray()
for metadata, payload in frames:
    struct.pack_into('<H', payload, 14, 0); crc = 0
    for word in struct.unpack('<256H', payload): crc ^= word
    struct.pack_into('<H', payload, 14, crc)
    metadata['sha256'] = hashlib.sha256(payload).hexdigest()
    encoded = json.dumps(metadata, sort_keys=True, separators=(',', ':')).encode()
    poison += b'ORACAP01' + struct.pack('<II', len(encoded), 512) + encoded + payload + hashlib.sha256(encoded + payload).digest()
path = OUT / 'last-value-invalid-utf8.oracap'; path.write_bytes(poison)
selected = inputs[:-2] + [str(path), hashlib.sha256(poison).hexdigest()]
raw = run('last-value-kernel-passes', base + ['kernel-only'] + selected).stdout.decode()
assert raw.startswith('RAW_BATCH|3|80')
run('last-value-projection-refuses', base + ['project'] + selected, 1, empty=True, reason='INVALID_VARCHAR2_ENCODING')
lines = (OUT / 'actual.stdout.txt').read_text().splitlines()
for name in ('missing-event', 'wrong-after'):
    changed = lines.copy(); index = next(i for i, line in enumerate(changed) if line.startswith('PROJECTED|'))
    if name == 'missing-event': del changed[index]
    else:
        fields = changed[index].split('|')
        # Preserve a valid encoding/count so the comparator exercises actual value
        # inequality rather than relying on a parse exception.
        fields[11] = re.sub(r'TXT=VARCHAR2:[^;]+', 'TXT=VARCHAR2:eA', fields[11])
        changed[index] = '|'.join(fields)
    path = OUT / (name + '.txt'); path.write_text('\n'.join(changed) + '\n')
    args = compare.copy(); args[2] = path; run(name, args, 1)
proof = {'bounded_projected_events': 3, 'repeat_bytes_equal': True, 'adapter_tests': 5,
         'public_decoder_rc': 2, 'public_decoder_events': 0, 'raw_kernel_refusals_at_adapter_boundary': 4,
         'late_utf8_raw_kernel_events': 3, 'late_utf8_projection_stdout_bytes': 0,
         'independent_comparator_refusals': 2, 'source_actions': 0, 'target_actions': 0,
         'legacy_lsn_and_unknown_source_fields': 'null', 'oracle_positions': 'typed sidecar; not SQL Server LSN',
         'product_deployed': False, 'site_accepted': False}
(OUT / 'proof.json').write_text(json.dumps(proof, indent=2)); print(json.dumps(proof))
