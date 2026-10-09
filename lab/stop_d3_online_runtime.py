"""Stop the owned proof after its known refusal; preserve logs, inputs and once-only markers."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(R/'lab/d3_stream_runtime.py'),'inspect','online-stop-r6']
exec((R/'lab/d3_stream_runtime.py').read_text().split("if action=='prepare':")[0])
N=R/'lab/out/d3-online-product-20261010';REMOTE='/tmp/ork-online-product-20261010'
props=dict(line.split('=',1) for line in (N/'runtime.secret.env').read_text().splitlines());secrets.extend(v.encode() for k,v in props.items() if 'PASSWORD' in k and v)
pid=run('controlled-stop-pid',['docker','exec',S,'cat',REMOTE+'/collector.pid']).strip();assert pid.isdecimal()
command=run('controlled-stop-owner',['docker','exec',S,'cat','/proc/'+pid+'/cmdline']);assert command.split('\0')[0]==REMOTE+'/sh_agent'
run('controlled-stop-collector',['docker','exec',S,'kill','-TERM',pid])
assert run('controlled-stop-helper-owner',['docker','inspect',HELPER,'--format','{{.Config.Labels.owner}}|{{.Config.Labels.scope}}']).strip()=='OracleRedoKernel|oracle-online-product'
run('controlled-stop-logs',['docker','logs',HELPER]);run('controlled-stop-helper',['docker','stop','--timeout','30',HELPER]);run('controlled-stop-remove',['docker','rm',HELPER])
run('controlled-stop-source',['docker','stop','--timeout','60',S])
