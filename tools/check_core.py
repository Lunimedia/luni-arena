#!/usr/bin/env python3
"""Inspect bundled ARM64 core for expected ELF and 16 KiB LOAD alignment."""
from pathlib import Path
import struct
import sys

root = Path(__file__).resolve().parents[1]
paths = [root / 'app/src/main/jniLibs/arm64-v8a/libbsnes_mercury_performance_libretro_android.so',
         root / 'app/src/main/jniLibs/arm64-v8a/libmesen_s_libretro_android.so']
paths.extend(sorted((root / 'app/build/intermediates').glob('**/liblunibridge.so')) if
             (root / 'app/build/intermediates').exists() else [])
ready = True
for path in paths:
    if not path.exists():
        print(f'MISSING: {path}')
        ready = False
        continue
    data = path.read_bytes()
    if len(data) < 64 or data[:5] != b'\x7fELF\x02' or data[5] != 1 or data[18:20] != b'\xb7\x00':
        print(f'INVALID ARM64 ELF: {path}')
        ready = False
        continue
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    aligns = []
    if size < 56 or count > 256 or offset + size * count > len(data):
        print(f'INVALID PROGRAM HEADERS: {path}')
        ready = False
        continue
    for i in range(count):
        base = offset + i * size
        if struct.unpack_from('<I', data, base)[0] == 1:
            aligns.append(struct.unpack_from('<Q', data, base + 48)[0])
    aligned = bool(aligns) and all(value >= 16384 for value in aligns)
    print(f'{path.name}: LOAD p_align={aligns}; 16 KiB: {"YES" if aligned else "NO"}')
    ready &= aligned
if not ready:
    print('Preview may run on 4 KiB devices, but is not ready for 16 KiB page-size release.')
    sys.exit(1)
