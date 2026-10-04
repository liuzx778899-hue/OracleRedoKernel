-- New isolated fixture. One execution only; do not replay after any attempted run.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 trimspool on feedback off serveroutput on
select 'START_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
select 'SOURCE|'||name||'|'||dbid||'|'||log_mode||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
declare lab_id number; lab_mode varchar2(30); minlog varchar2(30); alllog varchar2(30);
begin
  select dbid,log_mode,supplemental_log_data_min,supplemental_log_data_all into lab_id,lab_mode,minlog,alllog from v$database;
  if lab_id!=1276726821 or lab_mode!='ARCHIVELOG' or minlog!='NO' or alllog!='NO' then
    raise_application_error(-20001,'dedicated no-supplemental source binding mismatch');
  end if;
end;
/
alter session set container=ORKPDB;
create table ORKLAB.T_INDEX_RB_20261004 (ID number,TXT varchar2(100),constraint PK_INDEX_RB_20261004 primary key(ID));
insert into ORKLAB.T_INDEX_RB_20261004 values (1,'one');
insert into ORKLAB.T_INDEX_RB_20261004 values (2,'two');
insert into ORKLAB.T_INDEX_RB_20261004 values (3,'three');
select 'BASE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
select 'BASE_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
update ORKLAB.T_INDEX_RB_20261004 set TXT='keep' where ID=1;
savepoint keep_txt;
update ORKLAB.T_INDEX_RB_20261004 set ID=101,TXT='discard-move' where ID=1;
delete from ORKLAB.T_INDEX_RB_20261004 where ID=2;
insert into ORKLAB.T_INDEX_RB_20261004 values (4,'discard-insert');
select 'SAVEPOINT_TX|'||dbms_transaction.local_transaction_id from dual;
rollback to keep_txt;
select 'AFTER_SAVEPOINT_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
select 'SAVEPOINT_TRUTH|'||ID||'|'||TXT||'|'||dump(ID,16)||'|'||dump(TXT,16) from ORKLAB.T_INDEX_RB_20261004 order by ID;
update ORKLAB.T_INDEX_RB_20261004 set ID=103 where ID=3;
delete from ORKLAB.T_INDEX_RB_20261004 where ID=1;
select 'FULL_TX|'||dbms_transaction.local_transaction_id from dual;
rollback;
select 'AFTER_FULL_TX|'||nvl(dbms_transaction.local_transaction_id,'NONE') from dual;
select 'FINAL_TRUTH|'||ID||'|'||TXT||'|'||dump(ID,16)||'|'||dump(TXT,16) from ORKLAB.T_INDEX_RB_20261004 order by ID;
select 'OBJECT|'||object_name||'|'||object_id||'|'||data_object_id from dba_objects where owner='ORKLAB' and object_name in ('T_INDEX_RB_20261004','PK_INDEX_RB_20261004') order by object_name;
select 'HEAP_ROW|'||ID||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from ORKLAB.T_INDEX_RB_20261004 order by ID;
alter session set container=CDB$ROOT;
select 'END_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
exit success
