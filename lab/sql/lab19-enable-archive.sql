-- Only the dedicated OracleRedoKernel fixture container. Never shared/production.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
shutdown immediate;
startup mount;
alter database archivelog;
alter database open;
alter pluggable database all open;
select log_mode,supplemental_log_data_min,supplemental_log_data_all from v$database;
exit success
