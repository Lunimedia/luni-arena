package br.com.knightjoga;

import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated SNES codes and value-byte templates. No incomplete code reaches the native core. */
final class CheatCode {
 private static final Pattern GG=Pattern.compile("[0-9A-F]{4}-[0-9A-F]{4}");
 private static final Pattern PAR=Pattern.compile("[0-9A-F]{8}|[0-9A-F]{6}:[0-9A-F]{2}");
 // Only the value byte is selectable: PAR's final byte, or Game Genie's first byte.
 private static final Pattern VARIABLE=Pattern.compile("[0-9A-F]{6}(?::)?\\?\\?|\\?\\?[0-9A-F]{2}-[0-9A-F]{4}");
 private CheatCode(){}
 static String normalize(String raw,boolean allowTemplate){
  if(raw==null)return null;
  String input=raw.trim().toUpperCase(Locale.ROOT);
  if(input.isEmpty()||input.length()>200)return null;
  String[] parts=input.split("[+,.;\\s]+",-1);
  if(parts.length==0||parts.length>24)return null;
  int slots=0;
  for(int i=0;i<parts.length;i++){
   String part=parts[i];
   if(allowTemplate&&part.endsWith("XX"))part=part.substring(0,part.length()-2)+"??";
   if(allowTemplate&&part.startsWith("XX")&&part.indexOf('-')==4)part="??"+part.substring(2);
   parts[i]=part;
   if(GG.matcher(part).matches()||PAR.matcher(part).matches())continue;
   if(!allowTemplate||!VARIABLE.matcher(part).matches()||++slots>4)return null;
  }
  return String.join("+",parts);
 }
 static boolean variable(String code){return code!=null&&code.contains("??");}
 static int slots(String template){
  if(!variable(template))return 0;
  int n=0;for(int i=0;(i=template.indexOf("??",i))>=0;i+=2)n++;return n;
 }
 static String resolve(String template,String[] values){
  if(template==null||values==null||values.length!=slots(template))return null;
  String result=preview(template,values);
  return result.contains("??")?null:normalize(result,false);
 }
 static String preview(String template,String[] values){
  if(template==null||values==null||values.length!=slots(template))return template;
  StringBuilder result=new StringBuilder(template);int from=0;
  for(String value:values){
   int pos=template.indexOf("??",from);
   if(value!=null&&value.matches("[0-9a-fA-F]{2}"))result.replace(pos,pos+2,value.toUpperCase(Locale.ROOT));
   from=pos+2;
  }
  return result.toString();
 }
 static boolean matches(String template,String concrete){
  if(!variable(template)||concrete==null||!concrete.equals(normalize(concrete,false))||
      template.length()!=concrete.length())return false;
  for(int i=0;i<template.length();i++)if(template.charAt(i)!='?'&&template.charAt(i)!=concrete.charAt(i))return false;
  return true;
 }
 static String value(String template,String concrete,int slot){
  if(!matches(template,concrete)||slot<0||slot>=slots(template))return null;
  int from=0;for(int i=0;i<slot;i++)from=template.indexOf("??",from)+2;
  return concrete.substring(template.indexOf("??",from),template.indexOf("??",from)+2);
 }
 static String address(String concrete){
  if(concrete==null||concrete.contains("+")||normalize(concrete,false)==null)return null;
  return concrete.matches("[0-9A-F]{6}(?::)?[0-9A-F]{2}")?concrete.substring(0,6):null;
 }
 static boolean conflicts(String first,String second){
  if(first==null||second==null)return false;
  Set<String> parts=new LinkedHashSet<>(),addresses=new LinkedHashSet<>();
  for(String code:first.split("\\+")){
   parts.add(code);String address=address(code);if(address!=null)addresses.add(address);
  }
  for(String code:second.split("\\+")){
   if(parts.contains(code))return true;
   String address=address(code);if(address!=null&&addresses.contains(address))return true;
  }
  return false;
 }
}
