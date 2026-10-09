#!/usr/bin/env python3
"""Read-only audit before attempting a signed Luni Arena release build.

This checks technical release inputs. It cannot prove binary provenance,
license compatibility, ownership of artwork, or Play policy compliance.
"""

from pathlib import Path
import hashlib
import re
import sys

from verify_release_cores import CORES, check_elf_16k, load_manifest


ROOT = Path(__file__).resolve().parents[1]
CORE_DIR = ROOT / "app/src/main/jniLibs/arm64-v8a"


def main():
    problems = []
    source_url = ROOT / "app/src/main/assets/SOURCE_URL.txt"
    url = source_url.read_text(encoding="utf-8").strip() if source_url.is_file() else ""
    if not re.fullmatch(r"https://[^\s/]+/[^\s]+", url) or "example.org" in url.lower():
        problems.append("SOURCE_URL.txt: informe a URL HTTPS publica do codigo-fonte exato desta versao.")

    manifest = ROOT / "release-cores.properties"
    try:
        entries = load_manifest(manifest)
    except ValueError as error:
        problems.append(f"release-cores.properties: {error}")
        entries = {}

    for slug, filename in CORES.items():
        path = CORE_DIR / filename
        commit = entries.get(f"{slug}.sourceCommit", "")
        expected = entries.get(f"{slug}.sha256", "")
        if not re.fullmatch(r"[0-9a-fA-F]{40}", commit):
            problems.append(f"{slug}: falta commit de 40 caracteres da fonte usada na compilacao.")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", expected):
            problems.append(f"{slug}: falta SHA-256 do binario de release.")
        if not path.is_file():
            problems.append(f"{slug}: falta {path.relative_to(ROOT)} (nao use o nightly de debug).")
            continue
        data = path.read_bytes()
        if re.fullmatch(r"[0-9a-fA-F]{64}", expected):
            actual = hashlib.sha256(data).hexdigest()
            if actual != expected.lower():
                problems.append(f"{slug}: SHA-256 diferente do manifesto de release.")
        try:
            check_elf_16k(data, slug)
        except ValueError as error:
            problems.append(str(error))

    if problems:
        print("LUNI ARENA — RELEASE BLOQUEADO\n")
        for number, problem in enumerate(problems, 1):
            print(f"{number}. {problem}")
        print("\nDepois de corrigir, execute: py -3 tools\\verify_release_cores.py")
    else:
        print("Entradas tecnicas presentes e verificadas. Execute py -3 tools\\verify_release_cores.py.")
    print("\nAntes de distribuir: documente a compilacao dos cores e forneca a fonte correspondente;")
    print("revise GPLv3/ML Kit, cheats, imagens e declaracoes reais do Play Console.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
