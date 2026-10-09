package br.com.knightjoga;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Small, private metadata index. Never stores ROM bytes or changes the source files. */
final class RomIndex {
    private static final int MAGIC = 0x4c524932; // LRI2
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SOURCES = 1200;
    private static final int MAX_GAMES_PER_SOURCE = 500;
    final String scope;
    final Map<String, Source> sources = new HashMap<>();

    static final class Item {
        final String name, member, format, key, archiveKey, hash;
        Item(String name, String member, String format, String key, String archiveKey, String hash) {
            this.name = name; this.member = member; this.format = format;
            this.key = key; this.archiveKey = archiveKey; this.hash = hash;
        }
    }

    static final class Source {
        final String name;
        final long size, modified;
        final ArrayList<Item> games;
        Source(String name, long size, long modified, ArrayList<Item> games) {
            this.name = name; this.size = size; this.modified = modified; this.games = games;
        }
        boolean reusable(String candidateName, long candidateSize, long candidateModified) {
            if (!name.equals(candidateName) || candidateSize < 0 || candidateModified <= 0 ||
                size != candidateSize || modified != candidateModified ||
                // FAT/document providers can round modification time to seconds.
                System.currentTimeMillis() - candidateModified < 2500) return false;
            for (Item item : games) if (item.hash == null || !item.hash.matches("[0-9a-f]{64}")) return false;
            return true;
        }
    }

    RomIndex(String scope) { this.scope = scope; }

    static RomIndex load(File file, String scope) {
        RomIndex empty = new RomIndex(scope);
        if (!file.isFile() || file.length() < 12 || file.length() > MAX_BYTES) return empty;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC || !scope.equals(in.readUTF())) return empty;
            int count = in.readInt();
            if (count < 0 || count > MAX_SOURCES) return empty;
            for (int i = 0; i < count; i++) {
                String uri = in.readUTF(), name = in.readUTF();
                long size = in.readLong(), modified = in.readLong();
                int members = in.readInt();
                if (members < 0 || members > MAX_GAMES_PER_SOURCE || empty.sources.containsKey(uri)) return new RomIndex(scope);
                ArrayList<Item> games = new ArrayList<>(members);
                for (int j = 0; j < members; j++) {
                    String title = in.readUTF();
                    String member = in.readBoolean() ? in.readUTF() : null;
                    games.add(new Item(title, member, in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF()));
                }
                empty.sources.put(uri, new Source(name, size, modified, games));
            }
            return empty;
        } catch (Exception ignored) { return new RomIndex(scope); }
    }

    void save(File file) throws IOException {
        if (sources.size() > MAX_SOURCES) return;
        File temp = File.createTempFile(".luni-index-", ".tmp", file.getParentFile());
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))) {
                out.writeInt(MAGIC); out.writeUTF(scope); out.writeInt(sources.size());
                for (Map.Entry<String, Source> entry : sources.entrySet()) {
                    Source source = entry.getValue();
                    out.writeUTF(entry.getKey()); out.writeUTF(source.name);
                    out.writeLong(source.size); out.writeLong(source.modified);
                    out.writeInt(source.games.size());
                    for (Item item : source.games) {
                        out.writeUTF(item.name);
                        out.writeBoolean(item.member != null);
                        if (item.member != null) out.writeUTF(item.member);
                        out.writeUTF(item.format); out.writeUTF(item.key); out.writeUTF(item.archiveKey);
                        out.writeUTF(item.hash == null ? "" : item.hash);
                    }
                }
            }
            if (temp.length() > MAX_BYTES) return;
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { temp.delete(); }
    }
}
