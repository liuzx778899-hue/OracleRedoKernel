"""Build only the new adapter, with pinned kernel and committed product SPI.

Uses a private Maven repository. No shared product checkout edits or global Maven
artifact replacement; kernel's already accepted 245-test build is not repeated.
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'lab/out/release-oracle-projection-20261005'
PRODUCT = Path('D:/work/vibecode/StreamHouse')
JDK = Path('D:/work/Java/jdk-17.0.7')
MVN = 'D:/work/Java/apache-maven-3.9.11/bin/mvn.cmd'
continuing = sys.argv[1:] == ['--resume-after-encoding']
if not continuing:
    OUT.mkdir(parents=True, exist_ok=False)
env = dict(os.environ); env['JAVA_HOME'] = str(JDK); env['PATH'] = str(JDK / 'bin') + ';' + env['PATH']


def run(name, args, cwd=ROOT):
    args = list(map(str, args)); (OUT / (name + '.command.json')).write_text(json.dumps({'args': args, 'cwd': str(cwd)}))
    with (OUT / (name + '.stdout.txt')).open('wb') as stdout, (OUT / (name + '.stderr.txt')).open('wb') as stderr:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=stdout, stderr=stderr, timeout=900)
    (OUT / (name + '.rc.txt')).write_text(str(result.returncode))
    assert result.returncode == 0, name


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


product_commit = (json.loads((OUT / 'freeze-product-spi.command.json').read_text())['args'][-2] if continuing
                  else subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=PRODUCT).decode().strip())
if not continuing:
    run('freeze-product-spi', ['git', '-c', 'core.autocrlf=false', 'archive', '--format=zip',
                              '--output=' + str(OUT / 'product-spi-source.zip'), product_commit,
                              'server/streamhouse-spi/src/main/java'], PRODUCT)
source = OUT / 'product-spi-source'
if not continuing:
    with zipfile.ZipFile(OUT / 'product-spi-source.zip') as tree:
        tree.extractall(source)
else:
    assert (OUT / 'compile-spi-contract.rc.txt').read_text() != '0'
classes = OUT / 'spi-classes'; classes.mkdir(exist_ok=continuing)
java = sorted(source.rglob('*.java')); assert len(java) == 7
run('compile-spi-contract-utf8' if continuing else 'compile-spi-contract',
    [JDK / 'bin/javac.exe', '--release', '17', '-encoding', 'UTF-8', '-d', classes] + java)
spi = OUT / 'streamhouse-spi-0.1.0-SNAPSHOT.jar'
run('package-spi-contract', [JDK / 'bin/jar.exe', '--create', '--file', spi, '-C', classes, '.'])
kernel = ROOT / 'lab/out/release-captured-batch-20261005/kernel.jar'
assert sha(kernel) == '15b07ddf4da3f9537b5df8cd996add69e61e6a94b5e92d4e9c7a9c1ed780053f'
repo = OUT / 'm2'
maven = ['cmd', '/d', '/c', MVN, '--batch-mode', '-Dmaven.repo.local=' + str(repo)]
for name, artifact, group, file in [('install-spi', 'streamhouse-spi', 'com.streamhouse', spi),
                                    ('install-kernel', 'oracle-redo-kernel', 'com.streamhouse.oracle', kernel)]:
    run(name, maven + ['org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file',
                      '-Dfile=' + str(file), '-DgroupId=' + group, '-DartifactId=' + artifact,
                      '-Dversion=0.1.0-SNAPSHOT', '-Dpackaging=jar', '-DgeneratePom=true'])
module = ROOT / 'integration/streamhouse-engine-oracle'
run('adapter-verify', maven + ['clean', 'verify'], module)
run('adapter-dependencies', maven + ['org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree'], module)
shutil.copytree(module / 'target/surefire-reports', OUT / 'surefire-reports')
adapter = OUT / 'streamhouse-engine-oracle-0.1.0-SNAPSHOT.jar'
shutil.copyfile(module / 'target' / adapter.name, adapter)
counts = {key: sum(int(ET.parse(p).getroot().get(key, 0)) for p in (OUT / 'surefire-reports').glob('TEST-*.xml'))
          for key in ('tests', 'failures', 'errors', 'skipped')}
assert counts == {'tests': 5, 'failures': 0, 'errors': 0, 'skipped': 0}
runtime = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip()
run('freeze-adapter-tree', ['git', '-c', 'core.autocrlf=false', 'archive', '--format=zip',
                           '--output=' + str(OUT / 'adapter-source-tree.zip'), runtime])
proof = {'adapter_runtime_commit': runtime, 'product_spi_commit': product_commit,
         'kernel_runtime_commit': 'e7200134585e48959118272df2ddf1d0cb6309de', 'kernel_sha256': sha(kernel),
         'spi_contract_jar_sha256': sha(spi), 'adapter_jar_sha256': sha(adapter), 'tests': counts,
         'private_maven_repository': True, 'product_shared_checkout_edited': False,
         'product_full_reactor_verified': False, 'kernel_tests_rerun': False}
(OUT / 'proof.json').write_text(json.dumps(proof, indent=2)); print(json.dumps(proof))
