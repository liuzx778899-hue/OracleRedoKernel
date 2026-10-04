"""Independent bounded target-audit comparison with frozen source SQL snapshots.

Reads a real target database read-only. Does not call the consumer or use its
row parsing/identity functions. No Oracle NUMBER decoding claim beyond ID11.
"""
import json
import sqlite3
import sys
from pathlib import Path

target, ack, snapshot, actions = map(Path, sys.argv[1:5])
baseline = [line.split('|') for line in snapshot.read_text().splitlines() if line.startswith('ROW|')]
assert len(baseline) == 1 and baseline[0][5] == 'C10C'
p = baseline[0]
key = ':'.join([p[1], str((int(p[2]) << 22) | int(p[3])), p[4]])
before = ['VALUE:' + v.lower() for v in p[5:]]
expected = []
for sequence in (40, 41, 42):
    sql = [line.strip().split('|') for line in (actions / ('step-' + str(sequence) + '.stdout.txt')).read_text().splitlines()
           if line.strip().startswith('TRUTH|')]
    assert len(sql) == 1 and sql[0][1:3] == [str(sequence), '11']
    after = ['VALUE:c10c'] + ['VALUE:' + value.encode('utf-8').hex() for value in sql[0][3:]]
    expected.append((key, before, after))
    before = after
with sqlite3.connect(target.resolve().as_uri() + '?mode=ro', uri=True) as conn:
    applications = conn.execute('SELECT event_id,batch_id,ordinal,payload FROM applications ORDER BY ordinal').fetchall()
    receipts = conn.execute('SELECT batch_id,proof FROM commits').fetchall()
    rows = conn.execute('SELECT row_key,cells FROM rows').fetchall()
errors = []
if len(applications) != 3 or len(receipts) != 1:
    errors.append('TARGET_APPLICATION_OR_RECEIPT_COUNT')
else:
    ids = set()
    for ordinal, (actual, wanted) in enumerate(zip(applications, expected)):
        event_id, batch_id, index, payload = actual
        value = json.loads(payload)
        if (value.get('row_key'), value.get('before'), value.get('after')) != wanted:
            errors.append('SOURCE_SQL_COMPLETE_IMAGES_' + str(ordinal))
        if index != ordinal or batch_id != receipts[0][0] or event_id != value.get('event_id') or event_id in ids:
            errors.append('TARGET_APPLICATION_IDENTITY_' + str(ordinal))
        ids.add(event_id)
    if json.loads(ack.read_bytes()) != json.loads(receipts[0][1]):
        errors.append('ACK_TARGET_RECEIPT_DIFFERENCE')
if len(rows) != 1 or rows[0][0] != key or json.loads(rows[0][1]) != before:
    errors.append('SOURCE_SQL_FINAL_TARGET_STATE')
print(json.dumps({'post_commit_sql_images': 3, 'target_applications': len(applications),
                  'target_commit_receipts': len(receipts), 'mismatches': len(errors), 'errors': errors,
                  'scope': 'owned ID11 fixture; actual local SQLite, not StreamHouse/site acceptance'}))
sys.exit(bool(errors))
