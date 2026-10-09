#!/usr/bin/env python3
"""Fetch GPLv3 ARM64 libretro cores for private preview builds.

Nightlies are moving artifacts. A commercial release must pin the exact source,
build reproducibly, and provide the corresponding source to recipients.
"""
from pathlib import Path
from urllib.request import urlopen
from zipfile import ZipFile
from io import BytesIO
import hashlib

root = Path(__file__).resolve().parents[1]
directory = root / 'app/src/main/jniLibs/arm64-v8a'
base = 'https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/'
cores = (
    ('bsnes_mercury_performance', 'libbsnes_mercury_performance_libretro_android.so',
     'https://github.com/libretro/bsnes-mercury'),
    ('mesen-s', 'libmesen_s_libretro_android.so',
     'https://github.com/libretro/Mesen-S'),
)


def verify(native):
    if len(native) < 64 or native[:5] != b'\x7fELF\x02' or native[5] != 1 or native[18:20] != b'\xb7\x00':
        raise RuntimeError('Core is not an Android ARM64 ELF shared library')


for slug, name, source in cores:
    target = directory / name
    if target.is_file():
        native = target.read_bytes()
        verify(native)
        print('Using cached core:', target)
    else:
        url = base + slug + '_libretro_android.so.zip'
        print('Downloading GPLv3 preview core:', url)
        with urlopen(url, timeout=90) as response:
            payload = response.read(16 * 1024 * 1024 + 1)
        if len(payload) > 16 * 1024 * 1024:
            raise RuntimeError('Core archive exceeds 16 MiB')
        with ZipFile(BytesIO(payload)) as archive:
            entries = [p for p in archive.namelist() if p.endswith('.so') and not p.endswith('/')]
            if len(entries) != 1:
                raise RuntimeError(f'Expected exactly one shared library, got {entries!r}')
            native = archive.read(entries[0])
        verify(native)
        directory.mkdir(parents=True, exist_ok=True)
        target.write_bytes(native)
        print('Saved', target, len(native), 'bytes')
    print('Core SHA-256:', hashlib.sha256(native).hexdigest())
    print('Source (GPLv3):', source)
