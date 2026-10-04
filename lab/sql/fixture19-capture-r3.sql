-- One-shot dedicated capture fixture. Do not replay after any attempted execution.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 1000 feedback off trimspool on
select 'SOURCE|'||name||'|'||dbid||'|'||log_mode||'|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
select 'BEFORE_LOG|'||thread#||'|'||sequence#||'|'||group# from v$log where status='CURRENT';
declare seq number; minlog varchar2(30); alllog varchar2(30);
begin
  select sequence# into seq from v$log where status='CURRENT' and thread#=1;
  select supplemental_log_data_min,supplemental_log_data_all into minlog,alllog from v$database;
  if seq!=17 or minlog!='NO' or alllog!='NO' then raise_application_error(-20001,'capture fixture binding mismatch'); end if;
end;
/
alter session set container=ORKPDB;
create table ORKLAB.T_CAPTURE_R3_20261004 (ID number primary key,TXT varchar2(1000));
begin
  for i in 1..40 loop
    insert into ORKLAB.T_CAPTURE_R3_20261004 values(i,rpad('capture-'||i,900,'C'));
    commit;
    dbms_session.sleep(0.1);
  end loop;
end;
/
select 'TRUTH|'||count(*)||'|'||sum(lengthb(TXT)) from ORKLAB.T_CAPTURE_R3_20261004;
alter session set container=CDB$ROOT;
alter system archive log current;
select 'AFTER_LOG|'||thread#||'|'||sequence#||'|'||group# from v$log where status='CURRENT';
select 'ARCHIVE|'||sequence#||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where sequence#=17 and thread#=1 and name is not null order by recid;
exit success
