"""Build immutable binary integration candidate without modifying frozen inputs."""
import hashlib, json, os, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

R = Path(__file__).resolve().parent.parent
P = R / 'lab/out/streamhouse-oracle-gate-worktree'
N = R / 'lab/out/split-history-product-20261007'
K = R / 'lab/out/split-history-candidate-20261007/r6/kernel.jar'
OLD = R / 'lab/out/historical-target-candidate-20261006'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
env = dict(os.environ, JAVA_HOME='D:/work/Java/jdk-17.0.7')
env['PATH'] = env['JAVA_HOME'] + '/bin;' + env['PATH']
mvn = ['cmd', '/d', '/c', 'D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd', '--batch-mode']
version = '0.1.0-split-history-a84db1a3ff8f'

def run(name, args, cwd):
    assert not (N / (name + '.command.json')).exists(), name
    command = dict(args=args, cwd=str(cwd), commit=subprocess.check_output(
        ['git', 'rev-parse', 'HEAD'], cwd=cwd).decode().strip())
    (N / (name + '.command.json')).write_text(json.dumps(command, indent=2))
    with (N / (name + '.stdout.txt')).open('wb') as out, (N / (name + '.stderr.txt')).open('wb') as err:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=out, stderr=err, timeout=900)
    (N / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == 0, (name, result.returncode)

def reports(base, name):
    dest = N / (name + '-reports')
    dest.mkdir()
    total = [0, 0, 0, 0]
    for report in base.rglob('target/surefire-reports/TEST-*.xml'):
        shutil.copyfile(report, dest / (report.parents[2].name + '-' + report.name))
        attrs = ET.parse(report).getroot().attrib
        for i, key in enumerate(['tests', 'failures', 'errors', 'skipped']):
            total[i] += int(attrs[key])
    assert total[0] > 0 and total[1:] == [0, 0, 0], total
    return total

action = sys.argv[1]
if action == 'prepare':
    N.mkdir(exist_ok=False)
    assert sha(K) == 'a84db1a3ff8fddbe49258714c8b8d7945e6648f2caa75eb082cbf36b225e3a64'
    shutil.copyfile(K, N / 'kernel.jar')
    shutil.copyfile(OLD / 'spi.jar', N / 'spi.jar')
    shutil.copyfile(__file__, N / 'executed-build-driver.py')
    run('install-kernel', mvn + ['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file',
        '-Dfile=' + str(N / 'kernel.jar'), '-DgroupId=com.streamhouse.oracle',
        '-DartifactId=oracle-redo-kernel', '-Dversion=' + version,
        '-Dpackaging=jar', '-DgeneratePom=true'], R)
    run('adapter-build', mvn + ['clean', 'package', '-Doracle.kernel.version=' + version,
        '-Dstreamhouse.spi.version=0.1.0-schema-protocol-53b84d186ba2'], R / 'integration/streamhouse-engine-oracle')
    total = reports(R / 'integration/streamhouse-engine-oracle', 'adapter')
    shutil.copyfile(R / 'integration/streamhouse-engine-oracle/target/streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar', N / 'adapter.jar')
    adapter_version = '0.1.0-split-history-' + sha(N / 'adapter.jar')[:12]
    run('install-adapter', mvn + ['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file',
        '-Dfile=' + str(N / 'adapter.jar'), '-DgroupId=com.streamhouse',
        '-DartifactId=streamhouse-engine-oracle', '-Dversion=' + adapter_version,
        '-Dpackaging=jar', '-DgeneratePom=true'], R)
    pom = P / 'server/streamhouse-app/pom.xml'
    old = pom.read_bytes()
    for before, after in [('0.1.0-schema-protocol-240c2250f002', version),
                          ('0.1.0-historical-target-65e0fcbad44f', adapter_version)]:
        assert old.count(before.encode()) == 1
        old = old.replace(before.encode(), after.encode())
    pom.write_bytes(old)
    (N / 'adapter-proof.json').write_text(json.dumps(dict(tests=total,
        kernel_version=version, adapter_version=adapter_version,
        kernel_sha256=sha(N / 'kernel.jar'), adapter_sha256=sha(N / 'adapter.jar')), indent=2))
    print('ADAPTER_VERIFIED|' + str(total) + '|' + adapter_version)
elif action == 'product':
    assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=P).strip(), 'private tree dirty'
    run('product-build', mvn + ['-s', str(P / '.mvn/settings.xml'), 'clean', 'package'], P / 'server')
    total = reports(P / 'server', 'product')
    shutil.copyfile(P / 'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar', N / 'application.jar')
    embedded = {}
    with zipfile.ZipFile(N / 'application.jar') as jar:
        for artifact, frozen in [('oracle-redo-kernel', 'kernel'), ('streamhouse-engine-oracle', 'adapter'), ('streamhouse-spi', 'spi')]:
            paths = [p for p in jar.namelist() if p.startswith('BOOT-INF/lib/' + artifact + '-') and p.endswith('.jar')]
            assert len(paths) == 1, paths
            output = N / ('embedded-' + artifact + '.jar')
            output.write_bytes(jar.read(paths[0]))
            if frozen != 'spi':
                assert sha(output) == sha(N / (frozen + '.jar'))
            else:
                with zipfile.ZipFile(output) as actual, zipfile.ZipFile(N / 'spi.jar') as expected:
                    for name in expected.namelist():
                        if name.endswith('.class'):
                            assert actual.read(name) == expected.read(name), name
            embedded[artifact] = dict(path=paths[0], sha256=sha(output))
    assert total[0] >= 750, total
    proof = dict(tests=total, product_sha256=sha(N / 'application.jar'), embedded=embedded,
        spi_all_class_bytes_match=True, historical_http_verified=False,
        source_actions=0, target_actions=0)
    (N / 'product-proof.json').write_text(json.dumps(proof, indent=2))
    print(json.dumps(proof))
else:
    raise ValueError(action)
