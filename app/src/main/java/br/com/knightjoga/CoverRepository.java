package br.com.knightjoga;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;

/** Local covers first. The debug preview can also look up community thumbnails. */
public final class CoverRepository {
    private static final int MAX_IMAGE = 3 * 1024 * 1024;
    private static final String THUMBNAIL_ROOT = "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/";
    private static final String REPOSITORY = "libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System";
    private CoverRepository() {}

    public static File file(Context context, RomLibrary.Game game) {
        String id;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(game.name.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            id = hex.toString();
        } catch (Exception e) { id = Integer.toHexString(game.name.hashCode()); }
        File dir = new File(context.getFilesDir(), "covers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, id + ".img");
    }

    private static File originFile(Context context,RomLibrary.Game game){
        return new File(file(context,game).getAbsolutePath()+".origin");
    }
    static String origin(Context context,RomLibrary.Game game){
        File marker=originFile(context,game);
        if(!marker.isFile())return "";
        try(FileInputStream input=new FileInputStream(marker)){
            byte[] bytes=new byte[16];int count=input.read(bytes);
            return count<=0?"":new String(bytes,0,count,java.nio.charset.StandardCharsets.US_ASCII).trim();
        }catch(IOException ignored){return "";}
    }
    private static void mark(Context context,RomLibrary.Game game,String source)throws IOException{
        try(FileOutputStream output=new FileOutputStream(originFile(context,game))){
            output.write(source.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
    }

    /** Preview-only source: the public thumbnail repository does not grant this app
     * commercial rights to game artwork. Release builds never invoke this method. */
    public static boolean downloadCommunity(Context context, RomLibrary.Game game) {
        if (file(context, game).isFile()) return true;
        boolean indexed = false;
        for (String folder : new String[]{"Named_Boxarts", "Named_Titles", "Named_Snaps"}) {
            try {
                List<String> catalogue = RemoteCatalog.files(context, REPOSITORY, folder);
                indexed = true;
                ArrayList<String> candidates = new ArrayList<>();
                for (String filename : catalogue) {
                    if (!(filename.endsWith(".png") || filename.endsWith(".jpg"))) continue;
                    String title = filename.substring(0, filename.lastIndexOf('.'));
                    if (RemoteCatalog.sameGame(game.name, title)) candidates.add(filename);
                }
                String region = RemoteCatalog.region(game.name);
                candidates.sort((left, right) -> Integer.compare(
                    score(game.name, right, region), score(game.name, left, region)));
                for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
                    try {
                        if (downloadCommunityImage(context, game, THUMBNAIL_ROOT + folder + "/" +
                                RemoteCatalog.encode(candidates.get(i)))) return true;
                    } catch (IOException ignored) { /* Try the next regional variant. */ }
                }
            } catch (Exception ignored) { /* Try the other folders or direct filenames. */ }
        }
        if (indexed) return false;
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        String cleaned = game.name.trim().replaceAll("(?i)\\s+-\\s+[^ ]+\\.(com|net|org)(\\.[a-z]{2})?.*$", "")
            .replaceAll("\\s*\\[[^]]*\\]", "").replaceAll("\\s+", " ").trim();
        titles.add(game.name.trim()); titles.add(cleaned);
        String simple = cleaned.replaceAll("\\s*\\([^)]*\\)", "").trim();
        if (!simple.isEmpty()) {
            titles.add(simple); titles.add(simple + " (USA)"); titles.add(simple + " (Europe)");
            titles.add(simple + " (Japan)");
        }
        for (String title : titles) {
            if (title.isEmpty()) continue;
            try {
                if (downloadCommunityImage(context, game, THUMBNAIL_ROOT + "Named_Boxarts/" +
                        RemoteCatalog.encode(title + ".png"))) return true;
            } catch (IOException ignored) { /* Offline or no exact match. */ }
        }
        return false;
    }

    private static int score(String game, String filename, String region) {
        String title = filename.substring(0, filename.lastIndexOf('.'));
        int rank = RemoteCatalog.normalize(game).equals(RemoteCatalog.normalize(title)) ? 100 : 65;
        String found = RemoteCatalog.region(title);
        if (!region.isEmpty()) rank += region.equals(found) ? 18 : found.isEmpty() ? 0 : -14;
        if (filename.endsWith(".png")) rank += 2;
        return rank;
    }

    private static boolean downloadCommunityImage(Context context, RomLibrary.Game game, String url) throws IOException {
        byte[] data = RemoteCatalog.get(url, MAX_IMAGE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth < 64 || options.outHeight < 64 ||
            options.outWidth > 2048 || options.outHeight > 2048) return false;
        File target = file(context, game);
        File temp = File.createTempFile(".community-", ".img", target.getParentFile());
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(data); output.getFD().sync();
            }
            if (target.isFile()) return true; // A local/imported cover wins.
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            mark(context,game,"community");
            return true;
        } finally { temp.delete(); }
    }

    /** The phone never sees IGDB's Client Secret or access token. The configured service
     * must have permission to provide covers; a 404 means the title was not matched. */
    public static boolean downloadLicensed(Context context, RomLibrary.Game game,
                                           String service, String accessKey) throws IOException {
        if (service == null || service.trim().isEmpty()) return false;
        URI base;
        try { base = URI.create(service.trim()); }
        catch (IllegalArgumentException e) { throw new IOException("Endereço do catálogo inválido", e); }
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null ||
            base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null)
            throw new IOException("O catálogo precisa de um endereço HTTPS válido");
        if (accessKey == null || accessKey.isEmpty()) throw new IOException("Chave do catálogo ausente");
        String endpoint = service.trim().replaceAll("/+$", "") + "/v1/cover?title=" +
            URLEncoder.encode(game.name, "UTF-8");
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(3500); conn.setReadTimeout(6500);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("X-Luni-Key", accessKey);
        conn.setRequestProperty("Accept", "image/jpeg, image/png, image/webp");
        try {
            int status = conn.getResponseCode();
            if (status == 404) return false;
            if (status != 200) throw new IOException("Catálogo indisponível (" + status + ")");
            String contentType = conn.getContentType();
            if (contentType == null || !(contentType.startsWith("image/jpeg") ||
                contentType.startsWith("image/png") || contentType.startsWith("image/webp")))
                throw new IOException("O catálogo não enviou uma imagem");
            byte[] data;
            try (InputStream input = conn.getInputStream()) { data = read(input); }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth < 64 || options.outHeight < 64 ||
                options.outWidth > 2048 || options.outHeight > 2048)
                throw new IOException("Dimensões de capa inválidas");
            File target = file(context, game);
            File temp = File.createTempFile(".licensed-", ".img", target.getParentFile());
            try {
                try (FileOutputStream output = new FileOutputStream(temp)) {
                    output.write(data); output.getFD().sync();
                }
                // A cover selected by the player while the request was in flight wins.
                if (target.isFile()) return false;
                try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { temp.delete(); }
            mark(context,game,"licensed");
            return true;
        } finally { conn.disconnect(); }
    }

    public static void checkService(String service, String accessKey) throws IOException {
        if (service == null || !service.matches("https://[A-Za-z0-9._:-]+(?:/[^?#]*)?"))
            throw new IOException("Informe o endereço HTTPS do catálogo");
        HttpURLConnection conn = (HttpURLConnection) new URL(service.replaceAll("/+$", "")+
            "/v1/health").openConnection();
        conn.setConnectTimeout(3500);conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("X-Luni-Key", accessKey == null ? "" : accessKey);
        try { if (conn.getResponseCode() != 200)
            throw new IOException("Servidor respondeu " + conn.getResponseCode() +
                ". Confira a chave e o endereço.");
        } finally { conn.disconnect(); }
    }

    public static void importImage(Context context, RomLibrary.Game game, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Imagem não acessível");
            byte[] bytes = read(in);
            if (BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) throw new IOException("Imagem inválida");
            try (FileOutputStream out = new FileOutputStream(file(context, game))) { out.write(bytes); }
            mark(context,game,"user");
        }
    }

    /** Uses a frame explicitly chosen by the player; never takes a screenshot automatically. */
    public static void saveGameFrame(Context context,RomLibrary.Game game,Bitmap image)throws IOException{
        if(image==null||image.getWidth()<64||image.getHeight()<64||image.getWidth()>1024||image.getHeight()>1024)
            throw new IOException("Ainda não há uma imagem do jogo.");
        File target=file(context,game),temp=File.createTempFile(".frame-",".tmp",target.getParentFile());
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){
                if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Não foi possível salvar a imagem.");
                out.getFD().sync();
            }
            try{java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){java.nio.file.Files.move(temp.toPath(),target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        }finally{temp.delete();}
        mark(context,game,"user");
    }

    private static byte[] read(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            if (out.size() + n > MAX_IMAGE) throw new IOException("Capa maior que 3 MB");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
