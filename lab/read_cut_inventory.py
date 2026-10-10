"""Read-only inventory for the next contiguous capture window; does not switch or change source rows."""
from pathlib import Path
import sys
R=Path(__file__).resolve().parent.parent
sys.argv=[str(__file__),'inventory-r2']
exec((R/'lab/read_cut_runtime.py').read_text().split('try:\n    assert (N/')[0])
print(sql('source',"select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||name from v$archived_log where sequence# >= 102 and deleted='NO' and name is not null order by sequence#;\nselect 'CURRENT|'||sequence# from v$log where status='CURRENT';\nselect 'MEMBER|'||member from v$logfile order by group#;"))
