package br.com.knightjoga;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.*;
import android.hardware.input.InputManager;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import br.com.knightjoga.RomLibrary.Game;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import android.util.LruCache;

public final class MainActivity extends Activity implements InputManager.InputDeviceListener {
 private static final int PICK_FOLDER=42, PICK_FILE=43, PICK_COVER=44, PICK_SAVE_EXPORT=45,
  PICK_SAVE_IMPORT=46, PICK_REPLAY_EXPORT=47, PICK_REPLAY_IMPORT=48,
  PICK_IMPORT_FOLDER=49,
  BG=0xff0c1123, PANEL=0xff1b233e, PURPLE=0xff9568ff, MUTED=0xffa9b4d0,
  CYAN=0xff7ee4dc, BORDER=0xff405276;
 private LinearLayout root,library,recentRail,recentSection;
 private TextView status,featuredTitle,libraryCount,lobbyPlayers,roomDelayLabel,roomHealthLabel,liveRoomHealth;
 private LinearLayout lobbySeatStrip,liveSeatStrip;
 private View reconnectBanner;
 private TextView reconnectMessage;
 private volatile boolean reconnectNow;
 private ImageView featuredCover;
 private HorizontalScrollView landscapeRail;
 private TextView landscapeMeta,landscapeSearch,landscapeFavorites;
 private View landscapeMenuAnchor;
 private final ArrayList<Game> landscapeOrder=new ArrayList<>();
 private boolean landscapeHub;
 private int landscapeTotalVisible;
 private int landscapeStickDirection;
 private long landscapeStickAt;
 private GameView gameView;
 private FrameLayout gameFrame;
 private View gameOverlay;
 private int lastLibraryWidth, estimatedLibraryWidth;
 private View controlsEditor,editorToggle,comfortOverlay;
 private AlertDialog controlSettingsDialog;
 private TextView selectedControl,controlMode;
 private SeekBar controlSize,controlOpacity;
 private boolean updatingControlSlider;
 private final ArrayList<Game> games=new ArrayList<>();
 private final ArrayList<Game> duplicates=new ArrayList<>();
 private Game selected;
 private String searchQuery="",pendingArchiveId;
 private boolean onlyFavorites;
 private int visibleCards=48;
 private RoomInvite incomingInvite;
 private volatile boolean playing=false;
 private volatile boolean paused=false;
 private volatile boolean activeSolo=false;
 private volatile boolean activeMultitap=true;
 private volatile boolean launchPending=false;
 private volatile boolean coreReady=false;
 private volatile int actualFps=0,networkWaitMs=0,networkWorstMs=0;
 private volatile String activeRomId=null;
 private volatile String activeCoreHash=null;
 private int scanEpoch=0;
 private boolean hubVisible=false,activityResumed=false,skipNextHubResumeScan=false;
 private Thread gameThread;
 private View gameMenuAnchor;
 private boolean resetRequested;
 private volatile boolean recording,replaying,rewindEnabled,ghostMode;
 private volatile float soloSpeed=1f;
 private ReplayArchive.Recorder replayRecorder;
 private final ArrayList<MomentArchive.Mark> replayMoments=new ArrayList<>();
 private ReplayArchive.Player ghostPlayer;
 private final RewindBuffer rewindBuffer=new RewindBuffer();
 private final InputDiagnostics networkDiagnostics=new InputDiagnostics(10000);
 private volatile long emulatedFrames;
 private AudioOutput activeAudio;
 private final ConcurrentLinkedQueue<Runnable> emulatorCommands=new ConcurrentLinkedQueue<>();
 private LanSession lan;
 private CouchSession couchHost,couchClient;
 private TextView couchSeats;
 private TextView partyBanner;
 private volatile boolean controllerOnly;
 private TextView controllerVote;
 private boolean controllerVoteActive;
 private boolean preserveCouchPending;
 private InputManager inputManager;
 private final Handler deviceHandler=new Handler(Looper.getMainLooper());
 private final Runnable delayedControllerRefresh=this::refreshControllerMode;
 private final ExecutorService covers = Executors.newFixedThreadPool(3);
 private final ConcurrentHashMap<String,Long> coverFailures=new ConcurrentHashMap<>();
 private final ConcurrentHashMap<String,Object> coverLocks=new ConcurrentHashMap<>();
 private final ExecutorService cheatIo=Executors.newSingleThreadExecutor();
 private final ExecutorService libraryIo=Executors.newSingleThreadExecutor();
 private final LruCache<String,Bitmap> coverCache = new LruCache<String,Bitmap>(8*1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getByteCount();}};
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);inputManager=(InputManager)getSystemService(INPUT_SERVICE);inputManager.registerInputDeviceListener(this,new Handler(getMainLooper()));
  if(!getPreferences(0).getBoolean("luni_original_art_v1",false))
   getPreferences(0).edit().putBoolean("preview_community_covers",false).putBoolean("luni_original_art_v1",true).apply();
  rewindEnabled=getPreferences(0).getBoolean("rewind_enabled",false);home();acceptInvite(getIntent());}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptInvite(intent);}
 private void acceptInvite(Intent intent){
  if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction())||intent.getData()==null)return;
  try{
   RoomInvite link=RoomInvite.parse(intent.getData().toString());
   if(playing||launchPending){toast("Conclua a partida ou sala atual antes de abrir outro convite.");return;}
   incomingInvite=link;prepare("join");
  }catch(IOException e){toast("Convite inválido: "+e.getMessage());}
 }
 private GradientDrawable panel(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
 private GradientDrawable galaxy(int radius){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff433065,0xff252950,0xff152942});
  d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff695a98);return d;
 }
 private GradientDrawable surface(int color,int radius,int border){
  GradientDrawable d=panel(color,dp(radius));d.setStroke(dp(1),border);return d;
 }
 private void touchFeedback(View view){
  view.setForeground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x557ee4dc),null,panel(-1,dp(15))));
 }
 private TextView eyebrow(String title){
  TextView v=label(title.toUpperCase(Locale.ROOT),10,CYAN,true);
  v.setLetterSpacing(.13f);v.setPadding(0,0,0,dp(7));return v;
 }
 private TextView chip(String title,int color){
  TextView v=label(title,11,color,true);v.setGravity(Gravity.CENTER);
  v.setPadding(dp(12),dp(7),dp(12),dp(7));v.setBackground(surface(0xff263353,99,0xff43567d));
  return v;
 }
 private TextView navigation(String symbol,String title,String description,Runnable action){
  String text=symbol+"   "+title+"   ›\n"+description;
  android.text.SpannableString styled=new android.text.SpannableString(text);
  styled.setSpan(new android.text.style.ForegroundColorSpan(CYAN),0,symbol.length(),
   android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  int start=text.indexOf('\n')+1;
  styled.setSpan(new android.text.style.ForegroundColorSpan(MUTED),start,text.length(),
   android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  styled.setSpan(new android.text.style.RelativeSizeSpan(.83f),start,text.length(),
   android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  TextView tile=label("",13,-1,true);tile.setText(styled);
  tile.setLineSpacing(dp(4),1f);tile.setPadding(dp(16),dp(12),dp(14),dp(12));
  tile.setMinimumHeight(dp(66));
  tile.setBackground(surface(0xff202c4b,16,0xff465579));touchFeedback(tile);
  tile.setFocusable(true);
  tile.setOnFocusChangeListener((v,focused)->tile.setBackground(surface(
   focused?0xff303858:0xff222d4b,15,focused?CYAN:0xff33415f)));
  tile.setOnClickListener(v->action.run());return tile;
 }
 private AlertDialog showSheet(String title,String subtitle,LinearLayout content){
  LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);
  frame.setPadding(dp(18),dp(16),dp(18),dp(18));frame.setBackground(galaxy(22));
  View accent=new View(this);
  accent.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
    new int[]{PURPLE,CYAN,0xff5273cb}));
  add(frame,accent,dp(14));
  LinearLayout.LayoutParams accentPlace=(LinearLayout.LayoutParams)accent.getLayoutParams();
  accentPlace.height=dp(3);accent.setLayoutParams(accentPlace);
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
  ImageView emblem=new ImageView(this);emblem.setImageResource(R.drawable.luni_icon);
  top.addView(emblem,new LinearLayout.LayoutParams(dp(39),dp(39)));
  LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);titles.setPadding(dp(9),0,dp(3),0);
  TextView heading=label(title,19,-1,true);heading.setPadding(0,0,0,0);
  heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);titles.addView(heading);
  TextView sub=label(subtitle,11,CYAN,false);sub.setPadding(0,dp(3),0,0);
  sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);titles.addView(sub);
  top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  TextView close=chip("✕",MUTED);touchFeedback(close);top.addView(close);add(frame,top,dp(14));
  frame.addView(content,new LinearLayout.LayoutParams(-1,-2));
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(frame);
  AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).create();
  close.setOnClickListener(v->dialog.dismiss());dialog.show();
  dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
  int width=Math.min(dp(520),getResources().getDisplayMetrics().widthPixels-dp(24));
  frame.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
   View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  dialog.getWindow().setLayout(width,
   Math.min(getResources().getDisplayMetrics().heightPixels-dp(36),
    Math.max(dp(180),frame.getMeasuredHeight()+dp(18))));
  if(getPreferences(0).getBoolean("menu_animations",true)){
   frame.setAlpha(0);frame.setTranslationY(dp(9));
   frame.animate().alpha(1).translationY(0).setDuration(170).start();
  }
  return dialog;
 }
 private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(null,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(12,12,12,12);return v;}
 private void add(LinearLayout container,View view,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,margin);container.addView(view,p);}
 private TextView action(String title,int color,Runnable listener){TextView v=label(title,15,-1,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(13),dp(10),dp(13));v.setMinHeight(dp(52));v.setBackground(panel(color,dp(15)));touchFeedback(v);v.setFocusable(true);v.setOnClickListener(w->listener.run());return v;}
 private void info(String s){runOnUiThread(()->{if(status!=null)status.setText(s);});}
 private void lanInfo(String s){
  info(s);
  runOnUiThread(()->{if(lobbyPlayers!=null&&lan!=null){
    lobbyPlayers.setText(lan.count()+" / "+(lan.multitapEnabled()?5:2)+" jogadores · vagas em tempo real");
    renderLanSeats(lobbySeatStrip,lan.seatStates());
   }
   if(liveSeatStrip!=null&&lan!=null)renderLanSeats(liveSeatStrip,lan.seatStates());
   if(roomDelayLabel!=null&&lan!=null)roomDelayLabel.setText("Reserva de entrada: "+
    (lan.automaticDelay()?"automática · ":"manual · ")+lan.inputDelayFrames()+" quadros  ▾");
   if(roomHealthLabel!=null&&lan!=null)roomHealthLabel.setText(lan.diagnosticReport());
   if(liveRoomHealth!=null&&lan!=null)liveRoomHealth.setText(lan.diagnosticReport());
   if(playing&&lan!=null&&lan.player()==0&&(s.contains("caiu")||s.contains("voltou")||s.contains("fora de sincronia")))
    toast(s);
  });
 }
 private void renderLanSeats(LinearLayout strip,int[] seats){
  if(strip==null)return;
  strip.removeAllViews();
  String[] emblems={"♞","✦","◈","✸","◆"};
  int[] hues={PURPLE,CYAN,0xfff291bd,0xffffc979,0xff9cbbff};
  for(int i=0;i<seats.length;i++){
   final int state=seats[i];
   LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER);
   tile.setPadding(dp(5),dp(8),dp(5),dp(8));
   tile.setBackground(surface(state==1?0xff273658:0xff1b2641,17,state==1?hues[i]:BORDER));
   TextView avatar=label(emblems[i],26,state==1?hues[i]:MUTED,true);
   avatar.setGravity(Gravity.CENTER);avatar.setPadding(0,0,0,0);
   avatar.setBackground(surface(state==1?0xff36456a:0xff26314e,26,state==1?hues[i]:BORDER));
   tile.addView(avatar,new LinearLayout.LayoutParams(dp(47),dp(47)));
   TextView name=label("P"+(i+1)+(i==0?" · HOST":""),11,-1,true);
   name.setGravity(Gravity.CENTER);name.setPadding(0,dp(3),0,0);tile.addView(name);
   TextView status=label(state==1?"● ONLINE":state==2?"↻ VOLTANDO":"○ LIVRE",
    9,state==1?CYAN:state==2?0xffffc979:MUTED,true);
   status.setGravity(Gravity.CENTER);status.setPadding(0,0,0,0);tile.addView(status);
   LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(dp(i==0?102:89),dp(107));
   cell.rightMargin=dp(7);strip.addView(tile,cell);
   tile.setAlpha(0.45f);tile.animate().alpha(1f).setDuration(160).start();
  }
 }
 private void couchInfo(String s){
  runOnUiThread(()->{if(couchSeats!=null&&couchHost!=null)
   couchSeats.setText(roomSeats(couchHost.count())+"\n"+s);
   if(partyBanner!=null&&couchHost!=null)
    partyBanner.setText("▣  CONSOLE ATIVO  ·  "+couchHost.count()+"/5 CONTROLES");
   maybeCouchRewind();});
 }
 private void maybeCouchRewind(){
  CouchSession party=couchHost;
  if(party==null||!playing||!coreReady||recording||!rewindEnabled||
     !rewindBuffer.available(emulatedFrames,300)||!party.consumeUnanimousVote())return;
  emulatorCommands.add(()->{
   byte[] state=rewindBuffer.rewind(emulatedFrames,300);
   if(state!=null&&Core.restore(state)){
    rewindBuffer.clear();if(activeAudio!=null)activeAudio.flushSolo();
    party.ackRewind();
    toast("Máquina do tempo: a turma voltou cerca de 5 segundos.");
   }else toast("Não foi possível voltar. O jogo continua.");
  });
 }
 private String roomSeats(int players){
  return roomSeats(players,5);
 }
 private String roomSeats(int players,int max){
  StringBuilder seats=new StringBuilder(players+" / "+max+" jogadores conectados\n");
  for(int i=1;i<=max;i++)seats.append(i<=players?"● P":"○ P").append(i).append(i==max?"":"   ");
  return seats.toString();
 }
 private String shortTitle(Game g){
  if(g==null)return "Escolha um jogo";
  String name=g.name.replaceAll("(?i)\\s+-\\s+[^ ]+\\.(com|net|org)(\\.[a-z]{2})?.*$","");
  return name.replaceAll("\\s*\\[[^]]*\\]","").trim();
 }
 private View partyCard(){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(12),dp(8),dp(9),dp(8));row.setMinimumHeight(dp(60));
  row.setBackground(surface(0xff263a51,15,0xff69c9c7));touchFeedback(row);
  LinearLayout labels=new LinearLayout(this);labels.setOrientation(1);
  partyBanner=label("▣  CONSOLE ATIVO  ·  "+couchHost.count()+"/5 CONTROLES",11,CYAN,true);
  partyBanner.setPadding(0,0,0,dp(2));labels.addView(partyBanner);
  TextView detail=label("Escolha uma ROM e continue com a turma",11,-1,false);
  detail.setPadding(0,0,0,0);detail.setSingleLine(true);
  detail.setEllipsize(TextUtils.TruncateAt.END);labels.addView(detail);
  row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  TextView start=chip("▶  ABRIR",-1);row.addView(start);
  row.setOnClickListener(v->prepare("couchNext"));return row;
 }
 private void home(){home(true);}
 private void home(boolean updateLibrary){
  partyBanner=null;
  getWindow().getDecorView().setSystemUiVisibility(0);
  // The app follows the device sensor even when Android's automatic rotation is locked.
  // The game itself continues to request sensor landscape while it is running.
  if(getRequestedOrientation()!=ActivityInfo.SCREEN_ORIENTATION_SENSOR)
   setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
  if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){
   homeLandscape(updateLibrary);return;
  }
  getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  landscapeHub=false;landscapeRail=null;landscapeMenuAnchor=null;landscapeOrder.clear();
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff101633,0xff0c1123,0xff111b31}));
  scroll.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=35)scroll.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
  root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(18),dp(20),dp(32));scroll.addView(root);setContentView(scroll);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.luni_icon);header.addView(icon,new LinearLayout.LayoutParams(dp(47),dp(47)));
  LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);brand.setPadding(dp(8),0,0,0);
  TextView name=label("LUNI ARENA",19,-1,true);name.setPadding(0,0,0,0);brand.addView(name);
  TextView caption=label("SUA ARENA RETRÔ  •  0.1.21",10,CYAN,true);caption.setPadding(0,0,0,0);brand.addView(caption);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  TextView menu=label("⋮",28,-1,true);menu.setGravity(Gravity.CENTER);menu.setPadding(0,0,0,0);
  header.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(48)));menu.setOnClickListener(v->showHomeMenu(menu));add(root,header,dp(23));
  if(couchHost!=null)add(root,partyCard(),dp(13));
  int screenDp=getResources().getConfiguration().screenWidthDp;
  boolean wide=screenDp>=720;
  LinearLayout content=new LinearLayout(this);content.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  LinearLayout featuredPane=new LinearLayout(this);featuredPane.setOrientation(LinearLayout.VERTICAL);
  LinearLayout browsePane=new LinearLayout(this);browsePane.setOrientation(LinearLayout.VERTICAL);
  if(wide){
   int featureWidth=dp(Math.min(320,Math.max(270,screenDp*.37f)));
   content.addView(featuredPane,new LinearLayout.LayoutParams(featureWidth,-2));
   LinearLayout.LayoutParams browseParams=new LinearLayout.LayoutParams(0,-2,1);
   browseParams.leftMargin=dp(20);content.addView(browsePane,browseParams);
   estimatedLibraryWidth=dp(Math.max(280,screenDp-40))-featureWidth-dp(20);
  }else{
   content.addView(featuredPane,new LinearLayout.LayoutParams(-1,-2));
   content.addView(browsePane,new LinearLayout.LayoutParams(-1,-2));
   estimatedLibraryWidth=dp(Math.max(280,screenDp-40));
  }
  add(root,hubDock(scroll,featuredPane,browsePane),dp(13));
  add(root,content,0);
  add(featuredPane,eyebrow("◈  SUA PRÓXIMA AVENTURA"),0);
  LinearLayout hero=new LinearLayout(this);hero.setPadding(dp(14),dp(14),dp(14),dp(14));hero.setGravity(Gravity.CENTER_VERTICAL);hero.setBackground(galaxy(22));hero.setElevation(dp(3));touchFeedback(hero);
  featuredCover=new ImageView(this);featuredCover.setImageResource(R.drawable.luni_icon);featuredCover.setBackground(panel(0xff303a59,dp(13)));featuredCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
  hero.addView(featuredCover,new LinearLayout.LayoutParams(dp(100),dp(132)));
  LinearLayout heroText=new LinearLayout(this);heroText.setOrientation(1);heroText.setPadding(dp(15),0,0,0);
  TextView flag=eyebrow("EM DESTAQUE");heroText.addView(flag);
  featuredTitle=label("Escolha um jogo",20,-1,true);featuredTitle.setPadding(0,0,0,dp(5));featuredTitle.setMaxLines(3);featuredTitle.setEllipsize(TextUtils.TruncateAt.END);heroText.addView(featuredTitle);
  TextView context=label("SNES  •  Até 5 jogadores",12,MUTED,false);context.setPadding(0,0,0,0);heroText.addView(context);
  hero.addView(heroText,new LinearLayout.LayoutParams(0,-2,1));add(featuredPane,hero,dp(13));
  hero.setOnClickListener(v->{if(selected!=null)showGameDetails(selected);});
  TextView play=action(couchHost==null?"▶   JOGAR AGORA":"▶   JOGAR NO CONSOLE",PURPLE,
    ()->prepare(couchHost==null?"solo":"couchNext"));add(featuredPane,play,dp(9));
  LinearLayout network=new LinearLayout(this);TextView host=action("＋  Criar sala",0xff30385c,()->prepare("host"));TextView join=action("⌁  Entrar na sala",0xff30385c,()->prepare("join"));
  LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(52),1);half.setMargins(0,0,dp(5),0);network.addView(host,half);
  half=new LinearLayout.LayoutParams(0,dp(52),1);half.setMargins(dp(5),0,0,0);network.addView(join,half);
  if(couchHost==null)add(featuredPane,network,dp(19));
  if(getPreferences(0).getBoolean("menu_animations",true))
   for(View item:new View[]{hero,play,network}){item.setAlpha(0);item.setTranslationY(dp(9));
    item.animate().alpha(1).translationY(0).setDuration(190).start();}
  add(featuredPane,smallAction("◈  OFICINA DO JOGO",0xff30385c,this::showWorkshop),dp(16));
  recentSection=new LinearLayout(this);recentSection.setOrientation(1);recentSection.setVisibility(View.GONE);
  add(recentSection,eyebrow("✦  CONTINUAR JOGANDO"),dp(3));
  HorizontalScrollView recentScroll=new HorizontalScrollView(this);recentScroll.setHorizontalScrollBarEnabled(false);
  recentRail=new LinearLayout(this);recentRail.setOrientation(0);recentScroll.addView(recentRail);
  add(recentSection,recentScroll,dp(17));add(browsePane,recentSection,0);
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView libraryTitle=label("SUA BIBLIOTECA",14,-1,true);libraryTitle.setPadding(0,0,0,0);row.addView(libraryTitle,new LinearLayout.LayoutParams(0,-2,1));
  libraryCount=label("0 jogos",12,MUTED,false);libraryCount.setPadding(0,0,0,0);row.addView(libraryCount);add(browsePane,row,dp(9));
  if(!getPreferences(0).getString("cover_service_url","").isEmpty())
   add(browsePane,label("Capas do catálogo: IGDB.com",10,MUTED,false),dp(5));
  LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
  EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(-1);
  search.setPadding(dp(14),0,dp(10),0);search.setBackground(surface(PANEL,13,BORDER));
  search.setHintTextColor(MUTED);search.setHint("Buscar jogo…");search.setText(searchQuery);
  searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(50),1));
  final TextView[] favorite={null};
  favorite[0]=smallAction(onlyFavorites?"★ Favoritos":"☆ Favoritos",PANEL,()->{
   onlyFavorites=!onlyFavorites;favoriteButton(favorite[0],onlyFavorites);visibleCards=48;refreshGames();
  });searchRow.addView(favorite[0],new LinearLayout.LayoutParams(-2,dp(42)));
  add(browsePane,searchRow,dp(9));
  Handler filterHandler=new Handler(Looper.getMainLooper());final Runnable[] filter={null};
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){
    searchQuery=s.toString().trim();visibleCards=48;
    if(filter[0]!=null)filterHandler.removeCallbacks(filter[0]);
    filter[0]=()->{if(library!=null&&!landscapeHub&&search.isAttachedToWindow())refreshGames();};
    filterHandler.postDelayed(filter[0],130);
   }
   public void afterTextChanged(Editable text){}
  });
  library=new LinearLayout(this);library.setOrientation(1);add(browsePane,library,dp(10));
  final LinearLayout portraitLibrary=library;
  final Runnable[] pendingRedraw={null};
  Runnable redrawAfterLayout=()->{
   if(pendingRedraw[0]!=null)portraitLibrary.removeCallbacks(pendingRedraw[0]);
   pendingRedraw[0]=()->{
    if(library!=portraitLibrary||landscapeHub||!hubVisible)return;
    int measuredWidth=portraitLibrary.getWidth();
    if(measuredWidth<=0)return;
    lastLibraryWidth=measuredWidth;
    refreshGames();
   };
   portraitLibrary.post(pendingRedraw[0]);
  };
  lastLibraryWidth=0;
  portraitLibrary.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
   int width=right-left;
   if(width>0&&width!=lastLibraryWidth){
    lastLibraryWidth=width;
    redrawAfterLayout.run();
   }
  });
  status=label("Escolha sua pasta pelo menu ⋮. Os jogos aparecem aqui.",12,MUTED,false);status.setPadding(0,dp(3),0,dp(10));add(browsePane,status,0);
  refreshGames();
  hubVisible=true;
  // Always redraw once after the new portrait hierarchy is measured.
  // Mutating the grid from the layout listener can leave it at zero height.
  redrawAfterLayout.run();
  if(updateLibrary){skipNextHubResumeScan=!activityResumed;refreshSavedLibrary();}
 }
 private void homeLandscape(boolean updateLibrary){
  getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
  landscapeHub=true;landscapeStickDirection=0;
  final int height=getResources().getConfiguration().screenHeightDp;
  final boolean roomy=height>=500;
  root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(16),dp(9),dp(16),dp(8));
  root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
    new int[]{0xff0c1224,0xff172340,0xff101626}));
  root.setOnApplyWindowInsetsListener((view,insets)->{
   int left=0,right=0,top=0,bottom=0;
   if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
    android.view.DisplayCutout cutout=insets.getDisplayCutout();
    left=cutout.getSafeInsetLeft();right=cutout.getSafeInsetRight();top=cutout.getSafeInsetTop();
   }
   if(Build.VERSION.SDK_INT>=35){
    left=Math.max(left,insets.getSystemWindowInsetLeft());
    right=Math.max(right,insets.getSystemWindowInsetRight());
    top=Math.max(top,insets.getSystemWindowInsetTop());
    bottom=insets.getSystemWindowInsetBottom();
   }
   root.setPadding(dp(16)+left,dp(9)+top,dp(16)+right,dp(8)+bottom);
   return insets;
  });
  setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.luni_icon);
  header.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));
  LinearLayout brand=new LinearLayout(this);brand.setOrientation(1);brand.setPadding(dp(8),0,0,0);
  TextView title=label("LUNI ARENA",18,-1,true);title.setPadding(0,0,0,0);brand.addView(title);
  TextView subtitle=label("SUA ARENA  /  SNES",9,PURPLE,true);subtitle.setPadding(0,0,0,0);brand.addView(subtitle);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  landscapeSearch=smallAction("⌕  Buscar",PANEL,this::searchLandscape);
  header.addView(landscapeSearch,new LinearLayout.LayoutParams(-2,dp(37)));
  landscapeFavorites=smallAction(onlyFavorites?"★":"☆",PANEL,()->{
   onlyFavorites=!onlyFavorites;visibleCards=48;refreshGames();
  });
  landscapeFavorites.setContentDescription("Mostrar apenas favoritos");
  LinearLayout.LayoutParams control=new LinearLayout.LayoutParams(dp(42),dp(37));control.leftMargin=dp(7);
  header.addView(landscapeFavorites,control);
  TextView menu=smallAction("⋮",PANEL,()->showHomeMenu(landscapeMenuAnchor));
  menu.setTextSize(22);menu.setContentDescription("Opções da biblioteca");
  control=new LinearLayout.LayoutParams(dp(42),dp(37));control.leftMargin=dp(7);header.addView(menu,control);
  landscapeMenuAnchor=menu;
  add(root,header,dp(7));
  if(couchHost!=null)add(root,partyCard(),dp(7));

  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);
  LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);
  hero.setPadding(dp(12),dp(9),dp(12),dp(9));
  GradientDrawable heroSurface=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
    new int[]{0xff2c2451,0xff222b4b,0xff18263e});
  heroSurface.setCornerRadius(dp(18));heroSurface.setStroke(dp(1),0xff5e5286);
  hero.setBackground(heroSurface);hero.setElevation(dp(3));
  int coverHeight=dp(roomy?Math.min(198,Math.round(height*.30f)):
    Math.min(145,Math.max(116,Math.round(height*.34f))));
  featuredCover=new ImageView(this);featuredCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
  featuredCover.setImageResource(R.drawable.luni_icon);
  featuredCover.setBackground(panel(0xff394260,dp(12)));featuredCover.setClipToOutline(true);
  hero.addView(featuredCover,new LinearLayout.LayoutParams(Math.round(coverHeight*.76f),coverHeight));
  LinearLayout heroText=new LinearLayout(this);heroText.setOrientation(1);
  heroText.setGravity(Gravity.CENTER_VERTICAL);heroText.setPadding(dp(15),0,0,0);
  TextView tag=label("▶  PRONTO PARA JOGAR",10,0xffc5a5ff,true);
  tag.setPadding(0,0,0,dp(roomy?9:4));heroText.addView(tag);
  featuredTitle=label("Escolha um jogo",roomy?25:20,-1,true);
  featuredTitle.setMaxLines(roomy?2:1);featuredTitle.setEllipsize(TextUtils.TruncateAt.END);
  featuredTitle.setPadding(0,0,0,dp(3));heroText.addView(featuredTitle);
  landscapeMeta=label("Biblioteca de Super Nintendo",11,0xffd4cbed,false);
  landscapeMeta.setSingleLine(true);landscapeMeta.setEllipsize(TextUtils.TruncateAt.END);
  landscapeMeta.setPadding(0,0,0,dp(roomy?12:6));heroText.addView(landscapeMeta);
  LinearLayout heroActions=new LinearLayout(this);
  TextView play=smallAction(couchHost==null?"▶  Jogar":"▶  Console",PURPLE,
   ()->prepare(couchHost==null?"solo":"couchNext"));play.setMinHeight(dp(40));
  heroActions.addView(play,new LinearLayout.LayoutParams(0,dp(40),1));
  TextView host=smallAction("＋  Criar sala",0xff3c4467,()->prepare("host"));
  LinearLayout.LayoutParams hostParams=new LinearLayout.LayoutParams(-2,dp(40));hostParams.leftMargin=dp(8);
  if(couchHost==null)heroActions.addView(host,hostParams);heroText.addView(heroActions);
  hero.addView(heroText,new LinearLayout.LayoutParams(0,-2,1));
  hero.setOnClickListener(v->{if(selected!=null)showGameDetails(selected);});
  add(body,hero,dp(11));
  if(getPreferences(0).getBoolean("menu_animations",true)){
   hero.setAlpha(0);hero.setTranslationY(dp(7));
   hero.animate().alpha(1).translationY(0).setDuration(190).start();
  }

  LinearLayout railHeader=new LinearLayout(this);railHeader.setGravity(Gravity.CENTER_VERTICAL);
  TextView heading=label("SEUS JOGOS",12,-1,true);heading.setPadding(0,0,0,0);
  railHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
  libraryCount=label("0 jogos",11,MUTED,false);libraryCount.setPadding(0,0,0,0);
  railHeader.addView(libraryCount);add(body,railHeader,dp(7));
  if(!getPreferences(0).getString("cover_service_url","").isEmpty())
   add(body,label("Capas do catálogo: IGDB.com",10,MUTED,false),dp(4));
  landscapeRail=new HorizontalScrollView(this);landscapeRail.setHorizontalScrollBarEnabled(false);
  landscapeRail.setFillViewport(false);landscapeRail.setClipToPadding(false);
  library=new LinearLayout(this);library.setOrientation(0);landscapeRail.addView(library);
  add(body,landscapeRail,dp(6));
  root.addView(hubDock(scroll,hero,landscapeRail),1,
   new LinearLayout.LayoutParams(-1,dp(39)));
  TextView hint=label("CONTROLE   ← →  ESCOLHER    •    A  JOGAR    •    X  DETALHES    •    Y  FAVORITO",10,MUTED,true);
  hint.setPadding(0,dp(3),0,dp(4));hint.setSingleLine(true);
  hint.setEllipsize(TextUtils.TruncateAt.END);add(body,hint,dp(3));

  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);
  status=label("Carregando biblioteca…",11,MUTED,false);status.setSingleLine(true);
  status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,0,dp(8),0);
  footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
  TextView join=smallAction("⌁  Entrar na sala",PANEL,()->prepare("join"));
  footer.addView(join,new LinearLayout.LayoutParams(-2,dp(36)));
  TextView importRom=smallAction("＋  ROM",PANEL,this::chooseFile);
  LinearLayout.LayoutParams fileParams=new LinearLayout.LayoutParams(-2,dp(36));fileParams.leftMargin=dp(8);
  footer.addView(importRom,fileParams);add(root,footer,0);
  refreshGames();
  hubVisible=true;
  if(updateLibrary){skipNextHubResumeScan=!activityResumed;refreshSavedLibrary();}
 }
 private void searchLandscape(){
  EditText query=new EditText(this);query.setSingleLine(true);query.setTextColor(-1);
  query.setText(searchQuery);query.setSelectAllOnFocus(true);
  query.setHint("Nome do jogo");query.setHintTextColor(MUTED);
  query.setBackground(surface(0xff202c49,13,BORDER));
  query.setPadding(dp(13),dp(10),dp(13),dp(10));
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);
  final AlertDialog[] dialog={null};
  add(box,eyebrow("NOME OU TRECHO DO TÍTULO"),dp(3));add(box,query,dp(12));
  add(box,action("⌕  BUSCAR",PURPLE,()->{
   searchQuery=query.getText().toString().trim();visibleCards=48;dialog[0].dismiss();refreshGames();
  }),dp(7));
  add(box,navigation("↻","Mostrar todos os jogos","Limpar a busca atual",()->{
   searchQuery="";visibleCards=48;dialog[0].dismiss();refreshGames();
  }),0);
  dialog[0]=showSheet("Buscar na biblioteca","Encontre o próximo jogo",box);
 }
 private void favoriteButton(TextView button,boolean active){button.setText(active?"★ Favoritos":"☆ Favoritos");}
 private void refreshSavedLibrary(){
  String saved=getPreferences(0).getString("folder",null);
  if(saved!=null)scan(Uri.parse(saved));
  else{
   final int epoch=++scanEpoch;
   libraryIo.execute(()->{RomLibrary.Scan found=RomLibrary.scanRoomRoms(this);runOnUiThread(()->{
    if(epoch!=scanEpoch||!hubVisible)return;
    if(!found.complete){info(found.errors.isEmpty()?"Pasta Jogos indisponível.":found.errors.get(0));return;}
    String remembered=getPreferences(0).getString("selected","");
    games.clear();games.addAll(found.games);duplicates.clear();duplicates.addAll(found.duplicates);
    selected=games.isEmpty()?null:games.get(0);
    for(Game game:games)if(game.identity().equals(remembered)){selected=game;break;}
    refreshGames();info(games.size()+" jogo(s) no Luni · "+found.changedFiles+
     " novo(s)/alterado(s), "+found.removedFiles+" removido(s).");loadManual();
   });});
  }
 }
 private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_FOLDER);}
 private void chooseImportFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_IMPORT_FOLDER);}
 private void chooseFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_FILE);}
 private void chooseCover(){if(selected==null){info("Selecione uma ROM primeiro.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_COVER);}
 private void chooseArchive(int request){
  Game game=selected;if(game==null){info("Selecione primeiro um jogo.");return;}
  info("Conferindo a ROM de "+shortTitle(game)+"…");
  new Thread(()->{try{
   String hash=sha(RomLibrary.readRom(this,game));
   runOnUiThread(()->{
    if(request==PICK_SAVE_EXPORT&&!SaveArchive.hasFiles(getFilesDir(),hash)){
     info("Este jogo ainda não tem SRAM ou estado para exportar.");return;
    }
    if(request==PICK_REPLAY_EXPORT&&!ReplayArchive.file(getFilesDir(),hash).isFile()){
     info("Grave uma partida deste jogo antes de exportar o replay.");return;
    }
    pendingArchiveId=hash;
    boolean exporting=request==PICK_SAVE_EXPORT||request==PICK_REPLAY_EXPORT;
    Intent intent=new Intent(exporting?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType(request==PICK_SAVE_EXPORT||request==PICK_SAVE_IMPORT?"application/zip":"application/octet-stream");
    if(exporting)intent.putExtra(Intent.EXTRA_TITLE,"LuniArena_"+shortTitle(game).replaceAll("[^\\p{L}\\p{N}._-]","_")+"_"+hash.substring(0,8)+
      (request==PICK_SAVE_EXPORT?"_saves.zip":"_replay.kjr"));
    startActivityForResult(intent,request);
   });
  }catch(Exception e){info("Não foi possível ler a ROM: "+e.getMessage());}},"knight-archive-id").start();
 }
 private void processArchive(int request,Uri uri){
  String id=pendingArchiveId;pendingArchiveId=null;
  if(id==null){info("Selecione a ROM novamente antes de abrir o arquivo.");return;}
  boolean importing=request==PICK_SAVE_IMPORT||request==PICK_REPLAY_IMPORT;
  Runnable operation=()->new Thread(()->{try{
   int files=0;
   if(importing){try(InputStream input=getContentResolver().openInputStream(uri)){
    if(input==null)throw new IOException("Arquivo não acessível.");
    if(request==PICK_SAVE_IMPORT)files=SaveArchive.importGame(getFilesDir(),id,input);
    else{String core=shaFile(coreForSavedGame(id));
     ReplayArchive.importReplay(getFilesDir(),id,core,input);files=1;}
   }}else{try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
    if(output==null)throw new IOException("Destino não acessível.");
    if(request==PICK_SAVE_EXPORT)files=SaveArchive.exportGame(getFilesDir(),id,output);
    else{String core=shaFile(coreForSavedGame(id));
     ReplayArchive.exportReplay(getFilesDir(),id,core,output);files=1;}
   }}
   info((importing?"Importado(s) ":"Exportado(s) ")+files+" arquivo(s) do jogo.");
  }catch(Exception e){info("Falha "+(importing?"ao importar: ":"ao exportar: ")+e.getMessage());}},"knight-archive").start();
  if(importing){
   LinearLayout page=new LinearLayout(this);page.setOrientation(1);
   add(page,label("Os saves ou o replay existentes deste jogo serão substituídos após verificar a identidade da ROM e o arquivo.",13,-1,false),dp(12));
   final AlertDialog[] confirm={null};
   add(page,action("IMPORTAR ARQUIVOS",PURPLE,()->{confirm[0].dismiss();operation.run();}),0);
   confirm[0]=showSheet("Importar arquivos",shortTitle(selected),page);
  }
  else operation.run();
 }
 private void showHomeMenu(View anchor){
  LinearLayout menu=new LinearLayout(this);menu.setOrientation(1);
  final AlertDialog[] dialog={null};
  add(menu,chip("SUPER NINTENDO  ·  SUA ARENA",CYAN),dp(12));
  add(menu,eyebrow("JOGAR"),0);
  if(landscapeHub)homeOption(menu,dialog,"⌕","Buscar jogos","Encontrar uma ROM na biblioteca",this::searchLandscape);
  if(selected!=null&&couchHost==null)homeOption(menu,dialog,"▶","Jogar solo",shortTitle(selected),()->prepare("solo"));
  if(couchHost!=null)homeOption(menu,dialog,"▣","Continuar console com outro jogo",
   selected==null?"Escolha uma ROM na biblioteca":"Manter os controles P1–P5 conectados",()->prepare("couchNext"));
  if(couchHost==null){
   homeOption(menu,dialog,"＋","Criar sala","Convidar até quatro amigos",()->prepare("host"));
   homeOption(menu,dialog,"⌁","Entrar na sala","Descobrir ou digitar IP:porta",()->prepare("join"));
  }
  add(menu,eyebrow("EXPLORAR"),dp(10));
  homeOption(menu,dialog,"◆","Arena e desafios","Fantasmas, placar e jogos em grupo",this::showArenaOptions);
  homeOption(menu,dialog,"▣","Jogos e capas","Pasta, biblioteca e imagens",this::showLibraryOptions);
  homeOption(menu,dialog,"◉","Controles e aparência","Bluetooth, USB-C e preferências visuais",this::showControllerOptions);
  homeOption(menu,dialog,"⇧","Arquivos e replays","Saves, backups e gravações",this::showArchiveOptions);
  homeOption(menu,dialog,"◇","Privacidade","Como o Luni usa arquivos e conexão",this::showPrivacyInformation);
  homeOption(menu,dialog,"ⓘ","Licenças e código-fonte","GPLv3 e origem do projeto",this::showLegalInformation);
  dialog[0]=showSheet("Central Luni","Partidas · Biblioteca · Personalização",menu);
 }
 private View hubDock(ScrollView scroll,View play,View collection){
  HorizontalScrollView dock=new HorizontalScrollView(this);
  dock.setHorizontalScrollBarEnabled(false);dock.setFillViewport(true);
  LinearLayout links=new LinearLayout(this);links.setGravity(Gravity.CENTER_VERTICAL);
  String[] labels={"▶  Jogar","▣  Jogos","✥  Arena","◈  Oficina"};
  Runnable[] actions={()->scrollHubTo(scroll,play),()->scrollHubTo(scroll,collection),
   this::showArenaOptions,this::showWorkshop};
  for(int i=0;i<labels.length;i++){
   TextView link=smallAction(labels[i],i==0?0xff44376d:0xff283352,actions[i]);
   link.setTextSize(11);link.setMinWidth(dp(76));
   LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(36),1);
   if(i>0)cell.leftMargin=dp(5);links.addView(link,cell);
  }
  dock.addView(links,new HorizontalScrollView.LayoutParams(-2,-1));return dock;
 }
 private void scrollHubTo(ScrollView scroll,View target){
  target.post(()->{
   if(!target.isAttachedToWindow())return;
   int[] where=new int[2],origin=new int[2];
   target.getLocationOnScreen(where);scroll.getLocationOnScreen(origin);
   scroll.smoothScrollTo(0,Math.max(0,scroll.getScrollY()+where[1]-origin[1]-dp(12)));
  });
 }
 private String readLegalAsset(String name){
  try(InputStream input=getAssets().open(name);ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int count;
   while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
   return output.toString("UTF-8").trim();
  }catch(IOException ignored){return "";}
 }
 private void showPrivacyInformation(){
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  String policy=readLegalAsset("PRIVACIDADE.txt");
  TextView details=label(policy.isEmpty()?"As informações de privacidade não estão disponíveis nesta compilação.":policy,
    13,-1,false);
  details.setLineSpacing(dp(4),1f);
  add(page,details,0);
  showSheet("Privacidade","Arquivos locais · rede · serviços opcionais",page);
 }
 private void showLegalInformation(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);
  final AlertDialog[] dialog={null};
  add(list,label("O código Luni e os núcleos incluídos nesta versão são distribuídos sob a GNU GPLv3. Você pode consultar, modificar e redistribuir o código coberto pela licença. O software é fornecido sem garantia nos limites da licença.",13,-1,false),dp(10));
  add(list,label("Cheats consultados no catálogo libretro-database são atribuídos aos contribuidores do projeto e distribuídos sob CC BY-SA 4.0. Algumas descrições podem ser adaptadas por tradução. Consulte github.com/libretro/libretro-database e a licença em creativecommons.org/licenses/by-sa/4.0/. Traduções automáticas usam ML Kit e são identificadas como Powered by Google Translate.",12,MUTED,false),dp(10));
  String source=readLegalAsset("SOURCE_URL.txt");
  if(source.startsWith("https://"))homeOption(list,dialog,"↗","Código-fonte correspondente",source,()->{
   try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(source)));}
   catch(android.content.ActivityNotFoundException ignored){toast("Não há navegador para abrir o link.");}
  });
  else add(list,label("Esta prévia ainda não tem um endereço público do código-fonte da versão instalada.",12,MUTED,false),dp(10));
  add(list,action("LER LICENÇA GNU GPLv3",PURPLE,()->{
   String license=readLegalAsset("COPYING");
   LinearLayout text=new LinearLayout(this);text.setOrientation(1);
   add(text,label(license.isEmpty()?"Licença não encontrada nesta compilação.":license,12,-1,false),0);
   showSheet("GNU GPL versão 3","Texto integral da licença",text);
  }),0);
  dialog[0]=showSheet("Licenças e código-fonte","Informações legais desta versão",list);
 }
 private void showArenaOptions(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  add(list,eyebrow("PARTIDAS COM AMIGOS"),0);
  if(couchHost==null){
   homeOption(list,dialog,"＋","Criar sala","Até cinco celulares na mesma rede",()->prepare("host"));
   homeOption(list,dialog,"⌁","Entrar na sala","Localizar sala ou digitar IP e porta",()->prepare("join"));
  }
  add(list,eyebrow("UMA TELA PARA TODOS"),dp(8));
  if(couchHost==null)homeOption(list,dialog,"▣","Abrir console compartilhado",
   "Este aparelho exibe o jogo; amigos usam o celular como controle",()->prepare("couch"));
  else{
   homeOption(list,dialog,"▶","Continuar console com outro jogo",
    couchHost.count()+" jogadores seguem conectados",()->prepare("couchNext"));
   homeOption(list,dialog,"✣","Dupla no mesmo personagem",
    "Um amigo ajuda a controlar o P1",this::showAssistantSeat);
   homeOption(list,dialog,"✕","Encerrar console","Desconectar todos os celulares",()->{
    couchHost.close();couchHost=null;home(false);toast("Console compartilhado encerrado.");
   });
  }
  if(couchHost==null)homeOption(list,dialog,"◉","Usar este celular como controle",
   "Entrar no console da TV sem baixar a ROM",this::showCouchJoin);
  add(list,eyebrow("ARENA ASSÍNCRONA"),dp(8));
  if(selected!=null&&selected.hash!=null){
   File replay=ReplayArchive.file(getFilesDir(),selected.hash);
   if(replay.isFile()&&couchHost==null)homeOption(list,dialog,"♜","Arena Fantasma",
    "Enfrentar os comandos do último replay",()->prepare("ghost"));
   else add(list,label("Grave um replay deste jogo para liberar a Arena Fantasma.",12,MUTED,false),dp(6));
   homeOption(list,dialog,"◆","Desafios Luni","Registrar vitórias de P1 a P5",this::showChallengeBoard);
  }else add(list,label("Selecione uma ROM para ver desafios e replays.",12,MUTED,false),dp(7));
  homeOption(list,dialog,"‹","Voltar à Central Luni","Outras ferramentas",()->showHomeMenu(null));
  dialog[0]=showSheet("Arena Luni","Jogue junto ou desafie um replay",list);
 }
 private void showLibraryOptions(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  add(list,eyebrow("BIBLIOTECA LUNI"),0);
  add(list,label("Sua pasta Jogos é criada automaticamente no espaço do aplicativo. Importe jogos do telefone; o Luni guarda cópias e preserva os arquivos originais. Faça backup antes de desinstalar o app.",12,MUTED,false),dp(8));
  homeOption(list,dialog,"＋","Importar arquivos","Selecione uma ou várias ROMs, ZIPs ou 7Zs",this::chooseFile);
  homeOption(list,dialog,"⇩","Importar uma pasta","Copiar jogos e capas da pasta selecionada",this::chooseImportFolder);
  add(list,eyebrow("OUTRAS PASTAS"),dp(8));
  homeOption(list,dialog,"▣","Vincular pasta sem copiar","Acompanhar novos arquivos e remoções da pasta",this::chooseFolder);
  if(getPreferences(0).contains("folder"))homeOption(list,dialog,"○","Desvincular pasta externa",
   "Não apaga arquivos; mostra apenas os jogos importados",()->{
    getPreferences(0).edit().remove("folder").apply();refreshSavedLibrary();
   });
  homeOption(list,dialog,"↻","Atualizar biblioteca","Conferir novos, alterados e excluídos",this::refreshSavedLibrary);
  homeOption(list,dialog,"◇","Conferir conteúdo das ROMs","Recalcular hashes e verificar arquivos compactados",()->{
   info("Conferência completa da biblioteca iniciada…");
   libraryIo.execute(()->{RomLibrary.clearIndex(this);runOnUiThread(this::refreshSavedLibrary);});
  });
  if(selected!=null&&selected.managed)homeOption(list,dialog,"−","Remover cópia do Luni",
   "Excluir este arquivo importado após confirmação",()->confirmManagedRemoval(selected));
  add(list,eyebrow("CAPAS E ORGANIZAÇÃO"),dp(8));
  add(list,label(communityCoversAllowed()?"Busca comunitária ativada nesta prévia. Suas imagens sempre têm prioridade.":
   "Sem capa, o jogo recebe a arte original Luni. Capas suas e fontes licenciadas têm prioridade.",12,MUTED,false),dp(5));
  homeOption(list,dialog,"◈","Catálogo de capas",communityCoversAllowed()?"Busca automática e fonte das imagens":"Configurar busca automática · IGDB.com",this::showCoverSettings);
  homeOption(list,dialog,"↻","Atualizar imagens","Rever capas pessoais e fontes habilitadas",()->{
   coverFailures.clear();refreshGames();toast("Buscando capas ausentes.");
  });
  homeOption(list,dialog,"✦","Adicionar capa","Imagem para o jogo selecionado",this::chooseCover);
  homeOption(list,dialog,"◇","Cópias idênticas","Revisar a limpeza das ROMs",this::confirmCleanup);
  homeOption(list,dialog,"‹","Voltar à Central Luni","Todas as categorias",()->showHomeMenu(null));
  dialog[0]=showSheet("Jogos e capas","Sua coleção sempre organizada",list);
 }
 private void confirmManagedRemoval(Game game){
  if(game==null||!game.managed)return;
  String item=game.entry==null?game.name:"O pacote que contém "+game.name;
  new AlertDialog.Builder(this).setTitle("Remover cópia do Luni?")
   .setMessage(item+" será removido da pasta Jogos do aplicativo. A origem da importação não será apagada. Se houver outra cópia numa pasta vinculada, ela poderá voltar a aparecer.")
   .setNegativeButton("Cancelar",null)
   .setPositiveButton("Remover",(d,which)->libraryIo.execute(()->{
    try{
     boolean removed=RomLibrary.removeManaged(this,game);
     runOnUiThread(()->{if(removed){refreshSavedLibrary();toast("Cópia removida da Biblioteca Luni.");}
      else toast("A cópia já não está na pasta Jogos.");});
    }catch(IOException e){toast("Falha ao remover cópia: "+e.getMessage());}
   })).show();
 }
 private void showCoverSettings(){
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  if(isPreviewBuild()){
   add(page,eyebrow("CAPAS DA COMUNIDADE · PRÉVIA"),dp(7));
   Switch community=new Switch(this);community.setText("Buscar capas automaticamente");
   community.setTextColor(-1);community.setChecked(communityCoversAllowed());
   community.setOnCheckedChangeListener((button,checked)->{
    getPreferences(0).edit().putBoolean("preview_community_covers",checked).apply();
    coverFailures.clear();refreshGames();
   });
   add(page,community,dp(5));
   add(page,label("Fonte: Libretro Thumbnails. Só na versão de teste; capas de jogos de terceiros exigem autorização para publicação paga. Se estiver sem internet, as capas locais continuam disponíveis.",11,MUTED,false),dp(17));
  }
  add(page,eyebrow("CAPAS AUTOMÁTICAS · IGDB.COM"),dp(7));
  add(page,label("O serviço do Luni consulta a IGDB para jogos da sua biblioteca. A chave da IGDB fica no seu servidor, nunca no APK. Imagens que você escolher têm prioridade.",12,MUTED,false),dp(10));
  EditText address=new EditText(this);address.setSingleLine(true);address.setTextColor(-1);
  address.setHintTextColor(MUTED);address.setHint("https://seu-servidor.example");
  address.setText(getPreferences(0).getString("cover_service_url",""));
  address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
  add(page,address,dp(7));
  EditText key=new EditText(this);key.setSingleLine(true);key.setTextColor(-1);
  key.setHintTextColor(MUTED);key.setHint("Chave de acesso ao seu servidor");
  key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  key.setText(getPreferences(0).getString("cover_service_key",""));
  add(page,key,dp(10));
  add(page,label("Conexão HTTPS obrigatória. Para um aplicativo pago, confirme a parceria de uso comercial e a permissão para exibir as capas. Fonte: IGDB.com.",11,CYAN,false),dp(9));
  final AlertDialog[] dialog={null};
  add(page,navigation("◈","Testar conexão","Confere o endereço e a chave do serviço",()->{
   String url=address.getText().toString().trim();
   String token=key.getText().toString().trim();
   covers.execute(()->{try{
    CoverRepository.checkService(url,token);
    toast("Serviço de capas conectado. Fonte: IGDB.com.");
   }catch(IOException e){toast("Falha no catálogo: "+e.getMessage());}});
  }),dp(7));
  add(page,navigation("✓","Salvar catálogo","Buscar capas ausentes em segundo plano",()->{
   String url=address.getText().toString().trim().replaceAll("/+$","");
   String token=key.getText().toString().trim();
   if(!url.isEmpty()&&(!url.matches("https://[A-Za-z0-9._:-]+(?:/[^?#]*)?")||token.isEmpty()||
      token.length()>256||token.contains("\n")||token.contains("\r"))){
    toast("Informe HTTPS e a chave de acesso do servidor.");return;
   }
   getPreferences(0).edit().putString("cover_service_url",url)
    .putString("cover_service_key",token).apply();
   coverFailures.clear();if(dialog[0]!=null)dialog[0].dismiss();home(false);
   toast(url.isEmpty()?"Catálogo desligado.":"Catálogo configurado. Buscando capas ausentes.");
  }),0);
  dialog[0]=showSheet("Catálogo de capas","Serviço próprio · IGDB.com",page);
 }
 private void showControllerOptions(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  add(list,eyebrow("GAMEPAD"),0);
  homeOption(list,dialog,"◉","Controle externo","Mapear botões Bluetooth ou USB-C",this::configureController);
  homeOption(list,dialog,"✣","Sensibilidade do analógico","Ágil, equilibrado ou preciso",this::showStickOptions);
  add(list,eyebrow("PORTAS DO SUPER NINTENDO"),dp(8));
  homeOption(list,dialog,"✥","Multitap",multitapEnabled()?"Ligado · até 5 controles":"Desligado · até 2 controles",this::showMultitapOptions);
  add(list,eyebrow("APARÊNCIA"),dp(8));
  homeOption(list,dialog,"✦","Aparência do aplicativo","Animações leves e legibilidade",this::showAppearanceSettings);
  add(list,label("Posição, tamanho e transparência dos botões na tela ficam em ⋮ durante o jogo → Imagem e controles.",12,MUTED,false),dp(8));
  homeOption(list,dialog,"‹","Voltar à Central Luni","Todas as categorias",()->showHomeMenu(null));
  dialog[0]=showSheet("Controles e visual","Ajustes do aparelho e da interface",list);
 }
 private void showArchiveOptions(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  add(list,eyebrow("PROGRESSO"),0);
  homeOption(list,dialog,"⇧","Exportar saves","Guardar estados e SRAM",()->chooseArchive(PICK_SAVE_EXPORT));
  homeOption(list,dialog,"⇩","Importar saves","Restaurar backup da mesma ROM",()->chooseArchive(PICK_SAVE_IMPORT));
  add(list,eyebrow("REPLAYS"),dp(8));
  homeOption(list,dialog,"▶","Reproduzir replay","Rever a última partida gravada",()->prepare("replay"));
  homeOption(list,dialog,"⇧","Exportar replay","Compartilhar arquivo sem ROM",()->chooseArchive(PICK_REPLAY_EXPORT));
  homeOption(list,dialog,"✦","Cápsula de momentos","Ver marcações do último replay",this::showMoments);
  homeOption(list,dialog,"⇩","Importar replay","Conferir núcleo e ROM",()->chooseArchive(PICK_REPLAY_IMPORT));
  homeOption(list,dialog,"‹","Voltar à Central Luni","Todas as categorias",()->showHomeMenu(null));
  dialog[0]=showSheet("Arquivos e replays","Dados vinculados à ROM selecionada",list);
 }
 private void homeOption(LinearLayout list,AlertDialog[] dialog,String glyph,String title,String detail,Runnable task){
  add(list,navigation(glyph,title,detail,()->{if(dialog[0]!=null)dialog[0].dismiss();task.run();}),dp(7));
 }
 private void showAppearanceSettings(){
  LinearLayout settings=new LinearLayout(this);settings.setOrientation(1);
  add(settings,eyebrow("INTERFACE E CONFORTO"),0);
  add(settings,label("O sistema respeita o tamanho de fonte do Android. Você pode desligar os movimentos decorativos da biblioteca sem afetar o jogo.",12,MUTED,false),dp(10));
  boolean enabled=getPreferences(0).getBoolean("menu_animations",true);
  final TextView[] motion={null};
  motion[0]=navigation("✦","Animações leves",enabled?"LIGADAS · TOQUE PARA DESLIGAR":"DESLIGADAS · TOQUE PARA LIGAR",()->{
   boolean next=!getPreferences(0).getBoolean("menu_animations",true);
   getPreferences(0).edit().putBoolean("menu_animations",next).apply();
   motion[0].setText("✦   Animações leves   ›\n"+(next?"LIGADAS · TOQUE PARA DESLIGAR":"DESLIGADAS · TOQUE PARA LIGAR"));
  });
  add(settings,motion[0],dp(8));
  add(settings,chip("HUD Nebula · Neon · Carbono no menu do jogo",CYAN),0);
  showSheet("Aparência Luni","Ajustes leves para cada aparelho",settings);
 }
 @Override public void onConfigurationChanged(Configuration config){
  super.onConfigurationChanged(config);
  if(playing)controllerDeviceChanged();
  else if(!launchPending)home(!hubVisible);
 }
 @Override protected void onResume(){
  super.onResume();
  activityResumed=true;
  controllerDeviceChanged();
  if(playing&&gameView!=null&&controlsEditor==null&&gameOverlay==null)gameView.requestFocus();
  if(hubVisible){
   if(skipNextHubResumeScan)skipNextHubResumeScan=false;
   else refreshSavedLibrary();
  }
 }
 @Override protected void onPause(){activityResumed=false;super.onPause();}
 @Override public void onWindowFocusChanged(boolean focused){
  super.onWindowFocusChanged(focused);
  if(gameView==null)return;
  if(!focused)gameView.clearHardwareInput();
  else if(playing&&controlsEditor==null&&gameOverlay==null)gameView.requestFocus();
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(playing&&!paused&&gameView!=null&&controlsEditor==null&&gameOverlay==null&&
    getWindow().getDecorView().hasWindowFocus()&&gameView.handleHardwareKey(event))return true;
  if(landscapeHub&&hubVisible&&!launchPending&&getWindow().getDecorView().hasWindowFocus()&&
    isGamepadEvent(event)&&handleLandscapeKey(event))return true;
  return super.dispatchKeyEvent(event);
 }
 @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){
  if(playing&&!paused&&gameView!=null&&controlsEditor==null&&gameOverlay==null&&
    getWindow().getDecorView().hasWindowFocus()&&gameView.handleHardwareMotion(event))return true;
  if(landscapeHub&&hubVisible&&!launchPending&&event.getAction()==MotionEvent.ACTION_MOVE&&
    (event.getSource()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK){
   float x=event.getAxisValue(MotionEvent.AXIS_X);
   int direction=x>.70f?1:x<-.70f?-1:0;
   if(direction==0)landscapeStickDirection=0;
   else if(direction!=landscapeStickDirection||event.getEventTime()-landscapeStickAt>=280){
    landscapeStickDirection=direction;landscapeStickAt=event.getEventTime();
    moveLandscapeSelection(direction);return true;
   }
  }
  return super.dispatchGenericMotionEvent(event);
 }
 private boolean isGamepadEvent(KeyEvent event){
  int source=event.getSource();
  return (source&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD||
    (source&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK||
    (source&InputDevice.SOURCE_DPAD)==InputDevice.SOURCE_DPAD;
 }
 private boolean handleLandscapeKey(KeyEvent event){
  int code=event.getKeyCode();
  boolean handled=code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||
   code==KeyEvent.KEYCODE_BUTTON_L1||code==KeyEvent.KEYCODE_BUTTON_R1||
   code==KeyEvent.KEYCODE_BUTTON_A||code==KeyEvent.KEYCODE_BUTTON_X||
   code==KeyEvent.KEYCODE_BUTTON_Y||code==KeyEvent.KEYCODE_BUTTON_START||
   code==KeyEvent.KEYCODE_BUTTON_SELECT;
  if(!handled)return false;
  if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
  if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_BUTTON_L1)moveLandscapeSelection(-1);
  else if(code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_BUTTON_R1)moveLandscapeSelection(1);
  else if(event.getRepeatCount()==0){
   if(code==KeyEvent.KEYCODE_BUTTON_A||code==KeyEvent.KEYCODE_BUTTON_START)
    prepare(couchHost==null?"solo":"couchNext");
   else if(code==KeyEvent.KEYCODE_BUTTON_X){if(selected!=null)showGameDetails(selected);}
   else if(code==KeyEvent.KEYCODE_BUTTON_SELECT){if(landscapeMenuAnchor!=null)showHomeMenu(landscapeMenuAnchor);}
   else if(code==KeyEvent.KEYCODE_BUTTON_Y&&selected!=null){
    getPreferences(0).edit().putBoolean("fav_"+gameKey(selected),!isFavorite(selected)).apply();refreshGames();
   }
  }
  return true;
 }
 private void moveLandscapeSelection(int delta){
  if(landscapeOrder.isEmpty())return;
  int index=landscapeOrder.indexOf(selected);
  if(delta>0&&index+delta>=landscapeOrder.size()&&landscapeTotalVisible>landscapeOrder.size()){
   visibleCards+=40;refreshGames();
  }
  index=Math.max(0,Math.min(landscapeOrder.size()-1,index+delta));
  Game next=landscapeOrder.get(index);
  if(next!=selected){Game previous=selected;selected=next;
   getPreferences(0).edit().putString("selected",next.identity()).apply();
   updateLandscapeFeature();highlightLandscapeTile(previous);highlightLandscapeTile(next);
  }
  centerLandscapeSelection();
 }
 private void highlightLandscapeTile(Game game){
  int index=landscapeOrder.indexOf(game);
  if(library==null||index<0||index>=library.getChildCount())return;
  View tile=library.getChildAt(index);boolean active=game==selected;
  GradientDrawable surface=panel(active?0xff35284f:0xff1b2640,dp(13));
  surface.setStroke(dp(active?2:1),active?PURPLE:0xff303b57);
  tile.setBackground(surface);tile.setContentDescription(shortTitle(game)+(active?", selecionado":""));
 }
 private void centerLandscapeSelection(){
  HorizontalScrollView rail=landscapeRail;LinearLayout tiles=library;
  if(rail==null||tiles==null)return;
  int index=landscapeOrder.indexOf(selected);
  rail.post(()->{
   if(!landscapeHub||landscapeRail!=rail||library!=tiles||index<0||index>=tiles.getChildCount())return;
   View card=tiles.getChildAt(index);
   rail.smoothScrollTo(Math.max(0,card.getLeft()-(rail.getWidth()-card.getWidth())/2),0);
  });
 }
 private boolean external(){
  for(int id:InputDevice.getDeviceIds()){
   InputDevice device=InputDevice.getDevice(id);
   if(device==null)continue;
   int sources=device.getSources();
   if((sources&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD||
      (sources&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK)return true;
  }
  return false;
 }
 private void refreshControllerMode(){
  if(playing&&gameView!=null){gameView.setExternal(external());
   if(!paused&&controlsEditor==null&&gameOverlay==null)gameView.requestFocus();}
 }
 private void controllerDeviceChanged(){
  if(gameView!=null)gameView.refreshControllerProfiles();
  refreshControllerMode();
  deviceHandler.removeCallbacks(delayedControllerRefresh);
  deviceHandler.postDelayed(delayedControllerRefresh,200);
 }
 @Override public void onInputDeviceAdded(int deviceId){controllerDeviceChanged();}
 @Override public void onInputDeviceRemoved(int deviceId){
  if(gameView!=null)gameView.removeHardwareDevice(deviceId);
  controllerDeviceChanged();
 }
 @Override public void onInputDeviceChanged(int deviceId){
  if(gameView!=null)gameView.removeHardwareDevice(deviceId);
  controllerDeviceChanged();
 }
 private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private void renderRecents(){
  if(recentRail==null||recentSection==null)return;
  recentRail.removeAllViews();
  if(!searchQuery.isEmpty()||onlyFavorites){recentSection.setVisibility(View.GONE);return;}
  android.content.SharedPreferences prefs=getPreferences(0);
  ArrayList<Game> recent=new ArrayList<>();
  for(Game game:games)if(prefs.getLong("played_"+gameKey(game),0)>0)recent.add(game);
  recent.sort((a,b)->Long.compare(prefs.getLong("played_"+gameKey(b),0),
    prefs.getLong("played_"+gameKey(a),0)));
  recentSection.setVisibility(recent.isEmpty()?View.GONE:View.VISIBLE);
  for(int i=0;i<Math.min(5,recent.size());i++){
   Game game=recent.get(i);
   LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
   card.setPadding(dp(8),dp(8),dp(10),dp(8));card.setBackground(surface(0xff202c49,15,BORDER));
   touchFeedback(card);
   ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
   art.setImageDrawable(new CoverArt(game.name));art.setBackground(panel(PANEL,dp(9)));
   art.setClipToOutline(true);card.addView(art,new LinearLayout.LayoutParams(dp(59),dp(76)));
   LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);titles.setPadding(dp(10),0,0,0);
   TextView name=label(shortTitle(game),12,-1,true);name.setMaxLines(2);
   name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(0,0,0,dp(4));titles.addView(name);
   TextView hint=label("▶  ABRIR JOGO",10,CYAN,true);hint.setPadding(0,0,0,0);titles.addView(hint);
   card.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
   LinearLayout.LayoutParams tile=new LinearLayout.LayoutParams(dp(211),dp(92));tile.rightMargin=dp(9);
   recentRail.addView(card,tile);
   card.setOnClickListener(v->{selected=game;prefs.edit().putString("selected",game.identity()).apply();
    refreshGames();showGameDetails(game);});
   loadCover(game,art,game.identity(),true);
  }
 }
 private void refreshGames(){
  if(landscapeHub){refreshLandscapeGames();return;}
  if(library==null)return;library.removeAllViews();
  renderRecents();
  String query=searchQuery.toLowerCase(Locale.ROOT);
  ArrayList<Game> visible=new ArrayList<>();
  for(Game game:games)if((!onlyFavorites||isFavorite(game))&&
     (query.isEmpty()||game.name.toLowerCase(Locale.ROOT).contains(query)))visible.add(game);
  android.content.SharedPreferences prefs=getPreferences(0);
  visible.sort((a,b)->{
   if(isFavorite(a)!=isFavorite(b))return isFavorite(a)?-1:1;
   return Long.compare(prefs.getLong("played_"+gameKey(b),0),prefs.getLong("played_"+gameKey(a),0));
  });
  if(libraryCount!=null)libraryCount.setText(visible.size()+" / "+games.size()+" jogos"+
      (duplicates.isEmpty()?"":"  •  "+duplicates.size()+" repetidos"));
  if(featuredTitle!=null)featuredTitle.setText(shortTitle(selected));
  if(featuredCover!=null){featuredCover.setImageDrawable(selected==null?getDrawable(R.drawable.luni_icon):new CoverArt(selected.name));
   if(selected!=null)loadCover(selected,featuredCover,selected.identity(),true);}
  if(visible.isEmpty()){
   LinearLayout empty=new LinearLayout(this);empty.setOrientation(1);
   empty.setPadding(dp(18),dp(16),dp(18),dp(17));empty.setBackground(galaxy(17));
   TextView icon=label("♞  SUA PRÓXIMA AVENTURA",16,CYAN,true);icon.setPadding(0,0,0,dp(7));add(empty,icon,0);
   TextView hint=label(games.isEmpty()?"Importe seus jogos ou vincule uma pasta para começar a biblioteca.":
    "Nenhum jogo encontrado. Experimente outra busca ou veja todos os seus jogos.",13,MUTED,false);
   hint.setPadding(0,0,0,dp(12));add(empty,hint,0);
   add(empty,smallAction(games.isEmpty()?"＋  ADICIONAR JOGOS":"↻  VER TODOS OS JOGOS",PURPLE,()->{
    if(games.isEmpty())showLibraryOptions();else{searchQuery="";onlyFavorites=false;home(false);}
   }),0);add(library,empty,dp(10));return;
  }
  int available=lastLibraryWidth>0?lastLibraryWidth:estimatedLibraryWidth;
  int columns=available>=dp(900)?4:available>=dp(660)?3:available>=dp(300)?2:1;
  int cardWidth=Math.max(dp(124),(available-dp(10)*(columns-1))/columns);
  int coverHeight=Math.min(dp(258),Math.round(cardWidth*1.19f));
  int shown=Math.min(visibleCards,visible.size());
  for(int index=0;index<shown;index+=columns){
   LinearLayout row=new LinearLayout(this);
   for(int col=0;col<columns&&index+col<shown;col++){
    Game g=visible.get(index+col);LinearLayout card=new LinearLayout(this);card.setOrientation(1);
    card.setPadding(dp(10),dp(10),dp(10),dp(12));
    GradientDrawable background=surface(selected==g?0xff32284f:PANEL,18,selected==g?PURPLE:0xff32405f);
    card.setBackground(background);
    touchFeedback(card);
    ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(panel(0xff303a59,dp(11)));cover.setImageDrawable(new CoverArt(g.name));
    card.addView(cover,new LinearLayout.LayoutParams(-1,coverHeight));
    LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView text=label(shortTitle(g),13,-1,true);text.setMaxLines(2);text.setEllipsize(TextUtils.TruncateAt.END);text.setPadding(0,dp(8),0,0);
    titleRow.addView(text,new LinearLayout.LayoutParams(0,dp(47),1));
    TextView star=label(isFavorite(g)?"★":"☆",21,PURPLE,true);star.setGravity(Gravity.CENTER);
    star.setContentDescription("Favorito: "+shortTitle(g));
    titleRow.addView(star,new LinearLayout.LayoutParams(dp(36),dp(47)));
    star.setOnClickListener(v->{boolean next=!isFavorite(g);
     getPreferences(0).edit().putBoolean("fav_"+gameKey(g),next).apply();refreshGames();
    });
    card.addView(titleRow);
    TextView hint=label(g.save!=null||g.hash!=null&&new File(getFilesDir(),g.hash+".srm").isFile()?"SAVE DISPONÍVEL":
     (selected==g?"SELECIONADO":"SNES  •  JOGAR"),10,selected==g?PURPLE:MUTED,true);
    hint.setSingleLine(true);hint.setPadding(0,0,0,0);card.addView(hint);
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(cardWidth,coverHeight+dp(94));
    cp.setMargins(0,0,col==columns-1?0:dp(10),0);
    row.addView(card,cp);
    card.setOnClickListener(v->{selected=g;getPreferences(0).edit().putString("selected",g.identity()).apply();refreshGames();showGameDetails(g);});
    card.setOnLongClickListener(v->{selected=g;getPreferences(0).edit().putString("selected",g.identity()).apply();refreshGames();prepare(couchHost==null?"solo":"couchNext");return true;});
    loadCover(g,cover,g.identity(),true);
   }
   add(library,row,dp(10));
  }
  if(shown<visible.size())add(library,action("Mostrar mais jogos ("+(visible.size()-shown)+")",PANEL,()->{
   visibleCards+=40;refreshGames();
  }),dp(12));
 }
 private void refreshLandscapeGames(){
  if(library==null||landscapeRail==null)return;
  String query=searchQuery.toLowerCase(Locale.ROOT);
  ArrayList<Game> visible=new ArrayList<>();
  for(Game game:games)if((!onlyFavorites||isFavorite(game))&&
    (query.isEmpty()||game.name.toLowerCase(Locale.ROOT).contains(query)))visible.add(game);
  android.content.SharedPreferences prefs=getPreferences(0);
  visible.sort((a,b)->{
   if(isFavorite(a)!=isFavorite(b))return isFavorite(a)?-1:1;
   return Long.compare(prefs.getLong("played_"+gameKey(b),0),prefs.getLong("played_"+gameKey(a),0));
  });
  landscapeTotalVisible=visible.size();
  if(!visible.isEmpty()&&!visible.contains(selected))selected=visible.get(0);
  int rememberedIndex=visible.indexOf(selected);
  if(rememberedIndex>=visibleCards){visible.remove(rememberedIndex);visible.add(0,selected);}
  if(libraryCount!=null)libraryCount.setText(visible.size()+" / "+games.size()+" jogos");
  if(landscapeSearch!=null)landscapeSearch.setText(query.isEmpty()?"⌕  Buscar":
    "⌕  "+(searchQuery.length()>10?searchQuery.substring(0,10)+"…":searchQuery));
  if(landscapeFavorites!=null){landscapeFavorites.setText(onlyFavorites?"★":"☆");
   landscapeFavorites.setTextColor(onlyFavorites?PURPLE:-1);}
  updateLandscapeFeature();
  library.removeAllViews();landscapeOrder.clear();
  if(visible.isEmpty()){
   TextView empty=label(games.isEmpty()?"Sem jogos ainda  •  Toque em ＋ ROM ou importe uma pasta no menu ⋮":
    "Nenhum jogo encontrado  •  Limpe a busca ou desative Favoritos",13,MUTED,false);
   empty.setBackground(galaxy(14));empty.setGravity(Gravity.CENTER_VERTICAL);
   empty.setPadding(dp(15),dp(13),dp(15),dp(13));
   empty.setOnClickListener(v->{if(games.isEmpty())chooseFolder();else{
    searchQuery="";onlyFavorites=false;refreshGames();}});
   library.addView(empty,new LinearLayout.LayoutParams(-2,dp(86)));return;
  }
  boolean roomy=getResources().getConfiguration().screenHeightDp>=500;
  int tileWidth=dp(roomy?138:106),coverHeight=dp(roomy?116:83);
  int shown=Math.min(visibleCards,visible.size());
  for(int i=0;i<shown;i++){
   Game game=visible.get(i);landscapeOrder.add(game);
   LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);
   tile.setPadding(dp(7),dp(7),dp(7),dp(6));
   GradientDrawable surface=panel(game==selected?0xff35284f:0xff1b2640,dp(13));
   surface.setStroke(dp(game==selected?2:1),game==selected?PURPLE:0xff303b57);
   tile.setBackground(surface);tile.setContentDescription(shortTitle(game)+(game==selected?", selecionado":""));
   ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
   cover.setBackground(panel(0xff323e60,dp(8)));cover.setClipToOutline(true);
   cover.setImageDrawable(new CoverArt(game.name));
   tile.addView(cover,new LinearLayout.LayoutParams(-1,coverHeight));
   TextView title=label(shortTitle(game),roomy?12:11,-1,true);
   title.setPadding(dp(1),dp(5),dp(1),0);title.setMaxLines(2);
   title.setEllipsize(TextUtils.TruncateAt.END);tile.addView(title);
   tile.setOnClickListener(v->{
    if(selected==game){showGameDetails(game);return;}
    Game previous=selected;selected=game;prefs.edit().putString("selected",game.identity()).apply();
    updateLandscapeFeature();highlightLandscapeTile(previous);highlightLandscapeTile(game);
    centerLandscapeSelection();
   });
   tile.setOnLongClickListener(v->{selected=game;prefs.edit().putString("selected",game.identity()).apply();
    prepare(couchHost==null?"solo":"couchNext");return true;});
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(tileWidth,dp(roomy?169:134));
   params.rightMargin=dp(9);library.addView(tile,params);
   loadCover(game,cover,game.identity(),true);
  }
  if(shown<visible.size()){
   TextView more=smallAction("＋  Mais "+(visible.size()-shown)+" jogos",PANEL,()->{
    visibleCards+=40;refreshGames();
   });
   LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(115),dp(roomy?169:134));
   library.addView(more,moreParams);
  }
 }
 private void updateLandscapeFeature(){
  if(featuredTitle!=null)featuredTitle.setText(shortTitle(selected));
  if(featuredCover!=null){featuredCover.setImageDrawable(selected==null?
    getDrawable(R.drawable.luni_icon):new CoverArt(selected.name));
   if(selected==null)featuredCover.setTag(null);
   if(selected!=null)loadCover(selected,featuredCover,selected.identity(),true);
  }
  if(landscapeMeta!=null){
   if(selected==null)landscapeMeta.setText("Sua biblioteca de Super Nintendo");
   else{
    long last=getPreferences(0).getLong("played_"+gameKey(selected),0);
    String meta=last==0?"Primeira partida":
      "Jogado em "+new java.text.SimpleDateFormat("dd/MM",Locale.getDefault()).format(new Date(last));
    landscapeMeta.setText("SUPER NINTENDO  •  "+meta+(isFavorite(selected)?"  •  ★ Favorito":""));
   }
  }
 }
 private void showGameDetails(Game game){
  if(game==null)return;
  selected=game;getPreferences(0).edit().putString("selected",game.identity()).apply();
  LinearLayout sheet=new LinearLayout(this);sheet.setOrientation(1);
  LinearLayout summary=new LinearLayout(this);summary.setGravity(Gravity.CENTER_VERTICAL);
  summary.setPadding(dp(10),dp(10),dp(10),dp(10));summary.setBackground(surface(0xff202b49,18,BORDER));
  ImageView cover=new ImageView(this);cover.setImageDrawable(new CoverArt(game.name));
  cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setClipToOutline(true);
  cover.setBackground(panel(0xff344267,dp(12)));
  summary.addView(cover,new LinearLayout.LayoutParams(dp(104),dp(137)));
  LinearLayout details=new LinearLayout(this);details.setOrientation(1);details.setPadding(dp(13),0,0,0);
  details.addView(eyebrow("SUPER NINTENDO · PERFIL"));
  TextView title=label(shortTitle(game),20,-1,true);title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);
  title.setPadding(0,0,0,dp(7));details.addView(title);
  boolean hasSave=game.save!=null||game.hash!=null&&SaveArchive.hasFiles(getFilesDir(),game.hash);
  long last=getPreferences(0).getLong("played_"+gameKey(game),0);
  String when=last==0?"Primeira partida":
    "Última partida: "+new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new Date(last));
  TextView meta=label(when+(hasSave?"\n● Progresso disponível":""),12,MUTED,false);meta.setPadding(0,0,0,0);details.addView(meta);
  summary.addView(details,new LinearLayout.LayoutParams(0,-2,1));add(sheet,summary,dp(12));
  loadCover(game,cover,game.identity(),true);
  int count=0;if(game.hash!=null)for(int slot=1;slot<=5;slot++)
   if(new File(new File(getFilesDir(),"states"),game.hash+"-"+slot+".state").isFile())count++;
  LinearLayout badges=new LinearLayout(this);badges.setGravity(Gravity.CENTER_VERTICAL);
  badges.addView(chip(count+" estado"+(count==1?"":"s"),CYAN));
  TextView saveChip=chip(hasSave?"● Save disponível":"○ Sem save",MUTED);
  LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(-2,-2);second.leftMargin=dp(8);
  badges.addView(saveChip,second);add(sheet,badges,dp(14));
  final AlertDialog[] dialog={null};
  add(sheet,action(couchHost==null?"▶   JOGAR AGORA":"▶   JOGAR NO CONSOLE",PURPLE,
    ()->{dialog[0].dismiss();prepare(couchHost==null?"solo":"couchNext");}),dp(9));
  LinearLayout quick=new LinearLayout(this);
  TextView host=smallAction("＋  Criar sala",0xff364266,()->{dialog[0].dismiss();prepare("host");});
  if(couchHost==null)quick.addView(host,new LinearLayout.LayoutParams(0,dp(42),1));
  final TextView[] favorite={null};
  favorite[0]=smallAction(isFavorite(game)?"★  Favorito":"☆  Favorito",0xff364266,()->{
   boolean next=!isFavorite(game);getPreferences(0).edit().putBoolean("fav_"+gameKey(game),next).apply();
   favorite[0].setText(next?"★  Favorito":"☆  Favorito");refreshGames();
  });
  LinearLayout.LayoutParams favParams=new LinearLayout.LayoutParams(0,dp(42),1);favParams.leftMargin=dp(7);
  quick.addView(favorite[0],favParams);add(sheet,quick,dp(14));
  add(sheet,eyebrow("SUA PARTIDA"),0);
  add(sheet,navigation("◈","Oficina do jogo","Replays, saves e diagnóstico da última sala",()->{
   dialog[0].dismiss();showWorkshop();}),dp(7));
  add(sheet,navigation("▦","Backup dos saves","Exportar ou importar sua partida",()->{
   dialog[0].dismiss();showBackupOptions();}),dp(7));
  add(sheet,navigation("✦","Escolher capa","Personalizar a biblioteca com sua imagem",()->{
   dialog[0].dismiss();chooseCover();}),0);
  if(game.hash!=null){
   boolean forced=getPreferences(0).getBoolean("mesen_for_"+game.hash,false);
   add(sheet,navigation("◇",forced?"Núcleo: compatibilidade":"Núcleo: automático",
    "Tocar para alternar; ao criar sala, os convidados recebem esta escolha",()->{
     getPreferences(0).edit().putBoolean("mesen_for_"+game.hash,!forced).apply();
     dialog[0].dismiss();showGameDetails(game);
   }),dp(7));
  }
  dialog[0]=showSheet("Biblioteca Luni","Seu jogo, suas escolhas",sheet);
 }
 private void showBackupOptions(){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  homeOption(list,dialog,"⇧","Exportar backup","Salvar SRAM e estados em ZIP",()->chooseArchive(PICK_SAVE_EXPORT));
  homeOption(list,dialog,"⇩","Importar backup","Restaurar arquivos desta ROM",()->chooseArchive(PICK_SAVE_IMPORT));
  dialog[0]=showSheet("Saves e progresso","Arquivos ligados à ROM selecionada",list);
 }
 private String gameKey(Game g){return g.hash==null?g.identity():g.hash;}
 private void migrateVideoSettings(Game g){
  if(g==null||g.hash==null)return;
  android.content.SharedPreferences prefs=getPreferences(0);
  android.content.SharedPreferences.Editor edit=prefs.edit();boolean changed=false;
  for(String kind:new String[]{"video_mode_","video_filter_"}){
   if(!prefs.contains(kind+g.hash)&&prefs.contains(kind+g.identity())){
    edit.putInt(kind+g.hash,prefs.getInt(kind+g.identity(),0));changed=true;
   }
  }
  if(changed)edit.apply();
 }
 private boolean isFavorite(Game g){return getPreferences(0).getBoolean("fav_"+gameKey(g),false);}
 private void showWorkshop(){
  Game game=selected;if(game==null){info("Escolha primeiro um jogo na biblioteca.");return;}
  String hash=game.hash;
  if(hash==null){info("Não foi possível conferir esta ROM. Atualize a biblioteca.");return;}
  android.content.SharedPreferences prefs=getPreferences(0);
  File replay=ReplayArchive.file(getFilesDir(),hash);
  boolean saves=SaveArchive.hasFiles(getFilesDir(),hash),hasReplay=replay.isFile();
  long played=prefs.getLong("played_"+hash,0);
  String latest=played==0?"Ainda não iniciado":new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(played));
  int roomFps=prefs.getInt("room_fps_"+hash,-1),p95=prefs.getInt("room_p95_"+hash,-1);
  String report="ROM: "+hash.substring(0,12)+"…\nÚltima partida: "+latest+"\n"+
    "Save: "+(saves?"disponível":"ainda não")+"  •  Replay: "+(hasReplay?"disponível":"ainda não")+"\n"+
    (roomFps<1||p95<0?"Sem diagnóstico de sala anterior.":
        "Última sala: "+roomFps+" FPS • espera P95 "+p95+" ms\n"+
        "Use Luni Radar durante a sala para comparar aparelhos.");
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);final AlertDialog[] dialog={null};
  add(list,eyebrow("IDENTIDADE DO JOGO"),0);
  TextView overview=label("◈  "+shortTitle(game)+"\n"+report,12,-1,false);
  overview.setLineSpacing(dp(5),1f);overview.setPadding(dp(14),dp(12),dp(14),dp(12));
  overview.setBackground(surface(0xff202b49,15,BORDER));add(list,overview,dp(13));
  add(list,eyebrow("AÇÕES"),0);
  if(hasReplay)homeOption(list,dialog,"▶","Ver replay","Rever os comandos da última partida",()->prepare("replay"));
  if(hasReplay)homeOption(list,dialog,"♜","Arena Fantasma",
   "Jogue contra os comandos gravados dos seus amigos",()->prepare("ghost"));
  homeOption(list,dialog,"◆","Desafios Luni",
   "Placar de até cinco jogadores e séries de partidas",this::showChallengeBoard);
  homeOption(list,dialog,"◎","Radar dos dedos",
   "Sugestões de controles com base nos seus toques",this::showHudLearning);
  homeOption(list,dialog,"▦","Saves e backups",saves?"Progresso disponível":"Ainda sem progresso salvo",this::showBackupOptions);
  homeOption(list,dialog,"⧉","Copiar ficha","Nome, hash e último diagnóstico",()->{
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Ficha Luni Arena",shortTitle(game)+"\n"+report));
    toast("Ficha do jogo copiada.");
  });
  dialog[0]=showSheet("Oficina do jogo","Seu histórico em um lugar",list);
 }
 private void showChallengeBoard(){
  Game game=selected;
  if(game==null||game.hash==null){toast("Selecione uma ROM conferida na biblioteca.");return;}
  String id=game.hash;
  final ChallengeLedger board;
  try{board=ChallengeLedger.load(getFilesDir(),id);}
  catch(IOException e){toast("Não foi possível abrir o placar: "+e.getMessage());return;}
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,chip("◆  "+shortTitle(game),CYAN),dp(12));
  add(page,eyebrow("TORNEIO LOCAL · RESULTADO CONFIRMADO POR VOCÊ"),0);
  TextView summary=label("",14,-1,true);
  summary.setPadding(dp(14),dp(13),dp(14),dp(13));
  summary.setBackground(surface(0xff293356,15,PURPLE));add(page,summary,dp(10));
  TextView[] scores=new TextView[5];
  final Runnable[] refresh={null};
  for(int player=0;player<5;player++){
   final int seat=player;
   scores[player]=navigation("P"+(player+1),"Jogador "+(player+1),"Registrar vitória nesta rodada",()->{
    try{board.award(seat);board.save(getFilesDir(),id);refresh[0].run();}
    catch(Exception e){toast("Placar: "+e.getMessage());}
   });
   add(page,scores[player],dp(7));
  }
  refresh[0]=()->{
   int winner=board.winner();
   summary.setText("Rodadas "+board.rounds()+"  ·  Primeiro a "+board.firstTo()+" vitória"+
    (board.firstTo()==1?"":"s")+(winner>=0?"\n♜ Campeão: P"+(winner+1):""));
   for(int i=0;i<5;i++)scores[i].setText("P"+(i+1)+"   Jogador "+(i+1)+"  ·  "+
     board.wins(i)+" vitória"+(board.wins(i)==1?"":"s")+
     (winner>=0?"\nTorneio encerrado":"\nToque para registrar uma rodada"));
  };
  refresh[0].run();
  add(page,eyebrow("CONFIGURAÇÕES"),dp(8));
  add(page,navigation("◈","Duração do torneio","Melhor de 3, 5 ou 7",()->{
   showChoices("Duração do torneio","Altera o objetivo, preservando o placar atual",
    new String[]{"Melhor de 3 · 2 vitórias","Melhor de 5 · 3 vitórias","Melhor de 7 · 4 vitórias"},
    board.firstTo()-2,which->{try{board.setFirstTo(which+2);board.save(getFilesDir(),id);refresh[0].run();}
      catch(IOException e){toast("Falha ao salvar o placar: "+e.getMessage());}},false);
  }),dp(7));
  add(page,navigation("↻","Novo torneio","Zerar vitórias depois de confirmar",()->{
   showChoices("Começar outro torneio","As vitórias atuais serão apagadas",
    new String[]{"Zerar placar e começar novamente"},-1,which->{
     try{board.reset();board.save(getFilesDir(),id);refresh[0].run();}
     catch(IOException e){toast("Falha ao zerar: "+e.getMessage());}
    },false);
  }),dp(7));
  add(page,label("O placar organiza a disputa entre amigos. Ele não altera a ROM nem tenta adivinhar o vencedor pela imagem.",11,MUTED,false),0);
  AlertDialog dialog=showSheet("Desafios Luni",shortTitle(game),page);
  dialog.setOnDismissListener(d->{if(playing&&activeSolo&&controlsEditor==null)paused=false;});
 }
 private void showHudLearning(){
  Game game=selected;
  if(game==null||game.hash==null){toast("Selecione um jogo para ver o Radar dos dedos.");return;}
  HudLearning report;
  try{report=playing&&gameView!=null?gameView.touchLearning():
    HudLearning.load(getFilesDir(),game.hash);}
  catch(IOException e){toast("Perfil de toques indisponível: "+e.getMessage());return;}
  String[] names={"Direcional / analógico","L","R","X","Y","A","B","Select","Start"};
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,chip("◎  TOQUES ANALISADOS NESTE APARELHO",CYAN),dp(11));
  add(page,label("O Luni observa onde seus dedos acertam cada botão. Ele mostra sugestões depois do jogo e nunca move um controle durante a partida.",12,MUTED,false),dp(11));
  int populated=0;
  for(int i=0;i<names.length;i++){
   int count=report.count(i);if(count==0)continue;populated++;
   TextView card=label(names[i]+"  ·  "+count+" toque"+(count==1?"":"s")+"\n"+report.advice(i),12,-1,false);
   card.setLineSpacing(dp(4),1f);card.setPadding(dp(13),dp(10),dp(13),dp(10));
   card.setBackground(surface(0xff222f4f,14,BORDER));add(page,card,dp(7));
  }
  if(populated==0)add(page,label("Ainda não há toques nesta ROM. Jogue pela tela e volte aqui para ver as recomendações.",13,-1,false),dp(9));
  add(page,label("Para mudar o layout: durante o jogo abra ⋮ → Imagem e controles → Ajustar botões. Você escolhe o que salvar.",11,CYAN,false),0);
  AlertDialog dialog=showSheet("Radar dos dedos",shortTitle(game),page);
  dialog.setOnDismissListener(d->{if(playing&&activeSolo&&controlsEditor==null)paused=false;});
 }
 private void loadCover(Game game,ImageView target,String identity,boolean tryOnline){
  File savedCover=CoverRepository.file(this,game);
  String origin=CoverRepository.origin(this,game);
  boolean own=getPreferences(0).getBoolean("custom_cover_"+identity,false)||origin.equals("user");
  boolean useSaved=savedCover.isFile()&&(own||origin.equals("licensed")||communityCoversAllowed());
  boolean useLocal=game.cover!=null&&(!useSaved||!getPreferences(0).getBoolean("custom_cover_"+identity,false));
  // Old debug thumbnails may remain on disk; do not display them after preview is disabled.
  if(savedCover.isFile()&&!useSaved&&!useLocal)return;
  String key=useLocal?game.cover.toString():savedCover.getAbsolutePath();
  target.setTag(identity);
  Bitmap cached=coverCache.get(key);if(cached!=null){target.setImageBitmap(cached);return;}
  if(!useLocal&&!useSaved){
   String service=getPreferences(0).getString("cover_service_url","");
   String access=getPreferences(0).getString("cover_service_key","");
   boolean licensed=!service.isEmpty()&&!access.isEmpty();
   boolean community=communityCoversAllowed();
   if(!tryOnline||(!licensed&&!community))return;
   String failureKey=(licensed?service:"preview")+":"+identity;
   Long failedAt=coverFailures.get(failureKey);
   if(failedAt!=null&&System.currentTimeMillis()-failedAt<15*60*1000L)return;
   covers.execute(()->{
    Object lock=coverLocks.computeIfAbsent(failureKey,unused->new Object());
    boolean available=false;
    synchronized(lock){
     if(savedCover.isFile())available=true;
     else{
      Long last=coverFailures.get(failureKey);
      if(last==null||System.currentTimeMillis()-last>=15*60*1000L){
       try{if(licensed)available=CoverRepository.downloadLicensed(this,game,service,access);}
       catch(IOException ignored){}
       if(!available&&community)available=CoverRepository.downloadCommunity(this,game);
       if(!available)coverFailures.put(failureKey,System.currentTimeMillis());
      }
     }
    }
    if(available)runOnUiThread(()->{
     if(identity.equals(target.getTag()))loadCover(game,target,identity,false);
    });
   });return;
  }
  covers.execute(()->{try{
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
    if(useLocal){try(InputStream in=getContentResolver().openInputStream(game.cover)){BitmapFactory.decodeStream(in,null,opts);}}
    else BitmapFactory.decodeFile(savedCover.getAbsolutePath(),opts);
    opts.inJustDecodeBounds=false;opts.inSampleSize=Math.max(1,Math.max(opts.outWidth/240,opts.outHeight/320));
    Bitmap image;
    if(useLocal){try(InputStream in=getContentResolver().openInputStream(game.cover)){image=BitmapFactory.decodeStream(in,null,opts);}}
    else image=BitmapFactory.decodeFile(savedCover.getAbsolutePath(),opts);
    if(image!=null){coverCache.put(key,image);runOnUiThread(()->{if(identity.equals(target.getTag()))target.setImageBitmap(image);});}
   }catch(Exception ignored){} });
 }
 private boolean isPreviewBuild(){return (getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
 private boolean communityCoversAllowed(){return isPreviewBuild()&&getPreferences(0).getBoolean("preview_community_covers",false);}
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;
  if(!activityResumed)skipNextHubResumeScan=true;
  if(req==PICK_FILE){
   LinkedHashSet<Uri> sources=new LinkedHashSet<>();
   if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)
    if(data.getClipData().getItemAt(i).getUri()!=null)sources.add(data.getClipData().getItemAt(i).getUri());
   if(data.getData()!=null)sources.add(data.getData());
   if(sources.isEmpty())return;
   if(sources.size()==1){Uri only=sources.iterator().next();
    if(RomLibrary.isSave(RomLibrary.displayName(this,only))){importSave(only);return;}
   }
   info("Importando jogos para a Biblioteca Luni…");
   libraryIo.execute(()->{
    RomLibrary.ImportReport report=new RomLibrary.ImportReport();
    for(Uri source:sources){
     if(report.imported+report.alreadyPresent+report.failed>=500){
      report.error("Importe no máximo 500 arquivos por vez.");break;
     }
     RomLibrary.importFile(this,source,RomLibrary.displayName(this,source),report);
    }
    runOnUiThread(()->{
     if(report.imported>0){
      String old=getPreferences(0).getString("manual_uri",null);
      if(old!=null)for(Uri source:sources)if(old.equals(source.toString())){
       getPreferences(0).edit().remove("manual_uri").remove("manual_name").apply();break;
      }
      refreshSavedLibrary();
     }
     toast(report.imported+" importado(s), "+report.alreadyPresent+" já na pasta"+
       (report.failed>0?", "+report.failed+" falha(s).":"."));
     if(report.failed>0)info(report.errors.get(0));
    });
   });return;
  }
  if(data.getData()==null)return;
  if(req==PICK_IMPORT_FOLDER){
   Uri source=data.getData();info("Copiando jogos da pasta para a Biblioteca Luni…");
   libraryIo.execute(()->{RomLibrary.ImportReport report=RomLibrary.importFolder(this,source);
    runOnUiThread(()->{
     if(report.imported>0)refreshSavedLibrary();
     toast(report.imported+" importado(s), "+report.alreadyPresent+" já na pasta"+
      (report.failed>0?", "+report.failed+" falha(s).":"."));
     if(report.failed>0)info(report.errors.get(0));
    });
   });return;
  }
  if(req==PICK_SAVE_EXPORT||req==PICK_SAVE_IMPORT||req==PICK_REPLAY_EXPORT||req==PICK_REPLAY_IMPORT){
   processArchive(req,data.getData());return;
  }
  Uri uri=data.getData();int flags=Intent.FLAG_GRANT_READ_URI_PERMISSION;
  if(req==PICK_FOLDER&&(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)flags|=Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
  if(req==PICK_FOLDER||req==PICK_COVER)
   try{getContentResolver().takePersistableUriPermission(uri,flags);}catch(SecurityException ignored){}
  if(req==PICK_FOLDER){getPreferences(0).edit().putString("folder",uri.toString()).apply();scan(uri);}
  else if(req==PICK_COVER){Game game=selected;if(game==null)return;covers.execute(()->{try{CoverRepository.importImage(this,game,uri);getPreferences(0).edit().putBoolean("custom_cover_"+game.identity(),true).apply();runOnUiThread(()->{coverCache.evictAll();refreshGames();info("Capa adicionada a "+game.name);});}catch(Exception e){info("Falha na capa: "+e.getMessage());}});}
 }
 private void scan(Uri tree){
  final int epoch=++scanEpoch;
  info("Verificando jogos, capas e cópias idênticas…");
  libraryIo.execute(()->{RomLibrary.Scan found=RomLibrary.scanFolder(this,tree);runOnUiThread(()->{
    if(epoch!=scanEpoch||!hubVisible)return;
    if(!found.complete){info("Pasta sem acesso completo. Jogos anteriores preservados. " +
     (found.errors.isEmpty()?"Selecione novamente a pasta.":found.errors.get(0)));return;}
    String remembered=getPreferences(0).getString("selected","");games.clear();games.addAll(found.games);duplicates.clear();duplicates.addAll(found.duplicates);
    selected=games.isEmpty()?null:games.get(0);for(Game g:games)if(g.identity().equals(remembered)){selected=g;break;}
    coverCache.evictAll();
    refreshGames();String message=games.size()+" ROM(s) e "+found.saves+" save(s) encontrados. " +
     found.changedFiles+" novo(s)/alterado(s), "+found.removedFiles+" removido(s); " +
     found.unchangedFiles+" reaproveitado(s).";
    if(!duplicates.isEmpty())message+=" "+duplicates.size()+" cópia(s) idêntica(s) oculta(s).";
    if(!found.errors.isEmpty())message+=" Primeiro erro: "+found.errors.get(0);else if(games.isEmpty())message+=" Confira se a ROM está nesta pasta ou abra o arquivo diretamente.";
    info(message);loadManual();if(!duplicates.isEmpty()&&getPreferences(0).getBoolean("auto_cleanup",true))eraseDuplicates(tree,new ArrayList<>(duplicates));
  });});
 }
 private void loadManual(){
  final int epoch=scanEpoch;
  String saved=getPreferences(0).getString("manual_uri",null);
  if(saved==null)return;
  Uri uri=Uri.parse(saved);String name=getPreferences(0).getString("manual_name","jogo.sfc");
  ArrayList<Game> previous=new ArrayList<>(),previousDuplicates=new ArrayList<>();
  for(Game game:games)if(!uri.equals(game.uri))previous.add(game);
  for(Game game:duplicates)if(!uri.equals(game.uri))previousDuplicates.add(game);
  libraryIo.execute(()->{
   RomLibrary.ImportReport migration=new RomLibrary.ImportReport();
   RomLibrary.importFile(this,uri,name,migration);
   if(migration.imported+migration.alreadyPresent>0){
    runOnUiThread(()->{
     getPreferences(0).edit().remove("manual_uri").remove("manual_name").apply();
     if(epoch==scanEpoch&&hubVisible){info("Jogo antigo adicionado à pasta Jogos do Luni.");refreshSavedLibrary();}
    });return;
   }
   RomLibrary.Scan found=RomLibrary.scanFile(this,uri,name);RomLibrary.Scan merged=new RomLibrary.Scan();merged.games.addAll(previous);merged.games.addAll(found.games);RomLibrary.deduplicate(this,merged);runOnUiThread(()->{
    if(epoch!=scanEpoch||!hubVisible)return;
    games.clear();games.addAll(merged.games);duplicates.clear();duplicates.addAll(previousDuplicates);duplicates.addAll(merged.duplicates);
    String remembered=getPreferences(0).getString("selected","");
    selected=games.isEmpty()?null:games.get(0);
    for(Game game:games)if(game.identity().equals(remembered)){selected=game;break;}
    refreshGames();info(found.games.isEmpty()&&!found.errors.isEmpty()?found.errors.get(0):games.size()+" jogo(s) disponíveis.");
  });});
 }
 private void confirmCleanup(){
  String saved=getPreferences(0).getString("folder",null);
  if(saved==null){
   LinearLayout page=new LinearLayout(this);page.setOrientation(1);
   add(page,label("Escolha primeiro uma pasta de jogos. O Android precisa conceder acesso de escrita para apagar cópias.",13,-1,false),dp(12));
   final AlertDialog[] prompt={null};
   add(page,action("ESCOLHER PASTA",PURPLE,()->{prompt[0].dismiss();chooseFolder();}),0);
   prompt[0]=showSheet("Limpeza de ROMs","Permissão da pasta",page);return;
  }
  Uri tree=Uri.parse(saved);int removable=0;
  for(Game game:duplicates)if(deletable(game,tree))removable++;
  boolean active=getPreferences(0).getBoolean("auto_cleanup",true);
  String message=duplicates.size()+" cópia(s) idêntica(s) oculta(s) da biblioteca. " + removable + " ROM(s) individuais podem ser apagadas nesta pasta.\n\nA limpeza automática está "+(active?"ativada":"desativada")+". ZIP e 7Z são preservados porque podem conter outros jogos.";
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,chip("▣  ARQUIVOS IDÊNTICOS",CYAN),dp(10));
  TextView explanation=label(message,12,-1,false);
  explanation.setLineSpacing(dp(4),1f);explanation.setPadding(dp(13),dp(11),dp(13),dp(11));
  explanation.setBackground(surface(0xff23304d,15,BORDER));add(page,explanation,dp(11));
  final AlertDialog[] prompt={null};
  add(page,action(active?"LIMPAR CÓPIAS AGORA":"ATIVAR E LIMPAR",PURPLE,()->{
   prompt[0].dismiss();
   if(!hasWriteGrant(tree)){info("Escolha novamente a pasta pelo menu para conceder permissão de escrita.");chooseFolder();return;}
   getPreferences(0).edit().putBoolean("auto_cleanup",true).apply();
   if(!duplicates.isEmpty())eraseDuplicates(tree,new ArrayList<>(duplicates));
   else info("Limpeza automática ativada. Nenhuma cópia idêntica encontrada.");
  }),dp(7));
  add(page,navigation("○","Desativar limpeza automática","As cópias continuam ocultas na biblioteca",()->{
   prompt[0].dismiss();getPreferences(0).edit().putBoolean("auto_cleanup",false).apply();
   info("Limpeza automática desativada. Cópias continuam ocultas.");
  }),0);
  prompt[0]=showSheet("Limpar cópias","Organize a pasta de ROMs",page);
 }
 private boolean hasWriteGrant(Uri tree){for(UriPermission p:getContentResolver().getPersistedUriPermissions())if(tree.equals(p.getUri())&&p.isWritePermission())return true;return false;}
 private boolean deletable(Game game,Uri tree){
   if(game.entry!=null||!game.autoRemovable||!tree.getAuthority().equals(game.uri.getAuthority()))return false;
  try{String rootId=DocumentsContract.getTreeDocumentId(tree),fileId=DocumentsContract.getDocumentId(game.uri);
   return fileId.startsWith(rootId+"/")&& !fileId.equals(rootId);
  }catch(Exception e){return false;}
 }
 private void eraseDuplicates(Uri tree,ArrayList<Game> items){
  if(!hasWriteGrant(tree)){info("Limpeza pendente: escolha novamente a pasta para autorizar escrita.");return;}
  new Thread(()->{int removed=0,failed=0;for(Game game:items){
   if(!deletable(game,tree))continue;
   try{if(DocumentsContract.deleteDocument(getContentResolver(),game.uri))removed++;else failed++;}catch(Exception e){failed++;}
  }final int done=removed,errors=failed;runOnUiThread(()->{
   if(done>0){info(done+" cópia(s) removida(s). Atualizando biblioteca…");scan(tree);}
   else if(errors>0)info("O Android recusou a exclusão de "+errors+" arquivo(s). Confira as permissões da pasta.");
  });},"knight-clean-duplicates").start();
 }
 private void importSave(Uri uri){
  if(selected==null){info("Selecione primeiro a ROM correspondente ao .SRM.");return;}
  Game game=selected;new Thread(()->{try{
    byte[] rom=RomLibrary.readRom(this,game),save=RomLibrary.readSave(this,uri);String hash=sha(rom);
    Files.write(new File(getFilesDir(),hash+".srm").toPath(),save);info("Save importado para "+game.name+". Agora toque Jogar sozinho.");
   }catch(Exception e){info("Não foi possível importar o save: "+e.getMessage());}},"knight-import-save").start();
 }
 private String sha(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
 private String shaFile(File file)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream input=new FileInputStream(file)){byte[] block=new byte[32768];int n;
   while((n=input.read(block))!=-1)digest.update(block,0,n);
  }
  StringBuilder out=new StringBuilder();for(byte value:digest.digest())out.append(String.format(Locale.ROOT,"%02x",value&255));
  return out.toString();
 }
 private void configureController(){
  ArrayList<InputDevice> devices=new ArrayList<>();
  for(int id:InputDevice.getDeviceIds()){
   InputDevice pad=InputDevice.getDevice(id);if(pad!=null&&
    (((pad.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD)||
     ((pad.getSources()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK)))devices.add(pad);
  }
  if(devices.isEmpty()){
   LinearLayout help=new LinearLayout(this);help.setOrientation(1);
   add(help,label("Conecte o controle nas configurações do Android. Ao voltar, abra este menu para mapear os botões SNES.",13,MUTED,false),dp(10));
   final AlertDialog[] dialog={null};
   homeOption(help,dialog,"◉","Abrir Bluetooth","Parear controle no Android",()->{
    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
   });
   dialog[0]=showSheet("Controle externo","Nenhum controle encontrado",help);return;
  }
  if(devices.size()==1){configurePad(devices.get(0));return;}
  String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=devices.get(i).getName();
  showChoices("Controles conectados","Selecione o aparelho que deseja mapear",names,
   -1,which->configurePad(devices.get(which)),false);
 }
 private void configurePad(InputDevice pad){
  String[] buttons={"B","Y","Select","Start","Cima","Baixo","Esquerda","Direita","A","X","L","R"};
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);final AlertDialog[] dialog={null};
  add(layout,eyebrow("MAPEAMENTO SNES"),0);
  int[] mappings=ControllerProfiles.load(getPreferences(0),pad);
  for(int bit=0;bit<buttons.length;bit++){
   final int index=bit;
   String current=bit<mappings.length&&mappings[bit]>0?
    KeyEvent.keyCodeToString(mappings[bit]).replace("KEYCODE_",""):"Padrão do sistema";
   homeOption(layout,dialog,"◉",buttons[bit],current,()->showBinding(pad,index,buttons[index]));
  }
  homeOption(layout,dialog,"✦","Testar botões","Conferir resposta sem abrir um jogo",()->showControllerTest(pad));
  homeOption(layout,dialog,"↺","Restaurar perfil","Voltar ao mapeamento original",()->{
   ControllerProfiles.reset(getPreferences(0),pad);
   if(gameView!=null)gameView.refreshControllerProfiles();toast("Mapeamento padrão restaurado.");
  });
  dialog[0]=showSheet("Controle externo",pad.getName(),layout);
 }
 private void showBinding(InputDevice pad,int bit,String name){
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);
  TextView message=label("Pressione no controle o botão correspondente a "+name+".",16,-1,true);
  message.setPadding(dp(10),dp(14),dp(10),dp(18));add(content,message,0);
  AlertDialog prompt=showSheet("Mapear "+name,pad.getName(),content);
  final boolean[] mapped={false};
  prompt.setOnKeyListener((d,code,event)->{
   if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getDeviceId()==pad.getId()){
    ControllerProfiles.assign(getPreferences(0),pad,bit,code);mapped[0]=true;
    if(gameView!=null)gameView.refreshControllerProfiles();prompt.dismiss();
    toast(name+" configurado neste controle.");return true;
   }
   return false;
  });
  prompt.setOnDismissListener(d->{if(mapped[0])configurePad(pad);});
 }
 private void showControllerTest(InputDevice pad){
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);
  TextView feedback=label("Pressione os botões do seu controle.",15,CYAN,true);
  feedback.setPadding(dp(13),dp(14),dp(13),dp(14));
  feedback.setBackground(surface(0xff202b49,13,BORDER));add(content,feedback,dp(8));
  add(content,label("A indicação mostra se o Android entregou o botão ao aplicativo. Ela não mede o atraso total da TV ou da tela.",12,MUTED,false),0);
  AlertDialog check=showSheet("Teste de controle",pad.getName(),content);
  check.setOnKeyListener((d,key,event)->{
   if(key==KeyEvent.KEYCODE_BACK)return false;
   if(event.getDeviceId()!=pad.getId())return false;
   feedback.setText((event.getAction()==KeyEvent.ACTION_DOWN?"●  PRESSIONADO: ":"○  SOLTO: ")+
    KeyEvent.keyCodeToString(key).replace("KEYCODE_",""));
   return true;
  });
 }
 private void loading(Game game,String message){
  hubVisible=false;
  launchPending=true;
  boolean preserveCouch=couchHost!=null;preserveCouchPending=preserveCouch;
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff372953,0xff10172e,0xff0c1829}));
  scroll.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=35)scroll.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
  LinearLayout content=new LinearLayout(this);content.setGravity(Gravity.CENTER);content.setOrientation(1);content.setPadding(dp(34),dp(35),dp(34),dp(35));scroll.addView(content);
  ImageView art=new ImageView(this);art.setImageDrawable(new CoverArt(game==null?"Luni Arena":game.name));
  art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setBackground(surface(PANEL,18,BORDER));
  art.setClipToOutline(true);art.setElevation(dp(10));
  LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(dp(154),dp(196));artParams.setMargins(0,0,0,dp(25));content.addView(art,artParams);
  if(game!=null)loadCover(game,art,game.identity(),true);
  TextView mark=label("✦   LUNI ARENA  /  SNES   ✦",11,CYAN,true);mark.setGravity(Gravity.CENTER);add(content,mark,dp(5));
  TextView title=label(game==null?"Jogo da sala":shortTitle(game),25,-1,true);title.setGravity(Gravity.CENTER);title.setMaxLines(3);add(content,title,dp(20));
  ProgressBar progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(CYAN));LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(dp(38),dp(38));bar.gravity=Gravity.CENTER;bar.setMargins(0,0,0,dp(9));content.addView(progress,bar);
  status=label(message,14,MUTED,false);status.setGravity(Gravity.CENTER);add(content,status,dp(23));
  TextView cancel=action("Voltar à biblioteca",PANEL,()->{
   launchPending=false;if(lan!=null){lan.close();lan=null;}
   if(!preserveCouch&&couchHost!=null){couchHost.close();couchHost=null;}
   if(couchClient!=null){couchClient.close();couchClient=null;}home();
  });add(content,cancel,0);
  setContentView(scroll);
 }
 private void prepare(String mode){
  if(couchHost!=null&&!mode.equals("couchNext")){
   info("Console compartilhado ativo. Use Continuar console ou encerre-o em Arena e desafios.");return;
  }
  if(selected==null){info("Selecione sua própria ROM antes de abrir ou entrar numa sala.");return;}
  final Game g=selected;loading(g,mode.equals("solo")?"Preparando sua partida…":
      mode.equals("replay")?"Abrindo o último replay…":
      mode.equals("ghost")?"Invocando o fantasma deste jogo…":
      mode.equals("couch")?"Preparando console compartilhado…":
      mode.equals("couchNext")?"Mantendo os controles conectados…":"Procurando jogadores na mesma rede…");
  new Thread(()->{try{byte[] bytes=g==null?null:RomLibrary.readRom(this,g);String hash=bytes==null?null:sha(bytes);
   if(!launchPending)return;
   if(mode.equals("solo")){byte[] seed=g.save==null?null:RomLibrary.readSave(this,g.save);runOnUiThread(()->{if(launchPending)startGame(bytes,null,seed,g.format);});return;}
   if(mode.equals("replay")){
    ReplayArchive.Player playback=ReplayArchive.load(getFilesDir(),hash,shaFile(coreForGame(bytes,g.format,hash)));
    runOnUiThread(()->{if(launchPending)startGame(bytes,null,null,g.format,playback);});return;
   }
   if(mode.equals("ghost")){
    ReplayArchive.Player ghost=ReplayArchive.load(getFilesDir(),hash,shaFile(coreForGame(bytes,g.format,hash)));
    runOnUiThread(()->{if(launchPending)startGame(bytes,null,null,g.format,null,ghost);});return;
   }
   if(mode.equals("couch")){
    CouchSession host=CouchSession.host(this::couchInfo);couchHost=host;
    runOnUiThread(()->{if(launchPending)showCouchLobby(bytes,g.format,host);
     else{host.close();if(couchHost==host)couchHost=null;}});return;
   }
   if(mode.equals("couchNext")){
    CouchSession party=couchHost;
    if(party==null)throw new IOException("Abra primeiro o console compartilhado.");
    runOnUiThread(()->{if(launchPending)startGame(bytes,null,null,g.format,null,null,party);});return;
   }
   if(mode.equals("host")){
    boolean compatible=CoreChoice.preferMesen(bytes,g.format)||getPreferences(0).getBoolean("mesen_for_"+hash,false);
    File chosen=compatible?mesenCore():performanceCore();
    if(!chosen.isFile())throw new IOException("Núcleo "+chosen.getName()+" ausente no APK.");
    LanSession session=LanSession.host(bytes,hash,shortTitle(g),g.format,shaFile(chosen),
     compatible,multitapEnabled(),this::lanInfo);
    lan=session;runOnUiThread(()->{if(launchPending)lobby(bytes,g.format);else session.close();});return;
   }
   runOnUiThread(()->{if(launchPending)joinDialog(bytes,hash,g);});
  }catch(Exception e){runOnUiThread(()->{if(launchPending){launchPending=false;home();info("Falha: "+e.getMessage());}});}},"knight-prepare").start();
 }
 private void lobby(byte[] bytes,String format){
  LinearLayout panelView=new LinearLayout(this);panelView.setOrientation(1);
  add(panelView,chip(lan.multitapEnabled()?"✥  MULTITAP LIGADO · ATÉ 5 JOGADORES":"✥  MULTITAP DESLIGADO · ATÉ 2 JOGADORES",CYAN),dp(9));
  add(panelView,label("Convide os amigos para a mesma rede. Cada participante escolhe sua própria ROM idêntica.",13,MUTED,false),dp(12));
  LinearLayout addressCard=new LinearLayout(this);addressCard.setOrientation(1);
  addressCard.setPadding(dp(14),dp(12),dp(14),dp(11));addressCard.setBackground(surface(0xff222d4b,16,BORDER));
  add(addressCard,eyebrow("ENDEREÇO DA SALA"),0);
  TextView address=label(localIp()+":"+lan.port(),21,-1,true);address.setTextIsSelectable(true);
  address.setPadding(0,0,0,dp(5));add(addressCard,address,0);
  TextView ports=label("Jogo TCP "+lan.port()+"  ·  Busca UDP "+LanSession.BEACON,11,MUTED,false);
  ports.setPadding(0,0,0,0);add(addressCard,ports,0);add(panelView,addressCard,dp(9));
  add(panelView,smallAction("▦  MOSTRAR QR E CONVITE",PURPLE,()->showRoomInvite(localIp(),lan.port())),dp(13));
  add(panelView,eyebrow("JOGADORES NA ARENA"),0);
  lobbyPlayers=label(lan.count()+" / "+(lan.multitapEnabled()?5:2)+" jogadores · vagas em tempo real",13,CYAN,true);
  lobbyPlayers.setPadding(dp(13),dp(13),dp(13),dp(13));
  lobbyPlayers.setBackground(surface(0xff222d4b,15,BORDER));add(panelView,lobbyPlayers,dp(8));
  HorizontalScrollView seatScroller=new HorizontalScrollView(this);seatScroller.setHorizontalScrollBarEnabled(false);
  lobbySeatStrip=new LinearLayout(this);seatScroller.addView(lobbySeatStrip);
  renderLanSeats(lobbySeatStrip,lan.seatStates());add(panelView,seatScroller,dp(12));
  add(panelView,eyebrow("CHECKLIST DA SALA"),0);
  roomHealthLabel=label(lan.diagnosticReport(),12,-1,false);
  roomHealthLabel.setPadding(dp(13),dp(12),dp(13),dp(12));
  roomHealthLabel.setBackground(surface(0xff202c49,15,BORDER));
  add(panelView,roomHealthLabel,dp(8));
  add(panelView,label("A medição é uma amostra da conexão. Ela não representa a latência total do controle e da tela.",11,MUTED,false),dp(5));
  add(panelView,smallAction("⧉  Copiar diagnóstico",PANEL,()->copyRoomDiagnostic(lan)),dp(10));
  int savedDelay=getPreferences(0).getInt("room_delay",0);
  lan.setInputDelayFrames(savedDelay);
  TextView delay=smallAction("Reserva de entrada: "+(lan.automaticDelay()?"automática · ":"manual · ")+lan.inputDelayFrames()+" quadros  ▾",PANEL,()->{});
  roomDelayLabel=delay;
  delay.setOnClickListener(v->{
   String[] choices={"Automática · teste de rede","Rápida · 3 quadros (~50 ms)","Equilibrada · 4 quadros (~67 ms)",
    "Estável · 5 quadros (~83 ms)","Proteção extra · 6 quadros (~100 ms)"};
   showChoices("Resposta da sala","Escolha conforme a qualidade do Wi-Fi",choices,
    lan.automaticDelay()?0:lan.inputDelayFrames()-2,which->{if(lan==null)return;
     lan.setInputDelayFrames(which==0?0:which+2);
     getPreferences(0).edit().putInt("room_delay",which==0?0:which+2).apply();
     delay.setText("Reserva de entrada: "+(lan.automaticDelay()?"automática · ":"manual · ")+lan.inputDelayFrames()+" quadros  ▾");
     if(roomHealthLabel!=null)roomHealthLabel.setText(lan.diagnosticReport());},false);
  });add(panelView,delay,dp(4));
  add(panelView,label("Todos os aparelhos recebem a mesma opção. Menos quadros reduzem o atraso, mas podem aumentar pausas se o Wi-Fi oscilar.",11,MUTED,false),dp(5));
  add(panelView,label("O app confere o SHA-256 de cada ROM. Nenhum arquivo do jogo é distribuído pela sala.",11,MUTED,false),dp(5));
  final AlertDialog[] dialog={null};final boolean[] starting={false};
  add(panelView,action("▶  INICIAR PARTIDA",PURPLE,()->{
   if(lan!=null&&lan.start()){
    starting[0]=true;dialog[0].dismiss();startGame(bytes,lan,null,format);
   }else toast("Aguarde o diagnóstico de entrada do convidado terminar.");
  }),dp(8));
  dialog[0]=showSheet("Arena Luni","Super Nintendo · até "+(lan.multitapEnabled()?"cinco":"dois")+" jogadores",panelView);
  dialog[0].setOnDismissListener(d->{roomDelayLabel=null;lobbySeatStrip=null;roomHealthLabel=null;lobbyPlayers=null;if(!starting[0]){
   launchPending=false;if(lan!=null){lan.close();lan=null;}home();
  }});
 }
 private void copyRoomDiagnostic(LanSession room){
  if(room==null)return;
  android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
  clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Luni Arena",room.diagnosticReport()));
  toast("Diagnóstico da sala copiado.");
 }
 private void showRoomStatus(){
  LanSession room=lan;if(room==null)return;
  LinearLayout panelView=new LinearLayout(this);panelView.setOrientation(1);
  add(panelView,chip("◈  "+room.count()+" / "+(room.multitapEnabled()?5:2)+"  NA ARENA",CYAN),dp(9));
  add(panelView,label("Jogadores que perderam a conexão conservam sua vaga até o fim da partida. O jogo continua e a entrada retoma do quadro atual.",12,MUTED,false),dp(12));
  HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
  LinearLayout strip=new LinearLayout(this);scroller.addView(strip);
  liveSeatStrip=strip;renderLanSeats(strip,room.seatStates());add(panelView,scroller,dp(9));
  add(panelView,eyebrow("CONEXÃO E INTEGRIDADE"),dp(8));
  TextView diagnosis=label(room.diagnosticReport(),12,-1,false);
  diagnosis.setPadding(dp(13),dp(12),dp(13),dp(12));
  diagnosis.setBackground(surface(0xff202c49,15,BORDER));
  liveRoomHealth=diagnosis;add(panelView,diagnosis,dp(9));
  add(panelView,smallAction("⧉  Copiar diagnóstico",PANEL,()->copyRoomDiagnostic(room)),dp(9));
  final AlertDialog dialog=showSheet("Jogadores da sala","Entradas e saídas em tempo real",panelView);
  Handler refresh=new Handler(Looper.getMainLooper());
  final int[][] previousSeats={room.seatStates()};
  Runnable update=new Runnable(){@Override public void run(){
   if(!dialog.isShowing()||lan!=room)return;
   diagnosis.setText(room.diagnosticReport());int[] seats=room.seatStates();
   if(!Arrays.equals(previousSeats[0],seats)){renderLanSeats(strip,seats);previousSeats[0]=seats;}
   refresh.postDelayed(this,1200);
  }};
  refresh.postDelayed(update,1200);
  dialog.setOnDismissListener(d->{if(liveSeatStrip==strip)liveSeatStrip=null;
   if(liveRoomHealth==diagnosis)liveRoomHealth=null;refresh.removeCallbacks(update);});
 }
 private void showReconnect(String message){
  runOnUiThread(()->{
   if(!playing||gameFrame==null)return;
   if(reconnectBanner==null){
    LinearLayout box=new LinearLayout(this);box.setOrientation(1);
    box.setPadding(dp(18),dp(14),dp(18),dp(14));box.setElevation(dp(15));
    box.setBackground(surface(0xf51a2643,20,PURPLE));
    add(box,chip("↻  RECONECTANDO À ARENA",CYAN),dp(5));
    reconnectMessage=label(message,13,-1,false);add(box,reconnectMessage,dp(7));
    LinearLayout buttons=new LinearLayout(this);
    TextView retry=smallAction("Tentar agora",PURPLE,()->{reconnectNow=true;
     if(reconnectMessage!=null)reconnectMessage.setText("Tentando entrar na sua vaga…");
    });
    TextView leave=smallAction("Sair",0xff37435f,this::leaveGame);
    LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);
    half.rightMargin=dp(8);buttons.addView(retry,half);
    buttons.addView(leave,new LinearLayout.LayoutParams(0,-2,1));add(box,buttons,0);
    FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(
     Math.min(dp(425),getResources().getDisplayMetrics().widthPixels-dp(30)),-2,
     Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
    position.bottomMargin=dp(16);gameFrame.addView(box,position);reconnectBanner=box;
   }
   if(reconnectMessage!=null)reconnectMessage.setText(message);
  });
 }
 private void hideReconnect(){
  runOnUiThread(()->{if(reconnectBanner!=null&&gameFrame!=null)gameFrame.removeView(reconnectBanner);
   reconnectBanner=null;reconnectMessage=null;reconnectNow=false;});
 }
 private void showCouchLobby(byte[] bytes,String format,CouchSession host){
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,chip("▣  UMA TELA · ATÉ CINCO CONTROLES",CYAN),dp(11));
  add(page,label("Deixe este aparelho exibindo o jogo. Os amigos entram em Arena → Controle da TV e usam o celular como gamepad, sem baixar a ROM.",13,-1,false),dp(12));
  TextView address=label("IP  "+localIp()+":"+CouchSession.PORT,20,-1,true);
  address.setTextIsSelectable(true);address.setBackground(surface(0xff263454,15,BORDER));
  address.setPadding(dp(14),dp(13),dp(14),dp(13));add(page,address,dp(12));
  couchSeats=label(roomSeats(host.count()),13,CYAN,true);
  couchSeats.setPadding(dp(14),dp(12),dp(14),dp(12));
  couchSeats.setBackground(surface(0xff202e49,15,BORDER));add(page,couchSeats,dp(9));
  add(page,label("Os convidados veem só os controles. Para cada um ter a própria imagem, use Criar sala normal. Uma TV por espelhamento pode acrescentar atraso de vídeo.",11,MUTED,false),dp(12));
  final AlertDialog[] dialog={null};final boolean[] starting={false};
  add(page,action("▶  COMEÇAR NO CONSOLE",PURPLE,()->{
   starting[0]=true;dialog[0].dismiss();couchSeats=null;
   startGame(bytes,null,null,format,null,null,host);
  }),dp(4));
  dialog[0]=showSheet("Modo console",shortTitle(selected),page);
  dialog[0].setOnDismissListener(d->{if(!starting[0]){
   launchPending=false;couchSeats=null;host.close();if(couchHost==host)couchHost=null;home();
  }});
 }
 private void showAssistantSeat(){
  CouchSession host=couchHost;
  if(host==null){toast("Abra o console compartilhado primeiro.");return;}
  String[] choices={"Controles separados · até cinco personagens",
   "P2 ajuda P1 · mesmo personagem","P3 ajuda P1 · mesmo personagem",
   "P4 ajuda P1 · mesmo personagem","P5 ajuda P1 · mesmo personagem"};
  showChoices("Dupla no P1","Selecione um controle conectado. Direções opostas se cancelam.",
   choices,host.assistantSeat(),which->{
    if(couchHost!=host)return;
    if(!host.setAssistantSeat(which))toast("Esse controle ainda não entrou na sala.");
    else toast(which==0?"Controles separados.":"P"+(which+1)+" agora ajuda o P1.");
   },playing&&activeSolo);
 }
 private void showCouchJoin(){
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,chip("◉  CELULAR COMO CONTROLE",CYAN),dp(12));
  add(page,label("O console mostra o jogo na TV ou em sua tela. Este celular envia apenas os botões; nenhuma ROM é recebida.",13,-1,false),dp(9));
  add(page,eyebrow("CONSOLES ENCONTRADOS NO WI-FI"),0);
  LinearLayout found=new LinearLayout(this);found.setOrientation(1);add(page,found,dp(9));
  TextView scanning=label("Procurando…",12,MUTED,false);add(found,scanning,dp(6));
  add(page,eyebrow("OU DIGITE O ENDEREÇO DO CONSOLE"),dp(7));
  EditText address=new EditText(this);address.setSingleLine(true);
  address.setHint("192.168.1.10:35672");address.setTextColor(-1);address.setHintTextColor(MUTED);
  address.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
  address.setBackground(surface(0xff263454,14,BORDER));
  address.setPadding(dp(13),dp(11),dp(13),dp(11));add(page,address,dp(10));
  final AlertDialog[] dialog={null};
  add(page,action("CONECTAR CONTROLE",PURPLE,()->{
   String raw=address.getText().toString().trim();int port=CouchSession.PORT;
   int colon=raw.lastIndexOf(':');
   if(colon>=0){try{port=Integer.parseInt(raw.substring(colon+1));raw=raw.substring(0,colon);}
    catch(NumberFormatException e){toast("Porta inválida.");return;}}
   if(raw.isEmpty()||port<1||port>65535){toast("Confira IP e porta.");return;}
   dialog[0].dismiss();connectCouch(raw,port);
  }),dp(7));
  dialog[0]=showSheet("Controle da TV","Entre no console compartilhado",page);
  WifiManager.MulticastLock multicast=null;
  try{
   WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
   if(wifi!=null){multicast=wifi.createMulticastLock("knight-couch-discovery");
    multicast.setReferenceCounted(false);multicast.acquire();}
  }catch(RuntimeException ignored){}
  final WifiManager.MulticastLock discoveryLock=multicast;
  CouchSession.discover(new CouchSession.DiscoveryListener(){
   public void found(String ip,int port){runOnUiThread(()->{
    if(!dialog[0].isShowing())return;
    if(scanning.getParent()==found)found.removeView(scanning);
    add(found,navigation("▣",ip+":"+port,"Tocar para entrar como P2–P5",()->{
     dialog[0].dismiss();connectCouch(ip,port);
    }),dp(6));
   });}
   public void finished(){
    if(discoveryLock!=null&&discoveryLock.isHeld())discoveryLock.release();
    runOnUiThread(()->{
    if(dialog[0].isShowing()&&found.getChildCount()==1&&scanning.getParent()==found)
     scanning.setText("Nenhum console encontrado. Digite o IP mostrado no host.");
   });}
  });
 }
 private void connectCouch(String address,int port){
  loading(null,"Conectando ao console "+address+":"+port+"…");
  new Thread(()->{
   try{
    CouchSession guest=CouchSession.join(address,port,message->{
     if("rewind".equals(message)||"next".equals(message))runOnUiThread(()->{
      if(controllerOnly&&controllerVote!=null){controllerVoteActive=false;
       controllerVote.setText("⏪  Pedir volta");
       if("rewind".equals(message))toast("A turma voltou cinco segundos.");}
     });
    });
    runOnUiThread(()->{if(launchPending)startControllerOnly(guest);else guest.close();});
   }catch(Exception e){runOnUiThread(()->{if(launchPending){launchPending=false;home();toast("Controle: "+e.getMessage());}});}
  },"knight-couch-join").start();
 }
 private void startControllerOnly(CouchSession guest){
  launchPending=false;hubVisible=false;playing=true;paused=false;activeSolo=false;
  controllerOnly=true;couchClient=guest;
  GameView controller=new GameView(this);gameView=controller;
  controller.setExternal(external());
  setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
  getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(BG);
  frame.addView(controller,new FrameLayout.LayoutParams(-1,-1));gameFrame=frame;
  TextView title=chip("◉  CONTROLE P"+(guest.seat()+1)+"  ·  CONSOLE COMPARTILHADO",CYAN);
  FrameLayout.LayoutParams labelPlace=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  labelPlace.topMargin=dp(12);frame.addView(title,labelPlace);
  TextView exit=smallAction("✕  Sair",0xff344567,this::stopControllerOnly);
  FrameLayout.LayoutParams exitPlace=new FrameLayout.LayoutParams(-2,dp(42),Gravity.TOP|Gravity.RIGHT);
  exitPlace.setMargins(0,dp(10),dp(12),0);frame.addView(exit,exitPlace);
  final TextView[] rewindButton={null};
  rewindButton[0]=smallAction("⏪  Pedir volta",0xff344567,()->{
   try{
    boolean next=!controllerVoteActive;guest.voteRewind(next);controllerVoteActive=next;
    rewindButton[0].setText(next?"⏪  Pedido enviado":"⏪  Pedir volta");
   }catch(IOException e){toast("Voto não enviado: "+e.getMessage());}
  });
  controllerVote=rewindButton[0];controllerVoteActive=false;
  FrameLayout.LayoutParams rewindPlace=new FrameLayout.LayoutParams(-2,dp(42),Gravity.TOP|Gravity.LEFT);
  rewindPlace.setMargins(dp(12),dp(10),0,0);frame.addView(rewindButton[0],rewindPlace);
  frame.setOnApplyWindowInsetsListener((v,insets)->{
   int left=0,top=0,right=0,bottom=0;
   if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
    android.view.DisplayCutout cut=insets.getDisplayCutout();
    left=cut.getSafeInsetLeft();top=cut.getSafeInsetTop();
    right=cut.getSafeInsetRight();bottom=cut.getSafeInsetBottom();
   }
   if(Build.VERSION.SDK_INT>=29){
    android.graphics.Insets gestures=insets.getSystemGestureInsets();
    left=Math.max(left,gestures.left);right=Math.max(right,gestures.right);
    bottom=Math.max(bottom,gestures.bottom);
   }
   controller.setSafeInsets(left,top,right,bottom);
   FrameLayout.LayoutParams votePlace=(FrameLayout.LayoutParams)rewindButton[0].getLayoutParams();
   votePlace.topMargin=top+dp(10);votePlace.leftMargin=left+dp(12);rewindButton[0].setLayoutParams(votePlace);
   FrameLayout.LayoutParams place=(FrameLayout.LayoutParams)exit.getLayoutParams();
   place.topMargin=top+dp(10);place.rightMargin=right+dp(12);exit.setLayoutParams(place);
   return insets;
  });
  setContentView(frame);controller.requestFocus();
  new Thread(()->{
   String problem=null;
   try{while(controllerOnly&&couchClient==guest){
    guest.send(activityResumed?controller.mask():0);Thread.sleep(16);
   }}catch(Exception e){problem=e.getMessage();}
   finally{guest.close();String detail=problem;
    runOnUiThread(()->{if(controllerOnly&&couchClient==guest){
     stopControllerOnly();if(detail!=null)toast("Controle desconectado: "+detail);
    }});
   }
  },"knight-couch-controller").start();
 }
 private void stopControllerOnly(){
  if(!controllerOnly)return;
  controllerOnly=false;playing=false;paused=false;
  if(couchClient!=null){couchClient.close();couchClient=null;}
  controllerVote=null;controllerVoteActive=false;gameView=null;gameFrame=null;home();
 }
 private String localIp(){
  try{
   ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
   for(Network network:cm.getAllNetworks()){
    NetworkCapabilities capabilities=cm.getNetworkCapabilities(network);
    if(capabilities==null||!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
    LinkProperties link=cm.getLinkProperties(network);if(link==null)continue;
    for(LinkAddress address:link.getLinkAddresses()){
     InetAddress ip=address.getAddress();
     if(ip instanceof Inet4Address&&!ip.isLoopbackAddress())return ip.getHostAddress();
    }
   }
  }catch(Exception ignored){}
  try{
   String fallback=null;
   for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces())){
    if(!nic.isUp()||nic.isLoopback())continue;
    for(InetAddress ip:Collections.list(nic.getInetAddresses()))
     if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){
      String name=nic.getName().toLowerCase(Locale.ROOT);
      if(name.startsWith("wlan")||name.startsWith("swlan")||name.startsWith("ap"))return ip.getHostAddress();
      if(fallback==null)fallback=ip.getHostAddress();
     }
   }
   if(fallback!=null)return fallback;
  }catch(Exception ignored){}
  return "Consulte o IP do Wi-Fi no Android";
 }
 private void showRoomInvite(String address,int port){
  try{
   String link=RoomInvite.link(address,port);
   com.google.zxing.common.BitMatrix pixels=new com.google.zxing.qrcode.QRCodeWriter()
      .encode(link,com.google.zxing.BarcodeFormat.QR_CODE,dp(220),dp(220));
   int size=pixels.getWidth();Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
   int[] dots=new int[size*size];
   for(int y=0;y<size;y++)for(int x=0;x<size;x++)dots[y*size+x]=pixels.get(x,y)?0xff172047:0xffffffff;
   bitmap.setPixels(dots,0,size,0,0,size,size);
   LinearLayout box=new LinearLayout(this);box.setOrientation(1);
   ImageView code=new ImageView(this);code.setImageBitmap(bitmap);code.setContentDescription("QR do convite de sala");
   code.setBackground(panel(-1,dp(12)));code.setPadding(dp(8),dp(8),dp(8),dp(8));
   LinearLayout.LayoutParams qr=new LinearLayout.LayoutParams(dp(236),dp(236));qr.gravity=Gravity.CENTER_HORIZONTAL;
   qr.bottomMargin=dp(10);box.addView(code,qr);
   TextView text=label(link,13,CYAN,true);text.setTextIsSelectable(true);add(box,text,dp(5));
   add(box,label("O convite contém só IP e porta. Ambos os aparelhos precisam estar na mesma rede ou em uma rede que permita a conexão.",12,MUTED,false),dp(6));
   final AlertDialog[] dialog={null};
   homeOption(box,dialog,"↗","Compartilhar convite","Abrir opções de envio do Android",()->{
      Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");
      share.putExtra(Intent.EXTRA_TEXT,"Luni Arena • entre na minha sala: "+link);
      startActivity(Intent.createChooser(share,"Compartilhar convite"));
   });
   homeOption(box,dialog,"⧉","Copiar convite","IP e porta prontos para enviar",()->{
      android.content.ClipboardManager clip=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      clip.setPrimaryClip(ClipData.newPlainText("Convite Luni Arena",link));
      toast("Convite copiado.");
   });
   dialog[0]=showSheet("Convidar amigos","QR da sua sala local",box);
  }catch(Exception e){toast("Não foi possível gerar o convite: "+e.getMessage());}
 }
 private void connectToHost(byte[] bytes,String hash,Game localGame,String address,int port){
  info("Conectando a "+address+":"+port+"…");
  new Thread(()->{
   try{
    String standardHash=performanceCore().isFile()?shaFile(performanceCore()):"";
    String compatibleHash=mesenCore().isFile()?shaFile(mesenCore()):"";
    LanSession.Joined joined=LanSession.join(this,address,port,bytes,hash,standardHash,compatibleHash,
     localGame,()->launchPending,this::lanInfo);
    lan=joined.session;
    runOnUiThread(()->{if(launchPending){
     if(joined.game!=null){selected=joined.game;getPreferences(0).edit().putString("selected",selected.identity()).apply();}
     startGame(joined.bytes,joined.session,null,joined.format);
    }else joined.session.close();});
   }catch(Exception e){runOnUiThread(()->{if(launchPending){
    launchPending=false;home();
    LinearLayout details=new LinearLayout(this);details.setOrientation(1);
    add(details,label(e.getMessage()==null?"A conexão falhou.":e.getMessage(),15,-1,false),dp(10));
    final AlertDialog[] warning={null};
    add(details,action("↻  ESCOLHER OUTRA SALA",PURPLE,()->{
     warning[0].dismiss();prepare("join");
    }),0);
    warning[0]=showSheet("Não foi possível entrar",address+":"+port,details);
   }});}
  },"knight-join").start();
 }
 private void joinDialog(byte[] bytes,String hash,Game localGame){
  LinearLayout panelView=new LinearLayout(this);panelView.setOrientation(1);
  add(panelView,chip("✓  SUA ROM · "+hash.substring(0,12)+"…",CYAN),dp(8));
  add(panelView,label("Ao conectar, a sala confere a ROM e o núcleo deste APK e mede cinco amostras da rede.",12,MUTED,false),dp(10));
  add(panelView,eyebrow("SALAS NA MESMA REDE"),0);
  add(panelView,label("Selecione uma cópia idêntica da ROM em cada aparelho antes de entrar.",12,MUTED,false),dp(5));
  TextView searchStatus=label("Procurando salas…",13,MUTED,false);add(panelView,searchStatus,dp(5));
  LinearLayout rooms=new LinearLayout(this);rooms.setOrientation(1);add(panelView,rooms,dp(6));
  add(panelView,label("Se a sala não aparecer, use o endereço mostrado no aparelho que criou a sala.",12,MUTED,false),dp(7));
  EditText addressField=new EditText(this);addressField.setSingleLine(true);
  addressField.setHint("IP do host: 192.168.1.5");addressField.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
  addressField.setTextColor(-1);addressField.setHintTextColor(MUTED);
  addressField.setPadding(dp(12),0,dp(12),0);addressField.setBackground(surface(0xff202b49,13,BORDER));
  addressField.setMinHeight(dp(47));
  add(panelView,addressField,dp(5));
  EditText portField=new EditText(this);portField.setSingleLine(true);
  portField.setHint("Porta TCP");portField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  portField.setTextColor(-1);portField.setHintTextColor(MUTED);
  portField.setPadding(dp(12),0,dp(12),0);portField.setBackground(surface(0xff202b49,13,BORDER));
  portField.setMinHeight(dp(47));
  portField.setText(String.valueOf(LanSession.PORT));add(panelView,portField,dp(8));
  RoomInvite invited=incomingInvite;incomingInvite=null;
  if(invited!=null){addressField.setText(invited.address);portField.setText(String.valueOf(invited.port));}
  TextView paste=smallAction("Colar convite",PANEL,()->{
   android.content.ClipboardManager clip=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   if(!clip.hasPrimaryClip()||clip.getPrimaryClip().getItemCount()<1)return;
   try{
    RoomInvite found=RoomInvite.parse(clip.getPrimaryClip().getItemAt(0).coerceToText(this).toString());
    addressField.setText(found.address);portField.setText(String.valueOf(found.port));
   }catch(IOException e){toast("Convite inválido: "+e.getMessage());}
  });add(panelView,paste,dp(6));
  LanSession.Discovery[] scan={null};
  int[] scanNumber={0};
  Runnable[] startScan={null};
  final boolean[] connecting={false};
  TextView retry=smallAction("↻  Procurar novamente",PANEL,()->startScan[0].run());add(panelView,retry,dp(10));
  final AlertDialog[] dialog={null};
  add(panelView,action("⌁  CONECTAR PELO IP",PURPLE,()->{
   String ip=addressField.getText().toString().trim(),textPort=portField.getText().toString().trim();
   if(ip.isEmpty()){addressField.setError("Digite o IP do host");return;}
   int port;try{port=Integer.parseInt(textPort);}catch(NumberFormatException e){port=0;}
   if(port<1||port>65535){portField.setError("Porta entre 1 e 65535");return;}
   connecting[0]=true;dialog[0].dismiss();connectToHost(bytes,hash,localGame,ip,port);
  }),0);
  startScan[0]=()->{
   if(scan[0]!=null)scan[0].close();
   int currentScan=++scanNumber[0];
   rooms.removeAllViews();searchStatus.setText("Procurando salas no Wi-Fi…");
   scan[0]=LanSession.discover(this,new LanSession.DiscoveryListener(){
    @Override public void found(String ip,int port){runOnUiThread(()->{
     if(dialog[0]==null||!dialog[0].isShowing()||!launchPending||currentScan!=scanNumber[0])return;
     searchStatus.setText("Toque na sala para entrar:");
     TextView item=navigation("●","Sala "+ip+":"+port,"TOQUE PARA ENTRAR",()->{
      connecting[0]=true;dialog[0].dismiss();connectToHost(bytes,hash,localGame,ip,port);
     });add(rooms,item,dp(6));
    });}
    @Override public void finished(){runOnUiThread(()->{
     if(dialog[0]!=null&&dialog[0].isShowing()&&launchPending&&currentScan==scanNumber[0])searchStatus.setText(rooms.getChildCount()==0?
         "Nenhuma sala encontrada. Digite o IP e a porta abaixo.":"Salas disponíveis:");
    });}
   });
  };
  dialog[0]=showSheet("Entrar na Arena","Salas locais e acesso por IP",panelView);
  dialog[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  dialog[0].setOnDismissListener(d->{
   if(scan[0]!=null)scan[0].close();
   if(!connecting[0]&&launchPending){launchPending=false;home();}
  });
  startScan[0].run();
 }
 private void startGame(byte[] bytes,LanSession session,byte[] seedRam,String format){
  startGame(bytes,session,seedRam,format,null);
 }
 private void startGame(byte[] bytes,LanSession session,byte[] seedRam,String format,ReplayArchive.Player playback){
  startGame(bytes,session,seedRam,format,playback,null);
 }
 private void startGame(byte[] bytes,LanSession session,byte[] seedRam,String format,
                        ReplayArchive.Player playback,ReplayArchive.Player ghost){
  startGame(bytes,session,seedRam,format,playback,ghost,null);
 }
 private void startGame(byte[] bytes,LanSession session,byte[] seedRam,String format,
                        ReplayArchive.Player playback,ReplayArchive.Player ghost,CouchSession couch){
  if(playing||!launchPending)return;
  hubVisible=false;
  launchPending=false;preserveCouchPending=false;playing=true;paused=false;coreReady=false;
  activeSolo=session==null;resetRequested=false;emulatorCommands.clear();
  replaying=playback!=null;ghostMode=ghost!=null;ghostPlayer=ghost;
  recording=false;replayRecorder=null;rewindBuffer.clear();soloSpeed=1f;
  replayMoments.clear();
  actualFps=networkWaitMs=networkWorstMs=0;networkDiagnostics.clear();
  lobbyPlayers=null;
  try{
   gameView=new GameView(this);
   migrateVideoSettings(selected);
   gameView.configureVideo(selected==null?null:gameKey(selected),session==null?0:session.player());
   gameView.setReplaying(replaying);
   gameView.setExternal(external());
  }catch(RuntimeException|LinkageError e){
   android.util.Log.e("LuniArena","Falha ao preparar a tela do jogo",e);
   playing=false;coreReady=false;replaying=false;ghostMode=false;gameView=null;
   if(session!=null){session.close();if(lan==session)lan=null;}
   home();toast("Não foi possível abrir o jogo: "+e.getMessage());return;
  }
  if(selected!=null&&!replaying)getPreferences(0).edit().putLong("played_"+gameKey(selected),System.currentTimeMillis()).apply();
  setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
  getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(BG);frame.addView(gameView,new FrameLayout.LayoutParams(-1,-1));
  gameFrame=frame;
  TextView more=label("⋮",26,-1,true);more.setGravity(Gravity.CENTER);more.setPadding(0,0,0,0);
  more.setBackground(panel(0xb0263152,dp(13)));
  FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.RIGHT);
  mp.setMargins(0,dp(8),dp(8),0);frame.addView(more,mp);
  frame.setOnApplyWindowInsetsListener((v,insets)->{
   int left=0,top=0,right=0,bottom=0;
   if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){
    android.view.DisplayCutout cut=insets.getDisplayCutout();
    left=cut.getSafeInsetLeft();top=cut.getSafeInsetTop();
    right=cut.getSafeInsetRight();bottom=cut.getSafeInsetBottom();
   }
   if(Build.VERSION.SDK_INT>=29){
    android.graphics.Insets gestures=insets.getSystemGestureInsets();
    left=Math.max(left,gestures.left);right=Math.max(right,gestures.right);
    bottom=Math.max(bottom,gestures.bottom);
   }
   gameView.setSafeInsets(left,top,right,bottom);
   FrameLayout.LayoutParams menuPlace=(FrameLayout.LayoutParams)more.getLayoutParams();
   menuPlace.topMargin=top+dp(8);menuPlace.rightMargin=right+dp(8);
   more.setLayoutParams(menuPlace);
   return insets;
  });
  gameMenuAnchor=more;more.setOnClickListener(v->showGameMenu(more));
  setContentView(frame);gameView.requestFocus();
  gameThread=new Thread(()->gameLoop(bytes,session,seedRam,format,playback,ghost,couch),"knight-emulator");gameThread.start();
 }
 private void toast(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_SHORT).show());}
 private File stateFile(int slot){File dir=new File(getFilesDir(),"states");if(!dir.exists())dir.mkdirs();return new File(dir,activeRomId+"-"+slot+".state");}
 private void showGameMenu(View anchor){
  showGameMenu(anchor,false);
 }
 private void showGameMenu(View anchor,boolean advanced){
  if(!playing||gameFrame==null||gameView==null)return;
  if(gameOverlay!=null){closeGameMenu(false);return;}
  boolean solo=activeSolo,playback=replaying;
  if(solo)paused=true;
  gameView.setInputBlocked(true);
  FrameLayout overlay=new FrameLayout(this);
  overlay.setBackgroundColor(solo?0x9a060b1b:0x69060b1b);
  overlay.setOnClickListener(v->closeGameMenu(false));
  overlay.setFocusableInTouchMode(true);
  // The menu scrim fills the screen. Keep controller focus without letting
  // Android add its default full-screen focus highlight above the game.
  overlay.setDefaultFocusHighlightEnabled(false);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
  GradientDrawable drawer=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff30375b,0xff18233e,0xff101b31});
  drawer.setCornerRadius(dp(20));drawer.setStroke(dp(1),0xff57658b);
  scroll.setBackground(drawer);scroll.setClipToOutline(true);
  scroll.setElevation(dp(12));
  LinearLayout panelView=new LinearLayout(this);panelView.setOrientation(1);
  panelView.setPadding(dp(17),dp(16),dp(17),dp(18));scroll.addView(panelView);
  FrameLayout.LayoutParams place=new FrameLayout.LayoutParams(
    Math.min(dp(370),getResources().getDisplayMetrics().widthPixels-dp(24)),-1,Gravity.TOP|Gravity.RIGHT);
  FrameLayout.LayoutParams menuPlace=(FrameLayout.LayoutParams)anchor.getLayoutParams();
  place.setMargins(dp(8),menuPlace.topMargin,menuPlace.rightMargin,dp(8));
  overlay.addView(scroll,place);gameFrame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));gameOverlay=overlay;
  gameView.clearFocus();overlay.requestFocus();
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  if(selected!=null){
   ImageView art=new ImageView(this);art.setImageDrawable(new CoverArt(selected.name));
   art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setClipToOutline(true);
   art.setBackground(panel(PANEL,dp(8)));
   header.addView(art,new LinearLayout.LayoutParams(dp(48),dp(62)));
   loadCover(selected,art,selected.identity(),false);
  }
  LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);titles.setPadding(dp(9),0,0,0);
  TextView title=label("LUNI ARENA",17,-1,true);title.setPadding(0,0,0,dp(3));titles.addView(title);
  TextView game=label(shortTitle(selected),12,CYAN,false);game.setMaxLines(2);
  game.setEllipsize(TextUtils.TruncateAt.END);game.setPadding(0,0,0,0);titles.addView(game);
  header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  TextView close=smallAction("✕",0xff303a58,()->closeGameMenu(false));
  close.setContentDescription("Fechar menu e continuar jogo");
  header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));add(panelView,header,dp(5));
  String caption=solo?(playback?"REPRODUZINDO REPLAY":ghostMode?"ARENA FANTASMA · JOGO PAUSADO":
     couchHost!=null?"MODO CONSOLE · JOGO PAUSADO":"JOGO PAUSADO"):
     "SALA ATIVA · JOGO CONTINUA";
  TextView badge=chip(caption,CYAN);add(panelView,badge,dp(4));
  menuItem(panelView,"▶","Continuar jogo","Voltar para a partida",false,()->{});
  if(!advanced){
   menuSection(panelView,"ACESSO RÁPIDO");
   if(coreReady&&!playback&&!ghostMode&&(solo||lan!=null&&lan.player()==0))
    menuItem(panelView,"⟲","Reiniciar ROM",solo?"Voltar à tela inicial do jogo":"O host reinicia para todos",true,this::confirmRomReset);
   if(coreReady){
    if(!solo)menuNote(panelView,"Multitap "+(activeMultitap?"ligado":"desligado")+" · definido pelo host antes de criar a sala.");
    else if(!playback&&!ghostMode&&!recording)menuItem(panelView,"✥","Multitap",activeMultitap?"Ligado · até 5 controles":"Desligado · até 2 controles",true,this::showMultitapOptions);
   }
   if(recording)menuItem(panelView,"✦","Marcar momento",
    "Guardar um destaque no replay atual",true,this::markMoment);
   if(couchHost!=null)menuItem(panelView,"✣","Dupla no P1",
    couchHost.assistantSeat()==0?"Escolher controle auxiliar":"P"+(couchHost.assistantSeat()+1)+" ajuda P1",true,this::showAssistantSeat);
   if(solo&&!playback&&!ghostMode&&couchHost==null&&coreReady){
    menuItem(panelView,"▣","Salvar estado","Guardar o progresso em um dos cinco espaços",true,()->stateSlots(true));
    if(!recording){
     menuItem(panelView,"↻","Carregar estado","Continuar de um espaço salvo",true,()->stateSlots(false));
     menuItem(panelView,"✦","Cheats","Ativar códigos em jogo solo",true,this::showCheats);
    }
   }
   if(gameView.playerIndex()<2)menuItem(panelView,"◧",gameView.videoMode()==1?
    "Ver quadro inteiro":"Câmera pessoal rápida",
    gameView.videoMode()==1?"Voltar aos menus e à pista completa":
      "Ampliar minha metade nos jogos de tela dividida",false,this::togglePersonalCamera);
   menuItem(panelView,"▤","Imagem e controles","Filtros, enquadramento, HUD e ferramentas",true,
    ()->showGameMenu(anchor,true));
   if(!solo){
    menuItem(panelView,"♞","Jogadores da sala","Ver quem está conectado ou retomando",true,this::showRoomStatus);
    menuItem(panelView,"◈","Luni Radar","FPS e espera na sala",true,this::showDiagnostics);
    if(lan!=null&&lan.player()!=0&&reconnectBanner!=null)
     menuItem(panelView,"↻","Reconectar agora","Tentar retomar sua vaga",false,()->{reconnectNow=true;});
   }
   menuItem(panelView,"⌂",couchHost!=null?"Escolher outro jogo":"Biblioteca",
    couchHost!=null?"Manter os controles conectados":solo?"Encerrar a partida":"Sair da sala neste aparelho",false,this::leaveGame);
   return;
  }
  menuItem(panelView,"‹","Voltar ao menu rápido","Ações essenciais da partida",true,
   ()->showGameMenu(anchor,false));
  View[] destinations=new View[5];
  HorizontalScrollView categories=new HorizontalScrollView(this);
  categories.setHorizontalScrollBarEnabled(false);
  LinearLayout categoriesRow=new LinearLayout(this);
  String[] categoryNames={"Partida","Imagem","Controle","Sala","Radar"};
  for(int i=0;i<categoryNames.length;i++){
   if(i==2&&playback&&!gameView.isExternal()||i==3&&solo)continue;
   final int index=i;
   TextView tab=smallAction(categoryNames[i],0xff354362,()->{
    View destination=destinations[index];
    if(destination!=null)scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,destination.getTop()-dp(12))));
   });
   tab.setMinWidth(dp(70));
   LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(-2,dp(36));
   cell.rightMargin=dp(6);categoriesRow.addView(tab,cell);
  }
  categories.addView(categoriesRow);add(panelView,categories,dp(7));
  destinations[0]=menuSection(panelView,"PARTIDA");
  if(coreReady&&solo&&!playback&&!ghostMode&&!recording)
   menuItem(panelView,"✥","Multitap",activeMultitap?"Ligado · até 5 controles":"Desligado · até 2 controles",true,this::showMultitapOptions);
  else if(coreReady&&!solo)menuNote(panelView,"Multitap "+(activeMultitap?"ligado":"desligado")+" · altere antes de criar outra sala.");
  if(solo&&!playback&&coreReady){
   if(!ghostMode)menuItem(panelView,"▣","Salvar estado","Cinco espaços disponíveis",true,()->stateSlots(true));
   if(!recording&&!ghostMode){
    menuItem(panelView,"↻","Carregar estado","Retomar um save anterior",true,()->stateSlots(false));
    if(couchHost==null)menuItem(panelView,"⌁","Cheats e GameShark","Códigos para esta ROM",true,this::showCheats);
   }
   if(!recording){
    if(!ghostMode)menuItem(panelView,"⏪","Rebobinar",rewindEnabled?"Ativado; toque para desligar":"Desativado; toque para ligar",false,()->{
     rewindEnabled=!rewindEnabled;getPreferences(0).edit().putBoolean("rewind_enabled",rewindEnabled).apply();
     if(!rewindEnabled)emulatorCommands.add(rewindBuffer::clear);
    });
    if(!ghostMode&&rewindEnabled&&rewindBuffer.count()>0)menuItem(panelView,"◀","Voltar 5 segundos","Disponível depois de jogar",false,()->
     emulatorCommands.add(()->{byte[] point=rewindBuffer.rewind(emulatedFrames,300);
      if(point!=null&&Core.restore(point)){rewindBuffer.clear();if(activeAudio!=null)activeAudio.flushSolo();toast("Voltamos cerca de 5 segundos.");}
      else toast("Ainda não há histórico para rebobinar.");}));
   }
   if(ghostMode)menuNote(panelView,"Os rivais repetem comandos gravados. Saves e cheats ficam indisponíveis neste desafio.");
  }else if(!coreReady)menuNote(panelView,"Aguarde o jogo iniciar para acessar as ferramentas da partida.");
  else menuNote(panelView,playback?"Os comandos deste replay estão gravados.":
   "A sala continua em execução. Saves ficam disponíveis no jogo solo.");
  if(coreReady&&!playback&&!ghostMode&&(solo||lan!=null&&lan.player()==0))
   menuItem(panelView,"⟲","Reiniciar ROM",solo?"Recomeçar sem fechar o jogo":"O host reinicia a partida em todos os aparelhos",true,this::confirmRomReset);
  if(solo)menuItem(panelView,"»","Velocidade",soloSpeed+"×",true,this::showSpeedOptions);
  if(!playback&&!ghostMode&&coreReady)menuItem(panelView,"●",recording?"Parar e salvar replay":"Gravar replay",
    "Salva os comandos dos jogadores",false,this::toggleRecording);
  if(recording)menuItem(panelView,"✦","Marcar momento","Criar um ponto de destaque na gravação",true,this::markMoment);
  if(activeRomId!=null&&ReplayArchive.file(getFilesDir(),activeRomId).isFile())
   menuItem(panelView,"◈","Cápsula de momentos","Marcações do último replay",true,this::showMoments);
  if(couchHost!=null&&!recording)menuItem(panelView,"⏪","Máquina do tempo em grupo",
    couchHost.voteCount()+"/"+couchHost.count()+" votos · volta de 5 segundos",false,()->{
     if(!rewindEnabled){rewindEnabled=true;
      getPreferences(0).edit().putBoolean("rewind_enabled",true).apply();
      toast("Histórico ligado. Aguarde cerca de 5 segundos e peça o voto da turma.");
     }
     if(!rewindBuffer.available(emulatedFrames,300)){
      toast("Ainda não há cinco segundos de histórico neste aparelho.");return;
     }
     couchHost.voteHostRewind();maybeCouchRewind();
     if(couchHost!=null&&couchHost.voteCount()>0)toast("Pedido registrado: "+
       couchHost.voteCount()+"/"+couchHost.count()+" votos.");
    });
  if(selected!=null&&selected.hash!=null)menuItem(panelView,"◆","Desafios Luni",
    "Placar de até cinco jogadores",true,this::showChallengeBoard);
  if(selected!=null&&selected.hash!=null)menuItem(panelView,"◎","Radar dos dedos",
    "Veja sugestões para posicionar seus controles",true,this::showHudLearning);
  destinations[1]=menuSection(panelView,"IMAGEM");
  menuItem(panelView,"▣","Região da tela",videoModeTitle(),true,this::showVideoModeOptions);
  menuItem(panelView,"▤","Preenchimento da tela",screenModeTitle(),true,this::showScreenModeOptions);
  String filterName=gameView.videoFilter()==0?"suave":gameView.videoFilter()==1?"pixels nítidos":"escala 2x";
  menuItem(panelView,"✧","Filtro de imagem",(gameView.videoFilterMode()==-1?"Automático · ":"")+filterName,true,this::showVideoFilterOptions);
  menuItem(panelView,"▱","Margens para TV",gameView.tvMode()?"Ativadas":"Desativadas",false,()->gameView.setTvMode(!gameView.tvMode()));
  if(selected!=null&&coreReady)menuItem(panelView,"◧","Criar capa deste quadro","Aparecerá na biblioteca",false,this::captureGameCover);
  if(!playback||gameView.isExternal()){
   destinations[2]=menuSection(panelView,"CONTROLE");
   if(!playback){
    menuItem(panelView,"✣","Ajustar botões na tela","Tamanho, posição e analógico",true,this::showControlEditor);
    menuItem(panelView,"✦","Tema dos controles",new String[]{"Nebula","Neon","Carbono"}[gameView.hudTheme()],true,this::showHudThemeOptions);
   }
   if(gameView.isExternal()){
    String[] levels={"Ágil","Equilibrado","Preciso"};
    menuItem(panelView,"◉","Analógico externo",levels[gameView.stickSensitivity()],true,this::showStickOptions);
   }
  }
  if(!solo){
   destinations[3]=menuSection(panelView,"SALA");
   menuItem(panelView,"♞","Jogadores e conexão","ROM, núcleo, rede e recuperação",true,this::showRoomStatus);
   if(lan!=null&&lan.player()!=0&&reconnectBanner!=null)
    menuItem(panelView,"↻","Reconectar agora","Tentar retomar sua vaga",false,()->{reconnectNow=true;});
  }
  destinations[4]=menuSection(panelView,"FERRAMENTAS");
  menuItem(panelView,"◈","Luni Radar",solo?"FPS e entrada":"FPS e qualidade da conexão",true,this::showDiagnostics);
 menuItem(panelView,"⌂",couchHost!=null?"Trocar jogo na biblioteca":"Sair para a biblioteca",
   couchHost!=null?"Mantém os controles conectados":solo?"Encerra a partida":"Encerra a sala neste aparelho",false,this::leaveGame);
 }
 private void confirmRomReset(){
  if(!playing||!coreReady||replaying||ghostMode||(!activeSolo&&(lan==null||lan.player()!=0))){
   if(activeSolo)paused=false;
   toast("Reinício indisponível nesta partida.");return;
  }
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  add(page,eyebrow(activeSolo?"REINÍCIO LOCAL":"REINÍCIO DA SALA"),dp(8));
  add(page,label(activeSolo?
   "O jogo voltará à tela inicial. O progresso ainda não salvo será perdido; seus saves e estados gravados continuarão disponíveis.":
   "Todos os jogadores voltarão à tela inicial no mesmo quadro. Progresso ainda não salvo será perdido.",13,MUTED,false),dp(14));
  final AlertDialog[] prompt={null};
  add(page,action("⟲  REINICIAR ROM",PURPLE,()->{
   prompt[0].dismiss();
   if(!playing||!coreReady||replaying||ghostMode||(!activeSolo&&(lan==null||lan.player()!=0)))return;
   emulatorCommands.add(()->{if(playing&&coreReady)resetRequested=true;});
  }),dp(7));
  add(page,action("Cancelar",0xff303a58,()->prompt[0].dismiss()),0);
  prompt[0]=showSheet("Reiniciar a ROM?",activeSolo?"Sem sair do jogo":"Somente o host pode confirmar",page);
  prompt[0].setOnDismissListener(d->{if(playing&&activeSolo)paused=false;
   if(gameView!=null)gameView.requestFocus();});
 }
 private void closeGameMenu(boolean keepPaused){
  if(gameOverlay!=null&&gameFrame!=null)gameFrame.removeView(gameOverlay);
  gameOverlay=null;
  if(gameView!=null)gameView.setInputBlocked(false);
  if(activeSolo&&!keepPaused)paused=false;
  if(gameView!=null&&!keepPaused)gameView.requestFocus();
 }
 private TextView menuSection(LinearLayout container,String name){
  TextView heading=label(name,11,CYAN,true);
  heading.setPadding(dp(5),dp(15),0,dp(7));add(container,heading,0);
  return heading;
 }
 private void menuNote(LinearLayout container,String message){
  TextView note=label(message,12,MUTED,false);note.setPadding(dp(8),dp(6),dp(8),dp(10));add(container,note,0);
 }
 private void menuItem(LinearLayout container,String symbol,String name,String detail,boolean opensDialog,Runnable command){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(11),dp(8),dp(10),dp(8));row.setMinimumHeight(dp(58));
  row.setBackground(surface(0xff25314e,13,0xff384569));touchFeedback(row);
  TextView glyph=label(symbol,22,CYAN,true);glyph.setGravity(Gravity.CENTER);
  glyph.setPadding(0,0,0,0);row.addView(glyph,new LinearLayout.LayoutParams(dp(38),dp(42)));
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(7),0,dp(4),0);
  TextView title=label(name,14,-1,true);title.setPadding(0,0,0,0);body.addView(title);
  TextView subtitle=label(detail,11,MUTED,false);subtitle.setPadding(0,dp(2),0,0);body.addView(subtitle);
  row.addView(body,new LinearLayout.LayoutParams(0,-2,1));
  row.setContentDescription(name+". "+detail);
  row.setFocusable(true);
  row.setOnFocusChangeListener((v,focused)->row.setBackground(surface(
   focused?0xff303858:0xff25314e,13,focused?CYAN:0xff384569)));
  row.setOnClickListener(v->{closeGameMenu(opensDialog);command.run();});
  add(container,row,dp(6));
 }
 private void showChoices(String title,String subtitle,String[] options,int checked,
                          java.util.function.IntConsumer select,boolean resumeSolo){
  showChoices(title,subtitle,options,checked,select,resumeSolo,which->false);
 }
 private void showChoices(String title,String subtitle,String[] options,int checked,
                          java.util.function.IntConsumer select,boolean resumeSolo,
                          java.util.function.IntPredicate keepPaused){
  LinearLayout list=new LinearLayout(this);list.setOrientation(1);
  final AlertDialog[] dialog={null};final boolean[] deferred={false};
  for(int i=0;i<options.length;i++){
   final int index=i;
   TextView row=navigation(i==checked?"◉":"○",options[i],
    i==checked?"ATIVO NESTE JOGO":"TOQUE PARA APLICAR",()->{
     deferred[0]=keepPaused.test(index);dialog[0].dismiss();select.accept(index);
    });
   if(i==checked)row.setBackground(surface(0xff343357,15,PURPLE));
   add(list,row,dp(8));
  }
  dialog[0]=showSheet(title,subtitle,list);
  dialog[0].setOnDismissListener(d->{if(resumeSolo&&activeSolo&&!deferred[0])paused=false;});
 }
 private String videoModeTitle(){
  if(gameView==null)return "completa";
  switch(gameView.videoMode()){
   case 1:return "meu jogador";
   case 2:return "metade de cima";
   case 3:return "metade de baixo";
   case 4:return "metade esquerda";
   case 5:return "metade direita";
   default:return "completa";
  }
 }
 private void togglePersonalCamera(){
  if(gameView==null)return;
  int next=gameView.videoMode()==1?0:1;
  gameView.setVideoMode(next);
  toast(next==1?"Minha metade ampliada. Abra ⋮ para voltar ao quadro inteiro.":
    "Quadro inteiro restaurado.");
 }
 private String screenModeTitle(){
  if(gameView==null)return "Jogo inteiro com laterais";
  switch(gameView.screenMode()){
   case 1:return "Jogo inteiro · bordas escuras";
   case 2:return "Tela toda · com corte";
   default:return "Jogo inteiro · laterais imersivas";
  }
 }
 private void showScreenModeOptions(){
  if(gameView==null){if(activeSolo)paused=false;return;}
  String[] options={"Jogo inteiro + laterais imersivas (recomendado)",
    "Jogo inteiro + laterais escuras (clássico)",
    "Tela toda com corte (pode ocultar placar e cenário)"};
  showChoices("Enquadramento","Imagem proporcional em qualquer tela",options,gameView.screenMode(),which->{
     if(which==2){
      LinearLayout page=new LinearLayout(this);page.setOrientation(1);
      add(page,label("Para ocupar a largura toda sem esticar o SNES, este modo corta uma parte de cima e de baixo. Em telas largas pode ocultar o placar e partes do cenário.",13,-1,false),dp(11));
      final AlertDialog[] warning={null};
      add(page,action("USAR ZOOM",PURPLE,()->{
       if(gameView!=null)gameView.setScreenMode(2);warning[0].dismiss();
      }),dp(6));
      add(page,navigation("▣","Manter o jogo inteiro","Preservar placar e cenário",()->warning[0].dismiss()),0);
      warning[0]=showSheet("Zoom até as bordas","Imagem proporcional com corte",page);
      warning[0].setOnDismissListener(closed->{if(activeSolo)paused=false;});
     }else if(gameView!=null)gameView.setScreenMode(which);
  },true,which->which==2);
 }
 private void showVideoModeOptions(){
  if(gameView==null){if(activeSolo)paused=false;return;}
  String[] options={"Tela completa (menus e jogo)","Minha tela (P1 em cima / P2 embaixo)",
    "Metade de cima","Metade de baixo","Metade esquerda","Metade direita"};
  showChoices("Região da tela","Aumente a metade da imagem quando o jogo dividir a tela; volte ao quadro inteiro nos menus",options,gameView.videoMode(),which->{
     if(gameView!=null){gameView.setVideoMode(which);
      if(which==1&&gameView.playerIndex()>1)toast("Este jogo não tem uma metade automática para P3–P5. Escolha a região manualmente.");}
  },true);
 }
 private void showVideoFilterOptions(){
  if(gameView==null){if(activeSolo)paused=false;return;}
  String[] options=gameView.supportsScale2x()?
    new String[]{"Automático — ajusta ao aparelho","Suave — suaviza os pixels ampliados","Nítida — preserva os pixels originais",
      "Escala 2x — suaviza diagonais (GPU)"}:
    new String[]{"Automático — ajusta ao aparelho","Suave — suaviza os pixels ampliados","Nítida — preserva os pixels originais"};
  showChoices("Aparência da imagem","Automático se adapta ao desempenho",options,
   gameView.videoFilterMode()+1,which->{if(gameView!=null)gameView.setVideoFilter(which-1);},true);
 }
 private void showStickOptions(){
  String[] options={"Ágil — resposta cedo; pode haver movimento involuntário",
    "Equilibrado — recomendado","Preciso — evita movimentação involuntária"};
  int current=gameView==null?getPreferences(0).getInt("stick_sensitivity",1):gameView.stickSensitivity();
  showChoices("Analógico externo","Bluetooth e USB-C",options,current,which->{
    if(gameView!=null)gameView.setStickSensitivity(which);
    else getPreferences(0).edit().putInt("stick_sensitivity",which).apply();
  },playing);
 }
 private boolean multitapEnabled(){return getPreferences(0).getBoolean("multitap_enabled",true);}
 private void showMultitapOptions(){
  boolean inGame=playing;
  if(inGame&&(!coreReady||lan!=null||replaying||ghostMode||recording)){
   toast("Multitap só pode mudar em jogo local fora de replays e gravações.");return;
  }
  boolean current=inGame?activeMultitap:multitapEnabled();
  showChoices("Multitap","Ligado: P1–P5. Desligado: P1–P2. Alguns jogos reconhecem a mudança só ao reiniciar.",
   new String[]{"Desligado · 2 controles","Ligado · até 5 controles"},current?1:0,which->{
    boolean enabled=which==1;
    if(inGame&&(!playing||!coreReady||lan!=null||replaying||ghostMode||recording))return;
    getPreferences(0).edit().putBoolean("multitap_enabled",enabled).apply();
    if(inGame&&enabled!=activeMultitap)emulatorCommands.add(()->{
     Core.setMultitap(enabled);activeMultitap=enabled;rewindBuffer.clear();
     toast("Multitap "+(enabled?"ligado":"desligado")+". Se o jogo não reconhecer, reinicie a partida.");
    });
    else if(!inGame)toast("Multitap "+(enabled?"ligado":"desligado")+" para as próximas partidas.");
   },inGame);
 }
 private void showSpeedOptions(){
  String[] options={"Normal • som original","1,5× • sem áudio","2× • sem áudio","0,5× • sem áudio"};
  float[] speeds={1f,1.5f,2f,.5f};int chosen=0;
  for(int i=0;i<speeds.length;i++)if(soloSpeed==speeds[i])chosen=i;
  showChoices("Velocidade da partida","Solo · áudio original em 1×",options,chosen,which->{
    soloSpeed=speeds[which];
    emulatorCommands.add(()->{if(activeAudio!=null)activeAudio.flushSolo();});
  },true);
 }
 private void toggleRecording(){
  emulatorCommands.add(()->{
   if(replayRecorder!=null){
    finishReplay("Replay salvo");
    return;
   }
   if(!playing||!coreReady||replaying||activeRomId==null||activeCoreHash==null)return;
   if(activeSolo&&CheatRepository.activeCodes(this,activeRomId,
       CheatRepository.stored(this,activeRomId)).length>0){
    toast("Desative os cheats antes de gravar. O replay registra somente controles.");return;
   }
   try{replayRecorder=new ReplayArchive.Recorder(activeRomId,activeCoreHash,Core.snapshot(),activeMultitap);
    replayMoments.clear();recording=true;rewindBuffer.clear();toast("Gravação iniciada. O replay não inclui a ROM.");
   }catch(IOException e){toast("Falha ao gravar: "+e.getMessage());}
  });
 }
 private void finishReplay(String title){
  ReplayArchive.Recorder current=replayRecorder;if(current==null)return;
  try{
   current.save(getFilesDir());
   MomentArchive.save(getFilesDir(),activeRomId,activeCoreHash,replayMoments);
   toast(title+": "+current.count()+" quadros · "+replayMoments.size()+" momentos.");
  }catch(IOException e){toast("Falha ao salvar replay ou marcadores: "+e.getMessage());}
  finally{replayRecorder=null;recording=false;replayMoments.clear();}
 }
 private void markMoment(){
  String[] choices={"Vitória","Virada","Jogada especial","Momento divertido"};
  showChoices("Marcar momento","A cápsula registra o ponto exato da gravação.",choices,-1,which->{
   emulatorCommands.add(()->{
    if(replayRecorder==null)return;
    if(replayMoments.size()>=120){toast("Limite de 120 momentos por replay.");return;}
    replayMoments.add(new MomentArchive.Mark(replayRecorder.count(),choices[which]));
    toast("Momento marcado: "+choices[which]);
   });
  },activeSolo);
 }
 private void showMoments(){
  Game game=selected;
  if(game==null){toast("Selecione um jogo primeiro.");return;}
  String romHash=activeRomId,coreHash=activeCoreHash;
  if(romHash==null||coreHash==null){
   new Thread(()->{
    try{String hash=sha(RomLibrary.readRom(this,game));
     List<MomentArchive.Mark> marks=MomentArchive.load(getFilesDir(),hash,shaFile(coreForGame(RomLibrary.readRom(this,game),game.format,hash)));
     runOnUiThread(()->renderMoments(game,marks));
    }catch(Exception e){toast("Não foi possível abrir os momentos: "+e.getMessage());}
   },"luni-moments").start();return;
  }
  try{renderMoments(game,MomentArchive.load(getFilesDir(),romHash,coreHash));}
  catch(IOException e){toast(e.getMessage());}
 }
 private void renderMoments(Game game,List<MomentArchive.Mark> marks){
   LinearLayout page=new LinearLayout(this);page.setOrientation(1);
   add(page,eyebrow("DESTAQUES GRAVADOS"),0);
   if(marks.isEmpty())add(page,label("Nenhum momento marcado no último replay.",13,MUTED,false),dp(8));
   for(MomentArchive.Mark mark:marks){int seconds=mark.frame/60;
    add(page,navigation("✦",mark.label,String.format(Locale.ROOT,"%02d:%02d · quadro %d",seconds/60,seconds%60,mark.frame),()->{}),dp(5));}
   add(page,label("A cápsula guarda marcações locais. O arquivo .kjr exportado inclui comandos e estado inicial, mas não inclui estas marcações.",11,MUTED,false),dp(8));
   showSheet("Cápsula de momentos",shortTitle(game),page);
 }
 private void captureGameCover(){
  Game game=selected;GameView view=gameView;if(game==null||view==null)return;
  Bitmap bitmap=view.coverFrame();if(bitmap==null){toast("Aguarde o jogo mostrar uma imagem.");return;}
  covers.execute(()->{try{
   CoverRepository.saveGameFrame(this,game,bitmap);
   getPreferences(0).edit().putBoolean("custom_cover_"+game.identity(),true).apply();
   runOnUiThread(()->{coverCache.evictAll();toast("Capa do jogo salva. Ela aparecerá na biblioteca.");});
  }catch(Exception e){toast("Não foi possível criar a capa: "+e.getMessage());}
  finally{bitmap.recycle();}});
 }
 private void showDiagnostics(){
  String report="Luni Radar • "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+
    (gameView==null?"Sem entrada recente":gameView.inputReport())+"\n"+
    "Emulação: "+actualFps+" FPS\n"+
    (activeSolo?"Partida solo  •  "+(replaying?"replay":"jogo"):
      "Espera de rede média/pico: "+networkWaitMs+"/"+networkWorstMs+" ms\nReserva da sala: "+
          (lan==null?"—":lan.inputDelayFrames()+" quadros")+"\nRede P95: "+networkDiagnostics.p95()+" ms")+"\n\n"+
    "Fila Android: evento até o app. Coleta: app até quadro emulado. Não inclui rádio Bluetooth nem tela.";
  LinearLayout body=new LinearLayout(this);body.setOrientation(1);
  add(body,eyebrow(activeSolo?"PARTIDA SOLO":"QUALIDADE DA SALA"),0);
  add(body,radarMetric("FPS DO NÚCLEO",String.valueOf(actualFps),"Quadros emulados por segundo"),dp(7));
  if(!activeSolo){
   add(body,radarMetric("ESPERA DA REDE",networkWaitMs+" ms","Média · pico "+networkWorstMs+" ms · P95 "+networkDiagnostics.p95()+" ms"),dp(7));
   add(body,radarMetric("RESERVA DE ENTRADA",lan==null?"—":lan.inputDelayFrames()+" quadros",
    "A mesma configuração para toda a sala"),dp(7));
  }
  add(body,eyebrow("ENTRADA DO CONTROLE"),dp(8));
  TextView input=label(gameView==null?"Sem entrada recente":gameView.inputReport(),13,-1,true);
  input.setPadding(dp(13),dp(11),dp(13),dp(11));input.setBackground(surface(0xff202b49,13,BORDER));
  add(body,input,dp(9));
  add(body,label("Fila Android: evento até o app. Coleta: app até quadro emulado. Essas medidas não incluem rádio Bluetooth nem tela.",11,MUTED,false),dp(8));
  final AlertDialog[] dialog={null};
  homeOption(body,dialog,"⧉","Copiar diagnóstico","Compartilhar as medições para comparar aparelhos",()->{
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Luni Radar",report));toast("Diagnóstico copiado.");
  });
  dialog[0]=showSheet("Luni Radar",Build.MANUFACTURER+" "+Build.MODEL,body);
  dialog[0].setOnDismissListener(d->{if(activeSolo)paused=false;});
 }
 private View radarMetric(String heading,String value,String detail){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);
  card.setPadding(dp(13),dp(10),dp(13),dp(10));card.setBackground(surface(0xff202b49,13,BORDER));
  add(card,eyebrow(heading),0);
  TextView metric=label(value,23,CYAN,true);metric.setPadding(0,0,0,dp(2));add(card,metric,0);
  TextView hint=label(detail,11,MUTED,false);hint.setPadding(0,0,0,0);add(card,hint,0);
  return card;
 }
 private void applyCheats(String romId,List<CheatRepository.Entry> entries){
  String[] codes=CheatRepository.activeCodes(this,romId,entries);
  emulatorCommands.add(()->{if(playing&&activeSolo&&romId.equals(activeRomId)&&!Core.setCheats(codes)&&codes.length>0)
   toast("O núcleo bsnes-mercury instalado não aceitou os códigos.");});
 }
 /** Only binds visible rows; large catalogs do not build hundreds of checkboxes on the UI thread. */
 private final class CheatListAdapter extends BaseAdapter {
  private final String romId;
  private final TextView summary,empty;
  private final ListView list;
  private List<CheatRepository.Entry> entries=Collections.emptyList();
  private final ArrayList<CheatRepository.Entry> matches=new ArrayList<>();
  private String query="";
  private String sourceFilter;
  private boolean activeOnly;
  CheatListAdapter(String romId,TextView summary,TextView empty,ListView list){
   this.romId=romId;this.summary=summary;this.empty=empty;this.list=list;
  }
  void setEntries(List<CheatRepository.Entry> updated){
   entries=new ArrayList<>(updated);
   if(sourceFilter!=null&&!sources().contains(sourceFilter))sourceFilter=null;
   filter();
   list.setFastScrollEnabled(entries.size()>60);
  }
  void setQuery(String text){query=text.trim().toLowerCase(Locale.ROOT);filter();list.setSelection(0);}
  void setActiveOnly(boolean enabled){activeOnly=enabled;filter();list.setSelection(0);}
  void setSource(String source){sourceFilter=source;filter();list.setSelection(0);}
  String source(){return sourceFilter;}
  List<String> sources(){
   LinkedHashSet<String> names=new LinkedHashSet<>();for(CheatRepository.Entry entry:entries)names.add(entry.source);
   return new ArrayList<>(names);
  }
  int sourceCount(String name){int count=0;for(CheatRepository.Entry entry:entries)if(name.equals(entry.source))count++;return count;}
  int totalCount(){return entries.size();}
  void refreshLabels(){filter();}
  boolean activeOnly(){return activeOnly;}
  private void updateSummary(){
   int active=CheatRepository.activeCount(MainActivity.this,romId,entries);
   summary.setText("●  "+active+" ativo"+(active==1?"":"s")+"   ·   "+
       matches.size()+" / "+entries.size()+" código"+(entries.size()==1?"":"s"));
  }
  private void filter(){
   matches.clear();
   for(CheatRepository.Entry entry:entries){
    if(sourceFilter!=null&&!sourceFilter.equals(entry.source))continue;
    if(activeOnly&&!CheatRepository.isEnabled(MainActivity.this,romId,entry))continue;
    if(!query.isEmpty()&&!(entry.description+" "+CheatTranslator.display(MainActivity.this,entry)+
        " "+entry.code+" "+entry.source).toLowerCase(Locale.ROOT).contains(query))continue;
    matches.add(entry);
   }
   empty.setText(entries.isEmpty()?"Ainda não há códigos para esta ROM.\nUse ⋮ para consultar o catálogo ou adicione seu código.":
       activeOnly?"Nenhum código ativo nesta fonte ou busca.\nToque em Todos para ver o catálogo.":
       "Nenhum código corresponde à busca.");
   updateSummary();notifyDataSetChanged();
  }
  @Override public int getCount(){return matches.size();}
  @Override public CheatRepository.Entry getItem(int position){return matches.get(position);}
  @Override public long getItemId(int position){return position;}
  @Override public View getView(int position,View old,ViewGroup parent){
   CheckBox box=old instanceof CheckBox?(CheckBox)old:new CheckBox(MainActivity.this);
   if(old==null){
    box.setTextSize(13);box.setTextColor(-1);box.setGravity(Gravity.CENTER_VERTICAL);
    box.setPadding(dp(10),dp(7),dp(9),dp(7));box.setMinHeight(dp(59));
    box.setLineSpacing(dp(1),1f);box.setFocusable(false);box.setMaxLines(8);
    box.setEllipsize(TextUtils.TruncateAt.END);
   }
   CheatRepository.Entry entry=getItem(position);
   String translated=CheatTranslator.display(MainActivity.this,entry);
   boolean portuguese=getPreferences(0).getBoolean("cheats_portuguese",true);
   String title=portuguese?translated:entry.description;
   String credit=portuguese&&CheatTranslator.isMachine(MainActivity.this,entry)&&
       !title.equalsIgnoreCase(entry.description)?"\nTradução automática · Powered by Google Translate":"";
   boolean variable=CheatCode.variable(entry.code);
   String selectedCode=CheatRepository.selectedCode(MainActivity.this,romId,entry);
   String displayCode=variable?entry.code+(selectedCode==null?"  ·  escolha o valor ??":" → "+selectedCode):entry.code;
   String content=title+credit+"\n"+entry.source+"  ·  "+displayCode;
   android.text.SpannableString styled=new android.text.SpannableString(content);
   styled.setSpan(new android.text.style.ForegroundColorSpan(MUTED),title.length(),content.length(),
       android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   styled.setSpan(new android.text.style.RelativeSizeSpan(.80f),title.length(),content.length(),
       android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   int codeAt=content.length()-displayCode.length();
   styled.setSpan(new android.text.style.ForegroundColorSpan(CYAN),codeAt,content.length(),
       android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   box.setOnCheckedChangeListener(null);
   boolean enabled=CheatRepository.isEnabled(MainActivity.this,romId,entry);
   box.setChecked(enabled);box.setText(styled);
   box.setContentDescription((enabled?"Ativo: ":"Inativo: ")+title+", fonte "+entry.source+", "+displayCode);
   styleCheatRow(box,enabled);
   box.setOnLongClickListener(v->{showCheatRename(entry,this::filter);return true;});
   box.setOnCheckedChangeListener((button,checked)->{
    if(!playing||!activeSolo||!romId.equals(activeRomId)){
     box.setOnCheckedChangeListener(null);box.setChecked(!checked);return;
    }
    if(variable){
     box.setOnCheckedChangeListener(null);box.setChecked(enabled);filter();
     showCheatVariant(romId,entry,entries,this::filter);return;
    }
    if(CheatRepository.isEnabled(MainActivity.this,romId,entry)==checked)return;
    if(!CheatRepository.setEnabled(MainActivity.this,romId,entry,null,checked,entries)){
     box.setOnCheckedChangeListener(null);box.setChecked(!checked);filter();
     toast("Limite de "+CheatRepository.MAX_ACTIVE+" cheats ativos.");return;
    }
    applyCheats(romId,entries);filter();
    box.setContentDescription((checked?"Ativo: ":"Inativo: ")+entry.description+", "+entry.code);
   });
   return box;
  }
 }
 private void styleCheatRow(CheckBox box,boolean active){
  box.setButtonTintList(android.content.res.ColorStateList.valueOf(active?CYAN:PURPLE));
  box.setBackground(surface(active?0xff29365a:0xff212b48,12,active?CYAN:0xff3b4e6e));
 }
 private void showCheatRename(CheatRepository.Entry entry,Runnable refresh){
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  add(form,label("Original: "+entry.description,12,MUTED,false),dp(5));
  add(form,label(entry.source,11,CYAN,false),dp(10));
  EditText name=new EditText(this);name.setSingleLine(true);name.setTextSize(14);
  name.setTextColor(-1);name.setHintTextColor(MUTED);
  name.setHint("Nome mais claro para você");name.setText(CheatTranslator.display(this,entry));
  name.setSelection(name.length());name.setFilters(new android.text.InputFilter[]{
      new android.text.InputFilter.LengthFilter(100)});
  name.setPadding(dp(11),dp(5),dp(11),dp(5));
  name.setBackground(surface(0xff202b49,11,BORDER));add(form,name,dp(9));
  add(form,label("O nome original e o código permanecem disponíveis. Segure qualquer linha para editar a tradução.",11,MUTED,false),dp(11));
  final AlertDialog[] dialog={null};
  add(form,action("✓  SALVAR NOME",PURPLE,()->{
   CheatTranslator.rename(this,entry,name.getText().toString());refresh.run();dialog[0].dismiss();
  }),dp(7));
  add(form,smallAction("Restaurar nome automático",0xff344167,()->{
   CheatTranslator.rename(this,entry,"");refresh.run();dialog[0].dismiss();
  }),0);
  dialog[0]=showSheet("Nome do cheat","Corrija a tradução sem alterar o código",form);
 }
 private void updateCheatSource(TextView button,TextView detail,CheatListAdapter adapter){
  String source=adapter.source();int count=source==null?adapter.totalCount():adapter.sourceCount(source);
  String title=source==null?"Todas as fontes":source;
  if(source!=null&&source.startsWith("Libretro · ")){
   String file=source.substring("Libretro · ".length());
   title="Libretro · "+(file.contains("Game Genie")?"Game Genie":
       file.contains("Action Replay")?"Action Replay":"Arquivo do jogo");
  }
  button.setText("▦  "+title+"  ·  "+count+"  ▾");
  button.setContentDescription("Fonte dos cheats: "+title+"; "+count+" códigos. Toque para selecionar outra fonte.");
  detail.setText(source==null?adapter.sources().size()+" fonte(s) · toque para ver os arquivos e regiões":source);
 }
 private void showCheatSources(CheatListAdapter adapter,TextView button,TextView detail){
  ArrayList<String> sources=new ArrayList<>();sources.add(null);sources.addAll(adapter.sources());
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  page.setPadding(dp(14),dp(14),dp(14),dp(12));page.setBackground(galaxy(18));
  add(page,eyebrow("ESCOLHA A ORIGEM"),dp(6));
  add(page,label("Cada arquivo mantém seus códigos separados. Selecione uma fonte para filtrar a lista.",12,MUTED,false),dp(10));
  ListView list=new ListView(this);list.setDividerHeight(dp(5));
  list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
  list.setAdapter(new BaseAdapter(){
   public int getCount(){return sources.size();}
   public String getItem(int position){return sources.get(position);}
   public long getItemId(int position){return position;}
   public View getView(int position,View old,ViewGroup parent){
    String source=getItem(position);boolean current=java.util.Objects.equals(source,adapter.source());
    LinearLayout card=old instanceof LinearLayout?(LinearLayout)old:new LinearLayout(MainActivity.this);
    card.removeAllViews();card.setOrientation(1);card.setPadding(dp(12),dp(9),dp(12),dp(9));
    card.setBackground(surface(current?0xff29385b:0xff1d2a46,11,current?CYAN:BORDER));
    String heading=source==null?"Tudo junto":source.startsWith("Libretro · ")?
        source.substring("Libretro · ".length()):source;
    TextView name=label((current?"✓  ":"")+heading,13,-1,true);
    name.setPadding(0,0,0,0);
    card.addView(name);
    String group=source==null?"BIBLIOTECA COMPLETA":source.startsWith("Libretro · ")?
        (source.contains("Game Genie")?"LIBRETRO · GAME GENIE":
         source.contains("Action Replay")?"LIBRETRO · ACTION REPLAY":"LIBRETRO · CATÁLOGO"):
        source.contains("GameHacking")?
        "CÓDIGOS DE REFERÊNCIA":"CÓDIGOS ADICIONADOS";
    TextView metadata=label(group+"  •  "+(source==null?adapter.totalCount():adapter.sourceCount(source))+" códigos",10,current?CYAN:MUTED,false);
    metadata.setPadding(0,dp(4),0,0);card.addView(metadata);
    return card;
   }
  });
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
  final AlertDialog[] dialog={null};
  list.setOnItemClickListener((parent,view,position,id)->{
   adapter.setSource(sources.get(position));updateCheatSource(button,detail,adapter);dialog[0].dismiss();
  });
  dialog[0]=new AlertDialog.Builder(this).setView(page).create();dialog[0].show();
  dialog[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
  android.util.DisplayMetrics screen=getResources().getDisplayMetrics();
  dialog[0].getWindow().setLayout(Math.min(screen.widthPixels-dp(20),dp(530)),
      Math.min(screen.heightPixels-dp(28),dp(570)));
 }
 private void showCheatVariant(String romId,CheatRepository.Entry entry,
                               List<CheatRepository.Entry> entries,Runnable refresh){
  if(!playing||!activeSolo||!romId.equals(activeRomId))return;
  int slots=CheatCode.slots(entry.code);
  String selected=CheatRepository.selectedCode(this,romId,entry);
  String[] values=new String[slots];
  if(selected!=null)for(int i=0;i<slots;i++)values[i]=CheatCode.value(entry.code,selected,i);
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  add(form,eyebrow("CÓDIGO COM VALOR VARIÁVEL"),dp(6));
  add(form,label(entry.description,15,-1,true),dp(4));
  add(form,label(entry.source,11,MUTED,false),dp(8));
  TextView preview=label(entry.code,15,CYAN,true);
  preview.setPadding(dp(12),dp(11),dp(12),dp(11));
  preview.setBackground(surface(0xff202e4c,11,BORDER));add(form,preview,dp(10));
  TextView[] choices=new TextView[slots];
  Runnable update=()->{
   preview.setText(CheatCode.preview(entry.code,values));
   for(int i=0;i<slots;i++)choices[i].setText("Valor "+(i+1)+"  ·  "+
       (values[i]==null?"escolher 00–FF":values[i]+" hex / "+Integer.parseInt(values[i],16)+" decimal"));
  };
  for(int i=0;i<slots;i++){
   final int index=i;
   choices[i]=smallAction("Escolher valor "+(i+1),0xff344167,()->
    chooseCheatByte(entry,index,values,entries,value->{values[index]=value;update.run();}));
   add(form,choices[i],dp(7));
  }
  update.run();
  add(form,label("Cada ?? substitui um byte de valor. A fonte nomeia apenas as opções que realmente catalogou; os outros valores podem não corresponder a um item válido neste jogo.",11,MUTED,false),dp(12));
  final AlertDialog[] dialog={null};
  add(form,action("✓  APLICAR E ATIVAR",PURPLE,()->{
   String complete=CheatCode.resolve(entry.code,values);
   if(complete==null){toast("Escolha um valor para cada ?? antes de ativar.");return;}
   if(!playing||!activeSolo||!romId.equals(activeRomId))return;
   if(!CheatRepository.setEnabled(this,romId,entry,complete,true,entries)){
    toast("Código inválido ou limite de "+CheatRepository.MAX_ACTIVE+" ativos.");return;
   }
   applyCheats(romId,entries);refresh.run();dialog[0].dismiss();
   toast("Código aplicado: "+complete);
  }),dp(7));
  if(CheatRepository.isEnabled(this,romId,entry))
   add(form,smallAction("Desativar este código",0xff344167,()->{
    CheatRepository.setEnabled(this,romId,entry,null,false,entries);
    applyCheats(romId,entries);refresh.run();dialog[0].dismiss();
   }),0);
  dialog[0]=showSheet("Escolher valor do cheat","Confira o código completo antes de ativar",form);
 }
 private void chooseCheatByte(CheatRepository.Entry entry,int slot,String[] values,
                              List<CheatRepository.Entry> entries,java.util.function.Consumer<String> chosen){
  Map<String,String> named=CheatRepository.suggestedValues(entry,slot,values,entries);
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);
  page.setPadding(dp(13),dp(13),dp(13),dp(12));page.setBackground(galaxy(17));
  add(page,eyebrow("VALORES 00–FF"),dp(4));
  add(page,label(named.isEmpty()?"A fonte não nomeou opções para este byte. Consulte a documentação do jogo antes de escolher.":
    named.size()+" opção(ões) identificada(s) neste arquivo. As demais mostram apenas o número.",12,MUTED,false),dp(8));
  EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);
  search.setHint("Buscar hexadecimal, decimal ou nome do item");
  search.setTextColor(-1);search.setHintTextColor(MUTED);
  search.setBackground(surface(0xff202b49,11,BORDER));
  search.setPadding(dp(11),dp(5),dp(11),dp(5));add(page,search,dp(7));
  ArrayList<String> options=new ArrayList<>();
  for(int n=0;n<256;n++){
   String hex=String.format(Locale.ROOT,"%02X",n),label=named.get(hex);
   options.add(hex+"  ·  "+n+" decimal"+(label==null?"":"  ·  "+
       (CheatText.known(label)==null?label:CheatText.known(label))));
  }
  ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,options){
   @Override public View getView(int position,View old,ViewGroup parent){
    TextView row=(TextView)super.getView(position,old,parent);
    row.setTextColor(-1);row.setTextSize(13);row.setMinHeight(dp(43));
    row.setPadding(dp(11),dp(7),dp(9),dp(7));row.setBackground(surface(0xff243250,10,BORDER));
    return row;
   }
  };
  ListView list=new ListView(this);list.setAdapter(adapter);list.setFastScrollEnabled(true);
  list.setDividerHeight(dp(4));list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
  page.addView(list,new LinearLayout.LayoutParams(-1,0,1f));
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){adapter.getFilter().filter(s);}
   public void afterTextChanged(Editable s){}
  });
  final AlertDialog[] dialog={null};
  list.setOnItemClickListener((parent,view,position,id)->{
   String item=adapter.getItem(position);if(item==null)return;
   dialog[0].dismiss();chosen.accept(item.substring(0,2));
  });
  dialog[0]=new AlertDialog.Builder(this).setView(page).create();dialog[0].show();
  dialog[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
  android.util.DisplayMetrics screen=getResources().getDisplayMetrics();
  dialog[0].getWindow().setLayout(Math.min(screen.widthPixels-dp(20),dp(530)),
      Math.min(screen.heightPixels-dp(30),dp(590)));
 }
 private void showCheats(){
  String id=activeRomId;Game game=selected;
  if(!playing||!activeSolo||!coreReady||id==null||game==null){paused=false;return;}
  if(!getPreferences(0).getBoolean("cheats_translation_ui_v4",false))
   getPreferences(0).edit().putBoolean("cheats_portuguese",true).putBoolean("cheats_translation_ui_v4",true).apply();
  paused=true;
  java.util.concurrent.atomic.AtomicReference<ArrayList<CheatRepository.Entry>> current=
      new java.util.concurrent.atomic.AtomicReference<>(CheatRepository.stored(this,id));
  android.util.DisplayMetrics size=getResources().getDisplayMetrics();
  boolean wide=size.widthPixels>size.heightPixels&&size.widthPixels>=dp(600);
  LinearLayout frame=new LinearLayout(this);frame.setOrientation(1);
  frame.setPadding(dp(12),dp(10),dp(12),dp(12));frame.setBackground(galaxy(21));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  ImageView art=new ImageView(this);art.setImageDrawable(new CoverArt(game.name));
  art.setScaleType(ImageView.ScaleType.CENTER_CROP);
  art.setBackground(surface(0xff263353,9,BORDER));art.setClipToOutline(true);
  header.addView(art,new LinearLayout.LayoutParams(dp(34),dp(43)));
  loadCover(game,art,game.identity(),false);
  LinearLayout titles=new LinearLayout(this);titles.setOrientation(1);titles.setPadding(dp(9),0,0,0);
  TextView heading=label("LUNI CHEATS",15,-1,true);heading.setPadding(0,0,0,0);titles.addView(heading);
  TextView gameName=label(shortTitle(game)+"  ·  SOLO",11,CYAN,false);
  gameName.setSingleLine(true);gameName.setEllipsize(TextUtils.TruncateAt.END);
  gameName.setPadding(0,0,0,0);titles.addView(gameName);
  header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
  final AlertDialog[] panel={null};final Runnable[] download={null};
  TextView statusLine=label("Códigos salvos · jogo pausado",11,CYAN,false);
  statusLine.setPadding(dp(3),dp(5),dp(2),0);statusLine.setMaxLines(2);
  statusLine.setEllipsize(TextUtils.TruncateAt.END);
  TextView summary=label("",11,CYAN,true);
  summary.setPadding(dp(10),dp(7),dp(9),dp(7));
  summary.setBackground(surface(0xff202e4c,11,0xff455a83));
  summary.setSingleLine(true);summary.setEllipsize(TextUtils.TruncateAt.END);
  EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);
  search.setHint("Buscar nome ou código");search.setTextColor(-1);search.setHintTextColor(MUTED);
  search.setPadding(dp(11),0,dp(11),0);search.setBackground(surface(0xff202b49,11,BORDER));
  search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
  search.setMinHeight(dp(41));
  ListView list=new ListView(this);list.setBackgroundColor(android.graphics.Color.TRANSPARENT);
  list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
  list.setDividerHeight(dp(6));list.setPadding(dp(6),dp(6),dp(5),dp(6));
  list.setClipToPadding(false);list.setVerticalScrollBarEnabled(true);
  TextView empty=label("",13,MUTED,false);empty.setGravity(Gravity.CENTER);
  empty.setPadding(dp(15),dp(15),dp(15),dp(15));
  FrameLayout results=new FrameLayout(this);results.setBackground(surface(0xff131e37,13,BORDER));
  results.addView(list,new FrameLayout.LayoutParams(-1,-1));
  results.addView(empty,new FrameLayout.LayoutParams(-1,-1));
  CheatListAdapter adapter=new CheatListAdapter(id,summary,empty,list);
  list.setAdapter(adapter);list.setEmptyView(empty);adapter.setEntries(current.get());
  search.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){adapter.setQuery(s.toString());}
   public void afterTextChanged(Editable s){}
  });
  search.setOnEditorActionListener((v,action,event)->{
   ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
       .hideSoftInputFromWindow(search.getWindowToken(),0);
   search.clearFocus();return true;
  });
  final TextView[] language={null};
  LinearLayout sourceCard=new LinearLayout(this);sourceCard.setOrientation(1);
  sourceCard.setPadding(dp(8),dp(8),dp(8),dp(8));
  sourceCard.setBackground(surface(0xff182642,12,BORDER));
  TextView sourceDetail=label("",11,MUTED,false);sourceDetail.setMaxLines(2);
  sourceDetail.setEllipsize(TextUtils.TruncateAt.END);sourceDetail.setPadding(dp(6),0,dp(5),0);
  TextView sourceButton=smallAction("",0xff354368,()->{});
  sourceButton.setMaxLines(2);sourceButton.setEllipsize(TextUtils.TruncateAt.END);
  sourceButton.setOnClickListener(v->showCheatSources(adapter,sourceButton,sourceDetail));
  add(sourceCard,sourceButton,dp(5));add(sourceCard,sourceDetail,0);
  updateCheatSource(sourceButton,sourceDetail,adapter);
  final TextView[] translateAction={null};
  TextView manual=smallAction("＋",0xff344167,()->manualCheat(id,panel[0],current,adapter,statusLine,sourceButton,sourceDetail,
      ()->{if(translateAction[0]!=null)translateAction[0].performClick();}));

  manual.setContentDescription("Adicionar código manual");
  TextView translate=smallAction("✦",PURPLE,()->{
   CheatTranslator.translateRemaining(this,current.get(),new CheatTranslator.Progress(){
    public boolean active(){return panel[0]!=null&&panel[0].isShowing()&&id.equals(activeRomId);}
    public void update(String message){runOnUiThread(()->{if(active())statusLine.setText(message);});}
    public void changed(){runOnUiThread(()->{if(active())adapter.refreshLabels();});}
    public void finished(){runOnUiThread(()->{if(active())adapter.refreshLabels();});}
   });
  });
  translateAction[0]=translate;
  translate.setContentDescription("Traduzir todos os nomes restantes com Google Translate");
  TextView more=smallAction("⋮",0xff344167,()->{});
  more.setContentDescription("Mais opções de cheats");
  more.setOnClickListener(v->{
   PopupMenu menu=new PopupMenu(this,more);
   menu.getMenu().add("↻  Atualizar catálogo").setOnMenuItemClickListener(item->{download[0].run();return true;});
   menu.getMenu().add("✦  Traduzir nomes restantes").setOnMenuItemClickListener(item->{translate.performClick();return true;});
   menu.getMenu().add("✎  Corrigir nome de um cheat").setOnMenuItemClickListener(item->{
    toast("Segure uma linha da lista para editar o nome do cheat.");return true;
   });
   menu.getMenu().add("＋  Adicionar código").setOnMenuItemClickListener(item->{manual.performClick();return true;});
   menu.getMenu().add("Trocar idioma dos nomes").setOnMenuItemClickListener(item->{language[0].performClick();return true;});
   menu.show();
  });
  TextView close=smallAction("✕",0xff344167,()->panel[0].dismiss());
  close.setContentDescription("Fechar cheats e voltar ao jogo");
  for(TextView button:new TextView[]{manual,translate,more,close}){
   LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(dp(35),dp(36));
   position.leftMargin=dp(4);header.addView(button,position);
  }
  add(frame,header,dp(8));
  View accent=new View(this);accent.setBackgroundColor(0xff594785);
  frame.addView(accent,new LinearLayout.LayoutParams(-1,dp(1)));
  LinearLayout tools=new LinearLayout(this);tools.setOrientation(1);
  add(tools,summary,dp(7));add(tools,search,dp(7));
  final Runnable[] highlight={null};
  TextView all=smallAction("Todos",PURPLE,()->{adapter.setActiveOnly(false);highlight[0].run();});
  TextView active=smallAction("Ativos",0xff354368,()->{adapter.setActiveOnly(true);highlight[0].run();});
  language[0]=smallAction(getPreferences(0).getBoolean("cheats_portuguese",true)?
      (wide?"PT ✓":"Português ✓"):(wide?"EN":"Original EN"),0xff354368,()->{
   boolean next=!getPreferences(0).getBoolean("cheats_portuguese",true);
   getPreferences(0).edit().putBoolean("cheats_portuguese",next).apply();
   language[0].setText(next?(wide?"PT ✓":"Português ✓"):(wide?"EN":"Original EN"));
   language[0].setBackground(surface(next?PURPLE:0xff354368,11,BORDER));adapter.refreshLabels();
  });
  language[0].setBackground(surface(getPreferences(0).getBoolean("cheats_portuguese",true)?PURPLE:0xff354368,11,BORDER));
  language[0].setContentDescription("Alternar nomes em português e em inglês");
  highlight[0]=()->{
   all.setBackground(surface(adapter.activeOnly()?0xff354368:PURPLE,11,BORDER));
   active.setBackground(surface(adapter.activeOnly()?PURPLE:0xff354368,11,BORDER));
  };
  LinearLayout filters=new LinearLayout(this);
  for(TextView choice:new TextView[]{all,active,language[0]}){
   choice.setTextSize(11);choice.setPadding(dp(2),dp(6),dp(2),dp(6));
   LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(0,dp(36),1);
   if(filters.getChildCount()>0)position.leftMargin=dp(5);
   filters.addView(choice,position);
  }
  add(tools,filters,dp(5));add(tools,eyebrow("ORIGEM DOS CÓDIGOS"),dp(4));
  add(tools,sourceCard,dp(5));add(tools,statusLine,dp(4));
  TextView translationCredit=label("Traduções automáticas · Powered by Google Translate ↗",10,CYAN,false);
  translationCredit.setPadding(dp(3),dp(4),dp(3),dp(4));
  translationCredit.setContentDescription("Abrir Google Translate, usado nas traduções automáticas dos cheats");
  translationCredit.setOnClickListener(v->{try{startActivity(new android.content.Intent(
      android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://translate.google.com")));
   }catch(Exception ignored){toast("Abra translate.google.com no navegador.");}});
  add(tools,translationCredit,0);
  LinearLayout body=new LinearLayout(this);
  body.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
  if(wide){
   LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(dp(208),-1);
   left.rightMargin=dp(9);body.addView(tools,left);
   body.addView(results,new LinearLayout.LayoutParams(0,-1,1f));
  }else{
   body.addView(tools,new LinearLayout.LayoutParams(-1,-2));
   body.addView(results,new LinearLayout.LayoutParams(-1,0,1f));
  }
  LinearLayout.LayoutParams area=new LinearLayout.LayoutParams(-1,0,1f);
  area.topMargin=dp(9);frame.addView(body,area);
  download[0]=()->{
   statusLine.setText("Buscando códigos no catálogo…");
   cheatIo.execute(()->{
    try{CheatRepository.Result result=CheatRepository.refresh(this,id,game.name,true);
     runOnUiThread(()->{if(panel[0]==null||!panel[0].isShowing()||!id.equals(activeRomId))return;
      current.set(result.entries);statusLine.setText(result.note);
      adapter.setEntries(result.entries);updateCheatSource(sourceButton,sourceDetail,adapter);applyCheats(id,result.entries);
      translate.performClick();
     });
    }catch(Exception e){runOnUiThread(()->{
     if(panel[0]!=null&&panel[0].isShowing()&&id.equals(activeRomId)){
      statusLine.setText("Catálogo indisponível. Traduzindo códigos salvos…");translate.performClick();
     }
    });}
   });
  };
  panel[0]=new AlertDialog.Builder(this).setView(frame).create();
  panel[0].setOnDismissListener(d->{if(activeSolo)paused=false;});
  panel[0].show();
  panel[0].getWindow().setBackgroundDrawableResource(android.R.color.transparent);
  panel[0].getWindow().setSoftInputMode(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|
      WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  int width=Math.min(size.widthPixels-dp(wide?20:16),dp(wide?960:600));
  int height=Math.min(size.heightPixels-dp(wide?18:26),dp(wide?560:760));
  panel[0].getWindow().setLayout(width,height);
  if(getPreferences(0).getBoolean("menu_animations",true)){
   frame.setAlpha(0);frame.setTranslationY(dp(7));
   frame.animate().alpha(1).translationY(0).setDuration(170).start();
  }
  if(CheatRepository.needsSourceRefresh(current.get())||
      System.currentTimeMillis()-getSharedPreferences("cheat_fetch",0).getLong("last_"+id,0)>24L*60*60*1000)download[0].run();
  else {statusLine.setText("Lista salva · traduzindo nomes restantes…");translate.performClick();}
 }
 private void manualCheat(String romId,AlertDialog parent,
      java.util.concurrent.atomic.AtomicReference<ArrayList<CheatRepository.Entry>> current,
      CheatListAdapter adapter,TextView statusLine,TextView sourceButton,TextView sourceDetail,Runnable translateMissing){
  LinearLayout form=new LinearLayout(this);form.setOrientation(1);
  EditText name=new EditText(this);name.setHint("Nome do cheat");name.setSingleLine(true);
  name.setTextColor(-1);name.setHintTextColor(MUTED);name.setMinHeight(dp(46));
  name.setPadding(dp(10),0,dp(10),0);name.setBackground(surface(0xff202b49,12,BORDER));add(form,name,dp(9));
  EditText code=new EditText(this);code.setHint("Ex.: DF47-0915 ou 7E13F2FF");code.setSingleLine(true);
  code.setTextColor(-1);code.setHintTextColor(MUTED);code.setMinHeight(dp(46));
  code.setPadding(dp(10),0,dp(10),0);code.setBackground(surface(0xff202b49,12,BORDER));add(form,code,dp(8));
  add(form,label("Use apenas um código compatível com esta revisão da ROM. O código nunca é traduzido.",11,MUTED,false),dp(9));
  final AlertDialog[] editor={null};
  add(form,action("＋  ADICIONAR CÓDIGO",PURPLE,()->{
   String input=code.getText().toString();
   if(CheatCode.normalize(input,true)==null){code.setError("Use XXXX-XXXX, XXXXXXXX ou ?? no byte de valor.");return;}
   String title=name.getText().toString();editor[0].dismiss();statusLine.setText("Salvando código…");
   cheatIo.execute(()->{try{ArrayList<CheatRepository.Entry> updated=CheatRepository.addManual(this,romId,title,input);
    runOnUiThread(()->{if(!parent.isShowing()||!romId.equals(activeRomId))return;
     current.set(updated);adapter.setEntries(updated);updateCheatSource(sourceButton,sourceDetail,adapter);
     statusLine.setText("Código salvo. Marque a caixa para ativar.");translateMissing.run();});
   }catch(Exception e){runOnUiThread(()->{if(parent.isShowing())statusLine.setText(e.getMessage());});}});
  }),0);
  editor[0]=showSheet("Adicionar código SNES","Game Genie · Action Replay · solo",form);
 }
 private TextView smallAction(String text,int color,Runnable action){
  TextView view=label(text,12,-1,true);view.setGravity(Gravity.CENTER);
  view.setPadding(dp(9),dp(7),dp(9),dp(7));view.setMinHeight(dp(36));
  view.setBackground(panel(color,dp(11)));touchFeedback(view);view.setFocusable(true);
  view.setOnClickListener(v->action.run());return view;
 }
 private void showHudThemeOptions(){
  if(gameView==null){if(activeSolo)paused=false;return;}
  showChoices("Tema dos controles","Veja o resultado na partida antes de decidir",
   new String[]{"Nebula · violeta espacial","Neon · luz turquesa","Carbono · aço e âmbar"},
   gameView.hudTheme(),which->{if(gameView!=null)gameView.setHudTheme(which);},
   controlsEditor==null&&playing);
 }
 private void showControlEditor(){
  if(gameView==null||gameFrame==null||controlsEditor!=null)return;
  if(activeSolo)paused=true;
  gameMenuAnchor.setVisibility(View.GONE);
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
  bar.setPadding(dp(7),dp(6),dp(7),dp(6));
  bar.setBackground(surface(0xf3131d37,18,0xff60739e));bar.setElevation(dp(14));
  TextView heading=label("✣  EDITANDO HUD",12,CYAN,true);
  heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);
  bar.addView(heading,new LinearLayout.LayoutParams(0,dp(39),1));
  TextView settings=smallAction("⚙  Ajustes",0xff344567,this::showControlSettings);
  LinearLayout.LayoutParams tool=new LinearLayout.LayoutParams(-2,dp(39));tool.rightMargin=dp(5);
  bar.addView(settings,tool);
  TextView hide=smallAction("▾",0xff344567,()->{
   controlsEditor.setVisibility(View.GONE);editorToggle.setVisibility(View.VISIBLE);
  });
  hide.setContentDescription("Ocultar barra para posicionar os controles");
  LinearLayout.LayoutParams small=new LinearLayout.LayoutParams(dp(39),dp(39));small.rightMargin=dp(5);
  bar.addView(hide,small);
  TextView cancel=smallAction("✕",0xff344567,()->closeControlEditor(false));
  cancel.setContentDescription("Cancelar e restaurar os controles anteriores");
  LinearLayout.LayoutParams close=new LinearLayout.LayoutParams(dp(39),dp(39));close.rightMargin=dp(5);
  bar.addView(cancel,close);
  TextView save=smallAction("✓  Salvar",PURPLE,()->closeControlEditor(true));
  save.setContentDescription("Salvar layout dos controles");
  bar.addView(save,new LinearLayout.LayoutParams(-2,dp(39)));
  int width=Math.min(dp(440),getResources().getDisplayMetrics().widthPixels-dp(20));
  FrameLayout.LayoutParams placement=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  placement.topMargin=dp(8);gameFrame.addView(bar,placement);controlsEditor=bar;
  TextView expand=smallAction("✣  Editando · Mostrar barra",0xee263855,()->{
   controlsEditor.setVisibility(View.VISIBLE);editorToggle.setVisibility(View.GONE);
  });
  FrameLayout.LayoutParams togglePlace=new FrameLayout.LayoutParams(-2,dp(41),Gravity.TOP|Gravity.LEFT);
  togglePlace.setMargins(dp(12),dp(8),0,0);expand.setVisibility(View.GONE);
  gameFrame.addView(expand,togglePlace);editorToggle=expand;
  gameView.beginEditing((name,scale)->{
   if(selectedControl==null||controlSize==null)return;
   selectedControl.setText(name+"  ▾");
   updatingControlSlider=true;controlSize.setProgress(Math.round((scale-.65f)*100));updatingControlSlider=false;
  });
  toast("Arraste os botões. Use Ajustes para tamanho, tema e presets.");
 }
 private void showControlSettings(){
  if(gameView==null||controlsEditor==null||controlSettingsDialog!=null)return;
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);
  add(content,chip("✣  A PARTIDA ESTÁ PAUSADA DURANTE O AJUSTE",CYAN),dp(11));
  add(content,eyebrow("POSIÇÃO E ERGONOMIA"),0);
  final AlertDialog[] dialog={null};
  add(content,navigation("✣","Preset de posição","Padrão maior, canhoto ou compacto",()->{
   dialog[0].dismiss();showChoices("Posições do HUD","Escolha um ponto de partida",
    new String[]{"Padrão · botões maiores","Canhoto · lados invertidos","Compacto · tamanho antigo"},-1,
    which->{if(gameView!=null)gameView.applyPreset(which);},false);
  }),dp(7));
  add(content,navigation("◎","Posicionamento guiado","Indique onde ficam seus dois polegares",()->{
   dialog[0].dismiss();startComfortGuide();
  }),dp(7));
  controlMode=smallAction(gameView.isAnalog()?"◉  Analógico ativo · trocar":"✣  Direcional ativo · trocar",
   0xff304567,()->{
    gameView.setAnalog(!gameView.isAnalog());
    controlMode.setText(gameView.isAnalog()?"◉  Analógico ativo · trocar":"✣  Direcional ativo · trocar");
   });
  add(content,controlMode,dp(7));
  final TextView[] tactile={null};
  tactile[0]=smallAction(gameView.haptics()?"Vibração leve: ligada":"Vibração leve: desligada",
   0xff304567,()->{
    gameView.setHaptics(!gameView.haptics());
    tactile[0].setText(gameView.haptics()?"Vibração leve: ligada":"Vibração leve: desligada");
   });
  add(content,tactile[0],dp(12));
  add(content,eyebrow("TAMANHO DOS BOTÕES"),0);
  selectedControl=smallAction("Direcional  ▾",0xff344567,()->{
   PopupMenu pick=new PopupMenu(this,selectedControl);
   String[] names=gameView.controlNames();
   for(int i=0;i<names.length;i++)pick.getMenu().add(0,i+1,i,names[i]);
   pick.setOnMenuItemClickListener(item->{gameView.selectControl(item.getItemId()-1);return true;});pick.show();
  });
  add(content,selectedControl,dp(7));
  controlSize=new SeekBar(this);controlSize.setMax(110);
  content.addView(controlSize,new LinearLayout.LayoutParams(-1,dp(42)));
  add(content,label("Arraste o botão no jogo; ajuste aqui o tamanho do selecionado.",11,MUTED,false),dp(12));
  controlSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
    if(user&&!updatingControlSlider&&gameView!=null)gameView.setSelectedScale(.65f+progress/100f);
   }
   @Override public void onStartTrackingTouch(SeekBar bar){}
   @Override public void onStopTrackingTouch(SeekBar bar){}
  });
  add(content,eyebrow("VISUAL"),0);
  add(content,navigation("✦","Tema dos controles","Nebula, Neon ou Carbono",()->{
   dialog[0].dismiss();showHudThemeOptions();
  }),dp(7));
  add(content,label("VISIBILIDADE DOS BOTÕES",11,CYAN,true),dp(2));
  controlOpacity=new SeekBar(this);controlOpacity.setMax(62);
  controlOpacity.setProgress(Math.round((gameView.getOpacity()-.18f)*100));
  content.addView(controlOpacity,new LinearLayout.LayoutParams(-1,dp(42)));
  controlOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
    if(user&&gameView!=null)gameView.setOpacity(.18f+progress/100f);
   }
   @Override public void onStartTrackingTouch(SeekBar bar){}
   @Override public void onStopTrackingTouch(SeekBar bar){}
  });
  add(content,action("Voltar e posicionar botões",PURPLE,()->dialog[0].dismiss()),dp(2));
  dialog[0]=showSheet("Controles flutuantes","Ajuste fino · posições salvas só ao tocar Salvar",content);
  controlSettingsDialog=dialog[0];
  gameView.selectControl(0);
  dialog[0].setOnDismissListener(d->{
   if(controlSettingsDialog==dialog[0])controlSettingsDialog=null;
   selectedControl=null;controlMode=null;controlSize=null;controlOpacity=null;
  });
 }
 private void closeControlEditor(boolean save){
  if(controlsEditor==null)return;
  stopComfortGuide();
  if(controlSettingsDialog!=null){controlSettingsDialog.dismiss();controlSettingsDialog=null;}
  if(gameView!=null)gameView.endEditing(save);
  if(gameFrame!=null)gameFrame.removeView(controlsEditor);
  if(gameFrame!=null)gameFrame.removeView(editorToggle);
  controlsEditor=null;editorToggle=null;selectedControl=null;controlMode=null;controlSize=null;controlOpacity=null;
  if(gameMenuAnchor!=null)gameMenuAnchor.setVisibility(View.VISIBLE);
  if(activeSolo)paused=false;
  if(gameView!=null)gameView.requestFocus();
 }
 private void startComfortGuide(){
  if(comfortOverlay!=null||gameView==null||gameFrame==null||controlsEditor==null)return;
  controlsEditor.setVisibility(View.GONE);editorToggle.setVisibility(View.GONE);
  FrameLayout guide=new FrameLayout(this);guide.setBackgroundColor(0x77070d20);
  TextView instruction=label("1/2  Toque onde seu polegar ESQUERDO fica confortável",14,-1,true);
  instruction.setGravity(Gravity.CENTER);instruction.setBackground(panel(0xee192642,dp(14)));
  FrameLayout.LayoutParams message=new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP);
  message.setMargins(dp(16),dp(8),dp(110),0);guide.addView(instruction,message);
  final float[] first={-1,-1};
  guide.setOnTouchListener((v,event)->{
   if(event.getActionMasked()!=MotionEvent.ACTION_DOWN)return true;
   float x=event.getX(),y=event.getY();
   if(first[0]<0){
    if(x>guide.getWidth()*.49f){toast("Toque na metade esquerda da tela.");return true;}
    first[0]=x;first[1]=y;
    instruction.setText("2/2  Toque onde seu polegar DIREITO fica confortável");
   }else{
    if(x<guide.getWidth()*.51f){toast("Toque na metade direita da tela.");return true;}
    gameView.applyComfortAnchors(first[0],first[1],x,y);
    stopComfortGuide();
    if(controlsEditor!=null)controlsEditor.setVisibility(View.VISIBLE);
    toast("Posições sugeridas. Ajuste os botões e toque em Salvar.");
   }
   return true;
  });
  TextView cancel=smallAction("Cancelar",0xff303a58,()->{
   stopComfortGuide();if(controlsEditor!=null)controlsEditor.setVisibility(View.VISIBLE);
  });
  FrameLayout.LayoutParams cancelPlace=new FrameLayout.LayoutParams(dp(95),dp(44),Gravity.TOP|Gravity.RIGHT);
  cancelPlace.setMargins(0,dp(13),dp(12),0);guide.addView(cancel,cancelPlace);
  comfortOverlay=guide;gameFrame.addView(guide,new FrameLayout.LayoutParams(-1,-1));
 }
 private void stopComfortGuide(){
  if(comfortOverlay!=null&&gameFrame!=null)gameFrame.removeView(comfortOverlay);
  comfortOverlay=null;
 }
 private void stateSlots(boolean save){
  paused=true;
  String[] slots=new String[5];for(int n=0;n<5;n++){
   File file=stateFile(n+1);
   String time=file.exists()?new java.text.SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(file.lastModified())):"vazio";
   slots[n]="Espaço "+(n+1)+"   ·   "+time;
  }
  showChoices(save?"Salvar partida":"Carregar partida",save?
    "Escolha um espaço para guardar o progresso":"Estados gravados nesta ROM",slots,-1,which->{
    int slot=which+1;File file=stateFile(slot);
    if(!save&&!file.isFile()){toast("Espaço "+slot+" está vazio.");return;}
    emulatorCommands.add(()->{
     try{if(save){byte[] snapshot=Core.snapshot();if(snapshot==null)throw new IOException("O núcleo não gerou um estado.");
       File temp=new File(file.getParentFile(),file.getName()+".tmp");Files.write(temp.toPath(),snapshot);Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
       toast("Partida salva no espaço "+slot+".");
      }else{if(file.length()>16*1024*1024||file.length()<128||!Core.restore(Files.readAllBytes(file.toPath())))throw new IOException("Estado inválido ou incompatível.");
       rewindBuffer.clear();if(activeAudio!=null)activeAudio.flushSolo();
       toast("Espaço "+slot+" carregado.");}
     }catch(Exception e){toast("Falha no estado: "+e.getMessage());}
    });
  },true);
 }
 private void leaveGame(){playing=false;paused=false;if(lan!=null)lan.close();
  if(gameThread!=null)gameThread.interrupt();}
 private boolean recoverOnlineGame(LanSession session,AudioOutput audio){
  showReconnect("Conexão perdida. Procurando a sala e reservando seu P"+(session.player()+1)+"…");
  while(playing){
   try{
    LanSession.Resume checkpoint=session.reconnect();
    if(!Core.restore(checkpoint.state))throw new IOException("Estado da sala incompatível");
    if(audio!=null)audio.flushAfterReset();
    session.beginResumed(gameView.mask(),checkpoint.frame);
    hideReconnect();toast("Reconectado. Partida retomada do quadro atual.");
    return true;
   }catch(IOException e){
    if(!playing)break;
    showReconnect("Ainda sem conexão: "+e.getMessage()+"\nNova tentativa automática em instantes.");
   }
   long until=System.nanoTime()+2_000_000_000L;
   while(playing&&!reconnectNow&&System.nanoTime()<until){
    try{Thread.sleep(120);}catch(InterruptedException e){if(!playing)return false;}
   }
   reconnectNow=false;
  }
  return false;
 }
 private File mesenCore(){return new File(getApplicationInfo().nativeLibraryDir,"libmesen_s_libretro_android.so");}
 private File performanceCore(){return new File(getApplicationInfo().nativeLibraryDir,"libbsnes_mercury_performance_libretro_android.so");}
 private File coreForSavedGame(String romId){
  return getPreferences(0).getBoolean("mesen_for_"+romId,false)?mesenCore():performanceCore();
 }
 private File coreForGame(byte[] bytes,String format,String romId){
  return CoreChoice.preferMesen(bytes,format)||getPreferences(0).getBoolean("mesen_for_"+romId,false)?
   mesenCore():performanceCore();
 }
 private void gameLoop(byte[] bytes,LanSession session,byte[] seedRam,String format,
                       ReplayArchive.Player playback,ReplayArchive.Player ghost,CouchSession couch){
  AudioOutput audio=null;String error=null;boolean coreAvailable=false;
  LanSession.Resume initialResume=null;
  try{
   String id=sha(bytes);activeRomId=id;
   File chosen=session==null?coreForGame(bytes,format,id):
    session.compatibilityCore()?mesenCore():performanceCore();
   File compatibility=mesenCore();
   if(!chosen.isFile())throw new IOException("Núcleo "+chosen.getName()+" ausente. Compile pelo script para baixar os dois núcleos.");
   String core=chosen.getAbsolutePath();
   File cache=new File(getCacheDir(),id+"."+format);
   if(!cache.isFile()||cache.length()!=bytes.length){
    File temp=File.createTempFile(".rom-",".tmp",getCacheDir());
    try{
     try(FileOutputStream file=new FileOutputStream(temp)){file.write(bytes);file.getFD().sync();}
     Files.move(temp.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }finally{temp.delete();}
   }
   activeMultitap=session!=null?session.multitapEnabled():playback!=null?playback.multitapEnabled:
    ghost!=null?true:multitapEnabled();
   String issue=Core.open(core,bytes,cache.getAbsolutePath(),getFilesDir().getAbsolutePath(),activeMultitap);
   coreAvailable=true;
   if(issue!=null&&session==null&&!chosen.equals(compatibility)&&playback==null&&ghost==null&&compatibility.isFile()){
    String alternative=Core.open(compatibility.getAbsolutePath(),bytes,cache.getAbsolutePath(),getFilesDir().getAbsolutePath(),activeMultitap);
    if(alternative==null){core=compatibility.getAbsolutePath();toast("Tentando modo de compatibilidade para esta ROM.");}
    else throw new IOException("A ROM falhou em ambos os núcleos: "+issue+" / "+alternative);
   }else if(issue!=null)throw new IOException(issue);
   activeCoreHash=shaFile(new File(core));
   if(session==null&&core.equals(compatibility.getAbsolutePath()))
    getPreferences(0).edit().putBoolean("mesen_for_"+id,true).apply();
   if(session==null&&playback==null&&ghost==null&&couch==null)
    Core.setCheats(CheatRepository.activeCodes(this,id,CheatRepository.stored(this,id)));
   else Core.setCheats(new String[0]);
   File save=new File(getFilesDir(),id+".srm");
   if(session==null&&playback==null&&ghost==null){
    if(save.exists())Core.loadRam(Files.readAllBytes(save.toPath()));
    else if(seedRam!=null)Core.loadRam(seedRam);
   }else if(session!=null){
    if(session.player()==0)Core.step(new int[]{0,0,0,0,0});
    if(session.resuming())initialResume=session.awaitResume();
    byte[] state=initialResume!=null?initialResume.state:
     session.synchronize(session.player()==0?Core.snapshot():null);
    if(session.player()!=0&&!Core.restore(state))throw new IOException("Não foi possível sincronizar o jogo");
   }
   if(playback!=null){
    if(!Core.restore(playback.initial))throw new IOException("Núcleo não conseguiu abrir o estado do replay.");
   }else if(ghost!=null){
    if(!Core.restore(ghost.initial))throw new IOException("O estado inicial da Arena Fantasma é incompatível.");
   }else if(session==null)Core.step(new int[]{0,0,0,0,0});
   audio=AudioOutput.open(Core.rate(),session!=null);
   activeAudio=audio;
   if(!audio.available())toast("Áudio indisponível neste aparelho; o jogo continua.");
   if(session!=null){
    if(initialResume!=null)session.beginResumed(gameView.mask(),initialResume.frame);
    else session.begin(gameView.mask());
   }
   coreReady=true;
   double fps=Core.fps();if(fps<45||fps>65)fps=60;
   long nanos=(long)(1_000_000_000.0/fps),next=System.nanoTime(),statsAt=next;
   emulatedFrames=0;
   int skippedImages=0,statsFrames=0,statsNetworkMs=0,statsWorstMs=0;
   int[] soloInputs=new int[5];
   while(playing){
    Runnable command;while((command=emulatorCommands.poll())!=null)command.run();
    if(paused&&session==null){Thread.sleep(16);next=System.nanoTime();statsAt=next;
     statsFrames=statsNetworkMs=statsWorstMs=0;continue;}
    if(!playing)break;
    int mask=gameView.maskForFrame();
    if(resetRequested&&(session==null||session.player()==0)){
     mask|=ReplayArchive.RESET_FLAG;resetRequested=false;
    }
    long networkAt=System.nanoTime();
    int[] inputs;
    if(playback!=null){inputs=playback.next();if(inputs==null)break;}
    else if(session!=null){
     try{inputs=session.next(mask,Core::snapshot);}
     catch(IOException lost){
      if(session.player()!=0&&playing&&recoverOnlineGame(session,audio)){
       next=System.nanoTime();statsAt=next;statsFrames=statsNetworkMs=statsWorstMs=0;
       continue;
      }
      throw lost;
     }
    }
    else if(couch!=null)inputs=couch.inputs(mask);
    else if(ghost!=null)inputs=ghost.ghostInputs(mask);
    else{soloInputs[0]=mask;inputs=soloInputs;}
    if(playback!=null)gameView.updateReplayMasks(inputs);
    if((inputs[0]&ReplayArchive.RESET_FLAG)!=0){
     if(!Core.reset())throw new IOException("O núcleo não conseguiu reiniciar a ROM.");
     Arrays.fill(inputs,0);inputs[0]=ReplayArchive.RESET_FLAG;
     rewindBuffer.clear();
     if(activeAudio!=null)activeAudio.flushAfterReset();
     toast(session!=null?"ROM reiniciada para todos na sala.":"ROM reiniciada.");
    }
    if(session!=null){
     int waited=(int)Math.min(10000,(System.nanoTime()-networkAt)/1_000_000);
     statsNetworkMs+=waited;statsWorstMs=Math.max(statsWorstMs,waited);
     networkDiagnostics.add(waited);
    }
    Core.step(inputs);
    emulatedFrames++;
    if(!Core.hasPicture()&&emulatedFrames>=Math.round(fps*8)){
     if(session==null&&playback==null&&ghost==null&&!core.equals(compatibility.getAbsolutePath())&&compatibility.isFile()){
      Core.close();
      String alternative=Core.open(compatibility.getAbsolutePath(),bytes,cache.getAbsolutePath(),getFilesDir().getAbsolutePath(),activeMultitap);
      if(alternative!=null)throw new IOException("Tela preta em ambos os núcleos: "+alternative);
      core=compatibility.getAbsolutePath();activeCoreHash=shaFile(compatibility);
      Core.setCheats(CheatRepository.activeCodes(this,id,CheatRepository.stored(this,id)));
      if(save.exists())Core.loadRam(Files.readAllBytes(save.toPath()));
      else if(seedRam!=null)Core.loadRam(seedRam);
      Core.step(new int[]{0,0,0,0,0});
      if(audio!=null)audio.close();
      audio=AudioOutput.open(Core.rate(),false);activeAudio=audio;
      rewindBuffer.clear();
      getPreferences(0).edit().putBoolean("mesen_for_"+id,true).apply();
      toast("Tela preta detectada. Reiniciando em modo de compatibilidade.");
      fps=Core.fps();if(fps<45||fps>65)fps=60;
      nanos=(long)(1_000_000_000.0/fps);next=System.nanoTime();statsAt=next;
      statsFrames=statsNetworkMs=statsWorstMs=0;emulatedFrames=0;skippedImages=0;
     continue;
     }
     if(session!=null)throw new IOException("Sem imagem nesta sala. No perfil do jogo, escolha Núcleo: compatibilidade no aparelho que cria a sala e entre novamente com o mesmo APK.");
     throw new IOException("O núcleo não exibiu imagem após 8 segundos. Confira a ROM e, em jogos com chip especial, o firmware. Tente uma cópia SFC/SMC válida.");
    }
    if(replayRecorder!=null&&!replayRecorder.append(inputs)){
     finishReplay("Replay de uma hora salvo automaticamente");
    }
    if(session!=null||soloSpeed==1f)audio.write(Core.audio());
    if(session==null&&playback==null&&ghost==null&&rewindEnabled&&!recording&&emulatedFrames%30==0)
     rewindBuffer.add(emulatedFrames,Core.snapshot());
    long frameNanos=session==null?(long)(nanos/soloSpeed):nanos;
    next+=frameNanos;long wait=next-System.nanoTime();
    boolean showImage=session!=null||soloSpeed<1.5f||emulatedFrames%2==0;
    if(showImage&&(session==null||skippedImages>=1||wait>nanos/6)){
     gameView.image(Core.pixels(),Core.width(),Core.height());skippedImages=0;
    }else skippedImages++;
    wait=next-System.nanoTime();
    if(wait>0)Thread.sleep(wait/1_000_000,(int)(wait%1_000_000));
    else if(wait< -4*frameNanos)next=System.nanoTime();
    statsFrames++;
    long now=System.nanoTime(),elapsed=now-statsAt;
    if(elapsed>=1_000_000_000L){
     actualFps=(int)(statsFrames*1_000_000_000L/elapsed);
     if(session!=null){networkWaitMs=statsNetworkMs/statsFrames;networkWorstMs=statsWorstMs;}
     int measured=actualFps,waitMs=session==null?0:networkWaitMs,expected=(int)Math.round(fps);
     if(soloSpeed==1f&&gameView!=null)
      runOnUiThread(()->{if(gameView!=null)gameView.monitorPerformance(measured,expected,waitMs);});
     statsFrames=0;statsNetworkMs=0;statsWorstMs=0;statsAt=now;
    }
   }
  }catch(LinkageError e){
   android.util.Log.e("LuniArena","Biblioteca nativa incompatível",e);
   if(playing)error="Biblioteca do jogo incompatível. Recompile com -CleanNative.";
  }catch(Exception e){
   android.util.Log.e("LuniArena","Falha ao executar jogo",e);
   if(playing)error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
  }
  finally{
   if(session!=null&&activeRomId!=null){
    getPreferences(0).edit().putInt("room_fps_"+activeRomId,actualFps)
       .putInt("room_p95_"+activeRomId,networkDiagnostics.p95()).apply();
   }
   playing=false;coreReady=false;paused=false;emulatorCommands.clear();
   hideReconnect();
   if(replayRecorder!=null)finishReplay("Replay salvo ao sair da partida");
   replayRecorder=null;recording=false;replaying=false;ghostMode=false;ghostPlayer=null;
   rewindBuffer.clear();activeAudio=null;resetRequested=false;activeRomId=null;activeCoreHash=null;
   if(audio!=null)try{audio.close();}catch(Exception ignored){}
   if(coreAvailable&&session==null&&playback==null&&ghost==null){try{byte[] ram=Core.saveRam();if(ram!=null&&ram.length>0)
     Files.write(new File(getFilesDir(),sha(bytes)+".srm").toPath(),ram);
    }catch(Exception ignored){}}
   if(gameView!=null)gameView.saveTouchLearning();
   if(coreAvailable)Core.close();if(session!=null)session.close();lan=null;
   // A shared-screen party keeps its controller sockets when the host picks another ROM.
   if(couch!=null&&couchHost==couch)couch.nextGame();
   if(couch!=null&&couchHost!=couch)couch.close();
   final String end=error;runOnUiThread(()->{gameView=null;gameFrame=null;gameOverlay=null;controlsEditor=null;editorToggle=null;comfortOverlay=null;gameMenuAnchor=null;home();
    info(end==null?(couch!=null&&couchHost==couch?"Console mantido. Escolha outro jogo na biblioteca.":
     playback!=null?"Replay concluído.":ghost!=null?"Desafio Fantasma encerrado.":
     "Partida encerrada."):"Partida encerrada: "+end);});
  }
 }
 @Override public void onBackPressed(){
  if(controllerOnly)stopControllerOnly();
  else if(playing&&comfortOverlay!=null){stopComfortGuide();if(controlsEditor!=null)controlsEditor.setVisibility(View.VISIBLE);}
  else if(playing&&gameOverlay!=null)closeGameMenu(false);
  else if(playing&&controlsEditor!=null)closeControlEditor(true);
  else if(playing&&gameMenuAnchor!=null)showGameMenu(gameMenuAnchor);
  else if(launchPending){launchPending=false;if(lan!=null){lan.close();lan=null;}
   if(!preserveCouchPending&&couchHost!=null){couchHost.close();couchHost=null;}
   preserveCouchPending=false;
   if(couchClient!=null){couchClient.close();couchClient=null;}home();}
  else super.onBackPressed();
 }
 @Override protected void onDestroy(){playing=false;controllerOnly=false;
  deviceHandler.removeCallbacks(delayedControllerRefresh);if(lan!=null)lan.close();
  if(couchHost!=null)couchHost.close();if(couchClient!=null)couchClient.close();
  inputManager.unregisterInputDeviceListener(this);covers.shutdownNow();cheatIo.shutdownNow();
  libraryIo.shutdownNow();super.onDestroy();}
}
