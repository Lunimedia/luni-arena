package br.com.knightjoga;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds ROMs and nearby covers/saves without asking for a filesystem path. */
public final class RomLibrary {
    private static final int ROM_LIMIT = 16 * 1024 * 1024;
    private static final int ARCHIVE_LIMIT = 64 * 1024 * 1024;
    private static final int IMAGE_LIMIT = 8 * 1024 * 1024;
    private static final int MAX_GAMES = 500;
    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_IMPORTS = 500;
    private static final Set<String> ROM = new HashSet<>(Arrays.asList("sfc", "smc", "fig", "swc", "bs", "st"));
    private static final Set<String> COVER = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "webp"));

    public static final class Game {
        public final String name;
        public final Uri uri;
        public final String entry; // exact member name within ZIP/7Z, or null
        public final String format;
        public Uri cover;
        public Uri save;
        public String hash; // SHA-256 obtained during deduplication; stable across file moves.
        boolean managed, autoRemovable = true;
        final String key, archiveKey;
        Game(String name, Uri uri, String entry, String format, String key, String archiveKey) {
            this.name = name; this.uri = uri; this.entry = entry;
            this.format = format;
            this.key = key; this.archiveKey = archiveKey;
        }
        public String identity() { return uri.toString() + "#" + (entry == null ? "" : entry); }
    }

    public static final class RoomRom {
        public final Game game;
        public final byte[] bytes;
        RoomRom(Game game, byte[] bytes) { this.game = game; this.bytes = bytes; }
    }

    public static final class Scan {
        public final ArrayList<Game> games = new ArrayList<>();
        /** Hidden byte-for-byte copies. ZIP/7Z members must never trigger deletion of the archive. */
        public final ArrayList<Game> duplicates = new ArrayList<>();
        public int saves;
        public int unchangedFiles, changedFiles, removedFiles;
        public boolean complete = true;
        public final ArrayList<String> errors = new ArrayList<>();
        final Map<String, Uri> covers = new HashMap<>(), saveFiles = new HashMap<>();
        void match() {
            for (Game game : games) {
                game.cover = covers.get(game.key);
                if (game.cover == null) game.cover = covers.get(game.archiveKey);
                game.save = saveFiles.get(game.key);
                if (game.save == null) game.save = saveFiles.get(game.archiveKey);
            }
        }
    }

    private RomLibrary() {}
    public static boolean isRom(String name) { return ROM.contains(extension(name)); }
    public static boolean isSave(String name) { return "srm".equals(extension(name)); }
    public static boolean supported(String name) {
        String ext = extension(name);
        return isRom(name) || "zip".equals(ext) || "7z".equals(ext);
    }
    private static String extension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
    private static String base(String name) {
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1);
        int dot = leaf.lastIndexOf('.');
        return (dot > 0 ? leaf.substring(0, dot) : leaf).trim();
    }
    private static String key(String parent, String name) { return parent + "/" + base(name).toLowerCase(Locale.ROOT); }
    private static String leaf(String name) { return name.replace('\\', '/').substring(name.replace('\\', '/').lastIndexOf('/') + 1); }

    public static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "arquivo" : uri.getLastPathSegment();
    }

    private static File indexFile(Context context) { return new File(context.getFilesDir(), "luni-rom-index-v2.bin"); }
    public static void clearIndex(Context context) { indexFile(context).delete(); }

    /** Kept separately from save states and the network cache. Original imports are never moved. */
    public static File managedDirectory(Context context) throws IOException {
        File external = context.getExternalFilesDir(null);
        File base = external == null ? context.getFilesDir() : external;
        File directory = new File(base, "Luni Arena/Jogos");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Não foi possível criar a pasta de jogos do Luni");
        return directory;
    }

    private static final class SourceSeen {
        final String name;
        final long size, modified;
        final ArrayList<Game> games;
        SourceSeen(String name, long size, long modified, ArrayList<Game> games) {
            this.name=name; this.size=size; this.modified=modified; this.games=games;
        }
    }

    private static final class Session {
        final Context context;
        final RomIndex before, after;
        final HashMap<String, SourceSeen> seen = new HashMap<>();
        final Scan scan;
        Session(Context context, String scope, Scan scan) {
            this.context=context; this.scan=scan;
            this.before=RomIndex.load(indexFile(context),scope);
            this.after=new RomIndex(scope);
        }
        boolean reuse(Uri uri, String name, long size, long modified, boolean managed) {
            RomIndex.Source old=before.sources.get(uri.toString());
            if(old==null||!old.reusable(name,size,modified))return false;
            ArrayList<Game> restored=new ArrayList<>();
            for(RomIndex.Item item:old.games){
                if(scan.games.size()>=MAX_GAMES)break;
                Game game=new Game(item.name,uri,item.member,item.format,item.key,item.archiveKey);
                game.hash=item.hash;game.managed=managed;
                scan.games.add(game);restored.add(game);
            }
            seen.put(uri.toString(),new SourceSeen(name,size,modified,restored));
            scan.unchangedFiles++;return true;
        }
        void record(Uri uri, String name, long size, long modified, int from, boolean managed) {
            ArrayList<Game> items=new ArrayList<>();
            for(int i=from;i<scan.games.size();i++){
                Game game=scan.games.get(i);game.managed=managed;items.add(game);
            }
            seen.put(uri.toString(),new SourceSeen(name,size,modified,items));
            scan.changedFiles++;
        }
        void finish() {
            if(!scan.complete)return;
            for(Map.Entry<String,SourceSeen> pair:seen.entrySet()){
                SourceSeen source=pair.getValue();
                ArrayList<RomIndex.Item> games=new ArrayList<>();
                for(Game g:source.games)
                    games.add(new RomIndex.Item(g.name,g.entry,g.format,g.key,g.archiveKey,g.hash));
                after.sources.put(pair.getKey(),new RomIndex.Source(source.name,source.size,source.modified,games));
            }
            for(String old:before.sources.keySet())if(!after.sources.containsKey(old))scan.removedFiles++;
            try{after.save(indexFile(context));}catch(IOException e){scan.errors.add("Índice não salvo: "+e.getMessage());}
        }
    }

    public static Scan scanFolder(Context context, Uri tree) {
        Scan result = new Scan();
        Session session=new Session(context,"folder:"+tree, result);
        includeManaged(context,result,session);
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            walk(context, tree, id, 0, result, session);
        } catch (Exception e) { result.complete=false;result.errors.add("Não foi possível ler a pasta: " + e.getMessage()); }
        includeRoomRoms(context, result);
        result.match();
        deduplicate(context, result);
        session.finish();
        return result;
    }

    private static File roomDirectory(Context context) {
        return new File(context.getFilesDir(), "room_roms");
    }

    private static Game roomGame(File file) {
        String filename = file.getName();
        if (filename.length() < 68 || filename.charAt(64) != '-' ||
                !filename.substring(0, 64).matches("[0-9a-f]{64}") || !isRom(filename) ||
                file.length() < 1 || file.length() > ROM_LIMIT) return null;
        String title = base(filename.substring(65));
        if (title.isEmpty()) return null;
        String key = "room/" + filename.substring(0, 64);
        Game game=new Game(title, Uri.fromFile(file), null, extension(filename), key, key);
        game.hash=filename.substring(0,64);
        return game;
    }

    private static void includeRoomRoms(Context context, Scan result) {
        File[] files = roomDirectory(context).listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (result.games.size() >= MAX_GAMES) break;
            Game game = roomGame(file);
            if (game != null) result.games.add(game);
        }
    }

    public static Scan scanRoomRoms(Context context) {
        Scan result = new Scan();
        Session session=new Session(context,"managed",result);
        includeManaged(context,result,session);
        includeRoomRoms(context, result);
        result.match();
        deduplicate(context, result);
        session.finish();
        return result;
    }

    /** Only the app's own received ROMs are searched. The hash is verified before reuse. */
    public static RoomRom cachedRoomRom(Context context, String expectedHash) throws IOException {
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) return null;
        File[] files = roomDirectory(context).listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (!file.getName().startsWith(expectedHash + "-")) continue;
            Game game = roomGame(file);
            if (game == null) continue;
            try (InputStream in = new FileInputStream(file)) {
                byte[] bytes = readLimited(in, ROM_LIMIT);
                if (fingerprint(bytes).equals(expectedHash)) return new RoomRom(game, bytes);
            } catch (IOException ignored) { /* Incomplete cache; request the ROM again. */ }
        }
        return null;
    }

    public static RoomRom saveRoomRom(Context context, byte[] bytes, String expectedHash,
                                      String title, String format) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > ROM_LIMIT ||
                expectedHash == null || !expectedHash.matches("[0-9a-f]{64}") ||
                !expectedHash.equals(fingerprint(bytes)) ||
                format == null || !isRom("game." + format)) throw new IOException("ROM da sala inválida");
        RoomRom cached = cachedRoomRom(context, expectedHash);
        if (cached != null) return cached;
        String safe = title == null ? "" : title.replaceAll("[^\\p{L}\\p{N} _.-]", "_").trim();
        if (safe.length() > 36) safe = safe.substring(0, 36).trim();
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) safe = "Jogo da sala";
        File dir = roomDirectory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Sem espaço para salvar a ROM");
        File target = new File(dir, expectedHash + "-" + safe + "." + format.toLowerCase(Locale.ROOT));
        File temp = File.createTempFile(".recebendo-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(temp)) { out.write(bytes); }
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING); }
            Game game = roomGame(target);
            if (game == null) throw new IOException("Não foi possível salvar a ROM recebida");
            return new RoomRom(game, bytes);
        } finally { temp.delete(); }
    }

    private static String fingerprint(byte[] bytes) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (Exception e) { throw new IOException("Falha ao conferir a ROM", e); }
    }

    private static void includeManaged(Context context, Scan result, Session session) {
        try {
            File[] files=managedDirectory(context).listFiles();
            if(files==null)throw new IOException("A pasta de jogos não está disponível");
            Arrays.sort(files,Comparator.comparing(File::getName));
            for(File file:files){
                if(result.games.size()>=MAX_GAMES)break;
                String name=file.getName();
                if(!file.isFile())continue;
                Uri uri=Uri.fromFile(file);
                String ext=extension(name), fileKey=key("luni",name);
                if(COVER.contains(ext)){result.covers.put(fileKey,uri);continue;}
                if("srm".equals(ext)){result.saves++;result.saveFiles.put(fileKey,uri);continue;}
                if(!supported(name))continue;
                long size=file.length(),modified=file.lastModified();
                if(session.reuse(uri,name,size,modified,true))continue;
                int from=result.games.size();
                if(isRom(name))result.games.add(new Game(base(name),uri,null,ext,fileKey,fileKey));
                else if(!inspect(context,uri,name,"luni",result))continue;
                session.record(uri,name,size,modified,from,true);
            }
        } catch(Exception e){result.complete=false;result.errors.add("Biblioteca Luni: "+e.getMessage());}
    }

    private static void walk(Context context, Uri tree, String folder, int depth, Scan result, Session session) {
        if (depth > 12 || result.games.size() >= MAX_GAMES) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder);
        try (Cursor cursor = context.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (cursor == null) { result.complete=false;result.errors.add("Sem acesso à pasta escolhida."); return; }
            while (cursor.moveToNext() && result.games.size() < MAX_GAMES) {
                String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (name == null) continue;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(context, tree, id, depth + 1, result, session);
                } else {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    String ext = extension(name), nameKey = key(folder, name);
                    if (COVER.contains(ext)) result.covers.put(nameKey, document);
                    else if ("srm".equals(ext)) { result.saves++; result.saveFiles.put(nameKey, document); }
                    else if (supported(name)) {
                        long size=cursor.isNull(3)?-1:cursor.getLong(3);
                        long modified=cursor.isNull(4)?0:cursor.getLong(4);
                        if(session.reuse(document,name,size,modified,false))continue;
                        int from=result.games.size();
                        if(isRom(name))result.games.add(new Game(base(name),document,null,ext,nameKey,nameKey));
                        else if(!inspect(context,document,name,folder,result))continue;
                        session.record(document,name,size,modified,from,false);
                    }
                }
            }
        } catch (Exception e) { result.complete=false;result.errors.add("Pasta " + folder + ": " + e.getMessage()); }
    }

    public static Scan scanFile(Context context, Uri uri, String name) {
        Scan result = new Scan();
        if (isRom(name)) result.games.add(new Game(base(name), uri, null, extension(name), key("manual", name), key("manual", name)));
        else if (supported(name)) inspect(context, uri, name, "manual", result);
        else result.errors.add("Formato não aceito: " + name);
        deduplicate(context, result);
        return result;
    }

    /** Scan runs off the main thread. Keep accessible plain ROMs over archive members. */
    public static void deduplicate(Context context, Scan scan) {
        // TimSort is stable, so the first file of each kind remains the canonical copy.
        scan.games.sort((a,b)->{
            if(a.managed!=b.managed)return a.managed?-1:1;
            return Boolean.compare(a.entry!=null,b.entry!=null);
        });
        HashMap<String,Game> fingerprints=new HashMap<>();
        ArrayList<Game> unique=new ArrayList<>();
        for(Game game:scan.games){
            try {
                if(game.hash==null||!game.hash.matches("[0-9a-f]{64}"))game.hash=fingerprint(readRom(context,game));
                Game canonical=fingerprints.putIfAbsent(game.hash,game);
                if(canonical!=null){
                    if(canonical.cover==null)canonical.cover=game.cover;
                    if(canonical.save==null)canonical.save=game.save;
                    // Copying into Luni must never make automatic cleanup erase the owner's original.
                    game.autoRemovable=!canonical.managed&&!game.managed;
                    scan.duplicates.add(game);continue;
                }
            } catch(Exception e) {scan.errors.add("Não foi possível conferir " + game.name + ": " + e.getMessage());}
            unique.add(game);
        }
        scan.games.clear();scan.games.addAll(unique);
    }

    private static boolean inspect(Context context, Uri uri, String name, String folder, Scan result) {
        File archive = null;
        try {
            archive = tempArchive(context, uri, extension(name));
            if ("zip".equals(extension(name))) {
                try (ZipFile zip = new ZipFile(archive)) {
                    Enumeration<? extends ZipEntry> members = zip.entries();
                    int count = 0;
                    while (members.hasMoreElements() && ++count <= MAX_ENTRIES && result.games.size() < MAX_GAMES) {
                        ZipEntry member = members.nextElement();
                        if (!member.isDirectory() && isRom(member.getName()) && member.getSize() <= ROM_LIMIT)
                            result.games.add(new Game(base(member.getName()), uri, member.getName(), extension(member.getName()),
                                    key(folder, leaf(member.getName())), key(folder, name)));
                    }
                }
            } else {
                try (SevenZFile seven = SevenZFile.builder().setFile(archive)
                        .setMaxMemoryLimitKiB(65536).get()) {
                    int count = 0;
                    for (SevenZArchiveEntry member : seven.getEntries()) {
                        if (++count > MAX_ENTRIES || result.games.size() >= MAX_GAMES) break;
                        if (!member.isDirectory() && member.getName() != null &&
                                isRom(member.getName()) && member.getSize() <= ROM_LIMIT)
                            result.games.add(new Game(base(member.getName()), uri, member.getName(), extension(member.getName()),
                                    key(folder, leaf(member.getName())), key(folder, name)));
                    }
                }
            }
            return true;
        } catch (Exception e) { result.errors.add("Arquivo " + name + ": " + e.getMessage());return false; }
        finally { if (archive != null) archive.delete(); }
    }

    public static final class ImportReport {
        public int imported, alreadyPresent, failed;
        public final ArrayList<String> errors=new ArrayList<>();
        void error(String message){failed++;if(errors.size()<4)errors.add(message);}
    }

    /** Copies only files explicitly granted by the picker; never modifies their original location. */
    public static void importFile(Context context, Uri source, String name, ImportReport report) {
        String safe=leaf(name==null?"":name).replaceAll("[^\\p{L}\\p{N} ._()\\-]","_").trim();
        if(safe.length()>112)safe=safe.substring(0,108)+"."+extension(safe);
        String ext=extension(safe);
        int limit=isRom(safe)?ROM_LIMIT:("zip".equals(ext)||"7z".equals(ext))?ARCHIVE_LIMIT:
                COVER.contains(ext)?IMAGE_LIMIT:"srm".equals(ext)?2*1024*1024:0;
        if(limit==0||safe.startsWith(".")||safe.equals("..")){report.error("Formato não aceito: "+name);return;}
        File temp=null;
        try {
            File directory=managedDirectory(context);
            temp=File.createTempFile(".importando-",".tmp",directory);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            int count=0;
            try(InputStream in=context.getContentResolver().openInputStream(source);
                OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Sem acesso ao arquivo");
                byte[] block=new byte[32768];int n;
                while((n=in.read(block))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Importação interrompida");
                    count+=n;if(count>limit)throw new IOException("Arquivo maior que "+(limit/1048576)+" MB");
                    digest.update(block,0,n);out.write(block,0,n);
                }
            }
            if(count==0)throw new IOException("Arquivo vazio");
            byte[] incoming=digest.digest();
            int dot=safe.lastIndexOf('.');String stem=safe.substring(0,dot),suffix=safe.substring(dot);
            File destination=new File(directory,safe);
            for(int version=2;destination.exists();version++){
                if(version>1000)throw new IOException("Muitos arquivos com este nome");
                if(destination.length()==count&&Arrays.equals(incoming,shaFile(destination))){
                    report.alreadyPresent++;return;
                }
                destination=new File(directory,stem+" ("+version+")"+suffix);
            }
            try {Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),destination.toPath());}
            report.imported++;
        } catch(Exception e){report.error(name+": "+e.getMessage());}
        finally {if(temp!=null)temp.delete();}
    }

    private static byte[] shaFile(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] block=new byte[32768];int n;
            while((n=in.read(block))!=-1)digest.update(block,0,n);
        }
        return digest.digest();
    }

    public static ImportReport importFolder(Context context, Uri tree) {
        ImportReport result=new ImportReport();
        try {
            String root=DocumentsContract.getTreeDocumentId(tree);
            importChildren(context,tree,root,0,new HashSet<>(),result);
        } catch(Exception e){result.error("Pasta sem acesso: "+e.getMessage());}
        return result;
    }

    private static void importChildren(Context context, Uri tree, String parent, int depth,
                                       Set<String> visited, ImportReport result) {
        if(depth>12||!visited.add(parent)||result.imported+result.alreadyPresent+result.failed>=MAX_IMPORTS)return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
        try(Cursor cursor=context.getContentResolver().query(children,new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(cursor==null){result.error("Sem acesso à pasta selecionada");return;}
            while(cursor.moveToNext()){
                if(Thread.currentThread().isInterrupted())return;
                String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                if(id==null||name==null)continue;
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                    importChildren(context,tree,id,depth+1,visited,result);
                }else if(supported(name)||COVER.contains(extension(name))||isSave(name)){
                    if(result.imported+result.alreadyPresent+result.failed>=MAX_IMPORTS){
                        result.error("Limite de "+MAX_IMPORTS+" arquivos por importação");break;
                    }
                    importFile(context,DocumentsContract.buildDocumentUriUsingTree(tree,id),name,result);
                }
            }
        } catch(Exception e){result.error("Falha ao ler pasta: "+e.getMessage());}
    }

    public static boolean removeManaged(Context context, Game game) throws IOException {
        if(game==null||!game.managed||!"file".equals(game.uri.getScheme()))return false;
        File file=new File(game.uri.getPath()).getCanonicalFile();
        if(!managedDirectory(context).getCanonicalFile().equals(file.getParentFile()))return false;
        return file.isFile()&&file.delete();
    }

    public static byte[] readRom(Context context, Game game) throws IOException {
        if (game.entry == null) {
            try (InputStream stream = context.getContentResolver().openInputStream(game.uri)) {
                if (stream == null) throw new IOException("Não foi possível abrir a ROM");
                return readLimited(stream, ROM_LIMIT);
            }
        }
        File archive = tempArchive(context, game.uri, extension(displayName(context, game.uri)));
        try {
            if ("zip".equals(extension(displayName(context, game.uri)))) {
                try (ZipFile zip = new ZipFile(archive)) {
                    ZipEntry entry = zip.getEntry(game.entry);
                    if (entry == null || entry.isDirectory() || entry.getSize() > ROM_LIMIT)
                        throw new IOException("ROM não encontrada dentro do ZIP");
                    try (InputStream stream = zip.getInputStream(entry)) { return readLimited(stream, ROM_LIMIT); }
                }
            }
            try (SevenZFile seven = SevenZFile.builder().setFile(archive)
                    .setMaxMemoryLimitKiB(65536).get()) {
                SevenZArchiveEntry entry;
                int count = 0;
                while ((entry = seven.getNextEntry()) != null && ++count <= MAX_ENTRIES) {
                    if (game.entry.equals(entry.getName())) {
                        if (entry.getSize() > ROM_LIMIT) throw new IOException("ROM maior que 16 MB");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[32768];
                        int amount;
                        while ((amount = seven.read(buffer)) != -1) {
                            if (out.size() + amount > ROM_LIMIT) throw new IOException("ROM maior que 16 MB");
                            out.write(buffer, 0, amount);
                        }
                        return out.toByteArray();
                    }
                }
                throw new IOException("ROM não encontrada dentro do 7Z");
            }
        } finally { archive.delete(); }
    }

    public static byte[] readSave(Context context, Uri save) throws IOException {
        try (InputStream stream = context.getContentResolver().openInputStream(save)) {
            if (stream == null) throw new IOException("Save não acessível");
            return readLimited(stream, 2 * 1024 * 1024);
        }
    }

    private static byte[] readLimited(InputStream stream, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[32768];
        int amount;
        while ((amount = stream.read(buffer)) != -1) {
            if (out.size() + amount > max) throw new IOException("Arquivo excede o limite de " + (max / (1024 * 1024)) + " MB");
            out.write(buffer, 0, amount);
        }
        return out.toByteArray();
    }

    private static File tempArchive(Context context, Uri uri, String suffix) throws IOException {
        File file = File.createTempFile("knight-", "." + suffix, context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new IOException("Arquivo sem permissão de leitura");
            byte[] buffer = new byte[32768];
            int amount, total = 0;
            while ((amount = in.read(buffer)) != -1) {
                total += amount;
                if (total > ARCHIVE_LIMIT) throw new IOException("Compactado acima de 64 MB");
                out.write(buffer, 0, amount);
            }
            return file;
        } catch (IOException e) { file.delete(); throw e; }
    }
}
