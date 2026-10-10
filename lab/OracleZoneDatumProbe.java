import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Source-owned JDBC byte/context observation; no DML, dump, or SQL truth enters a production event. */
public class OracleZoneDatumProbe {
    static String context(Connection c)throws Exception {
        try(var s=c.createStatement();var r=s.executeQuery("select dbtimezone,filename,version from v$timezone_file")) {
            if(!r.next())throw new AssertionError("TIMEZONE_CONTEXT_EMPTY");
            String v=r.getString(1)+"|"+r.getString(2)+"|"+r.getInt(3);
            if(r.next())throw new AssertionError("TIMEZONE_CONTEXT_AMBIGUOUS");return v;
        }
    }
    public static void main(String[] args)throws Exception {
        var secret=Files.readAllLines(Path.of(args[0]));
        try(var c=DriverManager.getConnection("jdbc:oracle:thin:@//127.0.0.1:1521/ORKPDB",secret.get(0),secret.get(1))) {
            String before=context(c);System.out.println("CONTEXT|BEFORE|"+before);
            var api=Class.forName("oracle.jdbc.OracleConnection");var oracle=c.unwrap(api);
            var names=new HashSet<String>();
            try(var s=c.createStatement();var r=s.executeQuery("select distinct tzname from v$timezone_names")) {while(r.next())names.add(r.getString(1));}
            var map=Class.forName("oracle.sql.ZONEIDMAP");var regions=new TreeMap<Integer,String>();
            for(String name:names) {
                int id=(Integer)map.getMethod("getID",String.class).invoke(null,name);
                if(id<0)continue;String canonical=(String)map.getMethod("getRegion",int.class).invoke(null,id);
                if(canonical!=null && names.contains(canonical))regions.put(id,canonical);
            }
            if(!"Europe/Paris".equals(regions.get(382)))throw new AssertionError("OBSERVED_PARIS_REGION_MAPPING");
            for(var e:regions.entrySet())System.out.println("REGION|"+e.getKey()+"|"+e.getValue());
            for(String session:List.of("+00:00","+05:30")) {
                api.getMethod("setSessionTimeZone",String.class).invoke(oracle,session);
                try(var s=c.createStatement();var r=s.executeQuery("select ID,TZ,TZR,LTZ from ORKLAB.T_D4_VARIANTS_20261011 order by ID")) {
                    var resultApi=Class.forName("oracle.jdbc.OracleResultSet");var rows=r.unwrap(resultApi);
                    while(r.next()) {
                        var out=new StringBuilder("ROW|").append(session).append('|').append(r.getInt(1));
                        for(int i=2;i<=4;i++) {
                            Object datum=resultApi.getMethod(i==4?"getTIMESTAMPLTZ":"getTIMESTAMPTZ",int.class).invoke(rows,i);
                            byte[] bytes=(byte[])Class.forName("oracle.sql.Datum").getMethod("getBytes").invoke(datum);
                            out.append('|').append(HexFormat.of().formatHex(bytes));
                        }
                        System.out.println(out);
                    }
                }
            }
            String after=context(c);if(!before.equals(after))throw new AssertionError("TIMEZONE_CONTEXT_CHANGED");
            System.out.println("CONTEXT|AFTER|"+after);
        }
    }
}
