package br.com.knightjoga;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short display lettering for original Luni artwork; the library keeps the full ROM name. */
final class CoverTitle {
 private static final Pattern NUMBER=Pattern.compile("(?<![0-9])([1-9][0-9]{0,3})(?![0-9])");
 private CoverTitle(){}

 static String compact(String filename){
  if(filename==null||filename.trim().isEmpty())return "LUNI ARENA";
  String value=filename.replaceAll("(?i)\\s+-\\s+[^ ]+\\.(?:com|net|org)(?:\\.[a-z]{2})?.*$","")
      .replaceAll("\\[[^]]*]", " ").replaceAll("\\([^)]*\\)", " ")
      .replace('_',' ').replaceAll("(?i)\\.(?:sfc|smc|fig|swc|zip|7z)$", "");
  value=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","")
      .replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
  if(value.isEmpty())return "JOGO SNES";
  if(value.startsWith("DONKEY KONG COUNTRY")){
   String sequel=number(value.substring("DONKEY KONG COUNTRY".length()));
   return "DKC "+(sequel.isEmpty()?"1":sequel);
  }
  if(value.startsWith("SUPER MARIO RPG"))return "MARIO RPG";
  if(value.startsWith("SUPER BOMBERMAN")){
   String sequel=number(value.substring("SUPER BOMBERMAN".length()));
   return sequel.isEmpty()?"BOMBERMAN":"BOMBERMAN "+sequel;
  }
  if(value.startsWith("TOP GEAR")){
   String sequel=number(value.substring("TOP GEAR".length()));
   return sequel.isEmpty()?"TOP GEAR":"TOP GEAR "+sequel;
  }
  if(value.startsWith("SUPER MARIO "))value="MARIO "+value.substring("SUPER MARIO ".length());
  if(value.length()<=19)return value;
  String[] words=value.split(" ");
  StringBuilder shortName=new StringBuilder();
  for(String word:words){
   if(shortName.length()>0&&shortName.length()+word.length()+1>18)break;
   if(shortName.length()>0)shortName.append(' ');
   shortName.append(word);
  }
  String lastNumber=number(value);
  if(!lastNumber.isEmpty()&&!shortName.toString().matches(".*\\b"+lastNumber+"\\b.*")&&
      shortName.length()+lastNumber.length()+1<=19)shortName.append(' ').append(lastNumber);
  return shortName.length()==0?value.substring(0,Math.min(19,value.length())):shortName.toString();
 }
 private static String number(String value){
  Matcher match=NUMBER.matcher(value);return match.find()?match.group(1):"";
 }
}
