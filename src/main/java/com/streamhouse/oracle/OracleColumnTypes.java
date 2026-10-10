package com.streamhouse.oracle;

/** Supported business value types; SYS layout types have a separate contract. */
public final class OracleColumnTypes {
    /** Exact observed SYS.COL$ property for the supported physical columns. */
    public static long property(String type,boolean hidden) {
        return (hidden?17179869216L:0) | (type.equals("BLOB") || type.equals("CLOB")?128:0);
    }
    private OracleColumnTypes() {}
    /** Observed COL$ NULL$: 1 is the implicit primary-key non-null form, 2 explicit NOT NULL. */
    public static boolean nullable(long flag)throws RedoException {
        if(flag==0)return true;
        if(flag==1 || flag==2)return false;
        throw new RedoException("UNSUPPORTED_COLUMN_NULL_FLAG");
    }
    public static int oracleId(String type)throws RedoException {
        return switch(type) {
            case "VARCHAR2"->1;
            case "NUMBER"->2;
            case "DATE"->12;
            case "RAW"->23;
            case "TIMESTAMP"->180;
            case "CLOB"->112;
            case "BLOB"->113;
            default->throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        };
    }
    public static String name(long type)throws RedoException {
        if(type<0 || type>Integer.MAX_VALUE)throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        return switch((int)type) {
            case 1->"VARCHAR2";
            case 2->"NUMBER";
            case 12->"DATE";
            case 23->"RAW";
            case 180->"TIMESTAMP";
            case 112->"CLOB";
            case 113->"BLOB";
            default->throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        };
    }
}
