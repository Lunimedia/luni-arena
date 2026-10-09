package br.com.knightjoga;

import java.util.Locale;

/** Deterministic core selection for identical ROM bytes in a LAN room. */
final class CoreChoice {
    private CoreChoice() {}

    static boolean preferMesen(byte[] rom, String format) {
        String ext = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (ext.equals("fig") || ext.equals("swc") || ext.equals("bs")) return true;
        // bsnes-mercury Performance has a documented black screen on Cx4 games.
        // SNES internal headers can be LoROM, HiROM or ExHiROM, with a 512-byte
        // copier header in some dumps. Inspect the cartridge type in each slot.
        if (rom == null) return false;
        for (int copier : new int[]{0, 512}) {
            for (int header : new int[]{0x7fc0, 0xffc0, 0x40ffc0}) {
                int pos = copier + header;
                if (pos + 0x1b >= rom.length) continue;
                int printable = 0;
                for (int i = 0; i < 21; i++) {
                    int ch = rom[pos + i] & 255;
                    if (ch >= 32 && ch <= 126) printable++;
                }
                if (printable >= 16 && (rom[pos + 0x16] & 255) == 0xf3) return true;
            }
        }
        return false;
    }
}
