package br.com.knightjoga;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gaming vocabulary and composable patterns; proper names and code bytes are preserved. */
final class CheatText {
 private static final Pattern PLAYER=Pattern.compile("^(P[1-5]|Player [1-5])\\s*[:\\-–]?[ \\t]+(.+)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern PORTUGUESE=Pattern.compile("\\b(vida|vidas|tempo|bombas|invencibilidade|máximo|máxima|potência|munição|jogador|começar|infinito|infinitas|armadura|dinheiro|saúde|resistência|experiência|velocidade|pontuação|magia|defesa|ataque|força|corações|nível|personagem)\\b",Pattern.CASE_INSENSITIVE);
 private static final Pattern START=Pattern.compile("^(?:start(?: the game)? with|have) ([0-9]{1,4}) (lives|bombs|continues|credits|coins)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern GET_ITEM=Pattern.compile("^get (.+?) when unequip(?:ping)? (?:an? )?item(?: \\(alternate code #([0-9]+)\\))?$",Pattern.CASE_INSENSITIVE);
 private static final Pattern MODIFIER=Pattern.compile("^(.+?) (?:modifier|modifer|modfier|modifyer)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern PAREN=Pattern.compile("^(.+?)\\s*\\(([^()]*)\\)$");
 private static final Pattern WHIP=Pattern.compile("^have enhanced whip( #[0-9]+)?$",Pattern.CASE_INSENSITIVE);
 private static final Pattern TRAILING=Pattern.compile("^(.+?) (?:infinite|unlimited|endless|max|maximum)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern MINIMUM=Pattern.compile("^minimum of ([0-9]+) (hp|mp|experience|exp)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern ENEMY=Pattern.compile("^enemy ([0-9]+) automatically dead$",Pattern.CASE_INSENSITIVE);
 private static final Pattern EXCESS=Pattern.compile("^lots of (exp\\.?|xp|experience)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern CAUTION=Pattern.compile("^a value above ([0-9]+) will crash the game$",Pattern.CASE_INSENSITIVE);
 private static final Pattern TARGET=Pattern.compile("^(.+?) for (character [1-5]|player [1-5]|p[1-5]|all characters)$",Pattern.CASE_INSENSITIVE);
 private static final Pattern POSSESS=Pattern.compile("^(?:have|get|always have|start with) (.+)$",Pattern.CASE_INSENSITIVE);
 private CheatText(){}
 static boolean alreadyPortuguese(String text){return text!=null&&PORTUGUESE.matcher(text).find();}
 private static String infinite(String subject){
  switch(subject){
   case "lives":case "life":return "Vidas infinitas";
   case "health":case "hp":case "health points":return "Pontos de vida (HP) infinitos";
   case "mp":case "magic points":return "Pontos de magia (MP) infinitos";
   case "energy":return "Energia infinita";
   case "magic":return "Magia infinita";
   case "time":return "Tempo infinito";
   case "ammo":case "ammunition":return "Munição infinita";
   case "bombs":return "Bombas infinitas";
   case "money":case "gold":case "gil":case "cash":return "Dinheiro infinito";
   case "credits":return "Créditos infinitos";
   case "continues":return "Continues infinitos";
   case "power":return "Poder infinito";
   case "stamina":return "Resistência infinita";
   case "hearts":return "Corações infinitos";
   case "gems":return "Gemas infinitas";
   case "rupees":return "Rúpias infinitas";
   case "yen":return "Ienes infinitos";
   case "magirock":return "Magirock ilimitado";
   case "force power":return "Poder da Força infinito";
   case "exp":case "exp.":case "xp":case "experience":return "Experiência (EXP) infinita";
   case "arrows":return "Flechas infinitas";
   case "shield":case "armor":case "armour":case "shield/armor":return "Escudo/armadura infinitos";
   default:return null;
  }
 }
 private static String maximum(String subject){
  switch(subject){
   case "health":case "hp":case "health points":return "Pontos de vida (HP) máximos";
   case "mp":case "magic points":return "Pontos de magia (MP) máximos";
   case "magic":return "Magia máxima";
   case "lives":return "Máximo de vidas";
   case "bombs":return "Máximo de bombas";
   case "score":return "Pontuação máxima";
   case "money":case "gold":return "Dinheiro máximo";
   case "power":return "Poder máximo";
   case "level":case "lv":return "Nível máximo";
   case "experience":case "exp":case "exp.":case "xp":return "Experiência (EXP) máxima";
   case "attack":case "atk":return "Ataque máximo";
   case "defense":case "defence":case "def":return "Defesa máxima";
   case "strength":case "str":return "Força máxima";
   case "speed":case "spd":return "Velocidade máxima";
   case "hearts":return "Máximo de corações";
   case "gems":return "Máximo de gemas";
   case "ammo":return "Munição máxima";
   case "energy":return "Energia máxima";
   default:return null;
  }
 }
 private static String noun(String value){
  switch(value.trim().toLowerCase(Locale.ROOT)){
   case "hp":case "health":case "health points":return "pontos de vida (HP)";
   case "mp":case "magic points":return "pontos de magia (MP)";
   case "exp":case "exp.":case "xp":case "experience":case "experience points":return "experiência (EXP)";
   case "character":return "personagem";
   case "current character":return "personagem atual";
   case "level":case "lv":case "lvl":return "nível";
   case "current level":return "nível atual";
   case "item":return "item";
   case "weapon":return "arma";
   case "armor":case "armour":return "armadura";
   case "money":case "gold":return "dinheiro";
   case "attack":case "atk":return "ataque (ATK)";
   case "defense":case "defence":case "def":return "defesa (DEF)";
   case "strength":case "str":return "força (STR)";
   case "speed":case "spd":return "velocidade (SPD)";
   case "lives":return "vidas";
   case "time":return "tempo";
   case "score":return "pontuação";
   case "bombs":return "bombas";
   case "ammo":return "munição";
   case "energy":return "energia";
   case "magic":return "magia";
   default:return null;
  }
 }
 static String known(String original){
  if(original==null||original.trim().isEmpty())return null;
  String value=original.trim().replace('_',' ').replaceAll("\\s+"," ");String player="";
  Matcher who=PLAYER.matcher(value);
  if(who.matches()){
   player=who.group(1).replaceAll("(?i)Player ","P")+" · ";value=who.group(2);
  }
  String lower=value.toLowerCase(Locale.ROOT),result=null;
  switch(lower){
   case "invincibility":case "invincible":case "always invincible":result="Invencibilidade";break;
   case "no damage":case "never take damage":result="Sem sofrer dano";break;
   case "one hit kill":case "one hit kills":result="Derrotar com um golpe";break;
   case "all weapons":case "have all weapons":result="Todas as armas";break;
   case "all items":case "have all items":result="Todos os itens";break;
   case "all levels":result="Todas as fases";break;
   case "unlock all characters":result="Desbloquear todos os personagens";break;
   case "no time limit":result="Sem limite de tempo";break;
   case "no enemies":result="Sem inimigos";break;
   case "no abnormalities":result="Sem alterações de estado";break;
   case "can always move":result="Pode se mover sempre";break;
   case "you can fly!":result="Pode voar";break;
   case "activate debug mode":result="Ativar modo de depuração";break;
   case "access debug room":result="Acessar sala de depuração";break;
   case "total invincibility":result="Invencibilidade total";break;
   case "super jump":result="Super salto";break;
   case "max health gauge (get hit to enable)":result="Barra de vida no máximo (receba um golpe para ativar)";break;
   case "get hit to enable":result="Receba um golpe para ativar";break;
   case "level up fast (use only in areas with dungeons)":result="Subir de nível rápido (use apenas em áreas com masmorras)";break;
   case "level up fast":result="Subir de nível rápido";break;
   case "use only in areas with dungeons":result="Use apenas em áreas com masmorras";break;
   case "god mode (through no enemy collisions)":result="Invencível por ausência de colisão com inimigos";break;
   case "have maximum gold":result="Ter o máximo de ouro";break;
   case "character modifier":result="Alterar personagem";break;
   case "current level modifier":result="Alterar fase atual";break;
   case "character":result="Personagem";break;
   case "player 1":result="Jogador 1";break;
   case "player 2":result="Jogador 2";break;
   case "god mode":result="Modo invencível";break;
   case "through no enemy collisions":result="Sem colisão com inimigos";break;
   case "infinite money on hand":result="Dinheiro em mãos infinito";break;
   case "money in atm":result="Dinheiro no caixa eletrônico";break;
   case "fight one battle and get up to level 80":result="Lute uma batalha e chegue até o nível 80";break;
   case "press select to enable":result="Pressione Select para ativar";break;
   case "score always 9999999":result="Pontuação sempre em 9.999.999";break;
   case "no random battles":case "no random encounters":result="Sem encontros aleatórios";break;
   case "walk on air":result="Andar no ar";break;
   case "infinite shield/armor":result="Escudo/armadura infinitos";break;
   case "unequipping an item will add an item":result="Desequipar um item adiciona outro item";break;
   case "have 8 of all magic":result="Ter 8 de cada magia";break;
   case "have 8 of all medallions":result="Ter 8 de cada medalhão";break;
   case "999 str/atp/dfp":result="999 de força/ataque/defesa (STR/ATP/DFP)";break;
  }
  if(result==null){
   for(String prefix:new String[]{"infinite ","unlimited ","endless "})
    if(lower.startsWith(prefix)){result=infinite(lower.substring(prefix.length()));break;}
  }
  if(result==null){
   for(String prefix:new String[]{"max ","maximum ","full "})
    if(lower.startsWith(prefix)){result=maximum(lower.substring(prefix.length()));break;}
  }
  if(result==null){
   Matcher count=START.matcher(value);
   if(count.matches()){
    String unit=count.group(2).toLowerCase(Locale.ROOT);
    result="Começar com "+count.group(1)+" "+(unit.equals("lives")?"vidas":unit.equals("bombs")?"bombas":
      unit.equals("credits")?"créditos":unit.equals("coins")?"moedas":"continues");
   }
  }
  if(result==null){
   Matcher item=GET_ITEM.matcher(value);
   if(item.matches()&&noun(item.group(1))!=null)result="Obter "+noun(item.group(1))+" ao desequipar item"+
       (item.group(2)==null?"":" (código alternativo #"+item.group(2)+")");
  }
  if(result==null){
   Matcher whip=WHIP.matcher(value);
   if(whip.matches())result="Ter chicote aprimorado"+(whip.group(1)==null?"":whip.group(1));
  }
  if(result==null){
   Matcher modifier=MODIFIER.matcher(value);
   if(modifier.matches()){
    String target=noun(modifier.group(1));
    if(target!=null)result="Alterar "+target;
   }
  }
  if(result==null&&value.contains("/")){
   String[] pair=value.split("\\s*/\\s*",2);
   if(pair.length==2&&noun(pair[0])!=null&&noun(pair[1])!=null)
    result=Character.toUpperCase(noun(pair[0]).charAt(0))+noun(pair[0]).substring(1)+" / "+noun(pair[1]);
  }
  if(result==null){
   Matcher trailing=TRAILING.matcher(value);
   if(trailing.matches())result=lower.endsWith("max")||lower.endsWith("maximum")?
       maximum(trailing.group(1).toLowerCase(Locale.ROOT)):infinite(trailing.group(1).toLowerCase(Locale.ROOT));
  }
  if(result==null){
   Matcher minimum=MINIMUM.matcher(value);
   if(minimum.matches())result="No mínimo "+minimum.group(1)+" "+noun(minimum.group(2));
  }
  if(result==null){
   Matcher enemy=ENEMY.matcher(value);
   if(enemy.matches())result="Inimigo "+enemy.group(1)+" derrotado automaticamente";
  }
  if(result==null){
   Matcher excess=EXCESS.matcher(value);
   if(excess.matches())result="Muita experiência (EXP)";
  }
  if(result==null){
   Matcher caution=CAUTION.matcher(value);
   if(caution.matches())result="Valor acima de "+caution.group(1)+" travará o jogo";
  }
  if(result==null){
   Matcher context=PAREN.matcher(value);
   if(context.matches()){
    String effect=known(context.group(1)),note=known(context.group(2));
    if(effect!=null&&note!=null)result=effect+" ("+note+")";
   }
  }
  if(result==null){
   String[] segments=value.split("\\s+[-–—]\\s+",2);
   if(segments.length==2){
    String effect=known(segments[0]),context=known(segments[1]);
    if(effect!=null&&context!=null)result=effect+" — "+context;
   }
  }
  if(result==null){
   Matcher target=TARGET.matcher(value);
   if(target.matches()){
    String effect=known(target.group(1));
    if(effect!=null){String whom=target.group(2).toLowerCase(Locale.ROOT);
     result=effect+" para "+(whom.equals("all characters")?"todos os personagens":
         whom.replace("character","personagem").replace("player","jogador"));
    }
   }
  }
  if(result==null){
   Matcher possess=POSSESS.matcher(value);
   if(possess.matches()){
    String target=noun(possess.group(1));
    if(target!=null)result=(lower.startsWith("start")?"Começar com ":
        lower.startsWith("always")?"Ter sempre ":"Obter ")+target;
   }
  }
  return result==null?null:player+result;
 }
}
