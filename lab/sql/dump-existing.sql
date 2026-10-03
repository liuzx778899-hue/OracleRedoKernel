whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 300 feedback on
alter session set tracefile_identifier='ORK_R0_3064';
alter system dump logfile '/opt/oracle/homes/OraDBHome21cXE/dbs/arch1_3064_1181008281.dbf' scn min 41434081 scn max 41434100;
select value from v$diag_info where name='Default Trace File';
exit success
