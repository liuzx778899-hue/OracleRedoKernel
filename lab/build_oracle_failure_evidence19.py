"""Preserve the failing regression and build repaired private product from a clean commit."""
import hashlib, json, os, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
R = Path(__file__).resolve().parent.parent
P = R / 'lab/out/streamhouse-oracle-gate-worktree'
N = R / 'lab/out/split-history-late-http-20261007'
C = R / 'lab/out/split-history-product-20261007'
label = sys.argv[1]
A = N / label
A.mkdir(exist_ok=False)
shutil.copyfile(__file__, A / 'executed-driver.py')
for f in ['PipelineFacade.java', 'OracleFacadePublicationTest.java']:
    matches = list((P / 'server/streamhouse-pipeline/src').rglob(f))
    assert len(matches) == 1
    shutil.copyfile(matches[0], A / f)
env = dict(os.environ, JAVA_HOME='D:/work/Java/jdk-17.0.7')
env['PATH'] = env['JAVA_HOME'] + '/bin;' + env['PATH']
args = ['cmd', '/d', '/c', 'D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd', '--batch-mode', '-s', str(P / '.mvn/settings.xml')]
if label.startswith('regression-'):
    args += ['-pl', 'streamhouse-pipeline', '-am', 'clean', 'test', '-Dtest=OracleFacadePublicationTest', '-Dsurefire.failIfNoSpecifiedTests=false']
    expected = 1
elif label.startswith('repaired-build'):
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=P).strip()
    args += ['clean', 'package']
    expected = 0
else:
    raise ValueError(label)
(A / 'command.json').write_text(json.dumps(dict(args=args, cwd=str(P / 'server'), commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=P).decode().strip())))
with (A / 'stdout.txt').open('wb') as out, (A / 'stderr.txt').open('wb') as err:
    rc = subprocess.run(args, cwd=P / 'server', env=env, stdout=out, stderr=err, timeout=900).returncode
(A / 'rc.txt').write_text(str(rc))
reports = A / 'reports'
reports.mkdir()
total = [0, 0, 0, 0]
for path in (P / 'server').glob('*/target/surefire-reports/TEST-*.xml'):
    if label.startswith('regression-') and path.parents[2].name != 'streamhouse-pipeline':
        continue
    shutil.copyfile(path, reports / (path.parents[2].name + '-' + path.name))
    attrs = ET.parse(path).getroot().attrib
    for i, k in enumerate(['tests', 'failures', 'errors', 'skipped']):
        total[i] += int(attrs[k])
assert rc == expected, (rc, total)
if label.startswith('regression-'):
    assert total == ([7, 1, 0, 0] if label == 'regression-r1' else [8, 1, 0, 0]), total
else:
    assert total == ([751, 0, 0, 0] if label == 'repaired-build' else [752, 0, 0, 0]), total
    shutil.copyfile(P / 'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar', A / 'application.jar')
    sha = lambda f: hashlib.sha256(f.read_bytes()).hexdigest()
    embedded = {}
    with zipfile.ZipFile(A / 'application.jar') as jar:
        for artifact in ['oracle-redo-kernel', 'streamhouse-engine-oracle', 'streamhouse-spi']:
            paths = [p for p in jar.namelist() if p.startswith('BOOT-INF/lib/' + artifact + '-') and p.endswith('.jar')]
            assert len(paths) == 1
            output = A / (artifact + '.jar')
            output.write_bytes(jar.read(paths[0]))
            with zipfile.ZipFile(output) as actual, zipfile.ZipFile(C / ('embedded-' + artifact + '.jar')) as prior:
                for name in prior.namelist():
                    if name.endswith('.class'):
                        assert actual.read(name) == prior.read(name), name
            if artifact != 'streamhouse-spi':
                assert sha(output) == sha(C / ('embedded-' + artifact + '.jar'))
            embedded[artifact] = dict(path=paths[0], sha256=sha(output))
    (A / 'proof.json').write_text(json.dumps(dict(tests=total, product_sha256=sha(A / 'application.jar'), embedded=embedded, all_dependency_class_bytes_unchanged=True), indent=2))
print(json.dumps(dict(label=label, rc=rc, tests=total)))
