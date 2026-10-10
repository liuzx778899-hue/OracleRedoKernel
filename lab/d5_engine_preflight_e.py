"""Owned E-drive daemon and shared-block-device feasibility; no Oracle deployment claim."""
from pathlib import Path
import subprocess,sys,time
resume=sys.argv[1:]==['--resume-daemon']
argv_resume=sys.argv[1:]==['--resume-argv']
assert resume or argv_resume or sys.argv[1:]==['--execute']
root=Path('E:/OracleRedoKernel-lab/d5');evidence=root/('engine-preflight-r3' if argv_resume else 'engine-preflight-r2' if resume else 'engine-preflight-r1');evidence.mkdir(exist_ok=False)
if resume:assert (root/'engine-preflight-r1/stop.rc').read_text()=='0'
if argv_resume:assert (root/'engine-preflight-r2/stop.rc').read_text()=='0'
assert (root/'engine-r1/install.rc').read_text()=='0'
def run(label,args):
    result=subprocess.run(args,capture_output=True,timeout=120)
    for suffix,data in [('stdout',result.stdout),('stderr',result.stderr),('rc',str(result.returncode).encode())]:
        (evidence/(label+'.'+suffix)).write_bytes(data)
    assert result.returncode==0,(label,result.returncode)
    return result.stdout.decode(errors='replace')
def linux(label,command):
    return run(label,['wsl','--distribution','OracleRedoD5','--user','root','--exec','sh','-c',command])
daemon_log=(evidence/'daemon.log').open('wb')
daemon=subprocess.Popen(['wsl','--distribution','OracleRedoD5','--user','root','--exec','dockerd','--data-root','/var/lib/docker',
    '--exec-root','/run/ork-d5-docker','--pidfile','/run/ork-d5-docker.pid','--host','unix:///run/ork-d5-docker.sock'],
    stdout=daemon_log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
try:
    for attempt in range(20):
        try:
            info=linux('info-'+str(attempt),'docker --host unix:///run/ork-d5-docker.sock info --format "{{.DockerRootDir}}|{{.Driver}}|{{.ServerVersion}}"');break
        except AssertionError:
            if attempt==19:raise
        time.sleep(1)
    assert info.startswith('/var/lib/docker|')
    linux('block-device-probe','set -eu; mkdir -p /var/lib/ork-d5-preflight; truncate -s 16777216 /var/lib/ork-d5-preflight/block.img; device=$(losetup --find --show /var/lib/ork-d5-preflight/block.img); trap \'losetup -d "$device"\' EXIT; blockdev --getsize64 "$device"; findmnt -no SOURCE,FSTYPE /; cat /proc/filesystems')
    print('D5_E_DOCKER_AND_LOOP_PREFLIGHT_PASS|oracleStarted=false',flush=True)
finally:
    run('stop',['wsl','--terminate','OracleRedoD5'])
    daemon.wait(timeout=30);daemon_log.close()
