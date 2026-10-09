package br.com.knightjoga;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Portable save bundle. ROM bytes are never exported and imports are bound to their ROM hash. */
public final class SaveArchive {
 // Retain the older ZIP marker so manually exported SRAM backups remain readable.
 private static final String MARKER="KnightJogaSaves/1\n";
 private static final int STATE_MAX=16*1024*1024, RAM_MAX=2*1024*1024;
 private SaveArchive(){}
 private static void checkHash(String hash)throws IOException{
  if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IOException("Identificação da ROM inválida.");
 }
 private static File target(File data,String hash,String entry){
  return entry.equals("save.srm")?new File(data,hash+".srm"):
   new File(new File(data,"states"),hash+"-"+entry.charAt(5)+".state");
 }
 private static int max(String entry){return entry.equals("save.srm")?RAM_MAX:STATE_MAX;}
 private static boolean allowed(String name){
  return "save.srm".equals(name)||name.matches("state[1-5]\\.bin");
 }
 public static boolean hasFiles(File data,String hash){
  if(hash==null||!hash.matches("[0-9a-f]{64}"))return false;
  for(String entry:new String[]{"save.srm","state1.bin","state2.bin","state3.bin","state4.bin","state5.bin"})
   if(target(data,hash,entry).isFile())return true;
  return false;
 }
 public static int exportGame(File data,String hash,OutputStream output)throws IOException{
  checkHash(hash);ArrayList<String> available=new ArrayList<>();
  for(String entry:new String[]{"save.srm","state1.bin","state2.bin","state3.bin","state4.bin","state5.bin"}){
   File file=target(data,hash,entry);
   if(file.isFile()&&file.length()>0&&file.length()<=max(entry))available.add(entry);
  }
  if(available.isEmpty())throw new IOException("Este jogo ainda não tem save ou estado para exportar.");
  ZipOutputStream zip=new ZipOutputStream(output);
  zip.putNextEntry(new ZipEntry("manifest.txt"));
  zip.write((MARKER+hash+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));zip.closeEntry();
  for(String name:available){
   zip.putNextEntry(new ZipEntry(name));
   try(InputStream in=new FileInputStream(target(data,hash,name))){byte[] block=new byte[32768];int n;
    while((n=in.read(block))!=-1)zip.write(block,0,n);
   }
   zip.closeEntry();
  }
  zip.finish();return available.size();
 }
 public static int importGame(File data,String hash,InputStream input)throws IOException{
  checkHash(hash);if(!data.isDirectory()&&!data.mkdirs())throw new IOException("Sem espaço para importar saves.");
  File staging=Files.createTempDirectory(data.toPath(),".knight-import-").toFile();
  HashSet<String> present=new HashSet<>();String manifest=null;long total=0;
  try{
   try(ZipInputStream zip=new ZipInputStream(input)){
    ZipEntry entry;int count=0;
    while((entry=zip.getNextEntry())!=null){
     String name=entry.getName();
     if(entry.isDirectory()||!(allowed(name)||"manifest.txt".equals(name))||!present.add(name)||++count>7)
      throw new IOException("Arquivo de backup contém entradas inesperadas ou repetidas.");
     int limit=name.equals("manifest.txt")?128:max(name),size=0;
     File candidate=new File(staging,name);
     try(OutputStream out=new FileOutputStream(candidate)){
      byte[] block=new byte[32768];int n;
      while((n=zip.read(block))!=-1){size+=n;total+=n;
       if(size>limit||total>5L*STATE_MAX+RAM_MAX+128)throw new IOException("Backup grande demais: "+name);
       out.write(block,0,n);
      }
     }
     if(size==0)throw new IOException("Entrada vazia: "+name);
     if(name.equals("manifest.txt"))manifest=new String(Files.readAllBytes(candidate.toPath()),java.nio.charset.StandardCharsets.US_ASCII);
     zip.closeEntry();
    }
   }
   if(!(MARKER+hash+"\n").equals(manifest))throw new IOException("Este backup pertence a outra ROM ou é incompatível.");
   present.remove("manifest.txt");if(present.isEmpty())throw new IOException("Backup sem saves.");
   // Every entry is validated before any existing save is replaced.
   for(String name:present){
    File destination=target(data,hash,name),folder=destination.getParentFile();
    if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Sem acesso à pasta de saves.");
    try{Files.move(new File(staging,name).toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
    catch(AtomicMoveNotSupportedException e){Files.move(new File(staging,name).toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);}
   }
   return present.size();
  }finally{
   File[] staged=staging.listFiles();if(staged!=null)for(File item:staged)item.delete();
   staging.delete();
  }
 }
}
