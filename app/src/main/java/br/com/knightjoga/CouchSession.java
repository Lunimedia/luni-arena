package br.com.knightjoga;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

/** Shared-screen LAN mode: guests send controllers only; the host runs the SNES core. */
public final class CouchSession implements Closeable {
 public static final int PORT=35672,BEACON=35673;
 public interface Listener { void changed(String detail); }
 public interface DiscoveryListener { void found(String address,int port); void finished(); }
 private final boolean host;
 private final Listener listener;
 private final AtomicIntegerArray masks=new AtomicIntegerArray(5);
 private final AtomicLongArray touched=new AtomicLongArray(5);
 private final AtomicIntegerArray rewindVotes=new AtomicIntegerArray(5);
 private final HashMap<Integer,Socket> players=new HashMap<>();
 private volatile boolean running=true;
 private ServerSocket server;
 private Socket client;
 private DataInputStream input;
 private DataOutputStream output;
 private int seat;
 private volatile int assistantSeat;
 private volatile boolean hostRewindVote;
 private CouchSession(boolean host,Listener listener){this.host=host;this.listener=listener;}
 public static CouchSession host(Listener listener)throws IOException{
  CouchSession session=new CouchSession(true,listener);
  session.server=new ServerSocket();session.server.setReuseAddress(true);
  session.server.bind(new InetSocketAddress(PORT));
  new Thread(session::accept,"knight-couch-accept").start();
  new Thread(session::advertise,"knight-couch-beacon").start();
  return session;
 }
 public static CouchSession join(String address,int port,Listener listener)throws IOException{
  if(address==null||address.trim().isEmpty()||port<1||port>65535)throw new IOException("IP ou porta inválidos.");
  CouchSession session=new CouchSession(false,listener);
  try{
   Socket socket=new Socket();session.client=socket;
   socket.connect(new InetSocketAddress(address.trim(),port),5000);
   socket.setTcpNoDelay(true);socket.setSoTimeout(5000);
   session.output=new DataOutputStream(socket.getOutputStream());
   session.output.writeUTF("LUC1");session.output.flush();
   session.input=new DataInputStream(socket.getInputStream());
   int assigned=session.input.readInt();
   if(assigned<1||assigned>4)throw new IOException("Sala de controles cheia ou indisponível.");
   session.seat=assigned;socket.setSoTimeout(0);
   new Thread(session::receiveAcks,"knight-couch-feedback").start();
   return session;
  }catch(IOException|RuntimeException e){session.close();throw e;}
 }
 private void accept(){
  while(running){
   Socket socket=null;int assigned=0;
   try{
    socket=server.accept();socket.setTcpNoDelay(true);socket.setSoTimeout(5000);
    DataInputStream in=new DataInputStream(socket.getInputStream());
    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
    if(!"LUC1".equals(in.readUTF())){out.writeInt(-1);out.flush();continue;}
    synchronized(players){for(int i=1;i<=4;i++)if(!players.containsKey(i)){assigned=i;players.put(i,socket);break;}}
    out.writeInt(assigned==0?-1:assigned);out.flush();
    if(assigned==0)continue;
    final int slot=assigned;final Socket peer=socket;
    listener.changed("Controle P"+(slot+1)+" conectado · "+count()+"/5 jogadores");
    new Thread(()->receive(slot,peer),"knight-couch-P"+(slot+1)).start();
   }catch(IOException e){if(running)listener.changed("Controle: "+e.getMessage());}
   finally{if(assigned==0&&socket!=null)try{socket.close();}catch(IOException ignored){}}
  }
 }
 private void receive(int slot,Socket socket){
  try{
   socket.setSoTimeout(3000);
   DataInputStream in=new DataInputStream(socket.getInputStream());
   while(running){
    int value=in.readInt();
    if(value==-1||value==-2){
     rewindVotes.set(slot,value==-1?1:0);
     listener.changed("Máquina do tempo · "+voteCount()+"/"+count()+" votos");
     continue;
    }
    if((value&~0xfff)!=0)throw new IOException("Entrada inválida.");
    masks.set(slot,value);touched.set(slot,System.nanoTime());
   }
  }catch(IOException ignored){}
  finally{
   synchronized(players){if(players.get(slot)==socket)players.remove(slot);}
   if(assistantSeat==slot)assistantSeat=0;
   masks.set(slot,0);touched.set(slot,0);
   rewindVotes.set(slot,0);
   try{socket.close();}catch(IOException ignored){}
   if(running)listener.changed("P"+(slot+1)+" saiu · "+count()+"/5 jogadores");
  }
 }
 public int count(){synchronized(players){return players.size()+1;}}
 public int seat(){return seat;}
 /** A guest can share P1 with the console owner; the guest's former slot stays neutral. */
 public boolean setAssistantSeat(int slot){
  if(!host||slot<0||slot>4)return false;
  synchronized(players){if(slot!=0&&!players.containsKey(slot))return false;assistantSeat=slot;}
  listener.changed(slot==0?"Controles separados":"Dupla no P1: controle "+(slot+1));
  return true;
 }
 public int assistantSeat(){return assistantSeat;}
 public int[] inputs(int local){
  if(!host)throw new IllegalStateException("Apenas o console recebe vários controles.");
  int[] current={local,0,0,0,0};long now=System.nanoTime();
  for(int i=1;i<=4;i++)if(now-touched.get(i)<500_000_000L)current[i]=masks.get(i);
  int helper=assistantSeat;
  if(helper>=1&&helper<=4){current[0]|=current[helper];current[helper]=0;
   if((current[0]&(1<<4|1<<5))==(1<<4|1<<5))current[0]&=~(1<<4|1<<5);
   if((current[0]&(1<<6|1<<7))==(1<<6|1<<7))current[0]&=~(1<<6|1<<7);
  }
  return current;
 }
 public void send(int mask)throws IOException{
  if(host||!running||output==null)throw new IOException("Controle desconectado.");
  synchronized(output){output.writeInt(mask&0xfff);output.flush();}
 }
 public void voteRewind(boolean enabled)throws IOException{
  if(host||!running||output==null)throw new IOException("Controle desconectado.");
  synchronized(output){output.writeInt(enabled?-1:-2);output.flush();}
 }
 public void voteHostRewind(){if(host)hostRewindVote=true;}
 public int voteCount(){
  int count=hostRewindVote?1:0;
  synchronized(players){for(int seat:players.keySet())count+=rewindVotes.get(seat);}
  return count;
 }
 public boolean consumeUnanimousVote(){
  if(!host||!hostRewindVote)return false;
  synchronized(players){
   if(players.isEmpty())return false;
   for(int seat:players.keySet())if(rewindVotes.get(seat)==0)return false;
   hostRewindVote=false;
   for(int seat:players.keySet())rewindVotes.set(seat,0);
   return true;
  }
 }
 /** Notify connected controllers only after the emulator actually restores a frame. */
 public void ackRewind(){
  notifyGuests(-3);
 }
 /** Clear votes while preserving all controller sockets for the next ROM. */
 public void nextGame(){
  if(!host)return;
  hostRewindVote=false;
  for(int i=1;i<=4;i++)rewindVotes.set(i,0);
  notifyGuests(-4);
 }
 private void notifyGuests(int code){
  if(!host)return;
  new Thread(()->{
   ArrayList<Socket> targets;
   synchronized(players){targets=new ArrayList<>(players.values());}
   for(Socket peer:targets)try{
    synchronized(peer){DataOutputStream response=new DataOutputStream(peer.getOutputStream());
     response.writeInt(code);response.flush();}
   }catch(IOException ignored){}
  },"knight-couch-rewind-feedback").start();
 }
 private void receiveAcks(){
  try{while(running&&client!=null){
   int message=input.readInt();
   if(message==-3)listener.changed("rewind");
   if(message==-4)listener.changed("next");
  }}catch(IOException ignored){}
 }
 private void advertise(){
  try(DatagramSocket udp=new DatagramSocket()){
   udp.setBroadcast(true);byte[] data=("LUNICO1:"+PORT).getBytes(StandardCharsets.US_ASCII);
   while(running){
    try{
     DatagramPacket packet=new DatagramPacket(data,data.length,InetAddress.getByName("255.255.255.255"),BEACON);
     udp.send(packet);
    }catch(IOException ignored){}
    Thread.sleep(950);
   }
  }catch(Exception ignored){}
 }
 public static void discover(DiscoveryListener listener){
  new Thread(()->{
   Set<String> seen=new HashSet<>();
   try(DatagramSocket udp=new DatagramSocket(null)){
    udp.setReuseAddress(true);udp.bind(new InetSocketAddress(BEACON));udp.setSoTimeout(650);
    long until=System.nanoTime()+7_000_000_000L;
    while(System.nanoTime()<until){
     byte[] bytes=new byte[80];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
     try{udp.receive(packet);}catch(SocketTimeoutException ignored){continue;}
     String offer=new String(bytes,0,packet.getLength(),StandardCharsets.US_ASCII);
     if(!offer.startsWith("LUNICO1:"))continue;
     try{
      int port=Integer.parseInt(offer.substring("LUNICO1:".length()));
      if(port<1||port>65535)continue;
      String address=packet.getAddress().getHostAddress();
      if(seen.add(address+":"+port))listener.found(address,port);
     }catch(NumberFormatException ignored){}
    }
   }catch(IOException ignored){}finally{listener.finished();}
  },"knight-couch-discover").start();
 }
 @Override public void close(){
  running=false;
  try{if(server!=null)server.close();}catch(IOException ignored){}
  try{if(client!=null)client.close();}catch(IOException ignored){}
  synchronized(players){for(Socket peer:players.values())try{peer.close();}catch(IOException ignored){}players.clear();}
  for(int i=1;i<=4;i++){masks.set(i,0);touched.set(i,0);}
  hostRewindVote=false;for(int i=1;i<=4;i++)rewindVotes.set(i,0);
  assistantSeat=0;
 }
}
