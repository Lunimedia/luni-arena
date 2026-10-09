package br.com.knightjoga;

import android.content.SharedPreferences;
import android.view.InputDevice;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Mapping belongs to the gamepad descriptor, not to whichever pad connected first. */
public final class ControllerProfiles {
 private ControllerProfiles(){}
 private static String prefix(InputDevice device){
  String descriptor=device==null?"legacy":device.getDescriptor();
  if(descriptor==null||descriptor.isEmpty())descriptor="legacy";
  try{
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(descriptor.getBytes(StandardCharsets.UTF_8));
   StringBuilder id=new StringBuilder("pad_");
   for(int i=0;i<12;i++)id.append(String.format(java.util.Locale.ROOT,"%02x",bytes[i]&255));
   return id.toString();
  }catch(Exception e){return "pad_legacy";}
 }
 public static int[] load(SharedPreferences preferences,InputDevice device){
  String name=prefix(device),key=name+"_active";boolean specific=preferences.getBoolean(key,false);
  int[] map=new int[12];
  for(int bit=0;bit<12;bit++)map[bit]=preferences.getInt(specific?name+"_"+bit:"map_"+bit,-1);
  return map;
 }
 public static void assign(SharedPreferences preferences,InputDevice device,int bit,int keyCode){
  if(bit<0||bit>=12)return;
  int[] current=load(preferences,device);current[bit]=keyCode;
  // One physical key must not trigger two SNES buttons in the same profile.
  for(int i=0;i<current.length;i++)if(i!=bit&&current[i]==keyCode)current[i]=-1;
  String name=prefix(device);SharedPreferences.Editor edit=preferences.edit().putBoolean(name+"_active",true);
  for(int i=0;i<current.length;i++)edit.putInt(name+"_"+i,current[i]);edit.apply();
 }
 public static void reset(SharedPreferences preferences,InputDevice device){
  String name=prefix(device);SharedPreferences.Editor edit=preferences.edit().putBoolean(name+"_active",true);
  for(int i=0;i<12;i++)edit.putInt(name+"_"+i,-1);edit.apply();
 }
}
