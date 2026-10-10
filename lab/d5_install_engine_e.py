"""Install Docker only in the owned E-drive distro; Desktop Docker stays separate."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request

assert sys.argv[1:]==['--execute']
root=Path('E:/OracleRedoKernel-lab/d5');evidence=root/'engine-r1';evidence.mkdir(exist_ok=False)
assert (root/'prepare-r1/wsl-import.rc').read_text()=='0'
def run(label,args):
    result=subprocess.run(args,capture_output=True,timeout=300)
    for suffix,data in [('stdout',result.stdout),('stderr',result.stderr),('rc',str(result.returncode).encode())]:
        (evidence/(label+'.'+suffix)).write_bytes(data)
    assert result.returncode==0,(label,result.returncode)
    return result.stdout
def linux(label,command):
    return run(label,['wsl','--distribution','OracleRedoD5','--user','root','--','sh','-c',command])
package=root/'downloads/docker-29.8.0.tgz'
url='https://download.docker.com/linux/static/stable/x86_64/docker-29.8.0.tgz'
assert not package.exists()
with urllib.request.urlopen(url,timeout=120) as response,package.open('xb') as output:
    shutil.copyfileobj(response,output)
(evidence/'download.json').write_text(json.dumps(dict(url=url,bytes=package.stat().st_size,sha256=hashlib.file_digest(package.open('rb'),'sha256').hexdigest())))
try:
    linux('dependencies','apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates iptables iproute2 util-linux')
    linux('install','tar -xzf /mnt/e/OracleRedoKernel-lab/d5/downloads/docker-29.8.0.tgz -C /usr/local/bin --strip-components=1 && docker --version')
    linux('storage-preflight','findmnt -no SOURCE,FSTYPE /; command -v losetup; cat /proc/filesystems; ls -l /dev/loop-control')
finally:
    run('stop',['wsl','--terminate','OracleRedoD5'])
print('D5_E_ENGINE_INSTALLED|oracleStarted=false')
