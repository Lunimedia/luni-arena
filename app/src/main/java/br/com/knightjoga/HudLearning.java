package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;

/** Small local touch summary used for suggestions after a match, never for live movement. */
public final class HudLearning {
 private static final int MAGIC=0x4b4a4831,VERSION=1,CONTROLS=9,MAX_SAMPLES=100000;
 private final int[] count=new int[CONTROLS],edge=new int[CONTROLS];
 private final long[] sumX=new long[CONTROLS],sumY=new long[CONTROLS];
 public synchronized void observe(int index,float dx,float dy){
  if(index<0||index>=CONTROLS||!Float.isFinite(dx)||!Float.isFinite(dy)||count[index]>=MAX_SAMPLES)return;
  dx=Math.max(-1.3f,Math.min(1.3f,dx));dy=Math.max(-1.3f,Math.min(1.3f,dy));
  count[index]++;sumX[index]+=Math.round(dx*1000);sumY[index]+=Math.round(dy*1000);
  if(Math.hypot(dx,dy)>.7f)edge[index]++;
 }
 public synchronized int count(int index){return count[index];}
 public synchronized String advice(int index){
  if(index<0||index>=CONTROLS)return "Controle desconhecido";
  if(count[index]<20)return "Jogue mais um pouco para analisar os toques";
  float x=(float)sumX[index]/(count[index]*1000f);
  float y=(float)sumY[index]/(count[index]*1000f);
  if(edge[index]*3>count[index])return "Muitos toques na borda · experimente aumentar o botão";
  if(Math.abs(x)>.22f||Math.abs(y)>.22f){
   String horizontal=Math.abs(x)>.22f?(x>0?"direita":"esquerda"):"";
   String vertical=Math.abs(y)>.22f?(y>0?"baixo":"cima"):"";
   return "Seu dedo chega mais "+(horizontal.isEmpty()?"":"à "+horizontal)+
    (horizontal.isEmpty()||vertical.isEmpty()?"":" e ")+(vertical.isEmpty()?"":"para "+vertical)+
    " · ajuste a posição no editor";
  }
  return "Boa posição para os seus toques";
 }
 public static File file(File data,String hash){
  if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("ROM inválida");
  return new File(new File(data,"touch-profiles"),hash+".kjh");
 }
 public static HudLearning load(File data,String hash)throws IOException{
  File file=file(data,hash);HudLearning result=new HudLearning();
  if(!file.isFile())return result;
  if(file.length()!=8+CONTROLS*24)throw new IOException("Perfil de toques inválido.");
  try(DataInputStream input=new DataInputStream(new FileInputStream(file))){
   if(input.readInt()!=MAGIC||input.readInt()!=VERSION)throw new IOException("Perfil de outra versão.");
   for(int i=0;i<CONTROLS;i++){
    result.count[i]=input.readInt();result.edge[i]=input.readInt();
    result.sumX[i]=input.readLong();result.sumY[i]=input.readLong();
    if(result.count[i]<0||result.count[i]>MAX_SAMPLES||result.edge[i]<0||result.edge[i]>result.count[i]||
      Math.abs(result.sumX[i])>1300L*result.count[i]||Math.abs(result.sumY[i])>1300L*result.count[i])
     throw new IOException("Perfil de toques inconsistente.");
   }
  }
  return result;
 }
 public synchronized void save(File data,String hash)throws IOException{
  File target=file(data,hash),dir=target.getParentFile();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sem acesso aos ajustes.");
  File temp=File.createTempFile(".knight-touch-",".tmp",dir);
  try{
   try(FileOutputStream file=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(file)){
    out.writeInt(MAGIC);out.writeInt(VERSION);
    for(int i=0;i<CONTROLS;i++){
     out.writeInt(count[i]);out.writeInt(edge[i]);out.writeLong(sumX[i]);out.writeLong(sumY[i]);
    }
    out.flush();file.getFD().sync();
   }
   try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  }finally{temp.delete();}
 }
}
