"""Compile and exercise the LAN protocol without the Android runtime.

Run: python3 tests/test_lan_session.py
"""
import pathlib
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = pathlib.Path('/usr/lib/jvm/java-17-openjdk-amd64/bin')
JVM = shutil.which('java') or str(JAVA / 'java')
JAVAC = [shutil.which('javac')] if shutil.which('javac') else [JVM, 'com.sun.tools.javac.Main']
FIXTURES = {
    "android/content/SharedPreferences.java": """
package android.content;
public interface SharedPreferences {
 String getString(String key, String value);
 Editor edit();
 interface Editor { Editor putString(String key, String value); Editor remove(String key); void apply(); }
}
""",
    "android/content/Context.java": """
package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public static final String WIFI_SERVICE="wifi";
 public Context getApplicationContext(){return this;}
 public Object getSystemService(String key){return null;}
 public SharedPreferences getSharedPreferences(String key,int mode){return null;}
}
""",
    "android/net/wifi/WifiManager.java": """
package android.net.wifi;
public class WifiManager {
 public MulticastLock createMulticastLock(String name){return new MulticastLock();}
 public static class MulticastLock {
  public void setReferenceCounted(boolean value){}
  public void acquire(){}
  public boolean isHeld(){return false;}
  public void release(){}
 }
}
""",
    "br/com/knightjoga/RomLibrary.java": """
package br.com.knightjoga;
final class RomLibrary {
 static class Game {}
 static boolean isRom(String name){return name.toLowerCase().endsWith(".sfc");}
}
""",
    "br/com/knightjoga/ReplayArchive.java": """
package br.com.knightjoga;
final class ReplayArchive { static final int RESET_FLAG=1<<20; }
""",
    "br/com/knightjoga/RoomQuality.java": """
package br.com.knightjoga;
final class RoomQuality { static int delayFrames(int[] rtt){return 4;} }
""",
    "br/com/knightjoga/LanSessionProtocolTest.java": """
package br.com.knightjoga;
import android.content.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import java.net.*;

public class LanSessionProtocolTest {
 static final byte[] ROM=new byte[]{1,2,3,4,5};
 static final String HASH;
 static {
  try{
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(ROM);
   StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format("%02x",b&255));
   HASH=text.toString();
  }catch(Exception e){throw new RuntimeException(e);}
 }
 static final class Prefs implements SharedPreferences {
  final Map<String,String> saved=new ConcurrentHashMap<>();
  public String getString(String key,String fallback){return saved.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){
   public Editor putString(String key,String value){saved.put(key,value);return this;}
   public Editor remove(String key){saved.remove(key);return this;}
   public void apply(){}
  };}
 }
 static final class TestContext extends Context {
  final Prefs prefs=new Prefs();
  @Override public SharedPreferences getSharedPreferences(String key,int mode){return prefs;}
 }
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static void until(java.util.function.BooleanSupplier condition,String reason)throws Exception {
  long end=System.currentTimeMillis()+5000;
  while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(10);
  check(condition.getAsBoolean(),reason);
 }
 static LanSession.Joined join(TestContext context)throws Exception {
  return LanSession.join(context,"127.0.0.1",LanSession.PORT,ROM,HASH,HASH,HASH,
   new RomLibrary.Game(),()->true,message->{});
 }
 static void lobbyTest()throws Exception {
  TestContext one=new TestContext(),other=new TestContext();
  try(LanSession host=LanSession.host(ROM,HASH,"Demo","sfc",HASH,false,false,message->{})){
   try{LanSession.join(other,"127.0.0.1",LanSession.PORT,ROM,HASH,
    "0000000000000000000000000000000000000000000000000000000000000000",HASH,
    new RomLibrary.Game(),()->true,message->{});
    throw new AssertionError("Different core was accepted");
   }catch(IOException expected){check(expected.getMessage().contains("núcleo"),"No core mismatch explanation");}
   LanSession.Joined guest=join(one);
   check(guest.session.player()==1,"First guest should be P2");
   until(()->host.count()==2,"P2 did not occupy the room");
   check(host.diagnosticReport().contains("RTT ~")&&
    guest.session.diagnosticReport().contains("ROM SHA-256"),"Room diagnostic missing measurements");
   try{join(other);throw new AssertionError("Full room should be rejected");}
   catch(IOException expected){check(expected.getMessage().contains("Sala cheia"),"No full-room explanation");}
   guest.session.close();
   until(()->host.count()==1,"Departure did not release the seat");
   check(host.seatStates()[1]==0,"Seat should be free before starting");
   try(LanSession replacement=join(other).session){
    check(replacement.player()==1,"New guest should reuse P2");
    ExecutorService waiting=Executors.newSingleThreadExecutor();
    try{
     Future<byte[]> synchronizedGuest=waiting.submit(()->replacement.synchronize(null));
     Thread.sleep(1700); // one or more lobby heartbeats before START
     boolean started=false;long end=System.currentTimeMillis()+5000;
     while(!(started=host.start())&&System.currentTimeMillis()<end)Thread.sleep(10);
     check(started,"Host did not start after the guest waited in the lobby");
     host.synchronize(new byte[]{9});
     check(synchronizedGuest.get(10,TimeUnit.SECONDS)[0]==9,"Heartbeat raced with START");
    }finally{waiting.shutdownNow();}
   }
  }
 }
 static void reunionTest()throws Exception {
  TestContext one=new TestContext(),two=new TestContext(),stranger=new TestContext();
  ExecutorService pool=Executors.newFixedThreadPool(4);
  try(LanSession host=LanSession.host(ROM,HASH,"Demo","sfc",HASH,false,true,message->{})){
   LanSession guest=join(one).session,second=join(two).session;
   try {
    until(()->host.count()==3,"Guests did not finish joining");
    long readyUntil=System.currentTimeMillis()+5000;
    boolean started=false;
    while(!(started=host.start())&&System.currentTimeMillis()<readyUntil)Thread.sleep(10);
    check(started,"Host could not start");
    Future<byte[]> firstStart=pool.submit(()->guest.synchronize(null));
    Future<byte[]> secondStart=pool.submit(()->second.synchronize(null));
    byte[] state={10,20,30};
    host.synchronize(state);
    check(Arrays.equals(state,firstStart.get(10,TimeUnit.SECONDS)),"P2 initial state mismatch");
    check(Arrays.equals(state,secondStart.get(10,TimeUnit.SECONDS)),"P3 initial state mismatch");
    guest.begin(1);second.begin(2);host.begin(3);
    AtomicInteger hostFrames=new AtomicInteger(),otherFrames=new AtomicInteger();
    Future<?> hostWork=pool.submit(()->{
     try{
      for(int i=0;i<240;i++){
       host.next(3,()->new byte[]{42,17});
       hostFrames.incrementAndGet();Thread.sleep(8);
      }
     }catch(Exception e){throw new RuntimeException(e);}
    });
    Future<?> secondWork=pool.submit(()->{
     try{
      for(int i=0;i<240;i++){
       second.next(2);otherFrames.incrementAndGet();Thread.sleep(8);
      }
     }catch(Exception e){throw new RuntimeException(e);}
    });
    for(int i=0;i<18;i++)guest.next(1);
    guest.close();
    until(()->host.count()==2,"Host closed or retained disconnected P2");
    check(host.seatStates()[1]==2,"P2 should be reserved during the match");
    int before=hostFrames.get();
    until(()->hostFrames.get()>before+4,"Match stopped when P2 left");
    try{join(stranger);throw new AssertionError("Stranger should not join a running match");}
    catch(IOException expected){check(expected.getMessage().contains("partida já começou"),"No running-match explanation");}
    LanSession.Joined comeback=join(one);
    check(comeback.resuming&&comeback.session.player()==1,"Original P2 did not reclaim its seat");
    try(LanSession renewed=comeback.session){
     LanSession.Resume point=renewed.awaitResume();
     check(point.frame>18&&Arrays.equals(point.state,new byte[]{42,17}),"Wrong resume frame/state");
     renewed.beginResumed(1,point.frame);
     until(()->host.count()==3,"P2 did not reappear");
     for(int i=0;i<8;i++)renewed.next(1);
     check(hostFrames.get()>point.frame,"Host did not continue after reconnection");
    }
    hostWork.get(10,TimeUnit.SECONDS);
    secondWork.get(10,TimeUnit.SECONDS);
    check(hostFrames.get()==240&&otherFrames.get()==240,"P3 or host did not finish the match");
   }finally{guest.close();second.close();}
  }finally{pool.shutdownNow();}
 }
 static void dropAndRetryTest()throws Exception {
  TestContext context=new TestContext();
  ExecutorService pool=Executors.newFixedThreadPool(2);
  try(LanSession host=LanSession.host(ROM,HASH,"Demo","sfc",HASH,false,false,message->{});
      LanSession guest=join(context).session){
   boolean started=false;long until=System.currentTimeMillis()+5000;
   while(!(started=host.start())&&System.currentTimeMillis()<until)Thread.sleep(10);
   check(started,"Host did not start with one guest");
   Future<byte[]> initial=pool.submit(()->guest.synchronize(null));
   host.synchronize(new byte[]{7});
   check(initial.get(10,TimeUnit.SECONDS)[0]==7,"First state missing");
   guest.begin(1);host.begin(2);
   AtomicInteger frames=new AtomicInteger();
   Future<?> hostWork=pool.submit(()->{
    try{for(int i=0;i<220;i++){host.next(2,()->new byte[]{11});
      frames.incrementAndGet();Thread.sleep(8);
    }}catch(Exception e){throw new RuntimeException(e);}
   });
   for(int i=0;i<12;i++)guest.next(1);
   Field field=LanSession.class.getDeclaredField("client");field.setAccessible(true);
   ((Socket)field.get(guest)).close(); // mimic sudden radio loss, not an app-controlled exit
   until(()->host.count()==1,"Host did not stay alone after a socket failure");
   int prior=frames.get();
   until(()->frames.get()>prior+3,"Host alone stopped receiving frames");
   try{guest.next(1);throw new AssertionError("Dropped client must enter recovery");}
   catch(IOException expected){}
   LanSession.Resume point=guest.reconnect();
   check(point.frame>12&&point.state[0]==11,"Automatic recovery state mismatch");
   guest.beginResumed(1,point.frame);
   until(()->host.count()==2,"Automatic recovery did not reclaim P2");
   for(int i=0;i<4;i++)guest.next(1);
   hostWork.get(10,TimeUnit.SECONDS);
  }finally{pool.shutdownNow();}
 }
 static void integrityTest()throws Exception {
  TestContext context=new TestContext();
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try(LanSession host=LanSession.host(ROM,HASH,"Demo","sfc",HASH,false,false,message->{});
      LanSession guest=join(context).session){
   boolean started=false;long until=System.currentTimeMillis()+5000;
   while(!(started=host.start())&&System.currentTimeMillis()<until)Thread.sleep(10);
   check(started,"Integrity room did not start");
   Future<byte[]> initial=pool.submit(()->guest.synchronize(null));
   host.synchronize(new byte[]{11});initial.get(10,TimeUnit.SECONDS);
   guest.begin(1);host.begin(2);
   AtomicInteger hostFrames=new AtomicInteger();
   Future<?> hostWork=pool.submit(()->{
    try{for(int i=0;i<740;i++){
     host.next(2,()->new byte[]{11});hostFrames.incrementAndGet();Thread.sleep(7);
    }}catch(Exception e){throw new RuntimeException(e);}
   });
   boolean dropped=false;
   boolean verifiedBefore=false;
   AtomicInteger checks=new AtomicInteger();
   for(int i=0;i<590;i++){
    try{guest.next(1,()->new byte[]{(byte)(checks.incrementAndGet()==1?11:12)});
     verifiedBefore|=host.lastVerifiedFrame()>=179;Thread.sleep(7);}
    catch(IOException expected){dropped=true;break;}
   }
   check(verifiedBefore,"Matching snapshots were not verified");
   check(dropped,"Two mismatched snapshots did not disconnect the guest");
   until(()->host.count()==1,"Host did not reserve the desynchronized seat");
   check(host.seatStates()[1]==2&&host.integrityRepairs()==1,
    "A desynchronized P2 should retain its seat for repair");
   int before=hostFrames.get();
   until(()->hostFrames.get()>before+3,"Host stopped when integrity check failed");
   LanSession.Resume point=guest.reconnect();
   check(point.state[0]==11,"Recovery did not use the host state");
   guest.beginResumed(1,point.frame);
   until(()->host.count()==2,"Guest failed to return after a state repair");
   for(int i=0;i<8;i++)guest.next(1,()->new byte[]{11});
   hostWork.get(10,TimeUnit.SECONDS);
  }finally{pool.shutdownNow();}
 }
 public static void main(String[] args)throws Exception{
  lobbyTest();reunionTest();dropAndRetryTest();integrityTest();
  System.out.println("LanSessionProtocolTest OK: core preflight, RTT, full room, resume, desync repair.");
 }
}
""",
}

with tempfile.TemporaryDirectory(prefix="luni-net-test-") as temp:
    directory = pathlib.Path(temp)
    sources = []
    for name, body in FIXTURES.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body)
        sources.append(str(path))
    sources.append(str(ROOT / "app/src/main/java/br/com/knightjoga/LanSession.java"))
    subprocess.run([*JAVAC, "-d", temp, *sources], check=True)
    subprocess.run(
        [JVM, "-cp", temp, "br.com.knightjoga.LanSessionProtocolTest"],
        check=True, timeout=35,
    )
