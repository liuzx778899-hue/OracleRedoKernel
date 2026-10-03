"""Two real SQLPlus sessions: preserve a PDB transaction across a root log switch."""
import json
import queue
import subprocess
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT/'lab/out/oracle19-nosupp-20261004'
CONTAINER = 'oraredo-19c-lab-20261004'
if (OUT/'cross-session-a.txt').exists():
    raise RuntimeError('cross fixture already attempted; do not replay committed actions')
binding = subprocess.check_output(['docker','inspect',CONTAINER,'--format','{{.Config.Labels.owner}}'], text=True).strip()
if binding != 'OracleRedoKernel':
    raise RuntimeError('source binding mismatch')
args = ['docker','exec','-i','-e','NLS_LANG=.AL32UTF8',CONTAINER,'sqlplus','-s','/','as','sysdba']
process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', bufsize=1)
lines = []
mailbox = queue.Queue()

def read():
    for line in process.stdout:
        lines.append(line)
        mailbox.put(line.strip())
threading.Thread(target=read, daemon=True).start()

def write(sql):
    process.stdin.write(sql+'\n')
    process.stdin.flush()

def barrier(name):
    while mailbox.get(timeout=60) != name:
        if process.poll() is not None:
            raise RuntimeError('session exited before barrier '+name)

def other(name, sql):
    text='whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 250 feedback on\n'+sql+'\nexit success\n'
    (OUT/(name+'.sql')).write_text(text, encoding='utf-8')
    result=subprocess.run(args,input=text,capture_output=True,text=True,encoding='utf-8',timeout=120)
    (OUT/(name+'.stdout.txt')).write_text(result.stdout,encoding='utf-8')
    (OUT/(name+'.stderr.txt')).write_text(result.stderr,encoding='utf-8')
    with (OUT/'commands.jsonl').open('a',encoding='utf-8') as stream:
        stream.write(json.dumps({'case':name,'command':args,'rc':result.returncode})+'\n')
    if result.returncode:
        raise RuntimeError(name+' failed')

try:
    write("whenever sqlerror exit failure rollback\nwhenever oserror exit failure rollback\nset pages 0 lines 250 feedback on\nalter session set container=ORKPDB;\ninsert into ORKLAB.T_BASIC values (28,'cross-switch',28,date '2026-10-04');\nselect dbms_transaction.local_transaction_id from dual;\nprompt ORK_CROSS_READY")
    barrier('ORK_CROSS_READY')
    other('cross-root-switch', 'select sequence# from v$log where status=\'CURRENT\';\nalter system switch logfile;\nselect sequence# from v$log where status=\'CURRENT\';')
    write("update ORKLAB.T_BASIC set TXT='cross-committed' where ID=28;\ncommit;\ninsert into ORKLAB.T_BASIC values (29,'session-a',29,date '2026-10-04');\nselect dbms_transaction.local_transaction_id from dual;\nprompt ORK_CONCURRENT_READY")
    barrier('ORK_CONCURRENT_READY')
    other('concurrent-session-b', "alter session set container=ORKPDB;\ninsert into ORKLAB.T_BASIC values (30,'session-b',30,date '2026-10-04');\nselect dbms_transaction.local_transaction_id from dual;\ncommit;")
    write('commit;\nexit success')
    process.stdin.close()
    rc=process.wait(timeout=60)
    if rc:
        raise RuntimeError('session-a rc='+str(rc))
finally:
    if process.poll() is None:
        # Closing the client rolls back this fixture session; no container stop.
        process.terminate()
        process.wait(timeout=15)
    (OUT/'cross-session-a.txt').write_text(''.join(lines),encoding='utf-8')
print('T005/T008 source actions complete; parser/transaction acceptance still pending.')
