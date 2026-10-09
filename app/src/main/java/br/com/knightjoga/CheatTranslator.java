package br.com.knightjoga;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Game terminology first; an on-device translator handles every remaining English description. */
final class CheatTranslator {
 interface Progress { void update(String message); void changed(); void finished(); boolean active(); }
 private static boolean busy;
 private CheatTranslator(){}
 private static String key(CheatRepository.Entry entry){
  return entry.source+"\u0000"+entry.code+"\u0000"+entry.description;
 }
 private static String autoKey(String original){
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8));
   StringBuilder result=new StringBuilder("game_pt_v3_");
   for(byte b:hash)result.append(Character.forDigit((b>>4)&15,16)).append(Character.forDigit(b&15,16));
   return result.toString();
  }catch(Exception e){return "";}
 }
 static void rename(Context context,CheatRepository.Entry entry,String label){
  String clean=label==null?"":label.trim().replaceAll("[\\r\\n]+"," ");
  if(clean.length()>100)clean=clean.substring(0,100);
  if(clean.isEmpty())context.getSharedPreferences("cheat_names",0).edit().remove(key(entry)).apply();
  else context.getSharedPreferences("cheat_names",0).edit().putString(key(entry),clean).apply();
 }
 static boolean isMachine(Context context,CheatRepository.Entry entry){
  if(context.getSharedPreferences("cheat_names",0).contains(key(entry))||
      CheatText.alreadyPortuguese(entry.description)||CheatText.known(entry.description)!=null)return false;
  String cacheKey=autoKey(entry.description);
  return !cacheKey.isEmpty()&&context.getSharedPreferences("cheat_translations_v3",0).contains(cacheKey);
 }
 static String display(Context context,CheatRepository.Entry entry){
  String named=context.getSharedPreferences("cheat_names",0).getString(key(entry),null);
  if(named!=null&&!named.isEmpty())return named;
  if(CheatText.alreadyPortuguese(entry.description))return entry.description;
  String known=CheatText.known(entry.description);
  if(known!=null)return known;
  String cacheKey=autoKey(entry.description);
  String translated=cacheKey.isEmpty()?null:
      context.getSharedPreferences("cheat_translations_v3",0).getString(cacheKey,null);
  return translated==null||translated.isEmpty()?entry.description:translated;
 }
 static boolean differs(Context context,CheatRepository.Entry entry){
  return !display(context,entry).equalsIgnoreCase(entry.description);
 }
 /** Normalize common catalogue typos without changing the game code or the original label. */
 static String prepare(String original){
  return original.replaceAll("(?i)\\bmodifer\\b|\\bmodfier\\b|\\bmodifyer\\b","Modifier")
      .replaceAll("(?i)\\binfinte\\b|\\binifinite\\b","Infinite")
      .replaceAll("(?i)\\bexp\\.?(?=\\W|$)","experience (EXP)")
      .replaceAll("(?i)\\bxp\\b","experience (EXP)")
      .replaceAll("(?i)\\bhp\\b","health points (HP)")
      .replaceAll("(?i)\\bmp\\b","magic points (MP)")
      .replaceAll("(?i)\\batk\\b","attack (ATK)")
      .replaceAll("(?i)\\bdef\\b","defense (DEF)")
      .replaceAll("(?i)\\bspd\\b","speed (SPD)")
      .replaceAll("(?i)\\bstr\\b","strength (STR)")
      .replaceAll("(?i)\\blv(?:l)?\\b","level (LV)");
 }
 /** Reject changed numbers and obvious non-translations; retain the English line for review. */
 static String review(String original,String machine){
  if(machine==null)return null;
  String translated=machine.trim().replaceAll("\\s+"," ");
  if(translated.length()<3||translated.length()>240||translated.equalsIgnoreCase(original))return null;
  String originalNumbers=original.replaceAll("[^0-9]","");
  String translatedNumbers=translated.replaceAll("[^0-9]","");
  if(!originalNumbers.equals(translatedNumbers))return null;
  if(original.matches("(?is).*\\bHP\\b.*")){
   translated=translated.replaceAll("(?i)pontos? de saúde", "pontos de vida");
   if(translated.matches("(?is).*pontos de vida.*")&&!translated.matches("(?is).*pontos de vida\\s*\\(HP\\).*"))
    translated=translated.replaceFirst("(?i)pontos de vida","pontos de vida (HP)");
  }
  if(original.matches("(?is).*\\b(?:EXP|XP)\\b.*")&&
      translated.matches("(?is).*experiência.*")&&!translated.matches("(?is).*experiência\\s*\\(EXP\\).*"))
   translated=translated.replaceFirst("(?i)experiência","experiência (EXP)");
  return translated;
 }
 private static synchronized boolean begin(){if(busy)return false;busy=true;return true;}
 private static synchronized void release(){busy=false;}
 static void translateRemaining(Context context,List<CheatRepository.Entry> entries,Progress listener){
  if(!listener.active())return;
  SharedPreferences saved=context.getSharedPreferences("cheat_translations_v3",0);
  LinkedHashSet<String> names=new LinkedHashSet<>();
  for(CheatRepository.Entry entry:entries){
   if(CheatText.alreadyPortuguese(entry.description)||CheatText.known(entry.description)!=null||
       context.getSharedPreferences("cheat_names",0).contains(key(entry)))continue;
   String cacheKey=autoKey(entry.description);
   if(!cacheKey.isEmpty()&&!saved.contains(cacheKey))names.add(entry.description);
  }
  if(names.isEmpty()){
   listener.update("Todos os nomes desta lista já foram traduzidos ou estavam em português.");
   listener.finished();return;
  }
  if(!begin()){listener.update("A tradução automática já está em andamento…");return;}
  ArrayList<String> pending=new ArrayList<>(names);
  Translator client=Translation.getClient(new TranslatorOptions.Builder()
      .setSourceLanguage(TranslateLanguage.ENGLISH)
      .setTargetLanguage(TranslateLanguage.PORTUGUESE).build());
  listener.update("Preparando tradução automática de "+pending.size()+" nomes…");
  client.downloadModelIfNeeded(new DownloadConditions.Builder().build())
      .addOnSuccessListener(ignored->translateNext(client,saved,pending,0,0,listener))
      .addOnFailureListener(error->{client.close();release();
       listener.update("Modelo indisponível. Conecte à internet e toque em ✦ para tentar de novo.");
       listener.finished();});
 }
 private static void translateNext(Translator client,SharedPreferences saved,ArrayList<String> pending,
                                   int index,int failures,Progress listener){
  if(!listener.active()){client.close();release();return;}
  if(index>=pending.size()){
   client.close();release();
   listener.update("Tradução concluída: "+(pending.size()-failures)+" novos nomes"+
       (failures==0?".":"; "+failures+" precisam de revisão ou nova tentativa."));
   listener.finished();return;
  }
  if(index==0||index%12==0)listener.update("Traduzindo "+(index+1)+" / "+pending.size()+" nomes…");
  String original=pending.get(index);
  client.translate(prepare(original)).addOnCompleteListener(result->{
   String translated=result.isSuccessful()?review(original,result.getResult()):null;
   int failed=failures;
   if(translated!=null){saved.edit().putString(autoKey(original),translated).apply();
    if((index+1)%12==0)listener.changed();
   }else failed++;
   translateNext(client,saved,pending,index+1,failed,listener);
  });
 }
}
