whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 250 feedback on
alter session set tracefile_identifier='ORK19_NOSUPP_8_9';
alter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_8_1245693159.dbf';
alter system dump logfile '/opt/oracle/product/19c/dbhome_1/dbs/arch1_9_1245693159.dbf';
select value from v$diag_info where name='Default Trace File';
exit success
