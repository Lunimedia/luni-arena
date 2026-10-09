#!/usr/bin/env python3
"""Fail a Play release if its GPL native cores are unpinned or not 16 KiB ready.

The sourceCommit metadata is a build record, not a cryptographic proof that a
binary was built from that revision. Keep and publish the real corresponding
source and build instructions for the shipped libraries.
"""
from pathlib import Path
import hashlib
import re
import struct
import sys

CORES = {
    'bsnes_mercury_performance': 'libbsnes_mercury_performance_libretro_android.so',
    'mesen_s': 'libmesen_s_libretro_android.so',
}


def load_manifest(path):
    if not path.is_file():
        raise ValueError('release-cores.properties ausente; veja release-cores.properties.example')
    entries = {}
    for raw in path.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise ValueError(f'Linha inválida no manifesto: {line!r}')
        key, value = (part.strip() for part in line.split('=', 1))
        if key in entries:
            raise ValueError(f'Chave duplicada no manifesto: {key}')
        entries[key] = value
    return entries


def check_elf_16k(data, name):
    if (len(data) < 64 or data[:5] != b'\x7fELF\x02' or
            data[5] != 1 or data[18:20] != b'\xb7\x00'):
        raise ValueError(f'{name}: biblioteca ARM64 ELF inválida')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or not 1 <= count <= 256 or offset + size * count > len(data):
        raise ValueError(f'{name}: cabeçalhos ELF inválidos')
    aligns = [struct.unpack_from('<Q', data, offset + i * size + 48)[0]
              for i in range(count)
              if struct.unpack_from('<I', data, offset + i * size)[0] == 1]
    if not aligns or any(alignment < 16384 for alignment in aligns):
        raise ValueError(f'{name}: segmentos LOAD sem alinhamento de 16 KiB: {aligns}')


def verify(root):
    license_file = root / 'COPYING'
    license_asset = root / 'app/src/main/assets/COPYING'
    if (not license_file.is_file() or not license_asset.is_file() or
            license_file.read_bytes() != license_asset.read_bytes()):
        raise ValueError('inclua no app a mesma licença GPLv3 do arquivo COPYING da raiz')
    source_file = root / 'app/src/main/assets/SOURCE_URL.txt'
    source_url = source_file.read_text(encoding='utf-8').strip() if source_file.is_file() else ''
    if (not re.fullmatch(r'https://[^\s/]+/[^\s]+', source_url) or
            'example.org' in source_url.lower()):
        raise ValueError('configure app/src/main/assets/SOURCE_URL.txt com a URL HTTPS da fonte correspondente')
    entries = load_manifest(root / 'release-cores.properties')
    for slug, filename in CORES.items():
        expected = entries.get(f'{slug}.sha256', '')
        commit = entries.get(f'{slug}.sourceCommit', '')
        if not re.fullmatch(r'[a-fA-F0-9]{64}', expected):
            raise ValueError(f'{slug}: SHA-256 do core não fixado')
        if not re.fullmatch(r'[a-fA-F0-9]{40}', commit):
            raise ValueError(f'{slug}: revisão do código-fonte não fixada')
        path = root / 'app/src/main/jniLibs/arm64-v8a' / filename
        if not path.is_file():
            raise ValueError(f'{slug}: core ausente: {path}')
        data = path.read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        if actual != expected.lower():
            raise ValueError(f'{slug}: SHA-256 diferente do manifesto de release')
        check_elf_16k(data, slug)
        print(f'{slug}: SHA-256 e alinhamento 16 KiB conferidos; revisão {commit}')
    print('Cores fixados para este release. Ainda forneça o código-fonte correspondente.')


if __name__ == '__main__':
    try:
        verify(Path(__file__).resolve().parents[1])
    except ValueError as error:
        print(f'Release bloqueado: {error}', file=sys.stderr)
        sys.exit(1)
