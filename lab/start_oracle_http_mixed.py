"""Start the immutable candidate only on the owned Oracle network's loopback port."""
import hashlib,json,secrets,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent.parent;P=R/'lab/out/streamhouse-oracle-gate-worktree';O=R/'lab/out/oracle-http-mixed-20261005';A=R/'lab/out/oracle-bounded-runtime-20261005';F=R/'lab/out/oracle-null-sides-target-20261005';E=R/'lab/out/oracle-empty-target-20261005';SOURCE='oraredo-19c-lab-20261004';NAME='ork-oracle-http-mixed-20261005'
assert O.is_dir()
assert not (O/'runtime.secret.env').exists()
assert not subprocess.check_output(['git','status','--porcelain'],cwd=P)
assert subprocess.check_output(['docker','inspect',SOURCE,'--format','{{.Config.Labels.owner}}|{{.State.Status}}']).decode().strip()=='OracleRedoKernel|running'
assert not subprocess.check_output(['docker','ps','-a','--filter','name=^/'+NAME+'$','--format','{{.Names}}']).strip()
control=json.loads((O/'control.secret.json').read_text());target=(R/'lab/out/oracle-target-20261005/target.secret').read_text().splitlines();password='H'+secrets.token_hex(12)+'a1'
properties=dict(SH_DB_PROFILE='oracle',SPRING_DATASOURCE_URL=control['url'],SPRING_DATASOURCE_USERNAME=control['user'],SPRING_DATASOURCE_PASSWORD=control['password'],SERVER_ADDRESS='127.0.0.1',SERVER_PORT='37216',SH_WORKER_ID='16',SH_ADMIN_INITIAL_PASSWORD=password,SH_ORACLE_CONSUMER_ID='http-consumer-SMIX',SH_ORACLE_BOUNDED_ENABLED='true',SH_ORACLE_BOUNDED_TARGET_URL=target[2],SH_ORACLE_BOUNDED_TARGET_USER=target[0],SH_ORACLE_BOUNDED_TARGET_PASSWORD=target[1],SH_ORACLE_BOUNDED_TARGET_OWNER=target[0],SH_ORACLE_BOUNDED_TARGET_PREFIX='HTMIX',SH_ORACLE_BOUNDED_TARGET_ID='oracle-dml-HTMIX-20261005',SH_ORACLE_BOUNDED_ACK_DIRECTORY='/evidence/acks',SH_PIPELINE_AUTO_CONSUME_ENABLED='false')
(O/'runtime.secret.env').write_text(''.join(k+'='+v+'\n' for k,v in properties.items()));(O/'admin.secret.json').write_text(json.dumps(dict(username='admin',password=password)))
jar=P/'server/streamhouse-app/target/streamhouse-app-0.1.0-SNAPSHOT.jar';shutil.copyfile(jar,O/'application.jar')
(O/'runtime-pin.json').write_text(json.dumps(dict(product_code_commit=json.loads((A/'build-proof.json').read_text())['product_commit'],product_delivery_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=P).decode().strip(),app_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),container=NAME,source_container=SOURCE,owner='OracleRedoKernel',scope='oracle-http-mixed',control_schema=control['user'],loopback_port=37216,host_port_published=False,source_business_dml=0),indent=2))
args=['docker','run','-d','--name',NAME,'--label','owner=OracleRedoKernel','--label','scope=oracle-http-mixed','--user','0','--network','container:'+SOURCE,'--workdir','/evidence','--env-file',str(O/'runtime.secret.env')]
for path,dest in [(O,'/evidence'),(F,'/frozen'),(E,'/empty')]:args+=['--mount','type=bind,source='+str(path)+',target='+dest+(',readonly' if dest in ['/frozen','/empty'] else '')]
args+=['--entrypoint','java','flink:1.18-java17','-Xmx1536m','-jar','/evidence/application.jar']
(O/'start.command.json').write_text(json.dumps(args));p=subprocess.run(args,capture_output=True,timeout=120);(O/'start.stdout.txt').write_bytes(p.stdout);(O/'start.stderr.txt').write_bytes(p.stderr);(O/'start.rc.txt').write_text(str(p.returncode));assert p.returncode==0;print('OWNED_HTTP_RUNTIME_STARTED|'+NAME)
