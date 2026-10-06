"""Install frozen historical adapter and build the private product candidate."""
import hashlib, json, os, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

R = Path(__file__).resolve().parent.parent
N = R / 'lab/out/historical-target-candidate-20261006'
P = R / 'lab/out/streamhouse-oracle-gate-worktree'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(N / 'adapter.jar') == '65e0fcbad44f9df769db48fdf86ea66f13ab232d6c7872c25ee64c90a1d40dbe'
assert not (N / 'install-adapter.command.json').exists()
(N / 'executed-product-build-driver.py').write_bytes(Path(__file__).read_bytes())
env = dict(os.environ, JAVA_HOME='D:/work/Java/jdk-17.0.7')
env['PATH'] = env['JAVA_HOME'] + '/bin;' + env['PATH']
mvn = ['cmd', '/d', '/c', 'D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd', '--batch-mode']

def run(name, args, cwd):
    (N / (name + '.command.json')).write_text(json.dumps(dict(args=args, cwd=str(cwd))))
    with (N / (name + '.stdout.txt')).open('wb') as out, (N / (name + '.stderr.txt')).open('wb') as err:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=out, stderr=err, timeout=900)
    (N / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == 0, (name, result.returncode)

version = '0.1.0-historical-target-' + sha(N / 'adapter.jar')[:12]
run('install-adapter', mvn + ['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file',
    '-Dfile=' + str(N / 'adapter.jar'), '-DgroupId=com.streamhouse',
    '-DartifactId=streamhouse-engine-oracle', '-Dversion=' + version,
    '-Dpackaging=jar', '-DgeneratePom=true'], R)
pom = P / 'server/streamhouse-app/pom.xml'
old = pom.read_text(encoding='utf-8')
assert old.count('0.1.0-dictionary-snapshot-9516d4f9') == 1
assert old.count('0.1.0-inverse-tail-672f01bc') == 1
pom.write_text(old.replace('0.1.0-dictionary-snapshot-9516d4f9', version)
    .replace('0.1.0-inverse-tail-672f01bc', '0.1.0-schema-protocol-240c2250f002'), encoding='utf-8')
run('product-build', mvn + ['-s', str(P / '.mvn/settings.xml'), 'clean', 'package'], P / 'server')
shutil.copyfile(P / 'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar', N / 'product.jar')
total = [0, 0, 0, 0]
reports = N / 'product-surefire-reports'
reports.mkdir()
for report in (P / 'server').glob('*/target/surefire-reports/TEST-*.xml'):
    shutil.copyfile(report, reports / (report.parents[2].name + '-' + report.name))
    attrs = ET.parse(report).getroot().attrib
    for i, key in enumerate(['tests', 'failures', 'errors', 'skipped']):
        total[i] += int(attrs[key])
assert total[0] > 0 and total[1:] == [0, 0, 0], total
embedded = {}
with zipfile.ZipFile(N / 'product.jar') as archive:
    for artifact, frozen in [('oracle-redo-kernel', 'kernel'), ('streamhouse-engine-oracle', 'adapter'), ('streamhouse-spi', 'spi')]:
        paths = [p for p in archive.namelist() if p.startswith('BOOT-INF/lib/' + artifact + '-') and p.endswith('.jar')]
        assert len(paths) == 1, paths
        output = N / ('product-embedded-' + artifact + '.jar')
        output.write_bytes(archive.read(paths[0]))
        if frozen != 'spi':
            assert sha(output) == sha(N / (frozen + '.jar'))
        else:
            with zipfile.ZipFile(output) as actual, zipfile.ZipFile(N / 'spi.jar') as expected:
                for path in expected.namelist():
                    if path.endswith('.class'):
                        assert actual.read(path) == expected.read(path), path
        embedded[artifact] = dict(path=paths[0], sha256=sha(output))
proof = dict(adapter_version=version, tests=total, product_sha256=sha(N / 'product.jar'), embedded=embedded,
    spi_all_class_bytes_match=True, source_actions=0, target_actions=0, historical_http_verified=False)
(N / 'product-build-proof.json').write_text(json.dumps(proof, indent=2))
print(json.dumps(proof))
