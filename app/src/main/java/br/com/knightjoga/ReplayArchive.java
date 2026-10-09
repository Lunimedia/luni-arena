package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;
import java.util.zip.CRC32;

/** Luni Replay: ROM-free input recording with initial core state and optional solo ghosts. */
public final class ReplayArchive {
 // Reserved outside the 12 SNES buttons: a host-requested console reset on this frame.
 public static final int RESET_FLAG=1<<16;
 private static final int MAGIC=0x4b4a5231, MAX_STATE=16*1024*1024, MAX_FRAMES=216000;
 // Format 3 stores the controller-port mode; format 2 remains readable with its original Multitap default.
 private static final int FORMAT_VERSION=3, APP_VERSION=29, MIN_COMPAT_VERSION=17;
 private ReplayArchive(){}
 public static File file(File data,String hash){return new File(new File(data,"replays"),hash+".kjr");}
 public static final class Recorder {
  private final String hash;
  private final String coreHash;
  private final byte[] initial;
  private final boolean multitapEnabled;
  private final ByteArrayOutputStream frames=new ByteArrayOutputStream();
  private final DataOutputStream writer=new DataOutputStream(frames);
  private int count;
  public Recorder(String hash,String coreHash,byte[] state)throws IOException{this(hash,coreHash,state,true);}
  public Recorder(String hash,String coreHash,byte[] state,boolean multitapEnabled)throws IOException{
   if(hash==null||!hash.matches("[0-9a-f]{64}")||
       coreHash==null||!coreHash.matches("[0-9a-f]{64}")||state==null||state.length<128||state.length>MAX_STATE)
    throw new IOException("Núcleo não forneceu um estado para gravar o replay.");
   this.hash=hash;this.coreHash=coreHash;initial=state;this.multitapEnabled=multitapEnabled;
  }
  public int count(){return count;}
  public boolean append(int[] masks)throws IOException{
   if(masks==null||masks.length!=5||count>=MAX_FRAMES)return false;
   for(int i=0;i<masks.length;i++)
    if((masks[i]&~(0xfff|(i==0?RESET_FLAG:0)))!=0)
     throw new IOException("Entrada inválida no replay.");
   for(int mask:masks)writer.writeInt(mask);
   count++;return true;
  }
  public void save(File data)throws IOException{
   if(count<1)throw new IOException("Nenhum quadro foi gravado.");
   File destination=file(data,hash),dir=destination.getParentFile();
   if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sem acesso à pasta de replays.");
   File temp=File.createTempFile(".knight-replay-",".tmp",dir);
   try{
    byte[] input=frames.toByteArray();CRC32 crc=new CRC32();crc.update(multitapEnabled?1:0);crc.update(initial);crc.update(input);
    try(FileOutputStream stream=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(stream)){
     out.writeInt(MAGIC);out.writeInt(FORMAT_VERSION);out.writeInt(APP_VERSION);
     out.writeUTF(hash);out.writeUTF(coreHash);
     out.writeBoolean(multitapEnabled);
     out.writeInt(initial.length);out.write(initial);out.writeInt(count);out.write(input);
     out.writeLong(crc.getValue());out.flush();stream.getFD().sync();
    }
    try{Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
   }finally{temp.delete();}
  }
 }
 public static final class Player {
  public final byte[] initial;
  public final int count;
  public final boolean multitapEnabled;
  private final byte[] frames;
  private int cursor;
  private final boolean remoteActive;
  private Player(byte[] initial,byte[] frames,int count,boolean multitapEnabled){
   this.initial=initial;this.frames=frames;this.count=count;this.multitapEnabled=multitapEnabled;
   boolean found=false;
   for(int frame=0;frame<count&&!found;frame++)for(int offset=frame*20+4;offset<frame*20+20;offset++)
    if(frames[offset]!=0){found=true;break;}
   remoteActive=found;
  }
  public int progress(){return cursor;}
  public void rewind(){cursor=0;}
  public boolean hasRemoteInputs(){return remoteActive;}
  /** A recorded solo P1 becomes P2; recordings with guests retain their P2–P5 slots. */
  public int[] ghostInputs(int local){
   int[] result=new int[]{local,0,0,0,0};
   int[] recorded=next();
   if(recorded==null)return result;
   if((recorded[0]&RESET_FLAG)!=0)result[0]|=RESET_FLAG;
   if(remoteActive)System.arraycopy(recorded,1,result,1,4);
   else result[1]=recorded[0]&~RESET_FLAG;
   return result;
  }
  public int[] next(){
   if(cursor>=count)return null;
   int[] masks=new int[5];int offset=cursor++*20;
   for(int i=0;i<5;i++){masks[i]=((frames[offset]&255)<<24)|((frames[offset+1]&255)<<16)|
       ((frames[offset+2]&255)<<8)|(frames[offset+3]&255);offset+=4;}
   return masks;
  }
 }
 public static Player load(File data,String expectedHash,String expectedCore)throws IOException{
  if(expectedHash==null||!expectedHash.matches("[0-9a-f]{64}"))throw new IOException("ROM inválida.");
  if(expectedCore==null||!expectedCore.matches("[0-9a-f]{64}"))throw new IOException("Núcleo inválido.");
  return read(file(data,expectedHash),expectedHash,expectedCore);
 }
 public static void exportReplay(File data,String hash,String coreHash,OutputStream output)throws IOException{
  if(coreHash==null||!coreHash.matches("[0-9a-f]{64}"))throw new IOException("Núcleo inválido.");
  File replay=file(data,hash);read(replay,hash,coreHash);
  try(InputStream input=new FileInputStream(replay)){
   byte[] block=new byte[32768];int n;while((n=input.read(block))!=-1)output.write(block,0,n);
  }
 }
 public static void importReplay(File data,String hash,String coreHash,InputStream input)throws IOException{
  if(hash==null||!hash.matches("[0-9a-f]{64}")||coreHash==null||!coreHash.matches("[0-9a-f]{64}"))
   throw new IOException("ROM ou núcleo inválido.");
  File destination=file(data,hash),dir=destination.getParentFile();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Sem acesso à pasta de replays.");
  File temp=File.createTempFile(".knight-import-",".tmp",dir);
  try{
   try(FileOutputStream output=new FileOutputStream(temp)){
    byte[] block=new byte[32768];int n;long length=0;
    while((n=input.read(block))!=-1){length+=n;
     if(length>MAX_STATE+MAX_FRAMES*20L+256)throw new IOException("Replay grande demais.");
     output.write(block,0,n);
    }
    output.getFD().sync();
   }
   read(temp,hash,coreHash);
   try{Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  }finally{temp.delete();}
 }
 private static Player read(File replay,String expectedHash,String expectedCore)throws IOException{
  if(!replay.isFile()||replay.length()<128||replay.length()>MAX_STATE+MAX_FRAMES*20L+256)
   throw new IOException("Ainda não há replay compatível salvo para este jogo.");
  try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(replay)))){
   int magic=in.readInt(),format=in.readInt();
   if(magic!=MAGIC||(format!=2&&format!=FORMAT_VERSION))
    throw new IOException("Replay de outra versão do aplicativo.");
   int version=in.readInt();
   if(version<MIN_COMPAT_VERSION||version>APP_VERSION||!expectedHash.equals(in.readUTF()))
    throw new IOException("Replay de outra ROM ou versão do aplicativo.");
   String core=in.readUTF();
   if(!core.matches("[0-9a-f]{64}")||expectedCore!=null&&!expectedCore.equals(core))
    throw new IOException("Replay gravado com outro núcleo SNES. Instale o mesmo APK nos aparelhos.");
   boolean multitapEnabled=format==2||in.readBoolean();
   int size=in.readInt();if(size<128||size>MAX_STATE)throw new IOException("Estado inicial do replay inválido.");
   byte[] state=new byte[size];in.readFully(state);
   int count=in.readInt();if(count<1||count>MAX_FRAMES)throw new IOException("Replay longo demais.");
   byte[] frames=new byte[count*20];in.readFully(frames);
   CRC32 crc=new CRC32();if(format>=3)crc.update(multitapEnabled?1:0);crc.update(state);crc.update(frames);
   if(in.readLong()!=crc.getValue()||in.read()!=-1)throw new IOException("Replay incompleto ou alterado.");
   for(int offset=0;offset<frames.length;offset+=4){
    int mask=((frames[offset]&255)<<24)|((frames[offset+1]&255)<<16)|
      ((frames[offset+2]&255)<<8)|(frames[offset+3]&255);
    if((mask&~(0xfff|(offset%20==0?RESET_FLAG:0)))!=0)
     throw new IOException("Replay contém entradas inválidas.");
   }
   return new Player(state,frames,count,multitapEnabled);
  }
 }
}
