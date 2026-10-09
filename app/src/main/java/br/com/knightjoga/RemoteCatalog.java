package br.com.knightjoga;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/** Bounded, cached GitHub directory listings. Never called from the emulation thread. */
final class RemoteCatalog {
    private static final long WEEK=7L*24*60*60*1000;
    private static final ConcurrentHashMap<String,String> TREE_CACHE=new ConcurrentHashMap<>();
    private RemoteCatalog() {}

    static byte[] get(String url,int limit) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(4000);connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent","LuniArena/0.1");
        connection.setRequestProperty("Accept-Encoding","gzip");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Servidor respondeu "+connection.getResponseCode());
            try(InputStream raw=connection.getInputStream();
                InputStream in="gzip".equalsIgnoreCase(connection.getContentEncoding())?new GZIPInputStream(raw):raw;
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[16384];int n;
                while((n=in.read(buffer))!=-1){
                    if(out.size()+n>limit)throw new IOException("Catálogo grande demais");
                    out.write(buffer,0,n);
                }
                return out.toByteArray();
            }
        } finally {connection.disconnect();}
    }

    static String encode(String part) throws IOException {
        return java.net.URLEncoder.encode(part,"UTF-8").replace("+","%20");
    }
    static String normalize(String title){
        String name=title.replaceAll("(?i)\\s+-\\s+[^ ]+\\.(com|net|org)(\\.[a-z]{2})?.*$","")
                .replaceAll("\\[[^]]*]","").replaceAll("\\([^)]*\\)","");
        name=java.text.Normalizer.normalize(name,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }
    static String region(String title){
        String lower=title.toLowerCase(Locale.ROOT);
        if(lower.contains("japan")||lower.contains("japão"))return "japan";
        if(lower.contains("europe")||lower.contains("europa"))return "europe";
        if(lower.contains("usa")||lower.contains("united states"))return "usa";
        return "";
    }
    static boolean sameGame(String title,String candidate){
        String a=normalize(title),b=normalize(candidate);
        if(a.length()<3||b.length()<3)return false;
        if(a.equals(b))return true;
        // Do not mistake sequels for one another (e.g. Top Gear and Top Gear 2).
        if(!a.replaceAll("[^0-9]","").equals(b.replaceAll("[^0-9]","")))return false;
        return a.length()>=8&&(b.startsWith(a+" ")||a.startsWith(b+" "));
    }

    static synchronized List<String> files(Context context,String repository,String... folders)throws IOException{
        String key=repository+"/"+String.join("/",folders);
        File index=new File(context.getCacheDir(),"knight-index-"+hex(key)+".json");
        if(index.isFile()&&System.currentTimeMillis()-index.lastModified()<WEEK){
            try{return fromJson(readFile(index,8*1024*1024));}catch(Exception ignored){}
        }
        try {
            String ref="master";
            for(String folder:folders){
                String listing=repository+"/"+ref;
                String sha=TREE_CACHE.get(listing+"/"+folder);
                if(sha==null){
                    JSONObject tree=tree(repository,ref);
                    sha=find(tree,folder);
                    TREE_CACHE.put(listing+"/"+folder,sha);
                }
                ref=sha;
            }
            JSONObject tree=tree(repository,ref);
            JSONArray entries=tree.getJSONArray("tree");
            ArrayList<String> names=new ArrayList<>();
            for(int i=0;i<entries.length();i++){
                JSONObject item=entries.getJSONObject(i);
                if("blob".equals(item.optString("type")))names.add(item.getString("path"));
            }
            if(names.isEmpty())throw new IOException("Catálogo vazio");
            byte[] encoded=new JSONArray(names).toString().getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream out=new FileOutputStream(index)){out.write(encoded);}
            return names;
        }catch(Exception e){
            if(index.isFile())try{return fromJson(readFile(index,8*1024*1024));}catch(Exception ignored){}
            throw new IOException("Não foi possível consultar o catálogo: "+e.getMessage(),e);
        }
    }
    private static JSONObject tree(String repo,String ref)throws Exception{
        String url="https://api.github.com/repos/"+repo+"/git/trees/"+encode(ref);
        JSONObject tree=new JSONObject(new String(get(url,8*1024*1024),StandardCharsets.UTF_8));
        if(tree.optBoolean("truncated"))throw new IOException("Índice incompleto");
        return tree;
    }
    private static String find(JSONObject tree,String name)throws Exception{
        JSONArray entries=tree.getJSONArray("tree");
        for(int i=0;i<entries.length();i++){
            JSONObject item=entries.getJSONObject(i);
            if(name.equals(item.optString("path"))&&"tree".equals(item.optString("type")))return item.getString("sha");
        }
        throw new IOException("Pasta ausente: "+name);
    }
    private static byte[] readFile(File file,int limit)throws IOException{
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Índice grande demais");out.write(buffer,0,n);}
            return out.toByteArray();
        }
    }
    private static List<String> fromJson(byte[] bytes)throws Exception{
        JSONArray array=new JSONArray(new String(bytes,StandardCharsets.UTF_8));
        ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<array.length();i++)names.add(array.getString(i));
        return names;
    }
    private static String hex(String text)throws IOException{
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.substring(0,24);
        }catch(Exception e){throw new IOException(e);}
    }
}
