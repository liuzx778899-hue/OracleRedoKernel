whenever sqlerror exit failure rollback
whenever oserror exit failure rollback
set pages 0 lines 32767 long 32767 trimspool on feedback off
alter session set container=ORKPDB;
select json_object('id' value id,'txt' value txt,'amount' value amount,'dt' value to_char(dt,'YYYY-MM-DD HH24:MI:SS'),'id_dump' value dump(id,16),'txt_dump' value dump(txt,16),'amount_dump' value dump(amount,16),'date_dump' value dump(dt,16) null on null) from ORKLAB.T_BASIC order by ID;
exit success
