"""Prepare an owned E-drive WSL rootfs without changing Desktop Docker or starting Oracle."""
from pathlib import Path
import json,subprocess,sys

assert sys.argv[1:]==['--execute']
root=Path('E:/OracleRedoKernel-lab/d5');evidence=root/'prepare-r1'
evidence.mkdir(exist_ok=False)
def run(label,args):
    result=subprocess.run(args,capture_output=True,timeout=300)
    (evidence/(label+'.stdout')).write_bytes(result.stdout)
    (evidence/(label+'.stderr')).write_bytes(result.stderr)
    (evidence/(label+'.rc')).write_text(str(result.returncode))
    assert result.returncode==0,(label,result.returncode)
    return result.stdout

assert json.loads((root/'downloads/grid-verified.json').read_text())['passed']
names=run('wsl-inventory',['wsl','--list','--quiet']).decode('utf-16le')
assert 'OracleRedoD5' not in names
assert b'ork-d5-bootstrap' not in run('helper-inventory',['docker','ps','-a','--format','{{.Names}}'])
created=False
try:
    run('helper-create',['docker','create','--name','ork-d5-bootstrap','--label','owner=OracleRedoKernel','debian:13','/bin/true']);created=True
    run('rootfs-export',['docker','export','--output',str(root/'debian-rootfs.tar'),'ork-d5-bootstrap'])
finally:
    if created:run('helper-remove',['docker','rm','ork-d5-bootstrap'])
(root/'wsl').mkdir(exist_ok=False)
run('wsl-import',['wsl','--import','OracleRedoD5',str(root/'wsl'),str(root/'debian-rootfs.tar'),'--version','2'])
run('linux-preflight',['wsl','--distribution','OracleRedoD5','--user','root','--','sh','-c',
    'uname -r; cat /etc/os-release; cat /proc/filesystems; df -h /; cat /proc/meminfo'])
run('wsl-stop',['wsl','--terminate','OracleRedoD5'])
print('D5_E_ROOTFS_PREPARED|OracleRedoD5|oracleStarted=false')
