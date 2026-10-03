whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 300 feedback off trimspool on
select thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null and deleted='NO' order by recid desc fetch first 3 rows only;
exit success
