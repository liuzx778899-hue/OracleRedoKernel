"""Independent end-layout audit of selected native records, never event input."""
import json
import re
import sys
from pathlib import Path

raw, native_dir = Path(sys.argv[1]), Path(sys.argv[2])
differences = []
count = 0
for line in raw.read_text().splitlines():
    if not line.startswith('END|'):
        continue
    fields = line.split('|')
    position = tuple(map(int, re.fullmatch(
        r'Position\[archiveSequence=(\d+), block=(\d+), offset=(\d+), vector=0\]', fields[1]).groups()))
    end = tuple(map(int, re.fullmatch(
        r'TransactionEnd\[segmentDba=(\d+), slot=(\d+), sequence=(\d+), flags=(\d+)\]', fields[4]).groups()))
    text = (native_dir / ('native-end-%d-%d-%d.txt' % position)).read_text()
    native_position = tuple(int(v, 16) for v in re.search(
        r'REDO RECORD - Thread:\d+ RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', text).groups())
    native_scn = int(re.search(r'^SCN: 0x([0-9a-f]+)', text, re.M)[1], 16)
    markers = re.findall(r'ktucm redo: slt: 0x([0-9a-f]+) sqn: 0x([0-9a-f]+) srt: 0 sta: 9 flg: 0x([0-9a-f]+)', text)
    tails = re.findall(r'ktucf redo: uba: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', text)
    expected_tail = re.fullmatch(r'Uba\[dba=(\d+), sequence=(\d+), record=(\d+)\]', fields[6])
    matches = (native_position == position and native_scn == int(fields[3])
               and len(markers) == 1 and tuple(int(v, 16) for v in markers[0]) == end[1:])
    if expected_tail:
        matches = matches and len(tails) == 1 and tuple(int(v, 16) for v in tails[0]) == tuple(map(int, expected_tail.groups()))
    else:
        matches = matches and fields[6] == 'TRANSACTION_END_HAS_NO_UNDO_TAIL_REFERENCE' and not tails
    if not matches:
        differences.append(position)
    count += 1
if count != 3:
    differences.append('expected_three_selected_records')
print(json.dumps({'selected_records': count, 'differences': differences,
                  'audited_fields': ['RBA', 'recordSCN', 'slot', 'sequence', 'flags', 'tailUBA_or_absence'],
                  'full_dml_accepted': False}))
sys.exit(bool(differences))
