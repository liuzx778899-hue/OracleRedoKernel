package com.streamhouse.oracle;

/** Supported business value types; SYS layout types have a separate contract. */
public final class OracleColumnTypes {
    private OracleColumnTypes() {}
    public static int oracleId(String type)throws RedoException {
        return switch(type) {
            case "VARCHAR2"->1;
            case "NUMBER"->2;
            case "RAW"->23;
            default->throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        };
    }
    public static String name(long type)throws RedoException {
        if(type<0 || type>Integer.MAX_VALUE)throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        return switch((int)type) {
            case 1->"VARCHAR2";
            case 2->"NUMBER";
            case 23->"RAW";
            default->throw new RedoException("UNSUPPORTED_BUSINESS_COLUMN_TYPE");
        };
    }
}
