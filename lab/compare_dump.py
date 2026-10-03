"""Independent diagnostic comparator. Never imported by the production parser."""
import json
import re
import sys
from pathlib import Path

dump = Path(sys.argv[1]).read_text(encoding='utf-8', errors='strict')
rows = [line.split('|') for line in Path(sys.argv[2]).read_text(encoding='utf-8-sig').splitlines()]
actual = {(int(row[0]), int(row[1])): row for row in rows}
parts = re.split(r'(?=REDO RECORD - Thread:)', dump)
errors = []
compared = 0
vectors = 0
for part in parts:
    match = re.search(r'REDO RECORD - Thread:(\d+) RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+) LEN: 0x([0-9a-f]+) VLD: 0x([0-9a-f]+) CON_UID: (\d+)\s+SCN: 0x([0-9a-f]+) SUBSCN:\s*(\d+)', part)
    if not match:
        continue
    _, _, block, offset, length, validity, uid, scn, sub = match.groups()
    key = (int(block, 16), int(offset, 16))
    expected = [key[0], key[1], int(length, 16), int(validity, 16), int(scn, 16), int(sub), int(uid)]
    row = actual.get(key)
    opcodes = re.findall(r'CHANGE #\d+[^\n]*? OP:(\d+\.\d+)', part)
    if row is None or list(map(int, row[:7])) != expected or row[7].split(',') != opcodes:
        errors.append({'rba': key, 'expected': expected, 'ops': opcodes, 'actual': row})
    compared += 1
    vectors += len(opcodes)
if compared == 0:
    errors.append({'reason': 'ZERO_COMPARISONS'})
report = {'records_compared': compared, 'vectors_compared': vectors, 'mismatches': len(errors), 'errors': errors, 'scope': 'dump-selected records, not full archive semantic acceptance'}
print(json.dumps(report, ensure_ascii=False, indent=2))
sys.exit(bool(errors))
