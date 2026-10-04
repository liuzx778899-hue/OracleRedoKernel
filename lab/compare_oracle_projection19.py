"""Independent typed envelope values from the original three source SQL snapshots."""
import base64
import json
import sys
from pathlib import Path

actual, actions, typed_reference = map(Path, sys.argv[1:4])
reference = [line.split('|') for line in typed_reference.read_text().splitlines() if line.startswith('EVENT|')]
expected = []; before = {'ID': 11, 'TXT': 'blocking-closed', 'NOTE': 'new-note'}
for sequence, meta in zip((40, 41, 42), reference):
    sql = [line.strip().split('|') for line in (actions / ('step-' + str(sequence) + '.stdout.txt')).read_text().splitlines()
           if line.strip().startswith('TRUTH|')]
    assert len(sql) == 1 and sql[0][1:3] == [str(sequence), '11']
    after = {'ID': 11, 'TXT': sql[0][3], 'NOTE': sql[0][4]}
    expected.append((meta, before, after)); before = after


def image(text):
    values = {}; order = []
    for cell in text.split(';'):
        name, value = cell.split('=', 1); assert name not in values; order.append(name)
        if value == 'NULL': values[name] = None
        elif value.startswith('NUMBER:'):
            assert name == 'ID' and value[7:] == '11'; values[name] = 11
        else:
            assert value.startswith('VARCHAR2:')
            encoded = value[9:]; values[name] = base64.b64decode(encoded + '=' * (-len(encoded) % 4), altchars=b'-_', validate=True).decode('utf-8')
    assert order == ['ID', 'TXT', 'NOTE']
    return values


rows = [line.split('|') for line in actual.read_text().splitlines() if line.startswith('PROJECTED|')]
errors = []
if len(rows) != len(expected) or len(expected) != 3:
    errors.append('PROJECTED_EVENT_COUNT')
for ordinal, (row, (meta, before, after)) in enumerate(zip(rows, expected)):
    assert len(row) == 13
    if row[1:7] != ['oracle1:' + meta[1], meta[2], *meta[3:7]]:
        errors.append('KERNEL_TYPED_IDENTITY_' + str(ordinal))
    if row[7:10] != ['1', 'ORKLAB', 'T_BASELINE_20261004'] or row[12] != 'TXT':
        errors.append('TRANSACTION_TABLE_CHANGED_COLUMNS_' + str(ordinal))
    if image(row[10]) != before or image(row[11]) != after:
        errors.append('SOURCE_SQL_COMPLETE_TYPED_IMAGES_' + str(ordinal))
print(json.dumps({'original_source_commits': 3, 'complete_typed_images': 6, 'hidden_column_retained': True,
                  'number_type_verified': 'exact NUMBER 11 only; scalar unit tests separate',
                  'mismatches': len(errors), 'errors': errors, 'site_accepted': False}))
sys.exit(bool(errors))
