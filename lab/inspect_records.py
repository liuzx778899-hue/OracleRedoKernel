"""Research-only byte walk; no events, dictionary or dump text as decode input."""
from pathlib import Path
import struct
import collections
import sys

d = Path(sys.argv[1]).read_bytes()
p = b''.join(d[i+16:i+512] for i in range(1024, len(d), 512))
pos = 0
counts = collections.Counter()
records = []
while pos < len(p):
    remain = 496 - pos % 496
    if remain < 24:
        pos += remain
        continue
    n = struct.unpack_from('<I', p, pos)[0]
    if n == 0:
        pos += remain
        continue
    if n < 24 or n > len(p)-pos or n % 4:
        print('STOP', pos//496+2, pos%496+16, n, p[pos:pos+40].hex())
        break
    vld = p[pos+4]
    scn = (struct.unpack_from('<H', p, pos+6)[0]<<32) | struct.unpack_from('<I', p, pos+8)[0]
    counts[vld] += 1
    records.append((pos//496+2, pos%496+16, n, vld, scn))
    pos += n
print('records', len(records), 'vld', counts)
for rec in records[:20]:
    print(rec)
