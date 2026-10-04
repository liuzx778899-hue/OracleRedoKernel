"""Bounded laboratory SQLite target, not a production connector or public events API.

Consumes the buffered CapturedRowsProbe artifact under an externally supplied spec
digest. One target transaction contains all three fixture updates, application
identities and the commit receipt. Local ACK follows that transaction's commit.
"""
import hashlib
import json
import os
import sqlite3
import sys
from pathlib import Path


def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, reason):
    if not ok:
        raise RuntimeError(reason)


def pause(stage, selected, marker):
    if stage == selected:
        Path(marker).write_text(stage)
        # The supervising experiment kills this process; no finally/rollback runs.
        sys.stdin.buffer.read(1)
        raise RuntimeError('CRASH_INJECTION_NOT_KILLED')


def consume(spec_path, spec_sha, target_path, ack_path, selected='', marker=''):
    # This experiment runs on Windows. The fixture initializer creates the byte.
    import msvcrt
    with open(str(target_path) + '.consumer.lock', 'r+b') as lock:
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            raise RuntimeError('CONSUMER_ALREADY_RUNNING') from None
        return consume_locked(spec_path, spec_sha, target_path, ack_path, selected, marker)


def consume_locked(spec_path, spec_sha, target_path, ack_path, selected='', marker=''):
    raw = Path(spec_path).read_bytes()
    require(sha(raw) == spec_sha, 'CONSUMER_SPEC_DIGEST_MISMATCH')
    spec = json.loads(raw)
    for item in spec['inputs']:
        require(sha(Path(item['path']).read_bytes()) == item['sha256'],
                'CONSUMER_INPUT_DIGEST_MISMATCH')
    lines = Path(spec['rows_path']).read_text().splitlines()
    require(len(lines) == 3, 'LAB_ONLY_THREE_CAPTURED_UPDATES')
    events = []
    for sequence, line in zip((40, 41, 42), lines):
        p = line.split('|')
        require(len(p) == 9 and p[0] == 'ROW' and p[6] == 'UPDATE'
                and p[1].startswith(str(sequence) + ':'), 'LAB_CAPTURE_ROW_PROFILE')
        before, after = p[7].split(','), p[8].split(',')
        require(len(before) == len(after) == 3 and before[0] == after[0] == 'VALUE:c10c'
                and before[2] == after[2] == 'VALUE:6e65772d6e6f7465',
                'LAB_ONLY_OWNED_ID11_THREE_COLUMNS')
        identity = [spec['source'], spec['baseline_sha256'], p[1], p[2], p[3:6]]
        events.append({'event_id': sha(encoded(identity)), 'row_key': ':'.join(p[3:6]),
                       'before': before, 'after': after})
    binding = {'target_id': spec['target_id'], 'source': spec['source'],
               'baseline_sha256': spec['baseline_sha256'],
               'inputs': [item['sha256'] for item in spec['inputs']], 'events': events}
    batch_id = sha(encoded(binding))
    proof = {'format': 'ORK_LAB_TARGET_COMMIT_V1', 'batch_id': batch_id,
             'target_id': spec['target_id'], 'events': len(events),
             'input_sha256s': binding['inputs'],
             'position_kind': 'complete-buffered-batch-not-raw-offset'}
    proof_text = encoded(proof).decode()
    ack = Path(ack_path)
    if ack.exists():
        require(ack.read_bytes() == encoded(proof), 'CONSUMER_ACK_CONFLICT')
    # mode=rw prevents accidentally initializing or accepting a missing target.
    conn = sqlite3.connect(Path(target_path).resolve().as_uri() + '?mode=rw', uri=True)
    try:
        conn.execute('PRAGMA synchronous=FULL')
        conn.execute('BEGIN IMMEDIATE')
        require(conn.execute('SELECT target_id FROM target_identity').fetchall()
                == [(spec['target_id'],)], 'CONSUMER_TARGET_IDENTITY_MISMATCH')
        receipt = conn.execute('SELECT proof FROM commits WHERE batch_id=?', (batch_id,)).fetchone()
        if receipt:
            require(receipt[0] == proof_text, 'CONSUMER_TARGET_RECEIPT_CONFLICT')
            actual = conn.execute('SELECT event_id,payload FROM applications WHERE batch_id=? ORDER BY ordinal',
                                  (batch_id,)).fetchall()
            require(actual == [(e['event_id'], encoded(e).decode()) for e in events],
                    'CONSUMER_TARGET_APPLICATION_PROOF_MISMATCH')
            conn.commit()
            applied = 0
        else:
            require(not ack.exists(), 'CONSUMER_ACK_WITHOUT_TARGET_COMMIT')
            for ordinal, event in enumerate(events):
                before, after = encoded(event['before']).decode(), encoded(event['after']).decode()
                count = conn.execute('UPDATE rows SET cells=? WHERE row_key=? AND cells=?',
                                     (after, event['row_key'], before)).rowcount
                require(count == 1, 'CONSUMER_TARGET_BEFORE_MISMATCH')
                conn.execute('INSERT INTO applications VALUES(?,?,?,?)',
                             (event['event_id'], batch_id, ordinal, encoded(event).decode()))
                if ordinal == 1:
                    pause('before-commit', selected, marker)
            conn.execute('INSERT INTO commits VALUES(?,?)', (batch_id, proof_text))
            conn.commit()
            applied = len(events)
        pause('after-commit', selected, marker)
        temp = ack.with_name(ack.name + '.tmp')
        with temp.open('wb') as stream:
            stream.write(encoded(proof))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, ack)
        pause('after-ack', selected, marker)
        return {'batch_id': batch_id, 'new_applications': applied,
                'committed_events': len(events), 'lab_consumer_ack': True,
                'streamhouse_site_accepted': False}
    except BaseException:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == '__main__':
    # Optional pause arguments belong only to the crash experiment.
    try:
        print(json.dumps(consume(*sys.argv[1:])))
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
