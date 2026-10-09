package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;

/** Local tournament record. Results are confirmed by people, never guessed from SNES pixels. */
public final class ChallengeLedger {
 private static final int MAGIC=0x4b4a4331,VERSION=1;
 private final int[] wins=new int[5];
 private int rounds,firstTo=3;
 private ChallengeLedger(){}
 public static File file(File data,String hash){
  if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("ROM inválida");
  return new File(new File(data,"challenges"),hash+".kjc");
 }
 public static ChallengeLedger load(File data,String hash)throws IOException{
  File file=file(data,hash);ChallengeLedger board=new ChallengeLedger();
  if(!file.isFile())return board;
  if(file.length()!=36)throw new IOException("Placar inválido.");
  try(DataInputStream in=new DataInputStream(new FileInputStream(file))){
   if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Placar de outra versão.");
   board.rounds=in.readInt();board.firstTo=in.readInt();
   if(board.rounds<0||board.rounds>100000||board.firstTo<1||board.firstTo>20)
    throw new IOException("Placar fora dos limites.");
   int total=0;
   for(int i=0;i<5;i++){
    board.wins[i]=in.readInt();
    if(board.wins[i]<0||board.wins[i]>board.rounds)throw new IOException("Placar inconsistente.");
    total+=board.wins[i];
   }
   if(total!=board.rounds)throw new IOException("Placar inconsistente.");
  }
  return board;
 }
 public int rounds(){return rounds;}
 public int firstTo(){return firstTo;}
 public int wins(int player){return wins[player];}
 public int winner(){for(int i=0;i<5;i++)if(wins[i]>=firstTo)return i;return -1;}
 public void setFirstTo(int value){if(value<1||value>20)throw new IllegalArgumentException();firstTo=value;}
 public void award(int player){
  if(player<0||player>=5||rounds>=100000||winner()>=0)throw new IllegalStateException("Torneio encerrado.");
  wins[player]++;rounds++;
 }
 public void reset(){for(int i=0;i<5;i++)wins[i]=0;rounds=0;}
 public void save(File data,String hash)throws IOException{
  File target=file(data,hash),dir=target.getParentFile();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sem acesso à pasta do placar.");
  File temp=File.createTempFile(".knight-score-",".tmp",dir);
  try{
   try(FileOutputStream stream=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(stream)){
    out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(rounds);out.writeInt(firstTo);
    for(int score:wins)out.writeInt(score);
    out.flush();stream.getFD().sync();
   }
   try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  }finally{temp.delete();}
 }
}
