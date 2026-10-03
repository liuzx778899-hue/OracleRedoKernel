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
create table ORKLAB.T_UNDO_RESUME_20261004 (ID number primary key,TXT varchar2(1000));
begin
  for i in 1..20 loop
    insert into ORKLAB.T_UNDO_RESUME_20261004 values (i,rpad('baseline-'||i,900,'B'));
  end loop;
end;
/
select 'BASE_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
declare first_block number; last_block number;
begin
  select dbms_rowid.rowid_block_number(rowid) into first_block from ORKLAB.T_UNDO_RESUME_20261004 where ID=1;
  select dbms_rowid.rowid_block_number(rowid) into last_block from ORKLAB.T_UNDO_RESUME_20261004 where ID=20;
  if first_block=last_block then raise_application_error(-20002,'selected rows do not span heap blocks'); end if;
end;
/
select 'HEAP_BLOCK|'||ID||'|'||dbms_rowid.rowid_relative_fno(rowid)||'|'||dbms_rowid.rowid_block_number(rowid)||'|'||dbms_rowid.rowid_row_number(rowid) from ORKLAB.T_UNDO_RESUME_20261004 where ID in (1,20) order by ID;
declare lab_id number;
begin
  for i in 1..60 loop
    lab_id:=case when mod(i,2)=0 then 1 else 20 end;
    update ORKLAB.T_UNDO_RESUME_20261004 set TXT=rpad('keep-'||lab_id||'-'||to_char(i,'FM000'),900,'K') where ID=lab_id;
    if i=1 then dbms_output.put_line('CHAIN_TX|'||dbms_transaction.local_transaction_id); end if;
  end loop;
  savepoint kept60;
  for i in 1..60 loop
    lab_id:=case when mod(i,2)=0 then 1 else 20 end;
    update ORKLAB.T_UNDO_RESUME_20261004 set TXT=rpad('discard-'||lab_id||'-'||to_char(i,'FM000'),900,'D') where ID=lab_id;
  end loop;
end;
/
select 'BEFORE_ROLLBACK_TX|'||dbms_transaction.local_transaction_id from dual;
rollback to kept60;
select 'AFTER_ROLLBACK_TX|'||dbms_transaction.local_transaction_id from dual;
select 'KEPT|'||ID||'|'||dump(substr(TXT,1,12),16) from ORKLAB.T_UNDO_RESUME_20261004 where ID in (1,20) order by ID;
declare lab_id number;
begin
  for i in 1..10 loop
    lab_id:=case when mod(i,2)=0 then 1 else 20 end;
    update ORKLAB.T_UNDO_RESUME_20261004 set TXT=rpad('resume-'||lab_id||'-'||to_char(i,'FM000'),900,'R') where ID=lab_id;
  end loop;
end;
/
select 'CHAIN_TX|'||dbms_transaction.local_transaction_id from dual;
commit;
select json_object('id' value ID,'txt' value TXT,'txt_length' value lengthb(TXT),'txt_dump' value dump(TXT,16) returning varchar2(4000)) as row_json from ORKLAB.T_UNDO_RESUME_20261004;
select json_object('name' value object_name,'object_id' value object_id,'data_object_id' value data_object_id) from dba_objects where owner='ORKLAB' and object_name in ('T_UNDO_RESUME_20261004',(select constraint_name from dba_constraints where owner='ORKLAB' and table_name='T_UNDO_RESUME_20261004' and constraint_type='P'));
select json_object('column_id' value column_id,'internal_column_id' value internal_column_id,'name' value column_name,'type' value data_type) from dba_tab_cols where owner='ORKLAB' and table_name='T_UNDO_RESUME_20261004' order by internal_column_id;
alter session set container=CDB$ROOT;
select 'END_SCN|'||to_char(current_scn,'FM99999999999999999999') from v$database;
alter system archive log current;
select 'ARCHIVE|'||thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
exit success
