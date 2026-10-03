-- Read-only truth + archive freeze on the dedicated source; does not replay prior DML.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 long 32767 trimspool on feedback off
alter session set container=ORKPDB;
select json_object('id' value id,'txt' value txt,'amount' value amount,'dt' value to_char(dt,'YYYY-MM-DD HH24:MI:SS'),'id_dump' value dump(id,16),'txt_dump' value dump(txt,16),'amount_dump' value dump(amount,16),'date_dump' value dump(dt,16) null on null) from ORKLAB.T_BASIC order by ID;
select json_object('owner' value owner,'name' value object_name,'object_id' value object_id,'data_object_id' value data_object_id) from dba_objects where owner='ORKLAB' order by object_id;
select json_object('table' value table_name,'column_id' value column_id,'internal_column_id' value internal_column_id,'name' value column_name,'type' value data_type) from dba_tab_cols where owner='ORKLAB' order by table_name,internal_column_id;
alter session set container=CDB$ROOT;
select to_char(current_scn,'FM99999999999999999999') from v$database;
select supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
alter system archive log current;
select thread#||'|'||sequence#||'|'||to_char(first_change#,'FM99999999999999999999')||'|'||to_char(next_change#,'FM99999999999999999999')||'|'||blocks||'|'||block_size||'|'||name from v$archived_log where name is not null order by recid;
exit success
