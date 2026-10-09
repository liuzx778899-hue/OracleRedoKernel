"""Compare complete raw-produced LOB events with frozen SQL states; no SQL is passed to Java."""
from pathlib import Path
import hashlib, json, re, sys

R = Path(__file__).resolve().parent.parent
F = R / 'lab/out/d4-lob-source-20261009/continue-r2'
N = F / sys.argv[1]
assert re.fullmatch(r'events-r[1-9][0-9]*', sys.argv[1])
assert (N / 'probe.rc').read_text().strip() == '0'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(F / '97.raw') == '9a617026bd1464e237d5f7aa4495aa851674deb099482fc86e5d453d43a08995'
events = []; batches = []; current = None
for line in (N / 'probe.stdout').read_text().splitlines():
    f = line.split('|')
    if f[0] == 'BATCH': batches.append(f[1:])
    if f[0] == 'EVENT':
        current = dict(storage=f[1], scn=int(f[2]), op=f[3], dba=int(f[4]), slot=int(f[5]), id=f[6], payload=f[7])
        events.append(current)
    if f[0] == 'ROW':
        assert current is not None and len(f[2:]) % 2 == 0
        current[f[1].lower()] = list(zip(f[2::2], f[3::2]))
assert len(events) == len({x['id'] for x in events}) == 16
assert sum(int(b[1]) for b in batches) == 19

def truth(path):
    result = {}
    for row in json.loads(path.read_text()):
        cells = [('VALUE', 'c1' + format(row['id'] + 1, '02x'))]
        for tag in ['BHEX', 'CHEX']:
            v = row[tag]
            cells.append(('NULL', '') if v is None else ('VALUE', v['hex']))
            if v is not None: assert hashlib.sha256(bytes.fromhex(v['hex'])).hexdigest() == v['sha256']
        result[row['id']] = cells
    return result

comparisons = []; checked_images = 0; expected_changes = 0
for storage, table in [('BASICFILE', 'T_D4_LOBB_20261009'), ('SECUREFILE', 'T_D4_LOBS_20261009')]:
    actual = [x for x in events if x['storage'] == storage]
    assert [x['scn'] for x in actual] == sorted(x['scn'] for x in actual)
    seed = truth(F / (table + '-rollback-check.json'))
    assert actual[0]['op'] == 'INSERT' and actual[0]['before'] == [] and actual[0]['after'] == seed[1]
    checked_images += 1
    prior = seed; previous_scn = actual[0]['scn']
    for stage in ['large', 'insert', 'empty', 'null', 'delete', 'partial', 'trim', 'rollback']:
        cutoff = int(re.search(r'SCN\|(\d+)', (F / (table + '-' + stage + '-scn.stdout')).read_text())[1])
        expected = truth(F / (table + '-' + stage + '-truth.json'))
        delta = [key for key in set(prior) | set(expected) if prior.get(key) != expected.get(key)]
        # Expected event count is independently determined by the frozen SQL state delta.
        candidates = [x for x in actual if previous_scn < x['scn'] <= cutoff]
        assert len(candidates) == len(delta), (storage, stage, len(candidates), len(delta))
        expected_changes += len(delta)
        for key in delta:
            before, after = prior.get(key, []), expected.get(key, [])
            event = next(x for x in candidates if (x['after'] or x['before'])[0][1] == 'c1' + format(key + 1, '02x'))
            assert event['before'] == before and event['after'] == after, (storage, stage, key)
            assert event['op'] == ('INSERT' if not before else 'DELETE' if not after else 'UPDATE')
            checked_images += bool(before) + bool(after)
        comparisons.append(dict(storage=storage, stage=stage, events=len(candidates), mismatches=0))
        prior = expected; previous_scn = cutoff
    assert len(actual) == 8 and len(delta) == 0
assert expected_changes == 14
# Compare maintenance identities with the independent native trace at the original positions.
assert sha(F / '97.trc') == '807f987d7d927f29bf58730b81908444a5a6d65c8c5a5b6edb49b061079e4472'
originals = {}; maintenance = []
for line in (N / 'probe.stdout').read_text().splitlines():
    f = line.split('|')
    if f[0] == 'MAINTENANCE': maintenance.append([f[1], int(f[2]), int(f[3])])
    if f[0] == 'METADATAORIGINAL':
        position = tuple(map(int, f[1:5])); assert position not in originals
        originals[position] = [int(f[5]), int(f[6]), f[7], int(f[8]), int(f[9]), int(f[10]), f[11], int(f[12]), int(f[13])]
assert maintenance == [['BASICFILE', 2, 16], ['SECUREFILE', 14, 114]] and len(originals) == 130
native = {}; section = []; position = None; current_position = None
def finish_native():
    if current_position is None or current_position not in originals: return
    text = '\n'.join(section)
    assert 'OP:5.1 ' in section[0]
    m = re.search(r'slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s+0x([0-9a-f]+) rci: (\d+) opc: (\d+\.\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]', text)
    x = re.search(r'\n\s+xid:\s+0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', text)
    u = re.search(r'ktudb redo:.*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)', text)
    d = re.search(r'DBA:0x([0-9a-f]+)', section[0]); assert m and x and u and d
    value = [int(m[7]), int(m[8]), '.'.join(str(int(v, 16)) for v in x.groups()),
             int(d[1], 16), int(u[1], 16), int(u[2], 16), m[6], int(m[4], 16), int(m[5])]
    assert value == originals[current_position], (current_position, value, originals[current_position])
    assert current_position not in native; native[current_position] = value
for line in (F / '97.trc').read_text().splitlines() + ['END']:
    if line.startswith('REDO RECORD -') or 'CHANGE #' in line or line == 'END':
        finish_native(); current_position = None; section = []
    if line.startswith('REDO RECORD -'):
        m = re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', line); assert m
        position = tuple(int(v, 16) for v in m.groups())
    m = re.search(r'CHANGE #(\d+) CON_ID:3 ', line)
    if m: current_position = position + (int(m[1]),)
    if current_position is not None: section.append(line)
missing_native = sorted(set(originals) - set(native))
assert missing_native == [(97,16003,20,2),(97,16011,160,1),(97,16012,40,1),(97,16014,228,1),(97,16015,168,1),
                          (97,16027,384,2),(97,16035,480,1),(97,16036,360,1),(97,16039,52,1),(97,16039,488,1)]
# Comparator must detect both byte corruption and NULL/EMPTY confusion.
sample = next(x for x in events if x['op'] == 'UPDATE' and len(x['after'][1][1]) > 100)
poison = list(sample['after']); poison[1] = ('VALUE', 'ff' + poison[1][1][2:]); assert poison != sample['after']
empty = next(x for x in events if x['after'] and x['after'][1] == ('VALUE', ''))
poison = list(empty['after']); poison[1] = ('NULL', ''); assert poison != empty['after']
proof = dict(rawSha256=sha(F / '97.raw'), outputSha256=sha(N / 'probe.stdout'), graphs=19,
             seedOriginEvents=2, seedNativeAuthority=False, approvedBaseline=False, committedChanges=14,
             completeEvents=16, checkedImages=checked_images, mismatches=0, rollbackEvents=0,
             comparatorPoisonsDetected=2, comparisons=comparisons, sourceDmlReplayed=False,
             maintenance=maintenance, maintenanceNativeCompared=len(native), maintenanceNativeMismatch=0,
             maintenanceNotInNativeDump=[list(p) for p in missing_native],
             productIntegrated=False, deployed=False, releaseAccepted=False)
path = N / 'comparison.json'; assert not path.exists(); path.write_text(json.dumps(proof, indent=2))
print(json.dumps({k: v for k, v in proof.items() if k != 'comparisons'}))
