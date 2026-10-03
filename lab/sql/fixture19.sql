-- Dedicated ORK19/ORKPDB only; supplemental logging stays OFF for the strict experiment.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 1000 lines 250 trimspool on feedback on serveroutput on
column dump(id,16) format a60
column dump(txt,16) format a100
column dump(amount,16) format a60
column dump(dt,16) format a60
alter session set container=ORKPDB;
create user ORKLAB no authentication default tablespace USERS quota 10m on USERS;
create table ORKLAB.T_BASIC (ID number(10) primary key, TXT varchar2(100 char), AMOUNT number(12,2), DT date);
select owner,object_name,object_id,data_object_id from dba_objects where owner='ORKLAB' order by object_id;
select owner,table_name,column_id,internal_column_id,column_name,data_type,data_length,data_precision,data_scale from dba_tab_cols where owner='ORKLAB' order by table_name,internal_column_id;
alter session set container=CDB$ROOT;
alter system archive log current;
select current_scn from v$database;
alter session set container=ORKPDB;
prompt T001_INSERT
insert into ORKLAB.T_BASIC values (1,'alpha',123.45,to_date('2026-10-04 08:09:10','YYYY-MM-DD HH24:MI:SS'));
commit;
select id,txt,amount,to_char(dt,'YYYY-MM-DD HH24:MI:SS'),dump(id,16),dump(txt,16),dump(amount,16),dump(dt,16) from ORKLAB.T_BASIC;
prompt T002_UPDATE
update ORKLAB.T_BASIC set TXT='beta' where ID=1;
commit;
prompt T003_DELETE
delete from ORKLAB.T_BASIC where ID=1;
commit;
prompt T004_MULTIPLE_DML
insert into ORKLAB.T_BASIC values (2,'two',-12.34,date '2026-10-04');
update ORKLAB.T_BASIC set AMOUNT=0 where ID=2;
insert into ORKLAB.T_BASIC values (3,'three',NULL,NULL);
delete from ORKLAB.T_BASIC where ID=3;
commit;
prompt T006_ROLLBACK
insert into ORKLAB.T_BASIC values (6,'rollback',6,date '2026-10-04');
rollback;
prompt T007_SAVEPOINT
insert into ORKLAB.T_BASIC values (7,'keep',7,date '2026-10-04');
savepoint S1;
update ORKLAB.T_BASIC set TXT='discard' where ID=7;
insert into ORKLAB.T_BASIC values (8,'discard',8,date '2026-10-04');
rollback to S1;
commit;
prompt T012_CHINESE_NULL_EMPTY
insert into ORKLAB.T_BASIC values (12,unistr('\4E2D\6587'),NULL,NULL);
insert into ORKLAB.T_BASIC values (13,'',0,date '2026-10-04');
commit;
-- T005/T008 require a separate root/session actor, executed by cross19.py.
prompt FINAL_ROWS_AND_CODEC_TRUTH
select id,txt,amount,to_char(dt,'YYYY-MM-DD HH24:MI:SS'),dump(id,16),dump(txt,16),dump(amount,16),dump(dt,16) from ORKLAB.T_BASIC order by id;
alter session set container=CDB$ROOT;
select current_scn from v$database;
alter system archive log current;
set pages 0 feedback off
select thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
exit success
