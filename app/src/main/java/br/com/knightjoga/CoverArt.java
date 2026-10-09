package br.com.knightjoga;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Original, lightweight artwork shown when licensed or personal art is unavailable. */
final class CoverArt extends Drawable {
 private static final int[] ACCENTS={0xff6de2dc,0xffaa86fa,0xfff18cbc,0xfff6ba73,0xff79cba5};
 private static final int[] BASES={0xff102b3c,0xff211d44,0xff34213c,0xff332c38,0xff153239};
 private final String display,mark;
 private final int accent,base;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private int opacity=255;

 CoverArt(String name){
  display=CoverTitle.compact(name);
  String[] parts=display.split(" ");
  mark=parts.length>0?parts[0]:"LUNI";
  int palette=(name==null?0:name.hashCode()&0x7fffffff)%ACCENTS.length;
  accent=ACCENTS[palette];base=BASES[palette];
 }
 private static int tint(int color,int alpha){return (alpha<<24)|(color&0xffffff);}
 private void fill(int color){paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);}

 @Override public void draw(Canvas canvas){
  Rect bounds=getBounds();float w=bounds.width(),h=bounds.height();
  if(w<=0||h<=0)return;
  int saved=canvas.save();canvas.translate(bounds.left,bounds.top);
  if(opacity!=255)canvas.saveLayerAlpha(0,0,w,h,opacity);
  Path rounded=new Path();float radius=Math.min(w,h)*.075f;
  rounded.addRoundRect(0,0,w,h,radius,radius,Path.Direction.CW);
  canvas.clipPath(rounded);

  paint.setStyle(Paint.Style.FILL);
  paint.setShader(new LinearGradient(0,0,w,h,new int[]{base,0xff111a32,0xff0c142a},
      new float[]{0,.56f,1},Shader.TileMode.CLAMP));
  canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
  paint.setShader(new RadialGradient(w*.88f,h*.29f,w*.79f,
      new int[]{tint(accent,115),tint(accent,0)},null,Shader.TileMode.CLAMP));
  canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
  paint.setShader(new RadialGradient(w*.08f,h*.90f,w*.72f,
      new int[]{tint(accent,49),tint(accent,0)},null,Shader.TileMode.CLAMP));
  canvas.drawRect(0,0,w,h,paint);paint.setShader(null);

  drawWallpaper(canvas,w,h);
  fill(0x20ffffff);
  canvas.drawRoundRect(w*.07f,h*.055f,w*.93f,h*.058f,h*.003f,h*.003f,paint);
  fill(accent);
  canvas.drawRoundRect(w*.09f,h*.115f,w*.13f,h*.152f,w*.012f,w*.012f,paint);
  paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(Math.min(w*.070f,h*.066f));
  fill(0xffe6eafa);canvas.drawText("LUNI",w*.17f,h*.146f,paint);
  paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
  paint.setTextSize(Math.min(w*.046f,h*.043f));fill(0xffb9c4df);
  paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("16-BIT",w*.90f,h*.144f,paint);

  drawTitle(canvas,w,h);

  fill(0x8c0c152b);canvas.drawRect(0,h*.83f,w,h,paint);
  fill(tint(accent,220));canvas.drawRoundRect(w*.09f,h*.835f,w*.91f,h*.843f,h*.006f,h*.006f,paint);
  paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  paint.setTextSize(Math.min(w*.048f,h*.049f));paint.setTextAlign(Paint.Align.LEFT);
  fill(0xffe1e9fa);canvas.drawText("LUNI  /  ACERVO",w*.09f,h*.927f,paint);
  paint.setTextAlign(Paint.Align.RIGHT);fill(accent);
  canvas.drawText("◆",w*.91f,h*.927f,paint);
  canvas.restoreToCount(saved);
 }

 private void drawWallpaper(Canvas canvas,float w,float h){
  int seed=display.hashCode()&0x7fffffff;
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,w*.005f));
  for(int i=0;i<4;i++){
   paint.setShader(null);paint.setColor(tint(accent,32+i*7));
   float cx=w*(.91f+.07f*i),cy=h*(.55f+.035f*i);
   canvas.drawCircle(cx,cy,w*(.20f+.105f*i),paint);
  }
  if(display.startsWith("TOP GEAR")){
   paint.setStrokeWidth(w*.012f);paint.setColor(tint(accent,92));
   Path lane=new Path();lane.moveTo(-w*.16f,h*.80f);lane.cubicTo(w*.19f,h*.57f,w*.21f,h*.89f,w*.67f,h*.27f);
   canvas.drawPath(lane,paint);
   paint.setStrokeWidth(w*.003f);paint.setColor(0x63ffffff);
   canvas.drawLine(0,h*.72f,w*.52f,h*.28f,paint);
  }else if(display.startsWith("BOMBERMAN")){
   for(int y=0;y<4;y++)for(int x=0;x<3;x++){
    float px=w*(.58f+x*.15f),py=h*(.30f+y*.105f);
    fill(tint(accent,(x+y)%3==0?66:27));
    canvas.drawRoundRect(px,py,px+w*.065f,py+w*.065f,w*.009f,w*.009f,paint);
   }
  }else if(display.startsWith("DKC")){
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*.011f);
   paint.setColor(tint(accent,75));
   Path vine=new Path();vine.moveTo(w*.38f,h*.80f);
   vine.cubicTo(w*.91f,h*.58f,w*.37f,h*.53f,w*.84f,h*.19f);
   canvas.drawPath(vine,paint);
   paint.setStyle(Paint.Style.FILL);
   for(int i=0;i<4;i++){
    float x=w*(.52f+i*.10f),y=h*(.59f-i*.10f);
    fill(tint(accent,32+i*9));
    canvas.drawOval(x,y,x+w*.14f,y+h*.035f,paint);
   }
  }else if(display.startsWith("MARIO RPG")||display.startsWith("CHRONO")){
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*.006f);
   for(int i=0;i<3;i++){
    paint.setColor(tint(accent,65-i*12));
    canvas.drawArc(w*(.52f+i*.08f),h*(.25f+i*.075f),w*(1.15f+i*.08f),h*(.75f+i*.08f),
        185,130,false,paint);
   }
  }else{
   for(int i=0;i<8;i++){
    float px=w*(.38f+((seed+i*37)%57)/100f);
    float py=h*(.20f+((seed/13+i*29)%50)/100f);
    fill(tint(accent,43+i%3*17));
    canvas.drawCircle(px,py,w*(.006f+i%3*.004f),paint);
   }
  }
  paint.setStyle(Paint.Style.FILL);
  paint.setTypeface(Typeface.create("sans-serif-condensed-black",Typeface.BOLD));
  paint.setTextSize(Math.min(w*.50f,h*.36f));paint.setTextAlign(Paint.Align.RIGHT);
  fill(tint(accent,29));
  canvas.drawText(mark.length()>5?mark.substring(0,5):mark,w*1.02f,h*.75f,paint);
 }

 private void drawTitle(Canvas canvas,float w,float h){
  paint.setTextAlign(Paint.Align.LEFT);
  paint.setTypeface(Typeface.create("sans-serif-condensed-black",Typeface.BOLD));
  float maxWidth=w*.80f,size=Math.min(w*.174f,h*.142f);
  String[] lines;
  do{
   paint.setTextSize(size);
   lines=splitTitle(display,maxWidth);
   if(fits(lines,maxWidth))break;
   size*=.91f;
  }while(size>Math.max(11f,Math.min(w,h)*.046f));
  if(!fits(lines,maxWidth)){
   String remaining=display;
   while(remaining.length()>1&&paint.measureText(remaining+"…")>maxWidth)
    remaining=remaining.substring(0,remaining.length()-1);
   lines=new String[]{remaining+"…"};
  }
  float baseline=h*(lines.length==1?.535f:.465f),step=size*1.07f,x=w*.10f;
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);
  paint.setStrokeWidth(Math.max(1.2f,size*.035f));paint.setColor(0x99202a47);
  for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],x,baseline+i*step,paint);
  paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(0xfff6f7ff);
  for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],x,baseline+i*step,paint);
 }
 private boolean fits(String[] lines,float width){
  for(String line:lines)if(paint.measureText(line)>width)return false;
  return true;
 }
 private String[] splitTitle(String title,float width){
  if(title.matches("(?:TOP GEAR|BOMBERMAN) [0-9]+")){
   int at=title.lastIndexOf(' ');
   return new String[]{title.substring(0,at),title.substring(at+1)};
  }
  if(paint.measureText(title)<=width)return new String[]{title};
  String[] words=title.split(" ");
  if(words.length==1)return new String[]{title};
  int split=1;float best=Float.MAX_VALUE;
  for(int i=1;i<words.length;i++){
   String left=join(words,0,i),right=join(words,i,words.length);
   float a=paint.measureText(left),b=paint.measureText(right);
   float cost=Math.max(a,b)+Math.abs(a-b)*.20f;
   if(cost<best){best=cost;split=i;}
  }
  return new String[]{join(words,0,split),join(words,split,words.length)};
 }
 private static String join(String[] words,int from,int to){
  StringBuilder line=new StringBuilder();
  for(int i=from;i<to;i++){if(i>from)line.append(' ');line.append(words[i]);}
  return line.toString();
 }
 @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
