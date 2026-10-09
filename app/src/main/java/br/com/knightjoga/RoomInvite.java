package br.com.knightjoga;

import java.io.IOException;

/** Share a literal address, without DNS or tracking links. */
public final class RoomInvite {
 public final String address;
 public final int port;
 private RoomInvite(String address,int port){this.address=address;this.port=port;}
 public static RoomInvite parse(String raw)throws IOException{
  if(raw==null)throw new IOException("Convite vazio.");
  String value=raw.trim();
  int embedded=value.indexOf("luniarena://");
  if(embedded>0){value=value.substring(embedded);
   int end=0;while(end<value.length()&&!Character.isWhitespace(value.charAt(end)))end++;
   value=value.substring(0,end);
  }
  if(value.startsWith("luniarena://"))value=value.substring("luniarena://".length());
  if(value.length()>24||!value.matches("[0-9.]+:[0-9]{1,5}"))throw new IOException("Use um endereço IPv4:porta.");
  int colon=value.lastIndexOf(':'),port;
  try{port=Integer.parseInt(value.substring(colon+1));}catch(NumberFormatException e){throw new IOException("Porta inválida.");}
  String ip=value.substring(0,colon),parts[]=ip.split("\\.",-1);
  if(parts.length!=4||port<1||port>65535)throw new IOException("Endereço ou porta inválidos.");
  for(String part:parts){int n;
   if(part.isEmpty()||part.length()>3||part.length()>1&&part.charAt(0)=='0')throw new IOException("IP inválido.");
   try{n=Integer.parseInt(part);}catch(NumberFormatException e){throw new IOException("IP inválido.");}
   if(n<0||n>255)throw new IOException("IP inválido.");
  }
  return new RoomInvite(ip,port);
 }
 public static String link(String address,int port)throws IOException{
  RoomInvite invite=parse(address+":"+port);
  return "luniarena://"+invite.address+":"+invite.port;
 }
}
