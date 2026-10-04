package com.streamhouse.oracle;

import java.util.ArrayList;
import java.util.List;
import static com.streamhouse.oracle.RedoBytes.*;

/** Single-piece heap changes observed on 19.3. Patches include rollback redo, not CDC events. */
public final class HeapRowPatch {
    public enum Kind { INSERT, UPDATE, DELETE }
    public enum State { VALUE, NULL, ABSENT }
    public record Column(State state, byte[] bytes) {}
    public record Xid(int usn, int slot, long sequence) {}
    public record Uba(long dba, int sequence, int record) {}
    /** uba is a physical row ITL reference; XR rollback can restore an earlier undo pointer. */
    public record Patch(Kind kind, long dba, int slot, int xtype, Xid xid, Uba uba,
                        List<Column> columns) {}

    public Patch decode(RedoRecords.Vector vector, int dictionaryColumns) throws RedoException {
        if (vector.opcodeMajor() != 11 || (vector.opcodeMinor() != 2
                && vector.opcodeMinor() != 3 && vector.opcodeMinor() != 5))
            throw new RedoException("UNSUPPORTED_HEAP_OPCODE");
        if (dictionaryColumns < 1 || dictionaryColumns > 255)
            throw new RedoException("UNSUPPORTED_HEAP_COLUMN_COUNT");
        List<byte[]> fields=vector.fields();
        if (fields.size() < 2) throw new RedoException("MISSING_KTB_KDO_FIELDS");
        byte[] ktb=fields.get(0);
        // Observed short KTB Z carries no current XID/UBA. Only the proven packed XR update is accepted.
        if (ktb.length==8 && (ktb[0]&255)==3 && (ktb[1]&255)==0x0d) {
            if (vector.opcodeMinor()!=5 || fields.get(1).length!=28 || (fields.get(1)[11]&255)!=0x82)
                throw new RedoException("UNSUPPORTED_KTB_Z_ROW_PROFILE");
            return decodeKdo(fields,vector.opcodeMinor(),vector.dba(),dictionaryColumns);
        }
        if (ktb.length < 16) throw new RedoException("TRUNCATED_KTB");
        Xid xid=null;
        int ubaOffset=-1;
        if ((ktb[0]&255) == 1 || (ktb[0]&255) == 17) {
            if (ktb.length < 24) throw new RedoException("TRUNCATED_KTB_XID");
            xid=new Xid(u16(ktb,8),u16(ktb,10),u32(ktb,12));
            ubaOffset=16;
        } else if ((ktb[0]&255) == 2) ubaOffset=8;
        else if ((ktb[0]&255) == 4) {
            // KTB L on rollback references an older row ITL, not the current rollback XID/undo.
            if (ktb.length < 24) throw new RedoException("TRUNCATED_KTB_ITL");
        }
        else throw new RedoException("UNSUPPORTED_KTB_OPERATION");
        Uba uba=ubaOffset < 0 ? null : new Uba(u32(ktb,ubaOffset),u16(ktb,ubaOffset+4),ktb[ubaOffset+6]&255);
        var row=decodeKdo(fields,vector.opcodeMinor(),vector.dba(),dictionaryColumns);
        return new Patch(row.kind(),row.dba(),row.slot(),row.xtype(),xid,uba,row.columns());
    }

    /** Shared physical KDO decoding. Undo KTB does not describe the current transaction identity. */
    static Patch decodeKdo(List<byte[]> fields,int op,long dba,int dictionaryColumns) throws RedoException {
        if (dictionaryColumns < 1 || dictionaryColumns > 255)
            throw new RedoException("UNSUPPORTED_HEAP_COLUMN_COUNT");
        if (fields.size()<2 || (op!=2 && op!=3 && op!=5))
            throw new RedoException("UNSUPPORTED_HEAP_OPCODE_OR_FIELDS");
        byte[] kdo=fields.get(1);
        boolean packed=op==5 && kdo.length==28 && ((kdo[11]&255)==0x81 || (kdo[11]&255)==0x82);
        int expectedKdo=op == 2 ? 49 : op == 3 ? 20 : packed ? 28 : 29;
        if (kdo.length != expectedKdo || (kdo[10]&255) != op || u32(kdo,0) != dba)
            throw new RedoException("UNSUPPORTED_KDO_LAYOUT_OR_BLOCK");
        int xtype=(kdo[11]&255)&(packed ? 0x7f : 0xff);
        if (xtype != 1 && xtype != 2) throw new RedoException("UNSUPPORTED_KDO_XTYPE");
        List<Column> columns=new ArrayList<>();
        for (int i=0;i<dictionaryColumns;i++) columns.add(new Column(State.ABSENT,new byte[0]));
        int slot;
        Kind kind;
        if (op == 2) {
            kind=Kind.INSERT; slot=u16(kdo,42);
            int count=kdo[18]&255;
            if ((kdo[16]&255) != 0x2c || kdo[44] != 0 || count > dictionaryColumns
                    || fields.size() != count+2)
                throw new RedoException("UNSUPPORTED_INSERT_ROWPIECE");
            for (int i=0;i<count;i++) columns.set(i,value(fields.get(i+2)));
            // Trailing omitted columns remain ABSENT until schema/row completeness is proven.
        } else if (op == 5) {
            kind=Kind.UPDATE; slot=u16(kdo,20);
            int count=kdo[23]&255;
            if (packed && (count!=1 || u16(kdo,24)!=0 || u16(kdo,26)!=3))
                throw new RedoException("UNSUPPORTED_KDOM2_UPDATE_LAYOUT");
            if ((kdo[16]&255) != 0x2c || (kdo[22]&255) != dictionaryColumns
                    || fields.size() != count+3 || fields.get(2).length != count*2)
                throw new RedoException("UNSUPPORTED_UPDATE_ROWPIECE");
            for (int i=0;i<count;i++) {
                int index=u16(fields.get(2),i*2);
                if (index >= dictionaryColumns || columns.get(index).state() != State.ABSENT)
                    throw new RedoException("INVALID_UPDATE_COLUMN_MAP");
                columns.set(index,value(packed ? extendedPackedValue(fields.get(i+3)) : fields.get(i+3)));
            }
        } else {
            kind=Kind.DELETE; slot=u16(kdo,16);
            if (fields.size() != 2) throw new RedoException("UNSUPPORTED_DELETE_ROWPIECE");
        }
        return new Patch(kind,dba,slot,xtype,null,null,List.copyOf(columns));
    }

    private static Column value(byte[] bytes) {
        return new Column(bytes.length == 0 ? State.NULL : State.VALUE,bytes.clone());
    }

    /** Observed single-column KDOM2 extended value: 0xfe, little-endian length, exact payload. */
    private static byte[] extendedPackedValue(byte[] bytes) throws RedoException {
        if (bytes.length<3 || (bytes[0]&255)!=0xfe || u16(bytes,1)<254 || u16(bytes,1)!=bytes.length-3)
            throw new RedoException("UNSUPPORTED_KDOM2_VALUE_LENGTH");
        return java.util.Arrays.copyOfRange(bytes,3,bytes.length);
    }
}
