package br.com.knightjoga;

/** Loopback check for the optional controller-only TV mode; no Android runtime needed. */
public final class CouchSessionSmokeTest {
 public static void main(String[] ignored)throws Exception{
  java.util.concurrent.atomic.AtomicBoolean acknowledged=new java.util.concurrent.atomic.AtomicBoolean();
  java.util.concurrent.atomic.AtomicBoolean nextGame=new java.util.concurrent.atomic.AtomicBoolean();
  try(CouchSession host=CouchSession.host(message->{});
      CouchSession guest=CouchSession.join("127.0.0.1",CouchSession.PORT,
        message->{if("rewind".equals(message))acknowledged.set(true);
          if("next".equals(message))nextGame.set(true);})){
   if(guest.seat()!=1)throw new AssertionError("P2 should receive the first remote slot");
   guest.send(1<<8);long until=System.nanoTime()+2_000_000_000L;
   while(host.inputs(1)[1]!=(1<<8)&&System.nanoTime()<until)Thread.sleep(5);
   int[] inputs=host.inputs(1);
   if(inputs[0]!=1||inputs[1]!=(1<<8)||host.count()!=2)
    throw new AssertionError("Local and remote inputs should remain in separate slots");
   if(!host.setAssistantSeat(1))throw new AssertionError("Connected P2 should share P1");
   inputs=host.inputs(1);
   if(inputs[0]!=(1|(1<<8))||inputs[1]!=0)
    throw new AssertionError("Shared P1 must merge buttons and clear the assistant's slot");
   if(!host.setAssistantSeat(0))throw new AssertionError("Normal seats must restore");
   guest.send(0);until=System.nanoTime()+2_000_000_000L;
   while(host.inputs(1)[1]!=0&&System.nanoTime()<until)Thread.sleep(5);
   if(host.inputs(1)[1]!=0)throw new AssertionError("Released button remained held");
   guest.voteRewind(true);host.voteHostRewind();
   until=System.nanoTime()+2_000_000_000L;
   while(host.voteCount()!=2&&System.nanoTime()<until)Thread.sleep(5);
   if(!host.consumeUnanimousVote()||host.consumeUnanimousVote()||host.voteCount()!=0)
    throw new AssertionError("One rewind vote must be consumed only once");
   host.ackRewind();until=System.nanoTime()+2_000_000_000L;
   while(!acknowledged.get()&&System.nanoTime()<until)Thread.sleep(5);
   if(!acknowledged.get())throw new AssertionError("Guest did not receive rewind feedback");
   guest.voteRewind(true);host.voteHostRewind();
   until=System.nanoTime()+2_000_000_000L;
   while(host.voteCount()!=2&&System.nanoTime()<until)Thread.sleep(5);
   host.nextGame();until=System.nanoTime()+2_000_000_000L;
   while(!nextGame.get()&&System.nanoTime()<until)Thread.sleep(5);
   if(!nextGame.get()||host.voteCount()!=0)
    throw new AssertionError("New game must reset votes without disconnecting guests");
  }
  System.out.println("CouchSessionSmokeTest OK: P2, press and release.");
 }
}
