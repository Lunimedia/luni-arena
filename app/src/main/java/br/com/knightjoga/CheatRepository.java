package br.com.knightjoga;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads Libretro .cht entries in the background and stores them per exact ROM hash. */
final class CheatRepository {
    static final int MAX_ACTIVE=64;
    private static final int MAX_ENTRIES=600;
    private static final String SYSTEM="Nintendo - Super Nintendo Entertainment System";
    private static final String REPOSITORY="libretro/libretro-database";
    private static final Pattern KEY=Pattern.compile("^cheat([0-9]{1,3})_(desc|code)$");
    static final class Entry {
        final String description,code,source;
        final boolean manual;
        Entry(String description,String code,String source,boolean manual){
            this.description=description;this.code=code;this.source=source;this.manual=manual;
        }
    }
    static final class Result {
        final ArrayList<Entry> entries;
        final String note;
        Result(ArrayList<Entry> entries,String note){this.entries=entries;this.note=note;}
    }
    private CheatRepository(){}
    private static File cache(Context context,String romId){
        File folder=new File(context.getFilesDir(),"cheats");if(!folder.exists())folder.mkdirs();
        if(!romId.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Hash da ROM inválido");
        return new File(folder,romId+".json");
    }
    static String validCode(String raw){
        return CheatCode.normalize(raw,false);
    }
    static ArrayList<Entry> stored(Context context,String romId){
        ArrayList<Entry> result=new ArrayList<>();File file=cache(context,romId);
        if(!file.isFile()||file.length()>512*1024)return result;
        try{
            JSONArray array=new JSONArray(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
            for(int i=0;i<Math.min(array.length(),MAX_ENTRIES);i++){
                JSONObject row=array.getJSONObject(i);String code=CheatCode.normalize(row.optString("code"),true);
                if(code==null)continue;
                result.add(new Entry(row.optString("description","Código"),code,
                        row.optString("source","Catálogo"),row.optBoolean("manual")));
            }
        }catch(Exception ignored){}
        migrateLegacy(context,romId,result);
        return result;
    }
    private static void save(Context context,String romId,List<Entry> entries)throws IOException{
        File file=cache(context,romId);JSONArray json=new JSONArray();
        for(Entry item:entries){
            JSONObject row=new JSONObject();
            try{row.put("description",item.description);row.put("code",item.code);
                row.put("source",item.source);row.put("manual",item.manual);json.put(row);
            }catch(Exception e){throw new IOException(e);}
        }
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try{Files.write(temp.toPath(),json.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }finally{temp.delete();}
    }
    static ArrayList<Entry> addManual(Context context,String romId,String description,String input)throws IOException{
        String code=CheatCode.normalize(input,true);
        if(code==null)throw new IOException("Use Game Genie XXXX-XXXX ou Action Replay XXXXXXXX; ?? apenas no byte do valor.");
        String title=description.trim();if(title.isEmpty())title="Meu código";
        if(title.length()>80)title=title.substring(0,80);
        ArrayList<Entry> current=stored(context,romId);
        for(Entry entry:current)if(entry.code.equals(code)&&entry.manual)return current;
        if(current.size()>=MAX_ENTRIES)throw new IOException("Lista de cheats cheia.");
        current.add(new Entry(title,code,"Meu código",true));save(context,romId,current);return current;
    }
    private static SharedPreferences enabled(Context context){return context.getSharedPreferences("enabled_cheats",0);}
    private static String key(String romId,Entry entry){return romId+":"+entry.source+":"+entry.code;}
    private static String selectedKey(String romId,Entry entry){return key(romId,entry)+":value";}
    private static void migrateLegacy(Context context,String romId,List<Entry> entries){
        SharedPreferences prefs=enabled(context);SharedPreferences.Editor edit=prefs.edit();
        Set<String> used=new HashSet<>();boolean changed=false;
        for(Entry entry:entries){
            String old=romId+":"+entry.code;
            if(!prefs.contains(old))continue;
            if(prefs.getBoolean(old,false)&&used.add(entry.code)&&!CheatCode.variable(entry.code))
                edit.putBoolean(key(romId,entry),true);
            edit.remove(old);changed=true;
        }
        if(changed)edit.apply();
    }
    static String selectedCode(Context context,String romId,Entry entry){
        if(!CheatCode.variable(entry.code))return entry.code;
        String code=enabled(context).getString(selectedKey(romId,entry),null);
        return CheatCode.matches(entry.code,code)?code:null;
    }
    static boolean isEnabled(Context context,String romId,Entry entry){
        return selectedCode(context,romId,entry)!=null&&enabled(context).getBoolean(key(romId,entry),false);
    }
    static boolean setEnabled(Context context,String romId,Entry entry,String resolved,boolean active,List<Entry> all){
        SharedPreferences prefs=enabled(context);SharedPreferences.Editor edit=prefs.edit();
        if(!active){edit.putBoolean(key(romId,entry),false).apply();return true;}
        String code=CheatCode.variable(entry.code)?resolved:entry.code;
        if(code==null||CheatCode.normalize(code,false)==null||
            CheatCode.variable(entry.code)&&!CheatCode.matches(entry.code,code))return false;
        Set<String> remaining=new HashSet<>();
        for(Entry other:all){
            if(other==entry||!isEnabled(context,romId,other))continue;
            String existing=selectedCode(context,romId,other);
            if(CheatCode.conflicts(code,existing))
                edit.putBoolean(key(romId,other),false);
            else remaining.add(existing);
        }
        remaining.add(code);
        if(remaining.size()>MAX_ACTIVE)return false;
        if(CheatCode.variable(entry.code))edit.putString(selectedKey(romId,entry),code);
        edit.putBoolean(key(romId,entry),true).apply();return true;
    }
    static String[] activeCodes(Context context,String romId,List<Entry> entries){
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        for(Entry entry:entries)if(isEnabled(context,romId,entry)&&codes.size()<MAX_ACTIVE)
            codes.add(selectedCode(context,romId,entry));
        return codes.toArray(new String[0]);
    }
    static int activeCount(Context context,String romId,List<Entry> entries){
        Set<String> codes=new HashSet<>();
        for(Entry entry:entries)if(isEnabled(context,romId,entry))codes.add(selectedCode(context,romId,entry));
        return codes.size();
    }
    static Map<String,String> suggestedValues(Entry template,int slot,String[] picked,List<Entry> entries){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        if(!CheatCode.variable(template.code)||slot<0||slot>=CheatCode.slots(template.code))return result;
        if(picked.length!=CheatCode.slots(template.code))return result;
        StringBuilder pattern=new StringBuilder(template.code);
        int from=0;
        for(int i=0;i<picked.length;i++){
            int pos=template.code.indexOf("??",from);if(pos<0)return result;
            if(i!=slot){if(picked[i]==null)return result;pattern.replace(pos,pos+2,picked[i]);}
            from=pos+2;
        }
        // Compare fixed bytes and the selected byte with complete codes from the same file.
        for(Entry candidate:entries){
            if(!template.source.equals(candidate.source)||CheatCode.variable(candidate.code)||
                pattern.length()!=candidate.code.length())continue;
            boolean equal=true;String value=null;
            for(int i=0;i<pattern.length();i++){
                if(pattern.charAt(i)=='?'){value=candidate.code.substring(i,i+2);i++;continue;}
                if(pattern.charAt(i)!=candidate.code.charAt(i)){equal=false;break;}
            }
            if(equal&&value!=null)result.putIfAbsent(value,candidate.description);
        }
        return result;
    }
    private static String unquote(String text){
        String result=text.trim();
        if(result.length()>=2&&result.startsWith("\"")&&result.endsWith("\""))
            result=result.substring(1,result.length()-1);
        return result.replace("\\\"","\"");
    }
    static List<Entry> parse(String text,String source){
        Map<Integer,String> descriptions=new LinkedHashMap<>(),codes=new LinkedHashMap<>();
        for(String line:text.split("\\r?\\n")){
            if(line.length()>512)continue;
            int sep=line.indexOf('=');if(sep<0)continue;
            Matcher matcher=KEY.matcher(line.substring(0,sep).trim());if(!matcher.matches())continue;
            int number=Integer.parseInt(matcher.group(1));if(number>=MAX_ENTRIES)continue;
            String value=unquote(line.substring(sep+1));
            if("desc".equals(matcher.group(2)))descriptions.put(number,value);
            else codes.put(number,value);
        }
        ArrayList<Integer> order=new ArrayList<>(codes.keySet());Collections.sort(order);
        ArrayList<Entry> parsed=new ArrayList<>();
        for(Integer number:order){
            String code=CheatCode.normalize(codes.get(number),true);if(code==null)continue;
            String title=descriptions.get(number);
            if(title==null||title.trim().isEmpty())title="Código "+(number+1);
            parsed.add(new Entry(title.length()>120?title.substring(0,120):title,code,source,false));
        }
        return parsed;
    }
    private static String source(String filename){
        String base=filename.endsWith(".cht")?filename.substring(0,filename.length()-4):filename;
        return "Libretro · "+base;
    }
    static boolean needsSourceRefresh(List<Entry> entries){
        for(Entry entry:entries)if(!entry.manual&&("Libretro".equals(entry.source)||
                entry.source.endsWith(" • Libretro")||"Catálogo".equals(entry.source)))return true;
        return false;
    }
    private static boolean sameRegion(String name,String filename){
        String region=RemoteCatalog.region(name);
        return region.isEmpty()||RemoteCatalog.region(filename).isEmpty()
                ||filename.toLowerCase(Locale.ROOT).contains(region);
    }
    /** A small attributed offline starting set; these addresses belong to the Japanese release. */
    private static List<Entry> bombermanReference(String title){
        if(!"super bomberman 5".equals(RemoteCatalog.normalize(title)))return Collections.emptyList();
        String source="Super Bomberman 5 (Japão) • GameHacking.org";
        ArrayList<Entry> list=new ArrayList<>();
        list.add(new Entry("Vidas infinitas","7E1D23:09",source,false));
        list.add(new Entry("Bombas ilimitadas","7E1752:00",source,false));
        list.add(new Entry("Tempo infinito","7E0E1D:3B",source,false));
        list.add(new Entry("Invencibilidade","7E170A:E6",source,false));
        list.add(new Entry("Bombas remotas","7E4ED5:01",source,false));
        list.add(new Entry("Potência máxima das bombas","7E17CA:0A",source,false));
        list.add(new Entry("Quantidade máxima de bombas • P1","7E4EBD:09",source,false));
        return list;
    }
    private static Result reference(Context context,String romId,String title,String message)throws IOException{
        LinkedHashMap<String,Entry> merged=new LinkedHashMap<>();
        for(Entry item:stored(context,romId))merged.putIfAbsent(item.code,item);
        for(Entry item:bombermanReference(title))merged.putIfAbsent(item.code,item);
        ArrayList<Entry> list=new ArrayList<>(merged.values());save(context,romId,list);
        return new Result(list,message+" Referência da versão japonesa; hacks e traduções podem ser incompatíveis.");
    }
    static Result refresh(Context context,String romId,String gameTitle,boolean force)throws IOException{
        long now=System.currentTimeMillis();
        SharedPreferences stamp=context.getSharedPreferences("cheat_fetch",0);
        if(!force&&now-stamp.getLong("last_"+romId,0)<24L*60*60*1000)
            return new Result(stored(context,romId),"Lista salva no aparelho. Atualização diária disponível.");
        List<String> files;
        try{files=RemoteCatalog.files(context,REPOSITORY,"cht",SYSTEM);}
        catch(IOException error){
            if(!bombermanReference(gameTitle).isEmpty())return reference(context,romId,gameTitle,"Catálogo indisponível.");
            throw error;
        }
        ArrayList<String> matches=new ArrayList<>();
        for(String filename:files){
            if(!filename.toLowerCase(Locale.ROOT).endsWith(".cht"))continue;
            String title=filename.substring(0,filename.length()-4);
            if(RemoteCatalog.sameGame(gameTitle,title)&&sameRegion(gameTitle,title))matches.add(filename);
        }
        matches.sort(Comparator.comparingInt(filename->
                filename.toLowerCase(Locale.ROOT).contains(RemoteCatalog.region(gameTitle))?0:1));
        LinkedHashMap<String,Entry> found=new LinkedHashMap<>();int downloaded=0,failed=0;
        ArrayList<List<Entry>> lists=new ArrayList<>();
        for(int i=0;i<Math.min(matches.size(),12);i++){
            String filename=matches.get(i);
            String address="https://raw.githubusercontent.com/"+REPOSITORY+"/master/cht/"+
                    RemoteCatalog.encode(SYSTEM)+"/"+RemoteCatalog.encode(filename);
            try{
                String data=new String(RemoteCatalog.get(address,256*1024),StandardCharsets.UTF_8);
                lists.add(parse(data,source(filename)));
                downloaded++;
            }catch(Exception ignored){failed++;}
        }
        // Round-robin keeps smaller source files visible when one catalog contains hundreds of codes.
        for(int row=0;found.size()<MAX_ENTRIES;row++){
            boolean any=false;
            for(List<Entry> group:lists)if(row<group.size()){
                any=true;Entry item=group.get(row);
                found.putIfAbsent(item.source+"\u0000"+item.code,item);
                if(found.size()==MAX_ENTRIES)break;
            }
            if(!any)break;
        }
        if(!matches.isEmpty()&&downloaded==0){
            if(!bombermanReference(gameTitle).isEmpty())return reference(context,romId,gameTitle,"Falha ao baixar a lista online.");
            throw new IOException("Não foi possível baixar os cheats; verifique a internet e tente Atualizar.");
        }
        if(found.isEmpty())for(Entry item:bombermanReference(gameTitle))found.putIfAbsent(item.source+"\u0000"+item.code,item);
        // Keep personally entered codes first, even if the remote list reached its size cap.
        ArrayList<Entry> previous=stored(context,romId);
        LinkedHashMap<String,Entry> merged=new LinkedHashMap<>();
        for(Entry item:previous)if(item.manual||downloaded==0)merged.putIfAbsent(item.source+"\u0000"+item.code,item);
        for(Entry item:found.values())if(merged.size()<MAX_ENTRIES)merged.putIfAbsent(item.source+"\u0000"+item.code,item);
        ArrayList<Entry> list=new ArrayList<>(merged.values());
        SharedPreferences prefs=enabled(context);SharedPreferences.Editor migrated=prefs.edit();
        Set<String> claimed=new HashSet<>();
        for(Entry old:previous)if(isEnabled(context,romId,old)){
            String selected=selectedCode(context,romId,old);
            for(Entry next:list)if(next.code.equals(old.code)&&claimed.add(next.code)){
                migrated.putBoolean(key(romId,next),true);
                if(CheatCode.variable(next.code))migrated.putString(selectedKey(romId,next),selected);
                if(!key(romId,next).equals(key(romId,old)))migrated.remove(key(romId,old));
                break;
            }
        }
        migrated.apply();
        save(context,romId,list);stamp.edit().putLong("last_"+romId,now).apply();
        String message=downloaded==0&&found.isEmpty()?"Sem cheats catalogados para este título/região. Você pode adicionar um código.":
                downloaded==0?"Códigos de referência da versão japonesa; hacks e traduções podem ser incompatíveis.":
                list.size()+" códigos encontrados"+(failed==0?".":"; algumas listas não responderam.");
        return new Result(list,message);
    }
}
