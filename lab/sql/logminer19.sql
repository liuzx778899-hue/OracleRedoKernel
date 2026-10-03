-- Independent lab oracle only; never a production decode input.
whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 long 32767 longchunksize 32767 trimspool on feedback off
begin
  dbms_logmnr.add_logfile(logfilename=>'/opt/oracle/product/19c/dbhome_1/dbs/arch1_8_1245693159.dbf',options=>dbms_logmnr.new);
  dbms_logmnr.add_logfile(logfilename=>'/opt/oracle/product/19c/dbhome_1/dbs/arch1_9_1245693159.dbf',options=>dbms_logmnr.addfile);
  dbms_logmnr.start_logmnr(startscn=>2146496,endscn=>2149122,options=>dbms_logmnr.dict_from_online_catalog);
end;
/
select json_object('scn' value to_char(scn,'FM99999999999999999999'),'xid' value rawtohex(xid),'operation' value operation,'rollback' value rollback,'thread' value thread#,'rbasqn' value rbasqn,'rbablk' value rbablk,'rbabyte' value rbabyte,'data_obj' value data_obj#,'row_id' value row_id,'owner' value seg_owner,'table' value table_name,'csf' value csf,'status' value status,'info' value info,'sql_redo' value sql_redo,'sql_undo' value sql_undo null on null returning clob)
from v$logmnr_contents
where seg_owner='ORKLAB' or operation in ('START','COMMIT','ROLLBACK','MISSING_SCN','UNSUPPORTED')
order by scn,rbasqn,rbablk,rbabyte,ssn;
begin dbms_logmnr.end_logmnr; end;
/
exit success
