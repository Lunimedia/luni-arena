"""Offline checks for the release gate; no actual emulator binaries are included."""
from pathlib import Path
import hashlib
import importlib.util
import struct
import tempfile
import unittest


MODULE_PATH = Path(__file__).resolve().parents[1] / 'tools/verify_release_cores.py'
SPEC = importlib.util.spec_from_file_location('verify_release_cores', MODULE_PATH)
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


def dummy_elf(alignment=16384):
    data = bytearray(120)
    data[:5] = b'\x7fELF\x02'
    data[5] = 1
    data[18:20] = b'\xb7\x00'
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<I', data, 64, 1)
    struct.pack_into('<Q', data, 64 + 48, alignment)
    return bytes(data)


class ReleaseCoreGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.libs = self.root / 'app/src/main/jniLibs/arm64-v8a'
        self.libs.mkdir(parents=True)
        self.assets = self.root / 'app/src/main/assets'
        self.assets.mkdir(parents=True)
        (self.root / 'COPYING').write_text('GPL test fixture')
        (self.assets / 'COPYING').write_text('GPL test fixture')
        (self.assets / 'SOURCE_URL.txt').write_text('https://github.com/luni/source/releases/1\n')
        self.make_files()

    def make_files(self, alignment=16384):
        self.lines = []
        for slug, filename in module.CORES.items():
            data = dummy_elf(alignment)
            (self.libs / filename).write_bytes(data)
            self.lines.extend((f'{slug}.sha256={hashlib.sha256(data).hexdigest()}',
                               f'{slug}.sourceCommit=' + 'a' * 40))
        (self.root / 'release-cores.properties').write_text('\n'.join(self.lines) + '\n')

    def test_valid_pinned_cores_pass(self):
        module.verify(self.root)

    def test_changed_library_fails(self):
        (self.libs / module.CORES['mesen_s']).write_bytes(dummy_elf() + b'changed')
        with self.assertRaisesRegex(ValueError, 'SHA-256 diferente'):
            module.verify(self.root)

    def test_unaligned_core_fails_even_with_matching_hash(self):
        self.make_files(4096)
        with self.assertRaisesRegex(ValueError, '16 KiB'):
            module.verify(self.root)

    def test_missing_source_revision_fails(self):
        (self.root / 'release-cores.properties').write_text(
            '\n'.join(line for line in self.lines if not line.startswith('mesen_s.sourceCommit')))
        with self.assertRaisesRegex(ValueError, 'revisão do código-fonte'):
            module.verify(self.root)

    def test_missing_manifest_fails(self):
        (self.root / 'release-cores.properties').unlink()
        with self.assertRaisesRegex(ValueError, 'ausente'):
            module.verify(self.root)

    def test_missing_source_link_fails(self):
        (self.assets / 'SOURCE_URL.txt').write_text('PRE_PUBLICATION_SETUP_REQUIRED\n')
        with self.assertRaisesRegex(ValueError, 'URL HTTPS'):
            module.verify(self.root)

    def test_omitted_license_fails(self):
        (self.assets / 'COPYING').unlink()
        with self.assertRaisesRegex(ValueError, 'licença GPLv3'):
            module.verify(self.root)


if __name__ == '__main__':
    unittest.main()
