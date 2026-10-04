"""Derived frozen survey counterexamples; these validate the lab comparator, not the kernel."""
import json
import subprocess
import sys
from pathlib import Path

source = Path(sys.argv[1])
directory = Path(sys.argv[2])
directory.mkdir()
rows = source.read_text(encoding='utf-8-sig').splitlines()
cases = {}
marker = next(i for i, row in enumerate(rows) if row.split('|')[4] == '5.6')
cases['missing_compensation'] = rows[:marker] + rows[marker+1:]
cases['duplicate_position'] = rows + [rows[marker]]
for name, opcode, field, offset in [('wrong_key', '10.2', 2, 2), ('wrong_keydata', '10.2', 3, 5),
                                     ('wrong_undo_sdc', '5.1', 3, 12), ('wrong_undo_operation', '5.1', 3, 0)]:
    candidate = rows.copy()
    index = next(i for i, row in enumerate(rows) if row.split('|')[4] == opcode)
    parts = candidate[index].split('|')
    data = bytearray.fromhex(parts[7+field])
    data[offset] ^= 1
    parts[7+field] = data.hex()
    candidate[index] = '|'.join(parts)
    cases[name] = candidate
results = []
for name, candidate in cases.items():
    filename = directory / (name + '.txt')
    filename.write_text('\n'.join(candidate) + '\n', encoding='utf-8')
    command = [sys.executable, str(Path(__file__).with_name('compare_index_survey.py')), str(filename), *sys.argv[3:]]
    result = subprocess.run(command, capture_output=True)
    (directory / (name + '.stdout.json')).write_bytes(result.stdout)
    (directory / (name + '.stderr.txt')).write_bytes(result.stderr)
    (directory / (name + '.rc.txt')).write_text(str(result.returncode), encoding='utf-8')
    evidence = json.loads(result.stdout)
    if result.returncode != 1 or evidence['mismatches'] == 0 or result.stderr:
        raise RuntimeError('NEGATIVE_DID_NOT_PRODUCE_EVIDENCE:' + name)
    results.append({'case': name, 'rc': result.returncode, 'errors': evidence['errors']})
summary = {'derived_cases': results, 'scope': 'lab direct-field comparator only; no production refusal or CDC acceptance'}
(directory / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps(summary, indent=2))
