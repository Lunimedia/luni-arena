package br.com.knightjoga;

import java.util.ArrayDeque;

/** Bounded solo-only save states, sampled by the emulation thread. */
public final class RewindBuffer {
 private static final int MAX_BYTES=24*1024*1024, MAX_COUNT=32;
 private static final class Point {final long frame;final byte[] state;
  Point(long frame,byte[] state){this.frame=frame;this.state=state;}}
 private final ArrayDeque<Point> points=new ArrayDeque<>();
 private int used;
 public synchronized void clear(){points.clear();used=0;}
 public synchronized int count(){return points.size();}
 public synchronized boolean available(long currentFrame,long framesBack){
  return !points.isEmpty()&&points.peekFirst().frame<=currentFrame-Math.max(1,framesBack);
 }
 public synchronized void add(long frame,byte[] state){
  if(state==null||state.length<128||state.length>MAX_BYTES)return;
  points.addLast(new Point(frame,state));used+=state.length;
  while(points.size()>MAX_COUNT||used>MAX_BYTES)used-=points.removeFirst().state.length;
 }
 public synchronized byte[] rewind(long currentFrame,long framesBack){
  if(points.isEmpty())return null;
  long target=Math.max(0,currentFrame-Math.max(1,framesBack));
  Point chosen=points.peekFirst();
  for(Point point:points){if(point.frame<=target)chosen=point;else break;}
  while(points.peekLast()!=chosen)used-=points.removeLast().state.length;
  return chosen.state;
 }
}
