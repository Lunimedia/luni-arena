package br.com.knightjoga;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** LAN room negotiates a matching ROM before deterministic input transport begins. */
public final class LanSession implements Closeable {
 private static final String PROTOCOL="LUNI6";
 private static final int PING=0x48, START=0x53, START_ACK=0x41, RESUME_ACK=0x61;
 private static final int RESUME_MARKER=0x4c554e49;
 public static final int PORT=35671, BEACON=35670;
 public static final int INPUT_DELAY_FRAMES=4;
 private static final int CHECK_EVERY_FRAMES=180;
 private static final int NO_CHECK=-1;
 private static final int MAX_ROM=16*1024*1024;
 public interface Listener { void changed(String message); }
 public interface DiscoveryListener { void found(String address,int port); void finished(); }
 public static final class Joined {
  public final LanSession session;
  public final byte[] bytes;
  public final RomLibrary.Game game;
  public final String format;
  public final boolean downloaded;
  public final boolean resuming;
  Joined(LanSession session,byte[] bytes,RomLibrary.Game game,String format,boolean downloaded,boolean resuming){
   this.session=session;this.bytes=bytes;this.game=game;this.format=format;this.downloaded=downloaded;
   this.resuming=resuming;
  }
 }
 public static final class Resume {
  public final byte[] state;
  public final int frame;
  Resume(byte[] state,int frame){this.state=state;this.frame=frame;}
 }
 public interface SnapshotProvider { byte[] capture()throws IOException; }
 private final String hash;
 private final byte[] rom;
 private final String title,format;
 private String coreHash,standardCoreHash,compatibleCoreHash;
 private final Listener listener;
 private final Peer[] peers=new Peer[4];
 private final String[] seatTokens=new String[4];
 private volatile Peer pendingRejoin;
 private ServerSocket server;
 private Socket client;
 private DataInputStream in;
 private DataOutputStream out;
 private volatile boolean running=true,started=false;
 private volatile int inputDelayFrames=INPUT_DELAY_FRAMES;
 private volatile boolean automaticDelay=true;
 private boolean autoMeasured;
 private int measuredDelayFrames=3;
 private final boolean host;
 private final String token;
 private String address;
 private int remotePort;
 private volatile boolean resuming;
 private volatile boolean useMesen;
 private volatile boolean multitapEnabled=true;
 private volatile int lastVerifiedFrame=-1,lastSampleSentFrame=-1,integrityRepairs=0,
  estimatedRttMs=-1,estimatedJitterMs=-1;
 private volatile boolean integrityWarning;
 private int player=0,frame=0,syncing=0;
 private int pausedAt=-1;
 private boolean snapshotReady;
 private byte[] resumeSnapshot;
 private Thread acceptThread,beaconThread,sendThread,receiveThread;
 private final Object frameLock=new Object();
 private final HashMap<Integer,Input> localInputs=new HashMap<>();
 private final HashMap<Integer,int[]> readyFrames=new HashMap<>();
 private IOException networkError;
 private boolean frameWorkersStarted;
 private volatile int workerGeneration;
 private static class Input {
  final int mask,check;
  Input(int mask,int check){this.mask=mask;this.check=check;}
 }
 private static class Peer { final Socket socket; final DataInputStream in; final DataOutputStream out;
  int seat,rttMs,jitterMs,mismatches; volatile boolean startSent,acknowledged,outOfSync;
  final java.util.concurrent.CountDownLatch watchDone=new java.util.concurrent.CountDownLatch(1);
  Peer(Socket s)throws IOException{socket=s;socket.setTcpNoDelay(true);socket.setSoTimeout(2500);in=new DataInputStream(new BufferedInputStream(s.getInputStream()));out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));}
  void send(int value)throws IOException{out.writeInt(value);out.flush();}
 }
 private LanSession(boolean host,String hash,byte[] rom,String title,String format,String token,Listener listener){
  this.host=host;this.hash=hash;this.rom=rom;this.title=title;this.format=format;this.token=token;this.listener=listener;
 }
 public static LanSession host(byte[] rom,String hash,String title,String format,String coreHash,
                               boolean useMesen,boolean multitapEnabled,Listener listener)throws IOException{
  if(rom==null||rom.length<1||rom.length>MAX_ROM||hash==null||!hash.matches("[0-9a-f]{64}")||
    !RomLibrary.isRom("game."+format)||coreHash==null||!coreHash.matches("[0-9a-f]{64}"))
   throw new IOException("ROM ou núcleo da sala inválido");
  String safeTitle=title==null?"Jogo da sala":title.trim();
  if(safeTitle.isEmpty())safeTitle="Jogo da sala";
  if(safeTitle.length()>80)safeTitle=safeTitle.substring(0,80);
  LanSession l=new LanSession(true,hash,rom,safeTitle,format.toLowerCase(Locale.ROOT),null,listener);
  l.coreHash=coreHash;
  l.useMesen=useMesen;
  l.multitapEnabled=multitapEnabled;
  l.server=new ServerSocket(PORT);l.server.setReuseAddress(true);
  l.acceptThread=new Thread(l::accept,"knight-accept");l.acceptThread.start();
  l.beaconThread=new Thread(l::broadcast,"knight-beacon");l.beaconThread.start();return l;
 }
 public static Joined join(Context context,String address,int port,byte[] localRom,String localHash,
                            String standardCoreHash,String compatibleCoreHash,RomLibrary.Game localGame,
                            BooleanSupplier stillJoining,Listener listener)throws IOException{
  if(address==null||address.isEmpty()||port<1||port>65535)throw new IOException("IP ou porta inválidos");
  if(localRom==null||localHash==null)throw new IOException("Escolha uma ROM antes de entrar.");
  String key="seat_"+address+":"+port+":"+localHash;
  android.content.SharedPreferences prefs=context.getSharedPreferences("lan_tickets",Context.MODE_PRIVATE);
  String saved=prefs.getString(key,null);
  for(int attempt=0;attempt<2;attempt++){
   boolean resume=attempt==0&&saved!=null;
   if(attempt==1&&saved==null)break;
   String ticket=resume?saved:UUID.randomUUID().toString();
   LanSession l=new LanSession(false,localHash,localRom,null,null,ticket,listener);
   l.standardCoreHash=standardCoreHash;l.compatibleCoreHash=compatibleCoreHash;
   l.address=address;l.remotePort=port;
   try{
    String gameFormat=l.connect(stillJoining,resume);
    prefs.edit().putString(key,ticket).apply();
    listener.changed(resume?"Retomando P"+(l.player+1)+"…":"Conectado como P"+(l.player+1));
    return new Joined(l,localRom,localGame,gameFormat,false,resume);
   }catch(IOException|RuntimeException e){l.close();
    if(resume&&e instanceof SeatUnavailableException){prefs.edit().remove(key).apply();continue;}
    throw e;
   }
  }
  throw new IOException("Não foi possível reservar uma vaga nesta sala.");
 }
 private static final class SeatUnavailableException extends IOException {
  SeatUnavailableException(String reason){super(reason);}
 }
 private String connect(BooleanSupplier active,boolean resume)throws IOException{
  Socket s=new Socket();client=s;s.connect(new InetSocketAddress(address,remotePort),5000);
  s.setTcpNoDelay(true);s.setSoTimeout(20000);
  in=new DataInputStream(new BufferedInputStream(s.getInputStream()));out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
  out.writeUTF(PROTOCOL);out.writeByte(resume?1:0);out.writeUTF(token);out.flush();
  int offer=in.readInt();
  if(offer==-4)throw new SeatUnavailableException("Sua vaga anterior não pertence a esta sala.");
  if(offer!=0)throw new IOException(offer==-3?"Versões diferentes do Luni Arena. Instale o mesmo APK nos aparelhos.":
   offer==-2?"A partida já começou. Somente jogadores desta sala podem retomar.":
   offer==-5?"Este jogador já está conectado à sala.":"Sala cheia: todas as vagas estão ocupadas.");
  String roomTitle=in.readUTF(),gameFormat=in.readUTF(),expected=in.readUTF();
  int size=in.readInt();in.readBoolean(); // ROM sharing is disabled
  useMesen=in.readBoolean();multitapEnabled=in.readBoolean();
  String expectedCore=in.readUTF();
  if(roomTitle.length()>80||!RomLibrary.isRom("game."+gameFormat)||
    !expected.matches("[0-9a-f]{64}")||!expectedCore.matches("[0-9a-f]{64}")||
    size<1||size>MAX_ROM)throw new IOException("Dados da sala inválidos");
  if(!hash.equals(expected)||rom.length!=size)
   throw new IOException("A ROM é diferente. Cada jogador deve escolher sua própria cópia exata.");
  String installedCore=useMesen?compatibleCoreHash:standardCoreHash;
  if(installedCore==null||!installedCore.equals(expectedCore))
   throw new IOException("O núcleo de emulação é diferente. Instale o mesmo APK nos aparelhos.");
  coreHash=expectedCore;
  if(!active.getAsBoolean())throw new IOException("Entrada cancelada");
  out.writeByte(0);out.flush();
  if(in.readInt()!=0)throw new IOException("Resposta de sala inválida");
  if(!active.getAsBoolean())throw new IOException("Entrada cancelada");
  out.writeBoolean(true);out.flush();
  for(int i=0;i<5;i++){
   if(in.readUnsignedByte()!=0x51)throw new IOException("Diagnóstico da sala inválido");
   out.writeByte(0x52);out.flush();
  }
  int assigned=in.readInt();
  if(assigned<1||assigned>4||resume&&player!=0&&assigned!=player)throw new IOException("Vaga de jogador inválida");
  estimatedRttMs=in.readInt();estimatedJitterMs=in.readInt();
  if(estimatedRttMs<0||estimatedRttMs>30000||estimatedJitterMs<0||estimatedJitterMs>30000)
   throw new IOException("Medição de conexão inválida");
  player=assigned;resuming=resume;
  s.setSoTimeout(resume?15000:800);
  return gameFormat;
 }
 public int port(){return host?server.getLocalPort():client.getPort();}
 public int player(){return player;}
 public boolean resuming(){return resuming;}
 public boolean compatibilityCore(){return useMesen;}
 public boolean multitapEnabled(){return multitapEnabled;}
 public int lastVerifiedFrame(){return lastVerifiedFrame;}
 public int integrityRepairs(){return integrityRepairs;}
 public boolean integrityWarning(){return integrityWarning;}
 public int inputDelayFrames(){return inputDelayFrames;}
 public boolean automaticDelay(){return automaticDelay;}
 public synchronized void setInputDelayFrames(int frames){
  if(!host||started)return;
  if(frames==0){automaticDelay=true;inputDelayFrames=autoMeasured?measuredDelayFrames:INPUT_DELAY_FRAMES;}
  else if(frames>=3&&frames<=6){automaticDelay=false;inputDelayFrames=frames;}
 }
 public synchronized int count(){int total=1;for(Peer p:peers)if(p!=null)total++;return total;}
 /** 0 = free, 1 = connected, 2 = reserved for a disconnected player. */
 public synchronized int[] seatStates(){int[] states=new int[multitapEnabled?5:2];states[0]=1;
  for(int i=1;i<states.length;i++)states[i]=peers[i-1]!=null?1:seatTokens[i-1]!=null?2:0;
  return states;
 }
 /** A read-only summary of tests that actually completed during the room handshake. */
 public synchronized String diagnosticReport(){
  StringBuilder report=new StringBuilder("Jogo: ").append(title==null?"ROM selecionada":title)
   .append("\nROM SHA-256: ").append(hash.substring(0,12)).append("… · conferida na entrada")
   .append("\nNúcleo: ").append(useMesen?"Mesen-S":"bsnes Mercury Performance")
   .append(" · ").append(coreHash==null?"aguardando":coreHash.substring(0,12)+"…")
   .append("\nMultitap: ").append(multitapEnabled?"ligado (até 5)":"desligado (até 2)")
   .append("\nReserva: ").append(inputDelayFrames).append(" quadros")
   .append("\nConexão: ").append(host?"host TCP "+port():address+":"+remotePort);
  if(host){for(int i=0;i<(multitapEnabled?4:1);i++){
   Peer p=peers[i];report.append("\nP").append(i+2).append(": ");
   if(p!=null)report.append("conferido · RTT ~").append(p.rttMs)
     .append(" ms · variação ").append(p.jitterMs).append(" ms");
   else report.append(seatTokens[i]!=null?"vaga reservada":"livre");
  }}else report.append("\nTeste de conexão: RTT ~").append(estimatedRttMs)
    .append(" ms · variação ").append(estimatedJitterMs).append(" ms");
  report.append("\nIntegridade: ");
  if(integrityWarning)report.append("verificando divergência / recuperação");
  else if(host&&lastVerifiedFrame>=0)report.append("quadros conferidos até ").append(lastVerifiedFrame);
  else if(!host&&lastSampleSentFrame>=0)report.append("amostra enviada até o quadro ").append(lastSampleSentFrame);
  else report.append("aguardando amostras da partida");
  if(integrityRepairs>0)report.append(" · correções ").append(integrityRepairs);
  return report.toString();
 }
 // Preview comercial: não redistribui automaticamente software de terceiros.
 private static String fingerprint(byte[] bytes)throws IOException{
  try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
   StringBuilder hex=new StringBuilder(64);for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
   return hex.toString();
  }catch(Exception e){throw new IOException("Falha ao verificar a ROM",e);}
 }
 private synchronized int emptySeat(){
  for(int i=0;i<(multitapEnabled?4:1);i++)if(peers[i]==null&&seatTokens[i]==null)return i;
  return -1;
 }
 private synchronized int reservedSeat(String ticket){
  for(int i=0;i<(multitapEnabled?4:1);i++)if(ticket.equals(seatTokens[i]))return i;
  return -1;
 }
 private void accept(){while(running){Socket socket=null;Peer peer=null;boolean pending=false,accepted=false;
  try{
   socket=server.accept();Peer p=new Peer(socket);peer=p;socket.setSoTimeout(20000);
   String hello=p.in.readUTF();
   if(!PROTOCOL.equals(hello)){p.send(-3);continue;}
   int mode=p.in.readUnsignedByte();String ticket=p.in.readUTF();
   if((mode!=0&&mode!=1)||ticket.length()>80||ticket.isEmpty()){p.send(-4);continue;}
   int seat;
   synchronized(this){
    seat=mode==1?reservedSeat(ticket):emptySeat();
    int rejection=mode==1?(seat<0?-4:peers[seat]!=null||pendingRejoin!=null?-5:!started?-4:0):
       started?-2:seat<0?-1:0;
    if(rejection!=0){p.send(rejection);continue;}
    syncing++;pending=true;
   }
   p.send(0);
   p.out.writeUTF(title);p.out.writeUTF(format);p.out.writeUTF(hash);
   p.out.writeInt(rom.length);p.out.writeBoolean(false);p.out.writeBoolean(useMesen);
   p.out.writeBoolean(multitapEnabled);p.out.writeUTF(coreHash);p.out.flush();
   if(p.in.readUnsignedByte()!=0){p.send(-2);continue;}p.send(0);
   if(!p.in.readBoolean())continue;
   int[] probes=new int[5];
   for(int i=0;i<probes.length;i++){
    long at=System.nanoTime();p.out.writeByte(0x51);p.out.flush();
    if(p.in.readUnsignedByte()!=0x52)throw new IOException("Diagnóstico não respondeu");
    probes[i]=(int)Math.min(30000,(System.nanoTime()-at+999999)/1000000);
   }
   int[] sorted=probes.clone();Arrays.sort(sorted);
   p.rttMs=sorted[2];p.jitterMs=sorted[4]-sorted[0];
   synchronized(this){
    if(!running||mode==0&&started||mode==1&&!started||seatTokens[seat]!=null&&!ticket.equals(seatTokens[seat])
       ||peers[seat]!=null){p.send(-1);continue;}
    p.seat=seat+1;
    if(mode==0){
     measuredDelayFrames=Math.max(measuredDelayFrames,RoomQuality.delayFrames(probes));autoMeasured=true;
     if(automaticDelay)inputDelayFrames=measuredDelayFrames;
     seatTokens[seat]=ticket;peers[seat]=p;
    }else pendingRejoin=p;
    p.out.writeInt(p.seat);p.out.writeInt(p.rttMs);p.out.writeInt(p.jitterMs);
    p.out.flush();accepted=true;
   }
   if(mode==0){
    socket.setSoTimeout(1200);
    new Thread(()->watchLobby(p),"luni-lobby-P"+(seat+2)).start();
    listener.changed("P"+(seat+2)+" entrou · "+count()+"/"+(multitapEnabled?5:2)+" jogadores");
   }else{
    socket.setSoTimeout(15000);
    synchronized(frameLock){frameLock.notifyAll();}
    listener.changed("P"+(seat+2)+" está sincronizando a partida…");
   }
  }catch(IOException e){if(running)listener.changed("Entrada interrompida: "+e.getMessage());}
  finally{
   if(pending)synchronized(this){syncing--;}
   if(!accepted&&peer!=null&&peer.seat>0)disconnect(peer);
   if(!accepted&&socket!=null)try{socket.close();}catch(IOException ignored){}
  }
 }}
 private boolean owns(Peer peer){synchronized(this){return peers[peer.seat-1]==peer;}}
 private void watchLobby(Peer peer){
  long last=System.nanoTime();
  try{
   while(running&&owns(peer)){
    try{
     int signal=peer.in.readUnsignedByte();
     if(signal==START_ACK&&peer.startSent){peer.acknowledged=true;return;}
     // A ping may already be in flight when the host sends START.
     if(signal!=PING)throw new IOException("Resposta de sala inválida");
     last=System.nanoTime();
    }catch(SocketTimeoutException timeout){
     if(System.nanoTime()-last>45_000_000_000L)throw new IOException("Convidado sem resposta");
    }
   }
  }catch(IOException ignored){}
  finally{
   peer.watchDone.countDown();
   if(!peer.acknowledged)disconnect(peer);
  }
 }
 private void disconnect(Peer peer){
  boolean changed=false;
  synchronized(this){
   int seat=peer.seat-1;
   if(seat>=0&&seat<peers.length&&peers[seat]==peer){
    peers[seat]=null;if(!started||!peer.acknowledged)seatTokens[seat]=null;changed=true;
   }
   if(pendingRejoin==peer){pendingRejoin=null;changed=true;}
  }
  try{peer.socket.close();}catch(IOException ignored){}
  if(changed&&running)listener.changed("P"+(peer.seat+1)+(peer.outOfSync?
   " fora de sincronia · restaurando estado da sala":started&&peer.acknowledged?
   " caiu · vaga reservada para reconexão":" saiu · vaga liberada"));
 }
 private static Set<InetAddress> broadcastAddresses(){
  Set<InetAddress> destinations=new LinkedHashSet<>();
  try{destinations.add(InetAddress.getByName("255.255.255.255"));
   for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces())){
    if(!nic.isUp()||nic.isLoopback())continue;
    for(InterfaceAddress interfaceAddress:nic.getInterfaceAddresses()){
     InetAddress address=interfaceAddress.getBroadcast();
     if(address instanceof Inet4Address)destinations.add(address);
    }
   }
  }catch(Exception ignored){}
  return destinations;
 }
 private void broadcast(){
  try(DatagramSocket sock=new DatagramSocket()){
   sock.setBroadcast(true);
   byte[] packet=("LUNIARENA2:"+port()).getBytes(StandardCharsets.US_ASCII);
   while(running&&!started){
    for(InetAddress address:broadcastAddresses()){
     try{sock.send(new DatagramPacket(packet,packet.length,address,BEACON));}catch(IOException ignored){}
    }
    Thread.sleep(950);
   }
  }catch(Exception ignored){}
 }
 public static final class Discovery implements Closeable {
  private final Context context;
  private final DiscoveryListener listener;
  private volatile boolean searching=true;
  private volatile DatagramSocket socket;
  private Discovery(Context context,DiscoveryListener listener){this.context=context.getApplicationContext();this.listener=listener;}
  private void start(){new Thread(this::scan,"knight-discover").start();}
  private void scan(){
   WifiManager.MulticastLock lock=null;
   try{
    WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    if(wifi!=null){lock=wifi.createMulticastLock("luni-arena-discovery");lock.setReferenceCounted(false);lock.acquire();}
   }catch(RuntimeException ignored){}
   Set<String> seen=new HashSet<>();
   try(DatagramSocket sock=new DatagramSocket(null)){
    socket=sock;
    if(!searching)return;
    sock.setReuseAddress(true);sock.bind(new InetSocketAddress(BEACON));sock.setSoTimeout(650);
    long end=System.nanoTime()+7_000_000_000L;
    while(searching&&System.nanoTime()<end){
     byte[] buffer=new byte[64];DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
     try{sock.receive(packet);}catch(SocketTimeoutException ignored){continue;}
     String beacon=new String(packet.getData(),0,packet.getLength(),StandardCharsets.US_ASCII);
     int port=PORT;
     if(!beacon.startsWith("LUNIARENA2:"))continue;
     try{port=Integer.parseInt(beacon.substring("LUNIARENA2:".length()));}
      catch(NumberFormatException ignored){continue;}
      if(port<1||port>65535)continue;
     String address=packet.getAddress().getHostAddress();
     if(seen.add(address+":"+port))listener.found(address,port);
    }
   }catch(IOException ignored){}finally{
    socket=null;
    if(lock!=null&&lock.isHeld())lock.release();
    listener.finished();
   }
  }
  @Override public void close(){searching=false;DatagramSocket sock=socket;if(sock!=null)sock.close();}
 }
 public static Discovery discover(Context context,DiscoveryListener listener){
  Discovery discovery=new Discovery(context,listener);discovery.start();return discovery;
 }
 public synchronized boolean start(){
  if(!running||started||syncing>0)return false;
  started=true;
  return true;
 }
 public byte[] synchronize(byte[] state)throws IOException{
  if(host){
   if(state==null||state.length<1||state.length>16*1024*1024)throw new IOException("Estado do jogo indisponível");
   Peer[] group; synchronized(this){group=peers.clone();}
   for(Peer p:group)if(p!=null){
    try{p.startSent=true;p.out.writeByte(START);p.out.flush();
     if(!p.watchDone.await(8,java.util.concurrent.TimeUnit.SECONDS)||!p.acknowledged)
      throw new IOException("Convidado não iniciou a partida");
     p.socket.setSoTimeout(10000);
     p.out.writeInt(inputDelayFrames);p.out.writeInt(state.length);p.out.write(state);p.out.flush();
    }catch(InterruptedException e){Thread.currentThread().interrupt();disconnect(p);}
     catch(IOException e){disconnect(p);}
   }
   return state;
  }
  if(resuming)throw new IOException("A vaga precisa sincronizar o quadro atual.");
  while(running){
   try{if(in.readUnsignedByte()==START)break;throw new IOException("Início de sala inválido");}
   catch(SocketTimeoutException timeout){out.writeByte(PING);out.flush();}
  }
  if(!running)throw stopped();
  out.writeByte(START_ACK);out.flush();client.setSoTimeout(10000);
  inputDelayFrames=in.readInt();if(inputDelayFrames<3||inputDelayFrames>6)throw new IOException("Reserva de quadros inválida");
  int size=in.readInt();if(size<1||size>16*1024*1024)throw new IOException("Estado de jogo inválido");
  byte[] bytes=new byte[size];in.readFully(bytes);return bytes;
 }
 /** Resume receives a state at a frame boundary. No input can run until the core restores it. */
 public Resume awaitResume()throws IOException{
  if(host||!resuming)throw new IOException("Retomada indisponível.");
  if(in.readInt()!=RESUME_MARKER)throw new IOException("Resposta de reconexão inválida.");
  inputDelayFrames=in.readInt();int at=in.readInt(),size=in.readInt();
  if(inputDelayFrames<3||inputDelayFrames>6||at<0||size<1||size>16*1024*1024)
   throw new IOException("Estado de reconexão inválido.");
  byte[] state=new byte[size];in.readFully(state);
  return new Resume(state,at);
 }
 public Resume reconnect()throws IOException{
  if(host||!running)throw stopped();
  try{
   workerGeneration++;
   if(client!=null)client.close();
   connect(()->running,true);
   return awaitResume();
  }catch(IOException e){
   try{if(client!=null)client.close();}catch(IOException ignored){}
   throw e;
  }
 }
 public void beginResumed(int initialMask,int at)throws IOException{
  if(host||!resuming)throw new IOException("Retomada indisponível.");
  synchronized(frameLock){
   frame=at;localInputs.clear();readyFrames.clear();networkError=null;frameWorkersStarted=false;
  }
  lastSampleSentFrame=-1;
  out.writeByte(RESUME_ACK);out.flush();
  begin(initialMask);
  listener.changed("P"+(player+1)+" reconectado · partida retomada");
 }
 /** Seed a few future input frames before emulation starts. All devices run frame n with identical masks. */
 public void begin(int initialMask)throws IOException{
  final int from=frame,epoch=++workerGeneration;
  synchronized(frameLock){
   if(!running||frameWorkersStarted)throw new IOException("Sessão não está pronta");
   frameWorkersStarted=true;
   for(int n=frame;n<frame+inputDelayFrames;n++)localInputs.put(n,new Input(initialMask,NO_CHECK));
   frameLock.notifyAll();
  }
  if(host){
   receiveThread=new Thread(()->runNetwork(this::hostFrames,epoch),"knight-host-frames");
   receiveThread.start();
  }else{
   client.setSoTimeout(10000);
   sendThread=new Thread(()->runNetwork(()->sendFrames(from),epoch),"knight-send-input");
   receiveThread=new Thread(()->runNetwork(()->receiveFrames(from),epoch),"knight-receive-frames");
   sendThread.start();receiveThread.start();
  }
 }
 private interface NetworkWork { void run()throws IOException; }
 private void runNetwork(NetworkWork work,int epoch){
  try{work.run();}catch(IOException|RuntimeException problem){
   IOException error=problem instanceof IOException?(IOException)problem:new IOException("Falha na conexão local",problem);
   if(running&&epoch==workerGeneration){synchronized(frameLock){if(networkError==null)networkError=error;frameLock.notifyAll();}
    if(host)close();
    else{try{if(client!=null)client.close();}catch(IOException ignored){}
     listener.changed("Conexão perdida · tentando retomar a sala");}
   }
  }
 }
 private IOException stopped(){return networkError!=null?networkError:new IOException("Sessão encerrada");}
 private Input awaitLocal(int frameNumber)throws IOException{
  synchronized(frameLock){
   while(running&&networkError==null){
    Input input=localInputs.remove(frameNumber);
    if(input!=null)return input;
    try{frameLock.wait();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Conexão interrompida",e);}
   }
   throw stopped();
  }
 }
 private void publish(int frameNumber,int[] masks){
  synchronized(frameLock){readyFrames.put(frameNumber,masks);frameLock.notifyAll();}
 }
 private void hostFrames()throws IOException{
  for(int n=0;running;n++){
   Peer newcomer; synchronized(this){newcomer=pendingRejoin;}
   if(newcomer!=null)resumeAtBoundary(newcomer,n);
   int[] masks=new int[5];Input local=awaitLocal(n);masks[0]=local.mask;
   if((masks[0]&~(0xfff|ReplayArchive.RESET_FLAG))!=0)throw new IOException("Comando do host inválido");
   Peer[] group; synchronized(this){group=peers.clone();}
   for(int i=0;i<group.length;i++){
    Peer peer=group[i];if(peer==null)continue;
    try{
     if(peer.in.readInt()!=n)throw new IOException("Quadro fora de ordem");
     masks[i+1]=peer.in.readInt();
     int remoteCheck=peer.in.readInt();
     if((masks[i+1]&~0xfff)!=0)throw new IOException("Entrada de controle inválida");
     if(local.check!=NO_CHECK&&remoteCheck!=NO_CHECK){
      if(local.check==remoteCheck){
       peer.mismatches=0;integrityWarning=false;
       lastVerifiedFrame=n-inputDelayFrames-1;
      }else if(++peer.mismatches>=2){
       peer.outOfSync=true;integrityWarning=true;integrityRepairs++;lastVerifiedFrame=-1;
       disconnect(peer);masks[i+1]=0;
       continue;
      }else integrityWarning=true;
     }
     peer.socket.setSoTimeout(4000);
    }catch(IOException e){disconnect(peer);masks[i+1]=0;}
   }
   for(Peer peer:group)if(peer!=null&&owns(peer)){
    try{peer.out.writeInt(n);for(int mask:masks)peer.out.writeInt(mask);peer.out.flush();}
    catch(IOException e){disconnect(peer);}
   }
   publish(n,masks);
  }
 }
 private void resumeAtBoundary(Peer newcomer,int n)throws IOException{
  byte[] state;
  synchronized(frameLock){
   pausedAt=n;snapshotReady=false;resumeSnapshot=null;frameLock.notifyAll();
   while(running&&!snapshotReady&&pendingRejoin==newcomer)
    try{frameLock.wait(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Sincronização interrompida",e);}
   state=resumeSnapshot;
  }
  try{
   if(state==null||state.length<1||state.length>16*1024*1024)throw new IOException("Estado indisponível");
   newcomer.out.writeInt(RESUME_MARKER);newcomer.out.writeInt(inputDelayFrames);
   newcomer.out.writeInt(n);newcomer.out.writeInt(state.length);
   newcomer.out.write(state);newcomer.out.flush();
   newcomer.socket.setSoTimeout(15000);
   if(newcomer.in.readUnsignedByte()!=RESUME_ACK)throw new IOException("Jogador não restaurou o estado");
   newcomer.socket.setSoTimeout(4000);
   synchronized(this){
    if(pendingRejoin==newcomer){peers[newcomer.seat-1]=newcomer;pendingRejoin=null;}
   }
   integrityWarning=false;
   listener.changed("P"+(newcomer.seat+1)+" voltou à partida");
  }catch(IOException e){disconnect(newcomer);}
  finally{synchronized(frameLock){pausedAt=-1;resumeSnapshot=null;snapshotReady=false;frameLock.notifyAll();}}
 }
 private void sendFrames(int from)throws IOException{
  for(int n=from;running;n++){
   Input input=awaitLocal(n);
   out.writeInt(n);out.writeInt(input.mask);out.writeInt(input.check);out.flush();
   if(input.check!=NO_CHECK)lastSampleSentFrame=n-inputDelayFrames-1;
  }
 }
 private void receiveFrames(int from)throws IOException{
  for(int n=from;running;n++){
   if(in.readInt()!=n)throw new IOException("Quadro fora de ordem");
   int[] masks=new int[5];for(int i=0;i<5;i++){
    masks[i]=in.readInt();
    if((masks[i]&~(0xfff|(i==0?ReplayArchive.RESET_FLAG:0)))!=0)
     throw new IOException("Entrada da sala inválida");
   }
   if(n==0)client.setSoTimeout(4000);
   publish(n,masks);
  }
 }
 public int[] next(int local)throws IOException{return next(local,null);}
 public int[] next(int local,SnapshotProvider snapshot)throws IOException{
  int n=frame++;
  int check=NO_CHECK;
  if(snapshot!=null&&n>0&&n%CHECK_EVERY_FRAMES==0){
   // Both cores are sampled on the game thread before running frame n.
   // The checksum travels with the delayed input for frame n+inputDelayFrames.
   byte[] state=snapshot.capture();
   if(state!=null&&state.length>0){CRC32 crc=new CRC32();crc.update(state);check=(int)crc.getValue();}
  }
  synchronized(frameLock){
   if(!running||!frameWorkersStarted)throw stopped();
   localInputs.put(n+inputDelayFrames,new Input(local,check));frameLock.notifyAll();
  }
  while(true){
   boolean capture;
   synchronized(frameLock){
    while(running&&networkError==null){
     int[] masks=readyFrames.remove(n);
     if(masks!=null)return masks;
     if(snapshot!=null&&pausedAt==n&&!snapshotReady)break;
     try{frameLock.wait();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Conexão interrompida",e);}
    }
    if(!running||networkError!=null)throw stopped();
    capture=pausedAt==n&&!snapshotReady;
   }
   if(capture){byte[] state=null;
    try{state=snapshot.capture();}finally{
     synchronized(frameLock){if(pausedAt==n&&!snapshotReady){resumeSnapshot=state;snapshotReady=true;frameLock.notifyAll();}}
    }
   }
  }
 }
 @Override public void close(){
  running=false;
  synchronized(frameLock){frameLock.notifyAll();}
  try{if(server!=null)server.close();}catch(IOException ignored){}
  try{if(client!=null)client.close();}catch(IOException ignored){}
  synchronized(this){for(Peer peer:peers)if(peer!=null)try{peer.socket.close();}catch(IOException ignored){}
   if(pendingRejoin!=null)try{pendingRejoin.socket.close();}catch(IOException ignored){}
  }
 }
}
