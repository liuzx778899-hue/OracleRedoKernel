"""Independent native undo/start/end record positions for the frozen three-URP batch."""
import json
import re
import sys
from pathlib import Path

actual = Path(sys.argv[1]); records = []
for filename in sys.argv[2:]:
    for record in re.split(r'(?=REDO RECORD - Thread:)', Path(filename).read_text()):
        if not record.startswith('REDO RECORD - Thread:') or 'CON_UID: 677737149' not in record.splitlines()[0]:
            continue
        at = tuple(int(v, 16) for v in re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', record).groups())
        scn = int(re.search(r'^SCN: 0x([0-9a-f]+)', record, re.M)[1], 16)
        records.append((at, scn, re.split(r'(?=CHANGE #\d+)', record)[1:]))
starts = {}; owners = {}; expected = {}
for at, scn, changes in records:
    for undo in changes:
        h = re.search(r'^CHANGE #(\d+) CON_ID:3 .*OP:5\.1\b', undo)
        if not h or '[objn: 73073 objd: 73076 ' not in undo:
            continue
        assert 'Begin trans' in undo  # Deliberately only this three single-update fixture.
        xid = tuple(int(v, 16) for v in re.search(r'\n\s*xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', undo).groups())
        matching = []
        for start in changes:
            header = re.search(r'^CHANGE #\d+ CON_ID:3 .*DBA:0x([0-9a-f]+).*OP:5\.2\b', start)
            fields = re.search(r'ktudh redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+)', start)
            if header and fields and tuple(int(v, 16) for v in fields.groups()) == xid[1:]:
                matching.append((int(header[1], 16), xid[1], xid[2]))
        assert len(matching) == 1 and matching[0] not in owners and xid not in starts
        owners[matching[0]] = xid; starts[xid] = scn
        expected[':'.join(map(str, at + (int(h[1]),)))] = [xid, scn, None]
ends = {}
for at, scn, changes in records:
    for end in changes:
        header = re.search(r'^CHANGE #\d+ CON_ID:3 .*DBA:0x([0-9a-f]+).*OP:5\.4\b', end)
        fields = re.search(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+).*sta: 9 flg: 0x2\b', end)
        if not header or not fields:
            continue
        key = (int(header[1], 16),) + tuple(int(v, 16) for v in fields.groups())
        if key in owners:
            xid = owners[key]; assert xid not in ends; ends[xid] = scn
assert len(expected) == len(ends) == 3
for value in expected.values():
    value[2] = ends[value[0]]
actual_events = [line.split('|') for line in actual.read_text().splitlines() if line.startswith('EVENT|')]
errors = []; seen = set()
for event in actual_events:
    assert len(event) == 7
    xid = tuple(map(int, re.fullmatch(r'Xid\[usn=(\d+), slot=(\d+), sequence=(\d+)\]', event[4]).groups()))
    value = [xid, int(event[5]), int(event[6])]
    if event[3] in seen or expected.get(event[3]) != value:
        errors.append({'position': event[3], 'expected': expected.get(event[3]), 'actual': value})
    if not re.fullmatch('[0-9a-f]{64}', event[1]) or not re.fullmatch('[0-9a-f]{64}', event[2]):
        errors.append({'bad_identity_digest': event[3]})
    seen.add(event[3])
if seen != set(expected) or len(actual_events) != 3:
    errors.append({'missing_or_extra': sorted(set(expected) ^ seen)})
print(json.dumps({'native_transactions': 3, 'native_undo_positions': 3, 'native_begin_end_record_scns': 6,
                  'mismatches': len(errors), 'errors': errors,
                  'scope': 'bounded native record SCNs, not commit wall time or historical dictionary'}))
sys.exit(bool(errors))
