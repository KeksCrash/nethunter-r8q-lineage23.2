#!/usr/bin/env python3
"""Generate a simple radar-style launcher icon as PNG."""
import struct, zlib, io

SIZE = 48

def make_png(size):
    pixels = []
    cx, cy = size // 2, size // 2
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = x - cx, y - cy
            dist = (dx*dx + dy*dy) ** 0.5
            r = size // 2
            if dist > r:
                row.extend([0, 0, 0, 0])  # transparent
            elif dist > r - 2:
                row.extend([0, 255, 65, 200])  # green ring
            elif abs(dx) <= 1 and dy < 0 and dist < r - 4:
                row.extend([233, 69, 96, 255])  # red line up
            elif abs(dy) <= 1 and dx > 0 and dist < r - 4:
                row.extend([233, 69, 96, 255])  # red line right
            elif dist < 3:
                row.extend([0, 255, 65, 255])  # center dot
            elif int(dist) % 8 < 1 and dist < r - 4:
                row.extend([0, 255, 65, 80])  # rings
            else:
                row.extend([15, 15, 35, 255])  # dark bg
        pixels.append(bytes([0] + row))  # filter byte 0

    raw = b''.join(pixels)

    def chunk(ctype, data):
        c = ctype + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    ihdr = struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n' +
            chunk(b'IHDR', ihdr) +
            chunk(b'IDAT', zlib.compress(raw)) +
            chunk(b'IEND', b''))

with open('app/src/main/res/mipmap-hdpi/ic_launcher.png', 'wb') as f:
    f.write(make_png(SIZE))
print("Icon generated")
