"""Reuse the RAW normal product harness for one isolated temporal batch, with its complete derived driver preserved."""
from pathlib import Path
import json,ast
R=Path(__file__).resolve().parent.parent;E=R/'lab/out/d4-temporal-20261009/product'
text=(R/'lab/d4_raw_product.py').read_text()
def replace(old,new):
 global text
 assert old in text,old
 text=text.replace(old,new)
for old,new in [('d4-raw-product-20261009','d4-temporal-product-20261009'),('d4-raw-source-20261009','d4-temporal-source-20261009'),('oracle-d4-raw','oracle-d4-time'),('ORKRAWCTL_20261009','ORKTIMECTL_20261009'),('RRAW1','RTIME1'),('oracle-raw-RTIME1','oracle-time-RTIME1'),('1239501','1239601'),('resume-acquisition.rc','acquisition.rc'),('parser-r4/normal-proof.json','parser-r8/normal-proof.json'),('3021645','3030921'),('95.raw','96.raw'),('raw95','time96'),('T_D4_RAW_20261009','T_D4_TIME_20261009'),('PK_D4_RAW_20261009','PK_D4_TIME_20261009'),('19501','19601'),('19502','19602'),('(1,95,95,0,4)','(1,96,96,0,4)')]:replace(old,new)
replace("application=R/'lab/out/d4-raw-20261009/raw-application.jar';assert sha(application)=='073c95364a9a15305527a139e846bdd6ac754839930af8cb705cfd6cab1f23c9'",
        "application=R/'lab/out/d4-temporal-20261009/product/application.jar';assert sha(application)==json.loads((application.parent/'product-proof.json').read_text())['applicationSha256']")
replace("resume-acquisition.command.json","acquisition.command.json")
replace("rawtohex(BIN)","to_char(D,'YYYY-MM-DD\\\"T\\\"HH24:MI:SS')||'|'||to_char(TS,'YYYY-MM-DD\\\"T\\\"HH24:MI:SS.FF9')")
replace('dump(BIN,16)',"dump(D,16)||'|'||dump(TS,16)")
replace('SOURCE|1|010080FF','SOURCE|1|2026-10-09T12:13:14|2026-10-09T12:13:14.987654321')
replace('ROW|1|010080FF|','ROW|1|2026-10-09T12:13:14|2026-10-09T12:13:14.987654321|')
replace('(ID number primary key,BIN raw(256))','(ID number primary key,D date,TS timestamp(9))')
replace("values(1,hextoraw('00FF80C328'))","values(1,to_date('2024-02-29 23:59:58','YYYY-MM-DD HH24:MI:SS'),timestamp '2024-02-29 23:59:58.123456789')")
replace("'RAW:'+v['hex'] if isinstance(v,dict)","v['type']+':'+v['iso'] if isinstance(v,dict)")
ast.parse(text);driver=E/'runtime-derived.py';assert not driver.exists();driver.write_text(text)
# The unchanged harness copies this complete derived source before its first action.
exec(compile(text,str(driver),'exec'),dict(__file__=str(driver)))
