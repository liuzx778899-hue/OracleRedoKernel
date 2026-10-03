whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 1000 lines 250 trimspool on feedback on
select banner_full from v$version;
select name,dbid,log_mode,supplemental_log_data_min,supplemental_log_data_pk,supplemental_log_data_all,force_logging,cdb,current_scn,resetlogs_change# from v$database;
select instance_name,version_full from v$instance;
select name,open_mode from v$pdbs;
select name,value from v$parameter where name in ('compatible','db_block_size','db_create_file_dest','diagnostic_dest');
select group#,thread#,sequence#,bytes,blocksize,status,archived from v$log order by group#;
select group#,member from v$logfile order by group#;
select thread#,sequence#,first_change#,next_change#,blocks,block_size,name from v$archived_log where name is not null order by recid fetch first 20 rows only;
select parameter,value from nls_database_parameters where parameter in ('NLS_CHARACTERSET','NLS_NCHAR_CHARACTERSET');
exit success
