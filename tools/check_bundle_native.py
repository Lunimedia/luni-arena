#!/usr/bin/env python3
"""Check every packaged ARM64 library and the source link in an APK or AAB."""

from pathlib import Path
from zipfile import ZipFile
import re
import sys

from verify_release_cores import check_elf_16k


def main(path):
    if not path.is_file():
        raise ValueError(f"Arquivo ausente: {path}")
    with ZipFile(path) as archive:
        native = [name for name in archive.namelist() if name.endswith(".so")]
        if not native:
            raise ValueError("Nenhuma biblioteca nativa no pacote")
        for name in native:
            if not (name.startswith("lib/arm64-v8a/") or
                    name.startswith("base/lib/arm64-v8a/")):
                raise ValueError(f"ABI inesperada: {name}")
            check_elf_16k(archive.read(name), name)
            print("ARM64 ELF 16 KiB OK:", name)
        asset = next((name for name in ("assets/SOURCE_URL.txt", "base/assets/SOURCE_URL.txt")
                      if name in archive.namelist()), None)
        if asset is None:
            raise ValueError("SOURCE_URL.txt nao entrou no pacote")
        url = archive.read(asset).decode("utf-8").strip()
        if not re.fullmatch(r"https://[^\s/]+/[^\s]+", url):
            raise ValueError("SOURCE_URL.txt ainda nao aponta para fonte publica")
        print("Fonte registrada:", url)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Uso: py -3 tools\\check_bundle_native.py CAMINHO_DO_AAB")
    try:
        main(Path(sys.argv[1]))
    except (ValueError, OSError) as error:
        sys.exit(f"Pacote nao aprovado: {error}")
