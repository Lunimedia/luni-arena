package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small ROM-free replay marker file bound to exact ROM and emulator binary. */
final class MomentArchive {
 private static final int MAGIC=0x4c554d31,MAX=120;
 static final class Mark {
  final int frame;final String label;
  Mark(int frame,String label){this.frame=frame;this.label=label;}
 }
 private MomentArchive(){}
 static File file(File data,String hash){return new File(new File(data,"replays"),hash+".lum");}
 static void save(File data,String hash,String core,List<Mark> marks)throws IOException{
  validate(hash,core);
  if(marks.size()>MAX)throw new IOException("Muitos marcadores.");
  File dest=file(data,hash),dir=dest.getParentFile();
  if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Pasta de replays indisponível.");
  File temp=File.createTempFile(".luni-moment-",".tmp",dir);
  try{
   try(FileOutputStream stream=new FileOutputStream(temp);DataOutputStream out=new DataOutputStream(stream)){
    out.writeInt(MAGIC);out.writeUTF(hash);out.writeUTF(core);out.writeInt(marks.size());
    int previous=-1;
    for(Mark mark:marks){
     if(mark.frame<0||mark.frame>216000||mark.frame<previous||mark.label==null||mark.label.length()>48)
      throw new IOException("Marcador inválido.");
     previous=mark.frame;out.writeInt(mark.frame);out.writeUTF(mark.label);
    }
    out.flush();stream.getFD().sync();
   }
   try{Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  }finally{temp.delete();}
 }
 static List<Mark> load(File data,String hash,String core)throws IOException{
  validate(hash,core);File source=file(data,hash);
  if(!source.isFile())return Collections.emptyList();
  if(source.length()>16*1024)throw new IOException("Cápsula de momentos inválida.");
  try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(source)))){
   if(in.readInt()!=MAGIC||!hash.equals(in.readUTF())||!core.equals(in.readUTF()))
    throw new IOException("Marcadores de outra ROM ou núcleo.");
   int size=in.readInt();if(size<0||size>MAX)throw new IOException("Lista de marcadores inválida.");
   ArrayList<Mark> list=new ArrayList<>();int previous=-1;
   for(int i=0;i<size;i++){
    int frame=in.readInt();String label=in.readUTF();
    if(frame<0||frame>216000||frame<previous||label.length()>48)
     throw new IOException("Marcador inválido.");
    list.add(new Mark(frame,label));previous=frame;
   }
   if(in.read()!=-1)throw new IOException("Cápsula contém dados extras.");
   return list;
  }
 }
 private static void validate(String hash,String core)throws IOException{
  if(hash==null||core==null||!hash.matches("[0-9a-f]{64}")||!core.matches("[0-9a-f]{64}"))
   throw new IOException("Identidade da ROM ou do núcleo inválida.");
 }
}
