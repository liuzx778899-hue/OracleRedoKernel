"""Close two D2 target/CPACK tails using original HTTP receipts and frozen redo. No source DML."""
from pathlib import Path
import ast,json,os,shutil,subprocess,sys
R=Path(__file__).resolve().parent.parent
kind=sys.argv[1];assert kind in ['initial','overlap']
old=R/f'lab/out/d2-{kind}-runtime-20261008';source=R/f'lab/out/d2-{kind}-source-20261008'
N=R/f'lab/out/d2-{kind}-product-20261010';N.mkdir()
application=R/'lab/out/d3-online-product-20261010/application-final.jar'
libs=application.parent/'libs-final';cp=os.pathsep.join(map(str,libs.glob('*.jar')))
def prep(name,args):
 p=subprocess.run(list(map(str,args)),capture_output=True,timeout=180)
 (N/(name+'.stdout')).write_bytes(p.stdout);(N/(name+'.stderr')).write_bytes(p.stderr);(N/(name+'.rc')).write_text(str(p.returncode))
 assert p.returncode==0,(name,p.returncode)
prep('compile',['D:/work/Java/jdk-17.0.7/bin/javac.exe','-cp',cp,'-d',N,R/'lab/D2ProductPreparationProbe.java'])
b=json.loads((old/'baseline-reference.json').read_text());receipt=json.loads((old/'historical-acquire.stdout').read_text())['data']
assert b['trustedHistoricalHttp'] and (old/'historical-acquire.rc').read_text()=='0'
count=3 if kind=='initial' else 5;first=86 if kind=='initial' else 88;last=first+1
prep('preparation',['D:/work/Java/jdk-17.0.7/bin/java.exe','-Xmx1536m','-cp',str(N)+os.pathsep+cp,'D2ProductPreparationProbe',b['path'],b['sha256'],(old/'index-leaf.txt').read_text().strip(),count,N/'expected.state',source/f'{first}.raw',source/f'{last}.raw'])
prepared=json.loads((N/'preparation.stdout').read_text());prepared['metadata']=json.loads(receipt['metadataJson'])
(N/'preparation.stdout').write_text(json.dumps(prepared))
text=(R/'lab/d4_raw_product.py').read_text()
def replace(a,b):
 global text
 assert a in text,a
 text=text.replace(a,b)
for a,v in [('d4-raw-product-20261009',N.name),('d4-raw-source-20261009',source.name),('oracle-d4-raw','oracle-d2-'+kind),('ORKRAWCTL_20261009','ORKD2INITCTL_20261008' if kind=='initial' else 'ORKD2OVERCTL_20261008'),('RRAW1','RINIT1' if kind=='initial' else 'ROVER1'),('1239501',b['acquisition_id']),('T_D4_RAW_20261009',b['sourceFixture']),('PK_D4_RAW_20261009','PK_D2_'+('INITIAL' if kind=='initial' else 'OVERLAP')+'_20261008'),('95.raw',str(first)+'.raw'),('raw95','d2-'+kind),('3021645',str(b['scn'])),('resume-acquisition.rc','historical-acquire.rc')]:replace(a,v)
replace("assert (N/'preparation.rc').read_text().strip()=='0' and (A/'historical-acquire.rc').read_text()=='0'", "assert (N/'preparation.rc').read_text().strip()=='0'")
replace("assert (A/'parser-r4/normal-proof.json').exists()", "assert (Path("+repr(str(old))+ ")/'normal-summary.json').exists()")
replace("reference=json.loads((A/'baseline-reference.json').read_text())", "reference="+repr(b))
replace("application=R/'lab/out/d4-raw-20261009/raw-application.jar';assert sha(application)=='073c95364a9a15305527a139e846bdd6ac754839930af8cb705cfd6cab1f23c9'", "application=R/'lab/out/d3-online-product-20261010/application-final.jar';assert sha(application)=='d028ca5450bfe95bab4bcac09eafc39054b023e814f2dd32e83eeffb38ee4350'")
replace(f"os.link(A/'{first}.raw',N/'inputs/{first}.raw')", f"os.link(A/'{first}.raw',N/'inputs/{first}.raw');os.link(A/'{last}.raw',N/'inputs/{last}.raw')")
start=text.index("(N/'actual-early-acquisition-import.json')");end=text.index('\ndef clob',start);text=text[:start]+text[end:]
start=text.index(" assert 'FREE|0'");end=text.index(' props.update',start)
text=text[:start]+" props=dict(line.split('=',1) for line in (Path("+repr(str(old))+ ")/'runtime.secret.env').read_text().splitlines());password=props['SPRING_DATASOURCE_PASSWORD'];secrets.append(password.encode())\n"+text[end:]
replace("SH_ORACLE_ACQUISITION_AUTHORITY='lab-import-real-early-V3-record'","SH_ORACLE_ACQUISITION_AUTHORITY="+repr(receipt['authority']))
start=text.index(" sql('early-acquisition-import'");end=text.index(" verified=http",start);text=text[:start]+text[end:]
replace('rawtohex(BIN)',"TXT||'|'||NOTE")
replace('dump(BIN,16)',"dump(TXT,16)")
replace('SOURCE|1|010080FF','SOURCE|0|statistics-seed|anchor')
replace('ROW|1|010080FF|','ROW|0|statistics-seed|anchor|')
replace('(ID number primary key,BIN raw(256))','(ID number primary key,TXT varchar2(120),NOTE varchar2(80))')
replace("values(1,hextoraw('00FF80C328'))","values(0,'statistics-seed','anchor')"+(";\\ninsert into {T}_ROWS values(1,'statistics-seed','anchor')" if kind=='overlap' else ''))
replace(f"capturePaths=['/evidence/inputs/{first}.raw']",f"capturePaths=['/evidence/inputs/{first}.raw','/evidence/inputs/{last}.raw']")
replace("19501,'{SOURCE}','d2-"+kind+"',1,1", "19501,'{SOURCE}','d2-"+kind+"',1,2")
replace("19502,'{SOURCE}','oracle','d2-"+kind+"',1,1", "19502,'{SOURCE}','oracle','d2-"+kind+"',1,2")
replace(f"'{first}.raw {{sha(A/'{first}.raw')}}'",f"'{first}.raw {{sha(A/'{first}.raw')}}\\n{last}.raw {{sha(A/'{last}.raw')}}'")
replace("'RAW:'+v['hex'] if isinstance(v,dict)","'VARCHAR2:'+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=') if isinstance(v,str)")
replace("'BATCH|PUBLISHED|0|4'",repr(f'BATCH|PUBLISHED|0|{count}'))
replace("'COUNTS|4|4|1|4|1'",repr(f'COUNTS|{count}|{count}|1|{count}|1'))
replace("'ROW_COUNT|1'",repr('ROW_COUNT|'+('1' if kind=='initial' else '2')))
replace("|4\" in actual",f"|{count}\" in actual")
replace(f'(1,95,95,0,4)',f'(2,{last},{first},0,{count})')
replace('events=4,target=[4,4,1]',f'events={count},target=[{count},{count},1]')
replace("acquisitionMode='lab-import-of-real-early-acquisition'","acquisitionMode='original-trusted-HTTP-receipt-verified-again'")
# Both archived members comprise a single bounded publication window.
replace("'raw95'",repr('d2-'+kind)) if "'raw95'" in text else None
replace("{(A/'"+str(first)+".raw').stat().st_size}","{sum((A/f'{s}.raw').stat().st_size for s in ["+str(first)+','+str(last)+"])}")
ast.parse(text);driver=N/'runtime-derived.py';driver.write_text(text)
exec(compile(text,str(driver),'exec'),dict(__file__=str(driver)))
