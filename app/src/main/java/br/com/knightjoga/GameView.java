package br.com.knightjoga;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** SNES image with optional per-player split-screen view and floating touch controls. */
public final class GameView extends View {
    // libretro SNES bits: B,Y,Select,Start,Up,Down,Left,Right,A,X,L,R.
    private static final int B=0,Y=1,SELECT=2,START=3,UP=4,DOWN=5,LEFT=6,RIGHT=7,A=8,X=9,L=10,R=11;
    private static final int BG=0xff090d1d, INK=0xfff7f4ff, ACCENT=0xffad8aff;
    private static final float MIN_SCALE=.65f,MAX_SCALE=1.75f;
    // The original "Botões maiores" preset is the starting size for new installs.
    private static final float DEFAULT_TOUCH_SCALE=1.2f;
    private static final int AMBIENT_WIDTH=32,AMBIENT_HEIGHT=20,AMBIENT_INTERVAL=8;
    private static final float[] STICK_PRESS={.14f,.20f,.30f};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect videoSource=new Rect();
    private final RectF videoDestination=new RectF();
    private final RectF ambientDestination=new RectF();
    private final int[] ambientSamples=new int[AMBIENT_WIDTH*AMBIENT_HEIGHT];
    private final int[] ambientSmoothed=new int[AMBIENT_WIDTH*AMBIENT_HEIGHT];
    private final AtomicInteger touchMask=new AtomicInteger();
    // Android callbacks update the per-device state on the UI thread; the core reads one volatile mask.
    private final ControllerInputState hardware=new ControllerInputState();
    private volatile int hardwareMask;
    private final AtomicInteger pendingHardwarePress=new AtomicInteger();
    private final Control[] controls={
        new Control("pad","Direcional",-1,.13f,.64f,1f),
        new Control("l","L",L,.13f,.23f,.92f),
        new Control("r","R",R,.87f,.23f,.92f),
        new Control("x","X",X,.87f,.55f,1f),
        new Control("y","Y",Y,.82f,.65f,1f),
        new Control("a","A",A,.92f,.65f,1f),
        new Control("b","B",B,.87f,.75f,1f),
        new Control("select","Select",SELECT,.13f,.89f,.82f),
        new Control("start","Start",START,.87f,.89f,.82f)
    };
    private Bitmap bitmap;
    private Bitmap ambientBitmap;
    private PixelScaler pixelScaler;
    private volatile boolean external,editing,replaying,tvMode,inputBlocked;
    // 0=entire frame, 1=own player (P1 top / P2 bottom), 2/3=top/bottom, 4/5=left/right.
    // 0=linear, 1=nearest, 2=Scale2x (GPU on Android 13+).
    private volatile int videoMode,screenMode,ambientTick;
    private int videoFilter,videoFilterMode=-1,playerIndex,hudTheme;
    private int slowImageSeconds;
    private String videoKey;
    private boolean analog,padHeld,haptics;
    private float opacity=.33f,padX,padY,dragOffsetX,dragOffsetY;
    private int safeLeft,safeTop,safeRight,safeBottom;
    private final SparseArray<int[]> keyProfiles=new SparseArray<>();
    private final SparseArray<StickProfile> stickProfiles=new SparseArray<>();
    private final InputDiagnostics inputDiagnostics=new InputDiagnostics();
    private final InputDiagnostics samplingDiagnostics=new InputDiagnostics();
    private HudLearning touchLearning=new HudLearning();
    private volatile int[] replayMasks;
    private int slowShaderFrames;
    private volatile int lastDispatchAgeMs=-1;
    private volatile long lastHardwareAtMs;
    private volatile long lastHardwareChangeNanos;
    private volatile int hardwareVersion;
    private int sampledHardwareVersion,stickSensitivity,selected=0,dragPointer=-1,padPointer=-1;
    private EditorListener editorListener;

    private static final class StickProfile {
        final int source;
        final float flatX,flatY;
        StickProfile(InputDevice device,int source){
            this.source=source;
            InputDevice.MotionRange x=device==null?null:device.getMotionRange(MotionEvent.AXIS_X,source);
            InputDevice.MotionRange y=device==null?null:device.getMotionRange(MotionEvent.AXIS_Y,source);
            flatX=x==null?0:x.getFlat();flatY=y==null?0:y.getFlat();
        }
    }

    private static final class Control {
        final String id,title;
        final int bit;
        final float defaultX,defaultY,defaultScale;
        float x,y,scale;
        boolean customized;
        Control(String id,String title,int bit,float x,float y,float scale){
            this.id=id;this.title=title;this.bit=bit;
            defaultX=x;defaultY=y;defaultScale=scale*DEFAULT_TOUCH_SCALE;reset();
        }
        void reset(){x=defaultX;y=defaultY;scale=defaultScale;customized=false;}
    }
    public interface EditorListener { void selected(String title,float scale); }

    public GameView(Context context){
        super(context);
        setFocusable(true);setFocusableInTouchMode(true);setKeepScreenOn(true);
        // A gamepad gives this full-screen View focus. Android's default focus
        // highlight is drawn above onDraw and washes out every game pixel.
        // Input focus remains active; only the full-screen visual is disabled.
        setDefaultFocusHighlightEnabled(false);
        paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        loadControls();loadHardwareSettings();
    }
    private static float clamp(float n,float low,float high){return Math.max(low,Math.min(high,n));}
    private SharedPreferences preferences(){return getContext().getSharedPreferences("MainActivity",0);}
    public void configureVideo(String romIdentity,int player){
        videoKey=romIdentity==null?"":romIdentity;
        playerIndex=player;
        try{touchLearning=HudLearning.load(getContext().getFilesDir(),videoKey);}
        catch(IOException|IllegalArgumentException ignored){touchLearning=new HudLearning();}
        loadControls();
        SharedPreferences saved=preferences();
        videoMode=saved.getInt("video_mode_"+videoKey,0);
        screenMode=saved.getInt("screen_mode_"+videoKey,0);
        videoFilterMode=saved.getInt("video_filter_"+videoKey,-1);
        tvMode=saved.getBoolean("tv_mode_"+videoKey,false);
        if(videoMode<0||videoMode>5)videoMode=0;
        if(screenMode<0||screenMode>2)screenMode=0;
        if(videoFilterMode < -1 || videoFilterMode > 2)videoFilterMode=-1;
        videoFilter=videoFilterMode==-1?autoFilter():videoFilterMode;
        if(videoFilter==2&&Build.VERSION.SDK_INT<33)videoFilter=1;
        invalidate();
    }
    private int autoFilter(){
        ActivityManager manager=(ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null||manager.isLowRamDevice())return 0;
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
        int cores=Runtime.getRuntime().availableProcessors();
        long screen=(long)getResources().getDisplayMetrics().widthPixels*getResources().getDisplayMetrics().heightPixels;
        if(Build.VERSION.SDK_INT>=33&&memory.totalMem>=6L*1024*1024*1024&&cores>=8&&screen<=2700000
                &&!preferences().getBoolean("auto_shader_failed",false))return 2;
        return memory.totalMem>=3L*1024*1024*1024&&cores>=6?1:0;
    }
    public int videoMode(){return videoMode;}
    public int screenMode(){return screenMode;}
    public void setScreenMode(int mode){
        if(mode<0||mode>2)return;
        screenMode=mode;ambientTick=AMBIENT_INTERVAL;
        if(videoKey!=null)preferences().edit().putInt("screen_mode_"+videoKey,mode).apply();
        invalidate();
    }
    public int videoFilter(){return videoFilter;}
    public int videoFilterMode(){return videoFilterMode;}
    public boolean tvMode(){return tvMode;}
    public void setTvMode(boolean enabled){
        tvMode=enabled;
        if(videoKey!=null)preferences().edit().putBoolean("tv_mode_"+videoKey,enabled).apply();
        invalidate();
    }
    public boolean isExternal(){return external;}
    public boolean supportsScale2x(){return Build.VERSION.SDK_INT>=33&&isHardwareAccelerated();}
    public int playerIndex(){return playerIndex;}
    public void setVideoMode(int mode){
        if(mode<0||mode>5)return;
        videoMode=mode;ambientTick=AMBIENT_INTERVAL;
        if(videoKey!=null)preferences().edit().putInt("video_mode_"+videoKey,mode).apply();
        invalidate();
    }
    public void setVideoFilter(int filter){
        if(filter < -1 || filter > 2 || filter==2&&!supportsScale2x())return;
        videoFilterMode=filter;
        videoFilter=filter==-1?autoFilter():filter;
        if(videoKey!=null)preferences().edit().putInt("video_filter_"+videoKey,filter).apply();
        invalidate();
    }
    public void monitorPerformance(int actualFps,int expectedFps,int networkMs){
        if(videoFilterMode!=-1||videoFilter<1||expectedFps<45||networkMs>4)return;
        if(actualFps>=1&&actualFps<expectedFps*.85f)slowImageSeconds++;
        else slowImageSeconds=0;
        if(slowImageSeconds>=3){videoFilter--;slowImageSeconds=0;invalidate();}
    }
    private int visibleRegion(){
        if(videoMode==1)return playerIndex==0?2:playerIndex==1?3:0;
        return videoMode;
    }
    private void loadControls(){
        SharedPreferences saved=preferences();
        String prefix=videoKey==null||videoKey.isEmpty()?"":"hud_game_"+videoKey+"_";
        SharedPreferences.Editor migration=null;
        for(Control c:controls){
            String key=saved.contains(prefix+"x_"+c.id)?prefix:"hud_";
            float storedX=saved.getFloat(key+"x_"+c.id,c.defaultX);
            float storedY=saved.getFloat(key+"y_"+c.id,c.defaultY);
            // Earlier builds saved even unedited defaults. Keep only deliberate positions.
            c.customized=saved.getBoolean(key+"custom_"+c.id,
                Math.abs(storedX-c.defaultX)>.001f||Math.abs(storedY-c.defaultY)>.001f);
            c.x=c.customized?clamp(storedX,.02f,.98f):c.defaultX;
            c.y=c.customized?clamp(storedY,.02f,.98f):c.defaultY;
            String sizeKey=key+"size_"+c.id;
            float storedScale=saved.getFloat(sizeKey,c.defaultScale);
            boolean alreadyMigrated=saved.getBoolean(key+"large_size_default_v2",false);
            float upgraded=TouchSizeDefaults.migrate(storedScale,
                c.defaultScale/DEFAULT_TOUCH_SCALE,c.defaultScale,alreadyMigrated);
            c.scale=clamp(upgraded,MIN_SCALE,MAX_SCALE);
            if(!alreadyMigrated){
                if(migration==null)migration=saved.edit();
                migration.putBoolean(key+"large_size_default_v2",true);
                if(saved.contains(sizeKey)&&upgraded!=storedScale)migration.putFloat(sizeKey,c.scale);
            }
        }
        if(migration!=null)migration.apply();
        opacity=clamp(saved.getFloat(prefix+"opacity",saved.getFloat("hud_opacity",.33f)),.18f,.80f);
        analog=saved.getBoolean(prefix+"analog",saved.getBoolean("hud_analog",false));
        haptics=saved.getBoolean(prefix+"haptics",saved.getBoolean("hud_haptics",false));
        hudTheme=saved.getInt(prefix+"theme",saved.getInt("hud_theme",0));
        if(hudTheme<0||hudTheme>2)hudTheme=0;
    }
    private void loadHardwareSettings(){
        SharedPreferences saved=preferences();
        stickSensitivity=saved.getInt("stick_sensitivity",1);
        if(stickSensitivity<0||stickSensitivity>2)stickSensitivity=1;
        refreshControllerProfiles();
    }
    public void refreshControllerProfiles(){keyProfiles.clear();stickProfiles.clear();}
    public void setReplaying(boolean value){replaying=value;invalidate();}
    public boolean isReplaying(){return replaying;}
    public void updateReplayMasks(int[] masks){replayMasks=masks;}
    public Bitmap coverFrame(){
        synchronized(this){
            if(bitmap==null)return null;
            Bitmap frame=Bitmap.createBitmap(bitmap);
            Bitmap output=Bitmap.createScaledBitmap(frame,320,240,true);
            if(output!=frame)frame.recycle();
            return output;
        }
    }
    public String inputReport(){
        if(recentInputQueueMs()<0||inputDiagnostics.samples()<1)return "Entrada sem amostras recentes";
        String queue="Fila Android: média "+inputDiagnostics.average()+" ms  •  P95 "+inputDiagnostics.p95()+" ms";
        return samplingDiagnostics.samples()<1?queue:
            queue+"\nAté coleta do quadro: média "+samplingDiagnostics.average()+
            " ms  •  P95 "+samplingDiagnostics.p95()+" ms";
    }
    public int stickSensitivity(){return stickSensitivity;}
    public void setStickSensitivity(int value){
        if(value<0||value>2)return;
        stickSensitivity=value;
        preferences().edit().putInt("stick_sensitivity",value).apply();
    }
    public int recentInputQueueMs(){
        return SystemClock.uptimeMillis()-lastHardwareAtMs<10000?lastDispatchAgeMs:-1;
    }
    public HudLearning touchLearning(){return touchLearning;}
    public void saveTouchLearning(){
        if(videoKey==null||!videoKey.matches("[0-9a-f]{64}"))return;
        try{touchLearning.save(getContext().getFilesDir(),videoKey);}
        catch(IOException ignored){}
    }
    private void saveControls(){
        SharedPreferences.Editor edit=preferences().edit();
        String prefix=videoKey==null||videoKey.isEmpty()?"hud_":"hud_game_"+videoKey+"_";
        edit.putBoolean(prefix+"large_size_default_v2",true);
        for(Control c:controls){
            edit.putFloat(prefix+"x_"+c.id,c.x).putFloat(prefix+"y_"+c.id,c.y)
                .putFloat(prefix+"size_"+c.id,c.scale)
                .putBoolean(prefix+"custom_"+c.id,c.customized);
        }
        edit.putFloat(prefix+"opacity",opacity).putBoolean(prefix+"analog",analog)
            .putBoolean(prefix+"haptics",haptics).putInt(prefix+"theme",hudTheme).apply();
    }
    public void beginEditing(EditorListener listener){
        editing=true;editorListener=listener;selected=0;dragPointer=-1;
        touchMask.set(0);padHeld=false;padPointer=-1;notifySelection();invalidate();
    }
    public void endEditing(boolean save){
        if(save)saveControls();else loadControls();
        editing=false;editorListener=null;dragPointer=-1;padPointer=-1;
        touchMask.set(0);padHeld=false;invalidate();
    }
    public boolean isEditing(){return editing;}
    public boolean isAnalog(){return analog;}
    public void setAnalog(boolean value){analog=value;notifySelection();invalidate();}
    public boolean haptics(){return haptics;}
    public void setHaptics(boolean value){haptics=value;}
    public int hudTheme(){return hudTheme;}
    public void setHudTheme(int value){
        if(value<0||value>2)return;
        hudTheme=value;
        if(!editing){String prefix=videoKey==null||videoKey.isEmpty()?"hud_":"hud_game_"+videoKey+"_";
            preferences().edit().putInt(prefix+"theme",value).apply();}
        invalidate();
    }
    public void resetLayout(){
        for(Control c:controls)c.reset();
        selected=0;notifySelection();invalidate();
    }
    /** Reversible layouts: larger default, mirrored for left hand, and old compact size. */
    public void applyPreset(int preset){
        if(preset<0||preset>2)return;
        resetLayout();
        if(preset==1&&getWidth()>0&&getHeight()>0)for(Control c:controls){
            float mirrored=1f-x(c)/getWidth(),sameY=y(c)/getHeight();
            c.x=clamp(mirrored,.02f,.98f);c.y=clamp(sameY,.02f,.98f);c.customized=true;
        }
        if(preset==2)for(Control c:controls)c.scale=c.defaultScale/DEFAULT_TOUCH_SCALE;
        notifySelection();invalidate();
    }
    /** Place thumb clusters from two taps without changing the controls until the editor is saved. */
    public void applyComfortAnchors(float leftX,float leftY,float rightX,float rightY){
        if(getWidth()<1||getHeight()<1)return;
        Control pad=controls[0],shoulderL=controls[1],shoulderR=controls[2];
        place(pad,leftX,leftY);
        float step=radius(controls[3])*2.10f;
        place(controls[3],rightX,rightY-step);
        place(controls[4],rightX-step,rightY);
        place(controls[5],rightX+step,rightY);
        place(controls[6],rightX,rightY+step);
        float margin=16*getResources().getDisplayMetrics().density;
        place(shoulderL,leftX,Math.max(safeTop+halfHeight(shoulderL)+margin,leftY-radius(pad)*1.65f));
        place(shoulderR,rightX,Math.max(safeTop+halfHeight(shoulderR)+margin,rightY-step*2.35f));
        selected=0;notifySelection();invalidate();
    }
    private void place(Control c,float px,float py){
        c.x=px/getWidth();c.y=py/getHeight();c.customized=true;keepOnScreen(c);
    }
    public float getOpacity(){return opacity;}
    public void setOpacity(float value){opacity=clamp(value,.18f,.80f);invalidate();}
    public float selectedScale(){return controls[selected].scale;}
    public void setSelectedScale(float value){
        controls[selected].scale=clamp(value,MIN_SCALE,MAX_SCALE);
        if(controls[selected].customized)keepOnScreen(controls[selected]);
        invalidate();
    }
    public void selectControl(int index){
        if(index<0||index>=controls.length)return;
        selected=index;notifySelection();invalidate();
    }
    public String[] controlNames(){
        String[] names=new String[controls.length];
        for(int i=0;i<names.length;i++)names[i]=i==0?(analog?"Analógico":"Direcional"):controls[i].title;
        return names;
    }
    private void notifySelection(){
        if(editorListener!=null)
            editorListener.selected(selected==0?(analog?"Analógico":"Direcional"):controls[selected].title,
                    controls[selected].scale);
    }
    public void setExternal(boolean exists){
        if(external==exists)return;
        external=exists;
        touchMask.set(0);padHeld=false;padPointer=-1;
        if(!exists)clearHardwareInput();
        postInvalidate();
    }
    public void clearHardwareInput(){
        hardware.clear();hardwareMask=0;pendingHardwarePress.set(0);hardwareVersion++;
    }
    public void removeHardwareDevice(int deviceId){
        keyProfiles.remove(deviceId);stickProfiles.remove(deviceId);
        hardwareMask=hardware.remove(deviceId);pendingHardwarePress.set(0);hardwareVersion++;
    }
    public void clearTouchInput(){touchMask.set(0);padHeld=false;padPointer=-1;invalidate();}
    public void setInputBlocked(boolean blocked){
        inputBlocked=blocked;
        clearTouchInput();clearHardwareInput();
    }
    public void setSafeInsets(int left,int top,int right,int bottom){
        safeLeft=Math.max(0,left);safeTop=Math.max(0,top);
        safeRight=Math.max(0,right);safeBottom=Math.max(0,bottom);
        invalidate();
    }
    public int mask(){return inputBlocked?0:touchMask.get()|hardwareMask;}
    /** The emulator thread consumes brief press/release edges once, even between two frames. */
    public int maskForFrame(){
        if(inputBlocked){pendingHardwarePress.set(0);return 0;}
        int live=touchMask.get()|hardwareMask;
        int shortPress=pendingHardwarePress.getAndSet(0);
        int version=hardwareVersion;
        if(version!=sampledHardwareVersion){
            long at=lastHardwareChangeNanos,age=System.nanoTime()-at;
            if(at>0&&age>=0)samplingDiagnostics.add(age/1_000_000);
            sampledHardwareVersion=version;
        }
        return ControllerInputState.frameMask(live,shortPress);
    }
    public synchronized void image(int[] pixels,int w,int h){
        if(pixels==null||w<1||h<1||pixels.length!=w*h)return;
        if(bitmap==null||bitmap.getWidth()!=w||bitmap.getHeight()!=h)
        {
            bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            pixelScaler=null;
        }
        bitmap.setPixels(pixels,0,w,0,0,w,h);
        if(screenMode==0&&!tvMode&&(ambientBitmap==null||++ambientTick>=AMBIENT_INTERVAL)){
            ambientTick=0;
            updateAmbient(pixels,w,h);
        }
        postInvalidate();
    }
    /** Tiny, smoothed copy of the visible game region; updated off the UI thread at most 8 times/sec. */
    private void updateAmbient(int[] pixels,int width,int height){
        int region=visibleRegion();
        int left=region==5?width/2:0,right=region==4?width/2:width;
        int top=region==3?height/2:0,bottom=region==2?height/2:height;
        if(right<=left||bottom<=top)return;
        for(int y=0;y<AMBIENT_HEIGHT;y++){
            int sourceY=Math.min(bottom-1,top+(int)((y+.5f)*(bottom-top)/AMBIENT_HEIGHT));
            for(int x=0;x<AMBIENT_WIDTH;x++){
                int sourceX=Math.min(right-1,left+(int)((x+.5f)*(right-left)/AMBIENT_WIDTH));
                ambientSamples[y*AMBIENT_WIDTH+x]=pixels[sourceY*width+sourceX];
            }
        }
        for(int y=0;y<AMBIENT_HEIGHT;y++)for(int x=0;x<AMBIENT_WIDTH;x++){
            int red=0,green=0,blue=0,count=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int sy=Math.max(0,Math.min(AMBIENT_HEIGHT-1,y+dy));
                int sx=Math.max(0,Math.min(AMBIENT_WIDTH-1,x+dx));
                int color=ambientSamples[sy*AMBIENT_WIDTH+sx];
                red+=(color>>16)&255;green+=(color>>8)&255;blue+=color&255;count++;
            }
            ambientSmoothed[y*AMBIENT_WIDTH+x]=0xff000000|
                ((red/count)<<16)|((green/count)<<8)|(blue/count);
        }
        if(ambientBitmap==null)ambientBitmap=Bitmap.createBitmap(AMBIENT_WIDTH,AMBIENT_HEIGHT,Bitmap.Config.ARGB_8888);
        ambientBitmap.setPixels(ambientSmoothed,0,AMBIENT_WIDTH,0,0,AMBIENT_WIDTH,AMBIENT_HEIGHT);
    }
    private void drawAmbient(Canvas canvas,int width,int height){
        if(ambientBitmap==null)return;
        ambientDestination.set(0,0,width,height);
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setFilterBitmap(true);
        canvas.drawBitmap(ambientBitmap,null,ambientDestination,paint);
        canvas.drawColor(0xa60b1123);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        // Portrait-to-landscape resizing must never overwrite the actual saved positions.
        videoDestination.setEmpty();
        invalidate();
    }
    private float baseRadius(){
        float density=getResources().getDisplayMetrics().density;
        return Math.max(22*density,Math.min(getHeight()*.066f,40*density));
    }
    private boolean face(Control c){return c.bit==X||c.bit==Y||c.bit==A||c.bit==B;}
    private float faceRadiusLimit(float preferred){
        if(getHeight()<1)return preferred;
        float density=getResources().getDisplayMetrics().density;
        // The side cutout/gesture inset swaps sides at 180 degrees. It may move
        // the diamond but must never change the size the player chose for it.
        // Keep only the vertical cap that prevents L/R and Start from colliding.
        return HudGeometry.faceRadius(preferred,
                y(controls[2])+halfHeight(controls[2]),
                y(controls[8])-halfHeight(controls[8]),density);
    }
    private float radius(Control c){
        float preferred=baseRadius()*c.scale*(c.bit==-1?2.28f:1f);
        return face(c)&&!c.customized?faceRadiusLimit(preferred):preferred;
    }
    private float maxFaceRadius(){
        float largest=0;
        for(int i=3;i<=6;i++)largest=Math.max(largest,radius(controls[i]));
        return largest;
    }
    private float x(Control c){
        int width=getWidth();float position=c.x*width;
        if(!c.customized&&width>0){
            float gutter=Math.max(0,(width-Math.min(width,getHeight()*4f/3f))*.5f);
            float imageLeft=videoDestination.width()>0?videoDestination.left:gutter;
            float imageRight=videoDestination.width()>0?videoDestination.right:width-gutter;
            float density=getResources().getDisplayMetrics().density;
            if(c.bit==-1||c.bit==L||c.bit==SELECT)
                position=HudGeometry.leftCluster(width,imageLeft,radius(controls[0]),safeLeft,density);
            else{
                float faceRadius=maxFaceRadius();
                float center=HudGeometry.rightCluster(width,imageRight,faceRadius,safeRight,density);
                float step=HudGeometry.faceStep(width,imageRight,faceRadius,safeRight,density);
                position=center+(c.bit==Y?-step:c.bit==A?step:0);
            }
        }
        return HudGeometry.visible(position,width,halfWidth(c),safeLeft,safeRight);
    }
    private float y(Control c){
        int height=getHeight();float position=c.y*height;
        if(!c.customized&&height>0){
            if(c.bit==X||c.bit==Y||c.bit==A||c.bit==B){
                int width=getWidth();float density=getResources().getDisplayMetrics().density;
                float gutter=Math.max(0,(width-Math.min(width,height*4f/3f))*.5f);
                float imageRight=videoDestination.width()>0?videoDestination.right:width-gutter;
                float faceRadius=maxFaceRadius();
                float step=HudGeometry.faceStep(width,imageRight,faceRadius,safeRight,density);
                float center=HudGeometry.faceCenterY(height,
                        y(controls[2])+halfHeight(controls[2]),
                        y(controls[8])-halfHeight(controls[8]),faceRadius,step,density);
                position=center+(c.bit==X?-step:c.bit==B?step:0);
            }
        }
        return HudGeometry.visible(position,height,halfHeight(c),safeTop,safeBottom);
    }
    private boolean capsule(Control c){return c.bit==L||c.bit==R||c.bit==SELECT||c.bit==START;}
    private float halfWidth(Control c){
        if(c.bit==L||c.bit==R)return baseRadius()*c.scale*1.55f;
        if(c.bit==SELECT||c.bit==START)return baseRadius()*c.scale*1.35f;
        return radius(c);
    }
    private float halfHeight(Control c){
        return capsule(c)?baseRadius()*c.scale*.57f:radius(c);
    }
    private void keepOnScreen(Control c){
        if(getWidth()<1||getHeight()<1)return;
        c.x=HudGeometry.visible(c.x*getWidth(),getWidth(),halfWidth(c),safeLeft,safeRight)/getWidth();
        c.y=HudGeometry.visible(c.y*getHeight(),getHeight(),halfHeight(c),safeTop,safeBottom)/getHeight();
    }
    private int shade(int rgb,int alpha){
        return Color.argb(alpha,Color.red(rgb),Color.green(rgb),Color.blue(rgb));
    }
    private int fill(int rgb,boolean pressed){
        return shade(pressed?themeAccent():rgb,Math.min(235,(int)(opacity*255)+(pressed?95:0)));
    }
    private int themeAccent(){return hudTheme==1?0xff7ceae1:hudTheme==2?0xffe2c789:ACCENT;}
    private int themePad(){return hudTheme==1?0xff264a57:hudTheme==2?0xff454b55:0xff34456e;}
    private int themeButton(boolean primary){
        if(hudTheme==1)return primary?0xff387779:0xff385970;
        if(hudTheme==2)return primary?0xff6d6254:0xff545d6b;
        return primary?0xff744fa8:0xff465d91;
    }
    private void text(Canvas canvas,String value,float cx,float cy,float size,int alpha){
        paint.setStyle(Paint.Style.FILL);paint.setColor(shade(INK,alpha));
        paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value,cx,cy-(paint.ascent()+paint.descent())/2,paint);
    }
    private void ring(Canvas canvas,float cx,float cy,float r,int color,float stroke){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(color);
        canvas.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void drawPad(Canvas canvas,Control c,int pressed){
        float cx=x(c),cy=y(c),r=radius(c),d=getResources().getDisplayMetrics().density;
        boolean moving=(pressed&((1<<UP)|(1<<DOWN)|(1<<LEFT)|(1<<RIGHT)))!=0;
        if(analog){
            ring(canvas,cx,cy,r,fill(themeButton(false),moving),Math.max(3*d,r*.13f));
            paint.setColor(shade(0xff121a33,(int)(opacity*210)));
            canvas.drawCircle(cx,cy,r*.82f,paint);
            float dx=padHeld?padX-cx:0,dy=padHeld?padY-cy:0;
            float length=(float)Math.hypot(dx,dy),max=r*.52f;
            if(length>max){dx*=max/length;dy*=max/length;}
            paint.setColor(fill(themeAccent(),moving));canvas.drawCircle(cx+dx,cy+dy,r*.39f,paint);
            ring(canvas,cx+dx,cy+dy,r*.39f,shade(INK,150),1.2f*d);
        }else{
            paint.setColor(fill(themePad(),false));
            float arm=r*.95f,half=r*.34f;
            canvas.drawRoundRect(cx-half,cy-arm,cx+half,cy+arm,half*.32f,half*.32f,paint);
            canvas.drawRoundRect(cx-arm,cy-half,cx+arm,cy+half,half*.32f,half*.32f,paint);
            float off=r*.62f;
            paint.setColor(shade(themeAccent(),Math.min(220,(int)(opacity*255)+85)));
            float mark=r*.31f;
            if((pressed&(1<<UP))!=0)canvas.drawRoundRect(cx-mark,cy-r*.94f,cx+mark,cy-r*.38f,mark*.25f,mark*.25f,paint);
            if((pressed&(1<<DOWN))!=0)canvas.drawRoundRect(cx-mark,cy+r*.38f,cx+mark,cy+r*.94f,mark*.25f,mark*.25f,paint);
            if((pressed&(1<<LEFT))!=0)canvas.drawRoundRect(cx-r*.94f,cy-mark,cx-r*.38f,cy+mark,mark*.25f,mark*.25f,paint);
            if((pressed&(1<<RIGHT))!=0)canvas.drawRoundRect(cx+r*.38f,cy-mark,cx+r*.94f,cy+mark,mark*.25f,mark*.25f,paint);
            text(canvas,"▲",cx,cy-off,r*.29f,(pressed&(1<<UP))!=0?255:195);
            text(canvas,"▼",cx,cy+off,r*.29f,(pressed&(1<<DOWN))!=0?255:195);
            text(canvas,"◀",cx-off,cy,r*.29f,(pressed&(1<<LEFT))!=0?255:195);
            text(canvas,"▶",cx+off,cy,r*.29f,(pressed&(1<<RIGHT))!=0?255:195);
            paint.setColor(shade(0xff7f93c9,145));canvas.drawCircle(cx,cy,r*.20f,paint);
        }
        if(editing&&selected==0)ring(canvas,cx,cy,r*1.1f,shade(themeAccent(),235),2*d);
    }
    private void drawButton(Canvas canvas,Control c,int pressed){
        float cx=x(c),cy=y(c),halfW=halfWidth(c),halfH=halfHeight(c);
        float d=getResources().getDisplayMetrics().density;
        boolean down=(pressed&(1<<c.bit))!=0;
        paint.setColor(fill(themeButton(c.bit==A||c.bit==B),down));
        if(capsule(c)){
            canvas.drawRoundRect(cx-halfW,cy-halfH,cx+halfW,cy+halfH,halfH,halfH,paint);
        }else canvas.drawCircle(cx,cy,radius(c),paint);
        if(down&&!capsule(c))ring(canvas,cx,cy,radius(c)*.85f,shade(INK,190),1.6f*d);
        if(editing&&controls[selected]==c){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);
            paint.setColor(shade(themeAccent(),235));
            if(capsule(c))canvas.drawRoundRect(cx-halfW-4,cy-halfH-4,cx+halfW+4,cy+halfH+4,halfH,halfH,paint);
            else canvas.drawCircle(cx,cy,radius(c)+5,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float font=(c.bit==SELECT||c.bit==START)?Math.max(10*d,baseRadius()*.34f):baseRadius()*.72f;
        text(canvas,c.title.toUpperCase(java.util.Locale.ROOT),cx,cy,font,down?255:210);
    }
    @Override protected synchronized void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(BG);
        int width=getWidth(),height=getHeight();
        if(bitmap!=null){
            int imageWidth=bitmap.getWidth(),imageHeight=bitmap.getHeight();
            int region=visibleRegion();
            switch(region){
                case 2:videoSource.set(0,0,imageWidth,imageHeight/2);break;
                case 3:videoSource.set(0,imageHeight/2,imageWidth,imageHeight);break;
                case 4:videoSource.set(0,0,imageWidth/2,imageHeight);break;
                case 5:videoSource.set(imageWidth/2,0,imageWidth,imageHeight);break;
                default:videoSource.set(0,0,imageWidth,imageHeight);break;
            }
            if(videoSource.width()>0&&videoSource.height()>0){
                // SNES pixels are not necessarily square: a half-screen region retains
                // the proportions of the original 4:3 TV picture.
                float aspect=VideoGeometry.aspect(4f/3f,imageWidth,imageHeight,
                        videoSource.width(),videoSource.height());
                float safe=tvMode?.94f:1f;
                int layout=screenMode;
                float availableWidth=width*safe,availableHeight=height*safe;
                if(layout==2){
                    // Full-bleed can only preserve aspect by cropping; opt-in per ROM.
                    float target=availableWidth/availableHeight;
                    int originalW=videoSource.width(),originalH=videoSource.height();
                    int cropW=Math.max(1,Math.min(originalW,Math.round(originalW*
                            VideoGeometry.cropWidthFraction(aspect,target))));
                    int cropH=Math.max(1,Math.min(originalH,Math.round(originalH*
                            VideoGeometry.cropHeightFraction(aspect,target))));
                    videoSource.inset((originalW-cropW)/2,(originalH-cropH)/2);
                    // Inset rounds down for odd differences. Keep the precise crop size.
                    videoSource.right=videoSource.left+cropW;
                    videoSource.bottom=videoSource.top+cropH;
                    videoDestination.set((width-availableWidth)/2,(height-availableHeight)/2,
                            (width+availableWidth)/2,(height+availableHeight)/2);
                }else{
                    float drawWidth=Math.min(availableWidth,availableHeight*aspect),drawHeight=drawWidth/aspect;
                    videoDestination.set((width-drawWidth)/2,(height-drawHeight)/2,
                            (width+drawWidth)/2,(height+drawHeight)/2);
                    if(layout==0&&!tvMode&&(drawWidth<width-1||drawHeight<height-1))
                        drawAmbient(canvas,width,height);
                }
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                if(videoFilter==2&&supportsScale2x()){
                    try{
                        if(pixelScaler==null)pixelScaler=new PixelScaler(bitmap);
                        pixelScaler.draw(canvas,paint,videoSource,videoDestination);
                    }catch(RuntimeException ex){
                        // A shader or driver failure must never end a game or a LAN session.
                        pixelScaler=null;videoFilter=1;
                        if(videoFilterMode==-1)preferences().edit().putBoolean("auto_shader_failed",true).apply();
                        else if(videoKey!=null){videoFilterMode=1;preferences().edit().putInt("video_filter_"+videoKey,1).apply();}
                        paint.setShader(null);paint.setFilterBitmap(false);
                        canvas.drawBitmap(bitmap,videoSource,videoDestination,paint);
                    }
                }else{
                    paint.setFilterBitmap(videoFilter==0);
                    canvas.drawBitmap(bitmap,videoSource,videoDestination,paint);
                }
                if(layout==0&&!tvMode){
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(getResources().getDisplayMetrics().density*1.2f);
                    paint.setColor(0x805c6fa6);
                    canvas.drawRect(videoDestination,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }else{
            text(canvas,"LUNI ARENA",width*.5f,height*.5f,24*getResources().getDisplayMetrics().density,210);
        }
        if(replaying&&!editing){
            float density=getResources().getDisplayMetrics().density;
            text(canvas,"LUNI REPLAY  •  COMANDOS DOS CINCO JOGADORES",width*.5f,27*density,12*density,225);
            int[] replay=replayMasks;
            if(replay!=null){
                float unit=Math.min(76*density,width/5.4f),left=(width-unit*5)/2f,top=height-33*density;
                for(int player=0;player<5;player++){
                    int bits=replay[player];float cx=left+unit*(player+.5f);
                    paint.setColor(bits==0?0x8a222a46:0xc85b4696);
                    canvas.drawRoundRect(cx-unit*.46f,top,cx+unit*.46f,top+27*density,8*density,8*density,paint);
                    String buttons="P"+(player+1)+" "+
                        ((bits&(1<<UP))!=0?"↑":(bits&(1<<DOWN))!=0?"↓":(bits&(1<<LEFT))!=0?"←":(bits&(1<<RIGHT))!=0?"→":"·")+
                        ((bits&(1<<B))!=0?"B":"")+((bits&(1<<A))!=0?"A":"");
                    text(canvas,buttons,cx,top+13*density,11*density,238);
                }
            }
            return;
        }
        if(external&&!editing)return;
        int pressed=mask();
        drawPad(canvas,controls[0],pressed);
        for(int i=1;i<controls.length;i++)drawButton(canvas,controls[i],pressed);
    }

    /** Edge-aware Scale2x on the GPU. Runs only while the user selects the effect. */
    private static final class PixelScaler {
        private static final String SCALE2X =
                "uniform shader image;\n"+
                "uniform float4 crop;\n"+
                "uniform float4 dest;\n"+
                "half4 samplePixel(float2 p) {\n"+
                "  return image.eval(clamp(p, crop.xy, crop.xy+crop.zw-float2(1.0)) + float2(0.5));\n"+
                "}\n"+
                "bool same(half4 a, half4 b) {\n"+
                "  half3 diff=a.rgb-b.rgb;\n"+
                "  return dot(diff,diff)<0.000001;\n"+
                "}\n"+
                "half4 main(float2 fragCoord) {\n"+
                "  float2 pos=(fragCoord-dest.xy)/dest.zw*crop.zw+crop.xy;\n"+
                "  float2 center=floor(pos);\n"+
                "  float2 corner=fract(pos);\n"+
                "  half4 e=samplePixel(center);\n"+
                "  half4 b=samplePixel(center+float2(0.0,-1.0));\n"+
                "  half4 d=samplePixel(center+float2(-1.0,0.0));\n"+
                "  half4 f=samplePixel(center+float2(1.0,0.0));\n"+
                "  half4 h=samplePixel(center+float2(0.0,1.0));\n"+
                "  if (same(b,h) || same(d,f)) return e;\n"+
                "  if (corner.x<0.5) {\n"+
                "    if (corner.y<0.5) return same(d,b)?d:e;\n"+
                "    return same(d,h)?d:e;\n"+
                "  }\n"+
                "  if (corner.y<0.5) return same(b,f)?f:e;\n"+
                "  return same(h,f)?f:e;\n"+
                "}\n";
        private final RuntimeShader shader;
        PixelScaler(Bitmap bitmap){
            BitmapShader source=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            source.setFilterMode(BitmapShader.FILTER_MODE_NEAREST);
            shader=new RuntimeShader(SCALE2X);
            shader.setInputShader("image",source);
        }
        void draw(Canvas canvas,Paint paint,Rect crop,RectF destination){
            shader.setFloatUniform("crop",crop.left,crop.top,crop.width(),crop.height());
            shader.setFloatUniform("dest",destination.left,destination.top,destination.width(),destination.height());
            paint.setShader(shader);
            try{canvas.drawRect(destination,paint);}
            finally{paint.setShader(null);}
        }
    }
    private Control hit(float px,float py){
        Control closest=null;float distance=Float.MAX_VALUE;
        float minimum=24*getResources().getDisplayMetrics().density;
        for(Control c:controls){
            float dx=px-x(c),dy=py-y(c);
            float hitX=Math.max(minimum,halfWidth(c)*1.20f);
            float hitY=Math.max(minimum,halfHeight(c)*1.30f);
            if(face(c)&&!c.customized){
                float density=getResources().getDisplayMetrics().density;
                int width=getWidth();
                float gutter=Math.max(0,(width-Math.min(width,getHeight()*4f/3f))*.5f);
                float imageRight=videoDestination.width()>0?videoDestination.right:width-gutter;
                float step=HudGeometry.faceStep(width,imageRight,maxFaceRadius(),safeRight,density);
                hitX=hitY=HudGeometry.faceHitRadius(radius(c),step,density);
            }
            float normalized=dx*dx/(hitX*hitX)+dy*dy/(hitY*hitY);
            if(normalized<=1.0f&&normalized<distance){distance=normalized;closest=c;}
        }
        return closest;
    }
    private int direction(float px,float py,Control pad){
        float r=radius(pad),dx=(px-x(pad))/r,dy=(py-y(pad))/r;
        if(Math.hypot(dx,dy)<(analog?.25f:.19f))return 0;
        int direction=0;
        if(dx<-.23f)direction|=1<<LEFT;else if(dx>.23f)direction|=1<<RIGHT;
        if(dy<-.23f)direction|=1<<UP;else if(dy>.23f)direction|=1<<DOWN;
        return direction;
    }
    private boolean editTouch(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(dragPointer==-1){
                Control target=hit(e.getX(index),e.getY(index));
                if(target!=null){
                    for(int i=0;i<controls.length;i++)if(controls[i]==target)selected=i;
                    dragPointer=e.getPointerId(index);
                    dragOffsetX=e.getX(index)-x(target);
                    dragOffsetY=e.getY(index)-y(target);
                    notifySelection();invalidate();
                }
            }
        }else if(action==MotionEvent.ACTION_MOVE&&dragPointer!=-1){
            int pointer=e.findPointerIndex(dragPointer);
            if(pointer>=0&&getWidth()>0&&getHeight()>0){
                Control c=controls[selected];
                c.x=(e.getX(pointer)-dragOffsetX)/getWidth();
                c.y=(e.getY(pointer)-dragOffsetY)/getHeight();
                c.customized=true;
                keepOnScreen(c);invalidate();
            }
        }else if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP||
                (action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(index)==dragPointer)){
            dragPointer=-1;
        }
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(inputBlocked)return true;
        if(editing)return editTouch(e);
        if(external)return false;
        int action=e.getActionMasked(),bits=0;
        padHeld=false;
        if(action!=MotionEvent.ACTION_CANCEL&&action!=MotionEvent.ACTION_UP){
            boolean padFound=false;
            for(int i=0;i<e.getPointerCount();i++){
                if(action==MotionEvent.ACTION_POINTER_UP&&i==e.getActionIndex())continue;
                int id=e.getPointerId(i);
                if(id==padPointer){
                    Control pad=controls[0];
                    if(Math.hypot(e.getX(i)-x(pad),e.getY(i)-y(pad))<radius(pad)*2.2f){
                        padHeld=true;padFound=true;padX=e.getX(i);padY=e.getY(i);
                        bits|=direction(padX,padY,pad);continue;
                    }
                    padPointer=-1;
                }
                Control c=hit(e.getX(i),e.getY(i));
                if(c==null)continue;
                if(!replaying&&(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)&&
                   i==e.getActionIndex()){
                    for(int slot=0;slot<controls.length;slot++)if(controls[slot]==c){
                        float r=Math.max(1,radius(c));
                        touchLearning.observe(slot,(e.getX(i)-x(c))/r,(e.getY(i)-y(c))/r);
                        break;
                    }
                }
                if(c.bit==-1){
                    if(!padHeld){
                        padHeld=true;padFound=true;padPointer=id;
                        padX=e.getX(i);padY=e.getY(i);
                        bits|=direction(padX,padY,c);
                    }
                }else bits|=1<<c.bit;
            }
            if(!padFound)padPointer=-1;
        }else padPointer=-1;
        int before=touchMask.getAndSet(bits);
        if(haptics&&(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)&&
            (bits&~before)!=0)performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if(before!=bits||analog)invalidate();
        return true;
    }
    // Cached mappings avoid accessing preferences on each Bluetooth key event.
    private int map(int code,int deviceId){
        int[] profile=keyProfiles.get(deviceId);
        if(profile==null){
            profile=ControllerProfiles.load(preferences(),InputDevice.getDevice(deviceId));
            keyProfiles.put(deviceId,profile);
        }
        for(int bit=0;bit<profile.length;bit++)
            if(profile[bit]==code)return bit;
        switch(code){
            case KeyEvent.KEYCODE_BUTTON_A:return B;case KeyEvent.KEYCODE_BUTTON_B:return A;
            case KeyEvent.KEYCODE_BUTTON_X:return Y;case KeyEvent.KEYCODE_BUTTON_Y:return X;
            case KeyEvent.KEYCODE_BUTTON_L1:return L;case KeyEvent.KEYCODE_BUTTON_R1:return R;
            case KeyEvent.KEYCODE_BUTTON_L2:return L;case KeyEvent.KEYCODE_BUTTON_R2:return R;
            case KeyEvent.KEYCODE_BUTTON_START:return START;case KeyEvent.KEYCODE_BUTTON_SELECT:return SELECT;
            case KeyEvent.KEYCODE_DPAD_UP:return UP;case KeyEvent.KEYCODE_DPAD_DOWN:return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:return LEFT;case KeyEvent.KEYCODE_DPAD_RIGHT:return RIGHT;
            default:return -1;
        }
    }
    private void recordDispatch(long eventTime){
        long now=SystemClock.uptimeMillis(),age=now-eventTime;
        if(age>=0&&age<=1000){lastDispatchAgeMs=(int)age;lastHardwareAtMs=now;inputDiagnostics.add(age);}
    }
    private void publishHardware(int before,int after,long eventTime){
        if(before==after)return;
        int pressed=after&~before;
        if(pressed!=0)pendingHardwarePress.getAndAccumulate(pressed,(previous,edge)->previous|edge);
        hardwareMask=after;
        lastHardwareChangeNanos=System.nanoTime();
        hardwareVersion++;
        recordDispatch(eventTime);
    }
    public boolean handleHardwareKey(KeyEvent event){
        if(inputBlocked)return false;
        int bit=map(event.getKeyCode(),event.getDeviceId()),action=event.getAction();
        if(bit<0||action!=KeyEvent.ACTION_DOWN&&action!=KeyEvent.ACTION_UP)return false;
        if(action==KeyEvent.ACTION_DOWN&&event.getRepeatCount()>0)return true;
        int before=hardwareMask;
        int after=hardware.key(event.getDeviceId(),event.getKeyCode(),bit,action==KeyEvent.ACTION_DOWN);
        publishHardware(before,after,event.getEventTime());
        return true;
    }
    @Override public boolean onKeyDown(int code,KeyEvent e){
        return handleHardwareKey(e)||super.onKeyDown(code,e);
    }
    @Override public boolean onKeyUp(int code,KeyEvent e){
        return handleHardwareKey(e)||super.onKeyUp(code,e);
    }
    public boolean handleHardwareMotion(MotionEvent e){
        if(inputBlocked)return false;
        if((e.getSource()&InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK)return false;
        if(e.getActionMasked()!=MotionEvent.ACTION_MOVE&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL)return false;
        int deviceId=e.getDeviceId(),before=hardwareMask;
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            publishHardware(before,hardware.cancelAxes(deviceId),e.getEventTime());return true;
        }
        StickProfile stick=stickProfiles.get(deviceId);
        if(stick==null||stick.source!=e.getSource()){
            stick=new StickProfile(e.getDevice(),e.getSource());stickProfiles.put(deviceId,stick);
        }
        float pressX=ControllerAxis.press(STICK_PRESS[stickSensitivity],stick.flatX);
        float pressY=ControllerAxis.press(STICK_PRESS[stickSensitivity],stick.flatY);
        // A batched event may contain a full press and release; keep its edge for one frame.
        for(int i=0;i<=e.getHistorySize();i++){
            boolean latest=i==e.getHistorySize();
            float x=latest?e.getAxisValue(MotionEvent.AXIS_X):e.getHistoricalAxisValue(MotionEvent.AXIS_X,i);
            float y=latest?e.getAxisValue(MotionEvent.AXIS_Y):e.getHistoricalAxisValue(MotionEvent.AXIS_Y,i);
            float hatX=latest?e.getAxisValue(MotionEvent.AXIS_HAT_X):e.getHistoricalAxisValue(MotionEvent.AXIS_HAT_X,i);
            float hatY=latest?e.getAxisValue(MotionEvent.AXIS_HAT_Y):e.getHistoricalAxisValue(MotionEvent.AXIS_HAT_Y,i);
            float lt=Math.max(latest?e.getAxisValue(MotionEvent.AXIS_LTRIGGER):e.getHistoricalAxisValue(MotionEvent.AXIS_LTRIGGER,i),
                latest?e.getAxisValue(MotionEvent.AXIS_BRAKE):e.getHistoricalAxisValue(MotionEvent.AXIS_BRAKE,i));
            float rt=Math.max(latest?e.getAxisValue(MotionEvent.AXIS_RTRIGGER):e.getHistoricalAxisValue(MotionEvent.AXIS_RTRIGGER,i),
                latest?e.getAxisValue(MotionEvent.AXIS_GAS):e.getHistoricalAxisValue(MotionEvent.AXIS_GAS,i));
            int after=hardware.axes(deviceId,x,y,hatX,hatY,lt,rt,pressX,pressY,stick.flatX,stick.flatY);
            publishHardware(before,after,latest?e.getEventTime():e.getHistoricalEventTime(i));
            before=after;
        }
        return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e){
        return handleHardwareMotion(e)||super.onGenericMotionEvent(e);
    }
}
