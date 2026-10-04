"""Frozen 73025 index research: native position coverage and observed direct fields only."""
import json
import re
import sys
from collections import Counter
from pathlib import Path


def u16(b, o):
    return int.from_bytes(b[o:o+2], 'little')


def u32(b, o):
    return int.from_bytes(b[o:o+4], 'little')


actual = {}
errors = []
for line in Path(sys.argv[1]).read_text(encoding='utf-8-sig').splitlines():
    parts = line.split('|')
    position = tuple(map(int, parts[:4]))
    if position in actual:
        errors.append({'position': position, 'reason': 'DUPLICATE_POSITION'})
    actual[position] = (parts[4], int(parts[5]), int(parts[6]),
                        [bytes.fromhex(x) for x in parts[7:]])

expected = {}
counts = Counter()
checks = Counter()
for filename in sys.argv[2:]:
    text = Path(filename).read_text(encoding='utf-8')
    for record in re.split(r'(?=REDO RECORD - Thread:)', text):
        if not record.startswith('REDO RECORD - Thread:'):
            continue
        if 'CON_UID: 677737149' not in record.splitlines()[0]:
            continue
        rba = re.search(r'RBA: 0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', record)
        scn = int(re.search(r'\nSCN: 0x([0-9a-f]+)', record)[1], 16)
        for change in re.split(r'(?=CHANGE #\d+)', record)[1:]:
            h = re.search(r'CHANGE #(\d+) CON_ID:3 [^\n]*DBA:0x([0-9a-f]+) OBJ:(\d+)[^\n]*OP:(\d+\.\d+)\b', change)
            if not h:
                continue
            opcode = h[4]
            metadata = re.search(r'(?:ktubu|ktubl) redo: slt: (\d+) wrp: (\d+) flg: 0x([0-9a-f]+) prev dba:\s*0x([0-9a-f]+) rci: (\d+) opc: (\d+)\.(\d+) \[objn: (\d+) objd: (\d+) tsn: (\d+)\]', change)
            selected = (opcode.startswith('10.') and int(h[2], 16) == 0x0300008b)
            selected |= opcode in ('5.1', '5.6', '5.11') and metadata is not None and int(metadata[9]) == 73025
            if not selected:
                continue
            position = tuple(int(rba[i], 16) for i in (1, 2, 3)) + (int(h[1]),)
            if position in expected:
                raise RuntimeError('DUPLICATE_NATIVE_POSITION')
            expected[position] = opcode
            counts[opcode] += 1
            row = actual.get(position)
            if row is None:
                errors.append({'position': position, 'reason': 'MISSING_POSITION'})
                continue
            def check(reason, condition):
                checks[reason] += 1
                if not condition:
                    errors.append({'position': position, 'reason': reason})
            check('VECTOR_IDENTITY', row[:3] == (opcode, int(h[2], 16), scn))
            fields = row[3]
            if metadata:
                b = fields[1 if opcode == '5.1' else 0]
                raw = [b[18], u16(b, 22), u16(b, 20), u32(b, 12), b[19], b[16], b[17], u32(b, 0), u32(b, 4), u32(b, 8)]
                native = [int(metadata[i], 16 if i in (3, 4) else 10) for i in range(1, 11)]
                check('UNDO_METADATA', raw == native)
            if opcode == '5.1':
                b = fields[0]
                xid = re.search(r'xid:\s*0x([0-9a-f]+)\.([0-9a-f]+)\.([0-9a-f]+)', change)
                uba = re.search(r'ktudb redo:[^\n]*seq: 0x([0-9a-f]+) rec: 0x([0-9a-f]+)', change)
                check('UNDO_XID_UBA', [u16(b, 8), u16(b, 10), u32(b, 12), u16(b, 16), b[18]] ==
                      [*(int(xid[i], 16) for i in (1, 2, 3)), int(uba[1], 16), int(uba[2], 16)])
                leaf = fields[3]
                kdilk = re.search(r'Dump kdilk : itl=(\d+), kdxlkflg=0x([0-9a-f]+) sdc=(\d+) indexid=0x([0-9a-f]+) block=0x([0-9a-f]+)', change)
                check('UNDO_LEAF_DIRECT', len(leaf) == 20 and
                      [leaf[1], leaf[2], u16(leaf, 12), u32(leaf, 4), u32(leaf, 8)] ==
                      [int(kdilk[i], 16 if i in (2, 4, 5) else 10) for i in range(1, 6)])
                check('UNDO_LEAF_OPERATION', leaf[0] == (5 if '(kdxlre): restore leaf row' in change else 3))
                key = re.search(r'key\s*:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)', change)
                check('UNDO_KEY', fields[4] == bytes.fromhex(key[2]) and len(fields[4]) == int(key[1]))
                keydata = re.search(r'keydata(?:/bitmap)?:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)', change)
                if keydata:
                    check('UNDO_KEYDATA', len(fields) == 6 and fields[5] == bytes.fromhex(keydata[2]) and len(fields[5]) == int(keydata[1]))
                else:
                    check('UNDO_FIELD_COUNT', len(fields) == 5)
            elif opcode in ('5.6', '5.11'):
                extra = re.search(r'ktuxvoff: 0x([0-9a-f]+)\s+ktuxvflg: 0x([0-9a-f]+)', change)
                check('COMPENSATION_EXTRA', len(fields) == 2 and len(fields[0]) == 28 and len(fields[1]) == 8 and
                      [u32(fields[1], 0), u32(fields[1], 4)] == [int(extra[i], 16) for i in (1, 2)])
            elif opcode in ('10.2', '10.3', '10.4'):
                leaf = fields[1]
                direct = re.search(r'itl: (\d+), sno: (\d+), row size (\d+)', change)
                check('FORWARD_LEAF_DIRECT', len(leaf) == 6 and [leaf[0], u16(leaf, 2), u16(leaf, 4)] ==
                      [int(direct[i]) for i in (1, 2, 3)])
                check('FORWARD_MODE', leaf[1] == (1 if opcode == '10.3' else 0))
                if opcode == '10.2':
                    key = re.search(r'insert key:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)', change)
                    keydata = re.search(r'keydata:\s*\((\d+)\):\s*((?:[0-9a-f]{2}\s*)+)', change)
                    check('FORWARD_KEY', len(fields) == 4 and fields[2] == bytes.fromhex(key[2]) and len(fields[2]) == int(key[1]))
                    check('FORWARD_KEYDATA', fields[3] == bytes.fromhex(keydata[2]) and len(fields[3]) == int(keydata[1]))
                else:
                    check('FORWARD_FIELD_COUNT', len(fields) == 2)
            # 10.31 remains inventory-only. No generic index or KTB semantics are certified here.

for position in actual.keys() - expected.keys():
    errors.append({'position': position, 'reason': 'UNEXPECTED_POSITION'})
print(json.dumps({'native_vectors': len(expected), 'raw_vectors': len(actual), 'opcodes': dict(counts),
                  'checks': dict(checks), 'mismatches': len(errors), 'errors': errors,
                  'scope': 'frozen index direct fields; maintenance inventory only; no pairing, rollback graph, dictionary or CDC acceptance',
                  'cdc_events': 0}, indent=2))
sys.exit(bool(errors))
