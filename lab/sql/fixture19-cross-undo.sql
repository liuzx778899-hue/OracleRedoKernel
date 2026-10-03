-- New dedicated table; do not replay this script after any attempted execution.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 long 32767 trimspool on feedback off serveroutput on
set serveroutput on
column row_json format a4000
select 'START_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
select 'SOURCE|'||name||'|'||dbid||'|'||log_mode||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
declare
  lab_log_mode varchar2(30); minlog varchar2(30); alllog varchar2(30);
begin
  select log_mode,supplemental_log_data_min,supplemental_log_data_all into lab_log_mode,minlog,alllog from v$database;
  if lab_log_mode!='ARCHIVELOG' or minlog!='NO' or alllog!='NO' then raise_application_error(-20001,'dedicated no-supplemental binding mismatch'); end if;
end;
/
alter session set container=ORKPDB;
create table ORKLAB.T_UNDO_CHAIN_20261004 (ID number primary key,TXT varchar2(1000));
insert into ORKLAB.T_UNDO_CHAIN_20261004 values (1,rpad('baseline',900,'B'));
select 'BASE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
begin
  for i in 1..60 loop
    update ORKLAB.T_UNDO_CHAIN_20261004 set TXT=rpad('keep-'||to_char(i,'FM000'),900,'K') where ID=1;
    if i=1 then dbms_output.put_line('CHAIN_TX|'||dbms_transaction.local_transaction_id); end if;
  end loop;
  savepoint kept60;
  for i in 1..60 loop
    update ORKLAB.T_UNDO_CHAIN_20261004 set TXT=rpad('discard-'||to_char(i,'FM000'),900,'D') where ID=1;
  end loop;
  rollback to kept60;
  dbms_output.put_line('EXPECTED_KEEP|60|UNDONE|60');
end;
/
commit;
select json_object('id' value ID,'txt' value TXT,'txt_length' value lengthb(TXT),'txt_dump' value dump(TXT,16) returning varchar2(4000)) as row_json from ORKLAB.T_UNDO_CHAIN_20261004;
select json_object('name' value object_name,'object_id' value object_id,'data_object_id' value data_object_id) from dba_objects where owner='ORKLAB' and object_name in ('T_UNDO_CHAIN_20261004',(select constraint_name from dba_constraints where owner='ORKLAB' and table_name='T_UNDO_CHAIN_20261004' and constraint_type='P'));
select json_object('column_id' value column_id,'internal_column_id' value internal_column_id,'name' value column_name,'type' value data_type) from dba_tab_cols where owner='ORKLAB' and table_name='T_UNDO_CHAIN_20261004' order by internal_column_id;
alter session set container=CDB$ROOT;
select 'END_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
exit success
