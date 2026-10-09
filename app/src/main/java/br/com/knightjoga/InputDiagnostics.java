package br.com.knightjoga;

import java.util.Arrays;

/** Bounded rolling elapsed-time samples. The caller defines what was measured. */
public final class InputDiagnostics {
 private final int[] ages=new int[90];
 private final int maxMs;
 private int size,write;
 public InputDiagnostics(){this(1000);}
 public InputDiagnostics(int maxMs){this.maxMs=Math.max(1,maxMs);}
 public synchronized void clear(){size=write=0;Arrays.fill(ages,0);}
 public synchronized void add(long ageMs){
  if(ageMs<0||ageMs>maxMs)return;
  ages[write++%ages.length]=(int)ageMs;size=Math.min(ages.length,size+1);
 }
 public synchronized int average(){
  if(size==0)return -1;long total=0;for(int i=0;i<size;i++)total+=ages[i];return (int)(total/size);
 }
 public synchronized int p95(){
  if(size==0)return -1;int[] sorted=Arrays.copyOf(ages,size);Arrays.sort(sorted);
  return sorted[Math.min(size-1,(int)Math.ceil(size*.95)-1)];
 }
 public synchronized int samples(){return size;}
}
