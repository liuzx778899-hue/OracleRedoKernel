package com.streamhouse.oracle;

final class RedoBytes {
    private RedoBytes() {}
    static int u16(byte[] b, int p) { return (b[p] & 255) | ((b[p+1] & 255) << 8); }
    static long u32(byte[] b, int p) { return u16(b,p) | ((long)u16(b,p+2) << 16); }
    static long scn(byte[] b, int p) { return u32(b,p) | ((long)(u16(b,p+4) & 0x7fff) << 32); }
    static int aligned(int n) { return (n+3) & ~3; }
}
