"""Build owned RAC software on the E-drive daemon from verified media and Oracle scripts."""
from pathlib import Path
import hashlib,io,json,subprocess,sys,tarfile,time

resume=sys.argv[1:]==['--resume-local-archive']
memory_resume=sys.argv[1:]==['--resume-after-memory-release']
ol7=sys.argv[1:]==['--resume-ol7'] or memory_resume
assert resume or ol7 or sys.argv[1:]==['--execute']
root=Path('E:/OracleRedoKernel-lab/d5');evidence=root/('rac-build-r4' if memory_resume else 'rac-build-r3' if ol7 else 'rac-build-r2' if resume else 'rac-build-r1');evidence.mkdir(exist_ok=False)
if memory_resume:
    assert (root/'rac-build-r3/build.rc').read_text()=='1'
    assert (root/'rac-build-r3/stop.rc').read_text()=='0'
    assert (root/'rac-build-r3/user-stop.txt').is_file()
if ol7:
    assert (root/'rac-build-r2/build.rc').read_text()=='127'
    assert (root/'rac-build-r2/stop.rc').read_text()=='0'
    assert b'INS-08101' in (root/'rac-build-r2/build.log').read_bytes()
if resume:
    assert (root/'rac-build-r1/source-archive.rc').read_text() in ['124','130']
    assert not (root/'rac-build-r1/daemon.log').exists()
repository=root/'oracle-docker-images'
source='OracleDatabase/RAC/OracleRealApplicationClusters/dockerfiles/19.3.0'
socket='unix:///run/ork-d5-docker.sock'
for name in ['grid','database']:
    proof=json.loads((root/('downloads/'+name+'-verified.json')).read_text())
    assert proof['passed'] and Path(proof['file']).stat().st_size==proof['bytes']
assert (root/'engine-preflight-r3/stop.rc').read_text()=='0'
def run(label,args,timeout=300):
    try:result=subprocess.run(list(map(str,args)),capture_output=True,timeout=timeout)
    except subprocess.TimeoutExpired as error:
        (evidence/(label+'.stdout')).write_bytes(error.stdout or b'')
        (evidence/(label+'.stderr')).write_bytes(error.stderr or b'')
        (evidence/(label+'.rc')).write_text('124');raise
    for suffix,data in [('stdout',result.stdout),('stderr',result.stderr),('rc',str(result.returncode).encode())]:
        (evidence/(label+'.'+suffix)).write_bytes(data)
    assert result.returncode==0,(label,result.returncode)
    return result.stdout.decode(errors='replace')
def linux(label,args,timeout=300):
    return run(label,['wsl','--distribution','OracleRedoD5','--user','root','--exec']+args,timeout)

commit=run('source-commit',['git','-C',repository,'rev-parse','HEAD']).strip()
assert commit=='6a8129a1540baa292da85fb0cc94f9d402ffb53b'
if not resume and not ol7:run('source-archive',['git','-C',repository,'archive','--format=tar','--output='+str(evidence/'source.tar'),'HEAD',source])
elif resume:
    # Read already checked-out files; validate canonical bytes against the pinned Git blob IDs.
    entries=run('source-tree',['git','-C',repository,'ls-tree','-r','HEAD','--',source]).splitlines()
    with tarfile.open(evidence/'source.tar','w') as archive:
        for line in entries:
            metadata,path=line.split('\t',1);mode,kind,digest=metadata.split();assert kind=='blob' and path.startswith(source+'/')
            data=(repository/path).read_bytes()
            if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()!=digest:data=data.replace(b'\r\n',b'\n')
            assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==digest,path
            member=tarfile.TarInfo(path);member.mode=int(mode,8)&0o777
            if mode=='120000':member.type=tarfile.SYMTYPE;member.linkname=data.decode();archive.addfile(member)
            else:member.size=len(data);archive.addfile(member,io.BytesIO(data))
log=(evidence/'daemon.log').open('wb')
daemon=subprocess.Popen(['wsl','--distribution','OracleRedoD5','--user','root','--exec','dockerd','--data-root','/var/lib/docker',
    '--exec-root','/run/ork-d5-docker','--pidfile','/run/ork-d5-docker.pid','--host',socket],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
try:
    for attempt in range(20):
        try:
            assert linux('info-'+str(attempt),['docker','--host',socket,'info','--format','{{.DockerRootDir}}']).strip()=='/var/lib/docker';break
        except AssertionError:
            if attempt==19:raise
        time.sleep(1)
    context='/var/lib/ork-d5/build-source/'+source
    if not ol7:
        linux('stage-scripts',['sh','-c','mkdir -p /var/lib/ork-d5/build-source && tar -xf /mnt/e/OracleRedoKernel-lab/d5/'+evidence.name+'/source.tar -C /var/lib/ork-d5/build-source'])
        for name in ['LINUX.X64_193000_grid_home.zip','LINUX.X64_193000_db_home.zip']:
            linux('stage-'+name,['cp','/mnt/e/OracleRedoKernel-lab/d5/downloads/'+name,context+'/'+name])
    else:
        linux('staged-media',['sha256sum',context+'/LINUX.X64_193000_grid_home.zip',context+'/LINUX.X64_193000_db_home.zip'])
        checks=(evidence/'staged-media.stdout').read_text().splitlines()
        assert checks[0].split()[0]=='d668002664d9399cf61eb03c0d1e3687121fc890b1ddd50b35dcbe13c5307d2e'
        assert checks[1].split()[0]=='ba8329c757133da313ed3b6d7f86c5ac42cd9970a28bf2e6233f3235233aa8d8'
    linux('space',['df','-h','/var/lib/docker'])
    args=['wsl','--distribution','OracleRedoD5','--user','root','--exec','env','DOCKER_BUILDKIT=0','docker','--host',socket,
        'build','--memory','8g','--target','final','--build-arg','BASE_OL_IMAGE='+('oraclelinux:7-slim' if ol7 else 'oraclelinux:8'),'--tag','ork-d5-rac:19.3.0',context]
    (evidence/'build.command.json').write_text(json.dumps(args))
    print('D5_RAC_BUILD_STARTED|all-storage=E|oracleStarted=false',flush=True)
    with (evidence/'build.log').open('wb') as output:
        process=subprocess.Popen(args,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        try:rc=process.wait(timeout=3600)
        except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=30);rc=124
    (evidence/'build.rc').write_text(str(rc));assert rc==0,('build',rc)
    linux('image-check',['docker','--host',socket,'run','--rm','--name','ork-d5-image-check','--label','owner=OracleRedoKernel',
        '--entrypoint','/bin/bash','ork-d5-rac:19.3.0','-c',
        'set -e; test -x /u01/app/19.3.0/grid/bin/crsctl; test -x /u01/app/oracle/product/19.3.0/dbhome_1/bin/oracle; /u01/app/19.3.0/grid/bin/crsctl -version; /u01/app/oracle/product/19.3.0/dbhome_1/bin/sqlplus -V'])
    linux('image-inspect',['docker','--host',socket,'image','inspect','ork-d5-rac:19.3.0'])
    (evidence/'proof.json').write_text(json.dumps(dict(softwareImageBuilt=True,binariesChecked=True,sourceCommit=commit,oracleStarted=False,racValidated=False,asmValidated=False)))
    print('D5_RAC_SOFTWARE_IMAGE_BUILT|RAC_ASM_not_configured',flush=True)
finally:
    run('stop',['wsl','--terminate','OracleRedoD5'])
    daemon.wait(timeout=30);log.close()
