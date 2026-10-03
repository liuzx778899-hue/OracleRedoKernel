whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 long 32767 trimspool on feedback off
column row_json format a4000
alter session set container=ORKPDB;
select json_object('id' value ID,'txt' value TXT,'txt_length' value lengthb(TXT),'txt_dump' value dump(TXT,16) returning varchar2(4000)) as row_json from ORKLAB.T_UNDO_MULTI_20261004;
select json_object('name' value object_name,'object_id' value object_id,'data_object_id' value data_object_id) from dba_objects where owner='ORKLAB' and object_name in ('T_UNDO_MULTI_20261004',(select constraint_name from dba_constraints where owner='ORKLAB' and table_name='T_UNDO_MULTI_20261004' and constraint_type='P'));
select json_object('column_id' value column_id,'internal_column_id' value internal_column_id,'name' value column_name,'type' value data_type) from dba_tab_cols where owner='ORKLAB' and table_name='T_UNDO_MULTI_20261004' order by internal_column_id;
alter session set container=CDB$ROOT;
select 'SUPPLEMENTAL|'||supplemental_log_data_min||'|'||supplemental_log_data_all from v$database;
exit success
