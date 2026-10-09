package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Run with JDK 17. Checks portable files, exact-ROM binding and input history without Android. */
public final class ProjectSmokeTest {
 private static void check(boolean truth,String message){if(!truth)throw new AssertionError(message);}
 private static byte[] bytes(int count,int value){byte[] data=new byte[count];Arrays.fill(data,(byte)value);return data;}
 private static byte[] legacyReplay(byte[] modern)throws IOException{
  ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(modern));
      DataOutputStream out=new DataOutputStream(buffer)){
   out.writeInt(in.readInt());if(in.readInt()!=3)throw new IOException("Formato de teste inesperado");
   in.readInt();out.writeInt(2);out.writeInt(28);
   out.writeUTF(in.readUTF());out.writeUTF(in.readUTF());in.readBoolean();
   int stateSize=in.readInt();byte[] state=in.readNBytes(stateSize);
   int count=in.readInt();byte[] frames=in.readNBytes(count*20);in.readLong();
   out.writeInt(stateSize);out.write(state);out.writeInt(count);out.write(frames);
   java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(state);crc.update(frames);out.writeLong(crc.getValue());
  }
  return buffer.toByteArray();
 }
 public static void main(String[] ignored)throws Exception{
  String a="a".repeat(64),b="b".repeat(64);
  Path source=Files.createTempDirectory("knight-test-source-"),restored=Files.createTempDirectory("knight-test-restored-");
  Files.write(source.resolve(a+".srm"),bytes(8192,7));Files.createDirectories(source.resolve("states"));
  Files.write(source.resolve("states").resolve(a+"-2.state"),bytes(512,9));
  ByteArrayOutputStream save=new ByteArrayOutputStream();
  check(SaveArchive.exportGame(source.toFile(),a,save)==2,"saves exportados");
  check(SaveArchive.importGame(restored.toFile(),a,new ByteArrayInputStream(save.toByteArray()))==2,"saves importados");
  check(Arrays.equals(Files.readAllBytes(source.resolve(a+".srm")),Files.readAllBytes(restored.resolve(a+".srm"))),"SRAM intacta");
  boolean refused=false;
  try{SaveArchive.importGame(restored.toFile(),b,new ByteArrayInputStream(save.toByteArray()));}
  catch(IOException e){refused=true;}
  check(refused&&!Files.exists(restored.resolve(b+".srm")),"ROM diferente recusada sem sobrescrever");
  ByteArrayOutputStream attack=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(attack)){
   zip.putNextEntry(new ZipEntry("../foreign.srm"));zip.write(bytes(32,1));zip.closeEntry();
  }
  refused=false;
  try{SaveArchive.importGame(restored.toFile(),a,new ByteArrayInputStream(attack.toByteArray()));}
  catch(IOException e){refused=true;}
  check(refused&&!Files.exists(restored.getParent().resolve("foreign.srm")),"ZIP traversal recusado");
  Path resetRoom=Files.createTempDirectory("luni-reset-replay-");
  ReplayArchive.Recorder resetRecorder=new ReplayArchive.Recorder(a,b,bytes(300,3));
  resetRecorder.append(new int[]{1,2,0,0,0});
  resetRecorder.append(new int[]{ReplayArchive.RESET_FLAG,4,0,0,0});
  boolean invalidReset=false;
  try{resetRecorder.append(new int[]{0,ReplayArchive.RESET_FLAG,0,0,0});}
  catch(IOException e){invalidReset=true;}
  check(invalidReset&&resetRecorder.count()==2,"somente o host pode marcar reinício no replay");
  resetRecorder.save(resetRoom.toFile());
  ReplayArchive.Player resetPlayback=ReplayArchive.load(resetRoom.toFile(),a,b);
  check(resetPlayback.next()[0]==1&&resetPlayback.next()[0]==ReplayArchive.RESET_FLAG&&
   resetPlayback.next()==null,"replay registra o reinício no quadro correto");
  resetPlayback.rewind();resetPlayback.ghostInputs(0);
  int[] resetGhost=resetPlayback.ghostInputs(8);
  check(resetGhost[0]==(8|ReplayArchive.RESET_FLAG)&&resetGhost[1]==4,
   "Arena Fantasma acompanha o reinício gravado na sala");
  ReplayArchive.Recorder recorder=new ReplayArchive.Recorder(a,b,bytes(300,42),false);
  recorder.append(new int[]{1,2,4,8,16});recorder.append(new int[]{0,0,8,0,0});
  recorder.save(source.toFile());
  ReplayArchive.Player player=ReplayArchive.load(source.toFile(),a,b);
  check(!player.multitapEnabled,"replay preserva Multitap desligado");
  check(player.hasRemoteInputs(),"replay com jogadores adicionais detectado para a Arena Fantasma");
  check(player.count==2&&player.next()[2]==4&&player.next()[2]==8&&player.next()==null,"replay reproduz entradas de cinco jogadores");
  player.rewind();
  int[] ghostRound=player.ghostInputs(256);
  check(ghostRound[0]==256&&ghostRound[1]==2&&ghostRound[2]==4&&player.progress()==1,
   "o jogador atual enfrenta P2–P5 gravados no mesmo estado inicial");
  ReplayArchive.Recorder soloGhost=new ReplayArchive.Recorder(a,b,bytes(300,7));
  soloGhost.append(new int[]{8,0,0,0,0});soloGhost.save(restored.toFile());
  ReplayArchive.Player soloRival=ReplayArchive.load(restored.toFile(),a,b);
  check(soloRival.multitapEnabled,"replay padrão preserva Multitap ligado");
  check(!soloRival.hasRemoteInputs()&&soloRival.ghostInputs(1)[1]==8&&soloRival.ghostInputs(1)[1]==0,
   "o P1 de um replay solo vira P2 até acabar a gravação");
  ChallengeLedger board=ChallengeLedger.load(source.toFile(),a);
  board.setFirstTo(2);board.award(2);board.award(2);board.save(source.toFile(),a);
  ChallengeLedger restoredBoard=ChallengeLedger.load(source.toFile(),a);
  check(restoredBoard.rounds()==2&&restoredBoard.wins(2)==2&&restoredBoard.winner()==2,
   "placar por ROM guarda campeão e contagem de rodadas");
  restoredBoard.reset();restoredBoard.save(source.toFile(),a);
  check(ChallengeLedger.load(source.toFile(),a).rounds()==0,
   "novo torneio zera só o placar do jogo selecionado");
  HudLearning fingers=new HudLearning();
  for(int i=0;i<25;i++)fingers.observe(5,.55f,0f);
  fingers.save(source.toFile(),a);
  HudLearning prior=HudLearning.load(source.toFile(),a);
  check(prior.count(5)==25&&prior.advice(5).contains("direita"),
   "radar de toque persiste sugestão por botão sem alterar o HUD");
  ByteArrayOutputStream portable=new ByteArrayOutputStream();ReplayArchive.exportReplay(source.toFile(),a,b,portable);
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(portable.toByteArray()));
  check(ReplayArchive.load(restored.toFile(),a,b).count==2,"replay portátil por ROM e núcleo");
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(legacyReplay(portable.toByteArray())));
  check(ReplayArchive.load(restored.toFile(),a,b).multitapEnabled,"replay de formato antigo mantém Multitap ligado");
  MomentArchive.save(source.toFile(),a,b,Arrays.asList(
   new MomentArchive.Mark(21,"Virada"),new MomentArchive.Mark(72,"Vitória")));
  check(MomentArchive.load(source.toFile(),a,b).get(1).frame==72,
   "marcadores vinculados à gravação da mesma ROM e núcleo");
  refused=false;try{MomentArchive.load(source.toFile(),a,"c".repeat(64));}catch(IOException e){refused=true;}
  check(refused,"núcleo diferente não aceita a cápsula de momentos");
  byte[] previousBuild=portable.toByteArray().clone();previousBuild[11]=20;
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(previousBuild));
  check(ReplayArchive.load(restored.toFile(),a,b).count==2,"replay 0.16.0 com mesmo núcleo preservado");
  byte[] priorPatch=portable.toByteArray().clone();priorPatch[11]=19;
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(priorPatch));
  check(ReplayArchive.load(restored.toFile(),a,b).count==2,"replay 0.15.1 com mesmo núcleo preservado");
  byte[] previousVersion=portable.toByteArray().clone();previousVersion[11]=18;
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(previousVersion));
  check(ReplayArchive.load(restored.toFile(),a,b).count==2,"replay 0.15 com mesmo núcleo preservado");
  previousVersion[11]=17;
  ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(previousVersion));
  check(ReplayArchive.load(restored.toFile(),a,b).count==2,"replay 0.14 com mesmo núcleo preservado");
  previousVersion[11]=16;boolean olderRefused=false;
  try{ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(previousVersion));}
  catch(IOException e){olderRefused=true;}
  check(olderRefused&&ReplayArchive.load(restored.toFile(),a,b).count==2,"replay antigo recusado sem perder o atual");
  refused=false;try{ReplayArchive.load(restored.toFile(),a,"c".repeat(64));}catch(IOException e){refused=true;}
  check(refused,"núcleo diferente recusa reprodução");
  byte[] corrupted=portable.toByteArray();corrupted[corrupted.length-12]^=1;
  refused=false;try{ReplayArchive.importReplay(restored.toFile(),a,b,new ByteArrayInputStream(corrupted));}
  catch(IOException e){refused=true;}
  check(refused&&ReplayArchive.load(restored.toFile(),a,b).count==2,"importação inválida preserva o replay existente");
  RewindBuffer history=new RewindBuffer();history.add(30,bytes(256,3));history.add(60,bytes(256,6));history.add(90,bytes(256,9));
  check(history.available(120,50)&&!history.available(120,100),
   "voto de rebobinar só aparece se houver estado anterior ao alvo");
  check(history.rewind(120,50)[0]==6&&history.count()==2,"rebobina e descarta histórico futuro");
  history.clear();check(history.count()==0,"histórico apagado");
  InputDiagnostics samples=new InputDiagnostics();samples.add(1);samples.add(2);samples.add(30);
  check(samples.average()==11&&samples.p95()==30,"fila Android média e percentil");
  check(RoomInvite.parse(RoomInvite.link("192.168.1.12",35671)).port==35671,"convite de sala");
  check(RoomInvite.parse("Entre na sala: luniarena://192.168.1.12:35671").port==35671,"convite compartilhado em mensagem");
  refused=false;try{RoomInvite.parse("luniarena://192.168.1.999:35671");}catch(IOException e){refused=true;}
  check(refused,"IP inválido rejeitado");
  check(RoomQuality.delayFrames(new int[]{2,3,4,3,2})==3,"Wi-Fi local não força reserva grande");
  check(RoomQuality.delayFrames(new int[]{40,55,60,92,80})>=5,"oscilação aumenta reserva");
  check(HudGeometry.visible(6,400,25,30,45)==55,"controle respeita o recorte à esquerda");
  check(HudGeometry.visible(390,400,25,30,45)==330,"controle respeita o gesto à direita");
  float leftPad=HudGeometry.leftCluster(1600,307,125,40,2);
  float faceRadius=HudGeometry.faceRadius(55,190,610,2);
  float faceStep=HudGeometry.faceStep(1600,1293,faceRadius,80,2);
  float faceCenter=HudGeometry.rightCluster(1600,1293,faceRadius,80,2);
  check(leftPad>=165&&leftPad+125<=307,"direcional fica à esquerda da imagem na tela de exemplo");
  check(faceRadius==55&&faceCenter-faceStep-faceRadius>=0&&faceCenter+faceStep+faceRadius<=1520,
    "diamante X/Y/A/B conserva o tamanho escolhido e respeita a borda segura");
  float hit=HudGeometry.faceHitRadius(faceRadius,faceStep,2);
  check(faceStep*1.4142f>2*faceRadius&&faceStep*1.4142f>2*hit,
    "círculos e áreas de toque dos botões vizinhos não se encavalam");
  // Dimensões e recorte aproximados da captura enviada: 1536x709, vídeo até x=1240.
  float photoRadius=HudGeometry.faceRadius(47,186,610,2);
  float photoStep=HudGeometry.faceStep(1536,1240,photoRadius,80,2);
  float photoCenter=HudGeometry.rightCluster(1536,1240,photoRadius,80,2);
  float photoY=HudGeometry.faceCenterY(709,186,610,photoRadius,photoStep,2);
  check(photoCenter-photoStep-photoRadius>=0&&photoCenter+photoStep+photoRadius<=1456,
    "preset respeita a área de gesto mesmo quando flutua sobre a imagem");
  float flippedCenter=HudGeometry.rightCluster(1536,1240,photoRadius,0,2);
  check(photoRadius==47&&flippedCenter+photoStep+photoRadius<=1536&&
        Math.abs((flippedCenter-photoCenter)-80)<.001f,
    "virar o telefone troca a margem segura, sem encolher os botões");
  check(photoY-photoStep-photoRadius>186&&photoY+photoStep+photoRadius<610,
    "X e B não encostam nos botões de ombro e Start");
  check(photoStep*1.4142f>2*HudGeometry.faceHitRadius(photoRadius,photoStep,2),
    "toques diagonais na captura alcançam cada botão separadamente");
  float compactRadius=HudGeometry.faceVerticalRadius(190,360,2);
  float compactStep=HudGeometry.faceStep(780,540,compactRadius,40,2);
  float compactCenter=HudGeometry.faceCenterY(400,190,360,compactRadius,compactStep,2);
  check(compactCenter-compactStep-compactRadius>=206&&
        compactCenter+compactStep+compactRadius<=344,
    "preset curto conserva folga vertical para R e Start");
  check(HudGeometry.faceRadius(47,186,610,2)==47,
    "zoom sem lateral mantém o tamanho dos botões flutuantes");
  float full=VideoGeometry.aspect(4f/3f,256,224,256,224);
  float topHalf=VideoGeometry.aspect(4f/3f,256,224,256,112);
  check(Math.abs(full-4f/3f)<.001f&&Math.abs(topHalf-8f/3f)<.001f,
    "tela dividida preserva proporção do SNES");
  float crop=VideoGeometry.cropHeightFraction(4f/3f,20f/9f);
  check(Math.abs(crop-.6f)<.001f&&VideoGeometry.cropWidthFraction(4f/3f,20f/9f)==1f,
    "zoom até bordas corta altura sem esticar a imagem");
  check(VideoGeometry.cropHeightFraction(4f/3f,4f/3f)==1f,
    "imagem 4:3 inteira em tela 4:3");
  check("Vidas infinitas".equals(CheatText.known("Infinite Lives")),"tradução frequente de cheat");
  check("P2 · Pontos de vida (HP) infinitos".equals(CheatText.known("P2: Infinite Health")),
    "tradução de cheat mantém identificação do jogador");
  check(CheatText.known("Hold L + R at the title screen")==null,
    "instruções específicas ficam no original sem tradução segura");
  check(CheatText.alreadyPortuguese("Vidas infinitas")&&!CheatText.alreadyPortuguese("Infinite Lives"),
    "catálogo já em português não é retraduzido como inglês");
  check(!CheatText.alreadyPortuguese("Pokémon - Infinite Lives"),
    "nome próprio com acento não bloqueia a tradução inglesa");
  check("Pontos de vida (HP) máximos".equals(CheatText.known("Max HP"))&&
        "Ter chicote aprimorado #1".equals(CheatText.known("Have Enhanced Whip #1"))&&
        CheatText.known("Get Zircon Armor When Unequip Item")==null,
    "itens desconhecidos passam ao tradutor sem deixar Armor em inglês");
  check(CheatText.known("Titanium Wings / Moon Blessing") == null,"não inventa tradução para item desconhecido");
  check(CheatText.known("Have Infinite Lives")==null&&
        CheatText.known("Infinite HP (Hold L + R at the title screen)")==null&&
        CheatText.known("Max HP - Hold L + R at the title screen")==null,
    "regras locais não encerram frases ainda parcialmente em inglês");
  check("Pontos de vida (HP) máximos — Personagem".equals(CheatText.known("Max HP - Character"))&&
        "Alterar experiência (EXP)".equals(CheatText.known("Experience Modifer"))&&
        "Pontos de vida (HP) infinitos".equals(CheatText.known("Infinite HP")),
    "efeitos compostos, abreviações e erro de digitação comum são traduzidos");
  check("Pontos de vida (HP) máximos (Receba um golpe para ativar)".equals(
        CheatText.known("Max HP (Get Hit To Enable)")),"instrução importante preservada e traduzida");
  check("Muita experiência (EXP) (Lute uma batalha e chegue até o nível 80)".equals(
        CheatText.known("Lots of Exp. (Fight One Battle And Get Up To Level 80)"))&&
        "Dinheiro em mãos infinito (Valor acima de 9999999 travará o jogo)".equals(
        CheatText.known("Infinite Money On Hand (A Value Above 9999999 Will Crash The Game)")),
    "descrições compostas preservam requisitos e alertas do jogo");
  check("Pontos de vida (HP) máximos para personagem 1".equals(
        CheatText.known("Max HP for Character 1")),"qualificador de personagem traduzido");
  String template=CheatCode.normalize("7E1234??+7E5678:XX",true);
  check("7E1234??+7E5678:??".equals(template)&&CheatCode.slots(template)==2,
    "duas posições de valor reconhecidas e XX normalizado");
  String complete=CheatCode.resolve(template,new String[]{"0a","ff"});
  check("7E12340A+7E5678:FF".equals(complete)&&CheatCode.matches(template,complete)&&
        "FF".equals(CheatCode.value(template,complete,1)),"valor escolhido vira código completo validado");
  check(CheatCode.resolve(template,new String[]{"0A",null})==null&&
        CheatCode.normalize("7E12??01",true)==null&&CheatCode.normalize("7E1234??",false)==null,
    "código incompleto ou placeholder no endereço não pode ser ativado");
  check("AB47-0915".equals(CheatCode.resolve("??47-0915",new String[]{"AB"})),
    "byte variável do Game Genie só é enviado após seleção");
  check(CheatCode.conflicts("7E123401+7E222202","7E1234FF")&&
        CheatCode.conflicts("7E123401+7E222202","7E222202")&&
        !CheatCode.conflicts("7E123401+7E222202","7E333303"),
    "códigos compostos não duplicam gravações no mesmo endereço");
  System.out.println("ProjectSmokeTest OK: backup, replay, rede, HUD e tradução conservadora.");
 }
}
