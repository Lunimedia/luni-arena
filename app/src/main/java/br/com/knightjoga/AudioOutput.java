package br.com.knightjoga;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.io.Closeable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Android audio sink. A 16-bit stereo frame is four bytes, not one sample. */
final class AudioOutput implements Closeable {
 private static final int CHANNELS=AudioFormat.CHANNEL_OUT_STEREO;
 private static final int ENCODING=AudioFormat.ENCODING_PCM_16BIT;
 private static final int BYTES_PER_FRAME=4;
 private final AudioTrack track;
 private final int sourceRate,outputRate;
 private final ArrayBlockingQueue<short[]> blocks;
 private final Thread writer;
 private volatile boolean closing;
 private double sourcePosition;

 private AudioOutput(AudioTrack track,int sourceRate,int outputRate,boolean online){
  this.track=track;this.sourceRate=sourceRate;this.outputRate=outputRate;
  blocks=online&&track!=null?new ArrayBlockingQueue<>(8):null;
  writer=blocks==null?null:new Thread(this::playbackLoop,"knight-audio");
  if(writer!=null)writer.setDaemon(true);
 }
 static int bufferBytes(int minBytes,int sampleRate){
  return bufferBytes(minBytes,sampleRate,false);
 }
 static int bufferBytes(int minBytes,int sampleRate,boolean online){
  int frames=Math.max((Math.max(1,minBytes)+BYTES_PER_FRAME-1)/BYTES_PER_FRAME,
      Math.max(512,sampleRate*(online?180:100)/1000));
  return frames*BYTES_PER_FRAME;
 }
 static AudioOutput open(int sourceRate){return open(sourceRate,false);}
 static AudioOutput open(int sourceRate,boolean online){
  if(sourceRate<8000||sourceRate>192000)sourceRate=32040;
  int[] rates={sourceRate,48000,44100};
  for(int rate:rates){
   int min;
   try{min=AudioTrack.getMinBufferSize(rate,CHANNELS,ENCODING);}
   catch(RuntimeException ignored){continue;}
   if(min<=0)continue;
   AudioTrack candidate=null;
   try{
    candidate=new AudioTrack(AudioManager.STREAM_MUSIC,rate,CHANNELS,ENCODING,
        bufferBytes(min,rate,online),AudioTrack.MODE_STREAM);
    if(candidate.getState()==AudioTrack.STATE_INITIALIZED){
     if(online&&android.os.Build.VERSION.SDK_INT>=31)
      candidate.setStartThresholdInFrames(Math.max(1,Math.min(rate/12,candidate.getBufferSizeInFrames()/2)));
     AudioOutput output=new AudioOutput(candidate,sourceRate,rate,online);
     if(online)output.writer.start();else candidate.play();
     return output;
    }
   }catch(RuntimeException ignored){
    // A tablet can reject one output rate; try a standard rate below.
   }
   if(candidate!=null)try{candidate.release();}catch(RuntimeException ignored){}
  }
  // Video and local-network play must still work when no output route exists.
  return new AudioOutput(null,sourceRate,sourceRate,online);
 }
 boolean available(){return track!=null;}
 void flushSolo(){
  if(track==null||blocks!=null)return;
  try{track.pause();track.flush();track.play();}catch(RuntimeException ignored){}
 }
 void flushAfterReset(){
  if(blocks!=null)blocks.clear();
  if(track!=null)try{track.pause();track.flush();track.play();}catch(RuntimeException ignored){}
 }
 void write(short[] samples){
  if(track==null||samples.length<2)return;
  if(blocks!=null){
   if(closing)return;
   if(!blocks.offer(samples)){blocks.poll();blocks.offer(samples);}
   return;
  }
  short[] sound=sourceRate==outputRate?samples:resample(samples);
  if(sound.length>0)try{
   track.write(sound,0,sound.length,AudioTrack.WRITE_NON_BLOCKING);
  }catch(RuntimeException ignored){
   // Losing an audio route mid-game must not shut down the emulation session.
  }
 }
 private void playbackLoop(){
  try{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);}
  catch(RuntimeException ignored){}
  int primed=0,lastUnderruns=0;
  boolean started=false;
  try{
   int primeFrames=Math.max(1,Math.min(outputRate/12,track.getBufferSizeInFrames()/2));
   while(!closing){
    short[] source;
    try{source=blocks.poll(100,TimeUnit.MILLISECONDS);}
    catch(InterruptedException e){if(closing)break;else continue;}
    if(source==null)continue;
    short[] pcm=sourceRate==outputRate?source:resample(source);
    int offset=0;
    while(offset<pcm.length&&!closing){
     int written=track.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_BLOCKING);
     if(written<=0){
      if(closing)break;
      if(started)throw new IllegalStateException("Saída de áudio interrompida");
      track.play();started=true;lastUnderruns=track.getUnderrunCount();continue;
     }
     offset+=written;primed+=written/2;
    }
    if(!started&&primed>=primeFrames){
     track.play();started=true;lastUnderruns=track.getUnderrunCount();
    }else if(started){
     int underruns=track.getUnderrunCount();
     if(underruns>lastUnderruns){
      // A network pause emptied the sink. Refill briefly before restarting.
      track.pause();track.flush();started=false;primed=0;
     }
     lastUnderruns=underruns;
    }
   }
  }catch(RuntimeException ignored){
   // Audio output failure does not close the game or the LAN session.
  }finally{
   try{track.pause();track.flush();}catch(RuntimeException ignored){}
   track.release();
  }
 }
 private short[] resample(short[] source){
  int frames=source.length/2;
  if(frames==0)return new short[0];
  double step=sourceRate/(double)outputRate;
  int estimate=(int)Math.ceil((frames-sourcePosition)/step)+1;
  short[] output=new short[Math.max(0,estimate)*2];
  int count=0;
  while(sourcePosition<frames){
   int index=(int)sourcePosition,next=Math.min(index+1,frames-1);
   double fraction=sourcePosition-index;
   for(int channel=0;channel<2;channel++){
    int a=source[index*2+channel],b=source[next*2+channel];
    output[count*2+channel]=(short)Math.round(a+(b-a)*fraction);
   }
   count++;sourcePosition+=step;
  }
  sourcePosition-=frames;
  if(count==output.length/2)return output;
  return java.util.Arrays.copyOf(output,count*2);
 }
 @Override public void close(){
  if(track==null)return;
  if(writer!=null){
   closing=true;blocks.clear();writer.interrupt();
   try{track.pause();}catch(RuntimeException ignored){}
   try{writer.join(600);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   return;
  }
  try{track.pause();track.flush();}catch(RuntimeException ignored){}
  track.release();
 }
}
