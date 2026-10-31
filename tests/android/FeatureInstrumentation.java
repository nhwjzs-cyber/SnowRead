package com.snowread.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Dedicated-emulator integration checks; not packaged in the reader. */
public final class FeatureInstrumentation extends Instrumentation {
    interface Work {void run()throws Exception;}
    interface Condition {boolean done()throws Exception;}
    private Activity activity;private int checks;
    private void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private void ui(Work work)throws Exception{AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new Exception("UI check failed",failure.get());}
    private void await(Condition c)throws Exception{long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){AtomicBoolean done=new AtomicBoolean();ui(()->done.set(c.done()));if(done.get()){waitForIdleSync();return;}Thread.sleep(40);}throw new AssertionError("UI wait timed out");}
    private void open(LibraryStore.Book book)throws Exception{ui(()->invoke("openBook",new Class<?>[]{LibraryStore.Book.class},book));await(()->field(activity,"active")==book&&!(Boolean)field(activity,"busy")&&field(activity,"document")!=null);await(()->field(activity,"reader")!=null?((ListView)field(activity,"reader")).getChildCount()>0:field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing());}
    private AppSheet sheet()throws Exception{Field f=AppSheet.class.getDeclaredField("shown");f.setAccessible(true);for(Object item:new ArrayList<>((Set<?>)f.get(null))){AppSheet s=(AppSheet)item;if(s.isShowing()&&s.getOwnerActivity()==activity)return s;}return null;}
    private ArrayList<View> views(View root){ArrayList<View> list=new ArrayList<>();list.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)list.addAll(views(((ViewGroup)root).getChildAt(i)));return list;}
    private TextView text(AppSheet sheet,String prefix){for(View view:views(sheet.getWindow().getDecorView()))if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(prefix))return (TextView)view;return null;}
    private void event(View v,long start,long at,int action,float x,float y){MotionEvent e=MotionEvent.obtain(start,at,action,x,y,0);v.onTouchEvent(e);e.recycle();}
    private void swipe(View v,boolean cancel,boolean vertical){long now=SystemClock.uptimeMillis();float x=v.getWidth()*(vertical?.5f:.85f),y=v.getHeight()*(vertical?.8f:.5f),endX=v.getWidth()*(vertical?.5f:.1f),endY=v.getHeight()*(vertical?.1f:.5f);event(v,now,now,0,x,y);event(v,now,now+100,2,endX,endY);event(v,now,now+180,cancel?3:1,endX,endY);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{runChecks();result.putString("stream","FeatureInstrumentation: "+checks+" checks passed");finish(Activity.RESULT_OK,result);}catch(Throwable e){StringWriter out=new StringWriter();e.printStackTrace(new PrintWriter(out));result.putString("stream",out.toString());finish(Activity.RESULT_CANCELED,result);}}
    private void runChecks()throws Exception{
        if(!getTargetContext().getPackageName().equals("com.snowread.app.sandbox"))throw new IllegalStateException("Use the isolated sandbox app; production data tests are forbidden");
        Intent start=new Intent(Intent.ACTION_MAIN).setClassName("com.snowread.app","com.snowread.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=startActivitySync(start);waitForIdleSync();Context context=getTargetContext();context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).putBoolean("rightSwipeNext",false).putString("readerSettingsGroup","font").putInt("font",21).putInt("sideMargin",26).putInt("palette",0).putInt("autoNight",0).putBoolean("awake",false).apply();
        ui(()->{check(activity.findViewById(android.R.id.content)!=null,"cold launch content");Dialog dialog=new Dialog(activity);check(dialog.getWindow().peekDecorView()==null,"fresh window starts without decor");EdgeToEdge.immersive(dialog.getWindow(),false);EdgeToEdge.immersive(dialog.getWindow(),true);check(dialog.getWindow().peekDecorView()!=null,"early immersive safely creates decor");check(activity.getAssets().list("fonts").length>0,"activity="+activity.getPackageName()+" source="+activity.getApplicationInfo().sourceDir+" root="+java.util.Arrays.toString(activity.getAssets().list(""))+" target="+java.util.Arrays.toString(context.getAssets().list("")));File fontFile=new File(context.getCacheDir(),"font-native-check.otf");try(InputStream in=activity.getAssets().open("fonts/RongxueSerif-Regular.otf");OutputStream out=new FileOutputStream(fontFile)){check(in.read()==79,"OTF asset opens");out.write(79);byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}Typeface direct=Typeface.createFromAsset(activity.getAssets(),"fonts/RongxueSerif-Regular.otf");check(direct!=Typeface.DEFAULT,"asset font parsed by Android");Typeface face=BundledFont.serif(activity);check(face!=Typeface.SERIF,"bundled serif really loaded");Paint p=new Paint();p.setTypeface(face);check(p.hasGlyph("雪"),"bundled Chinese glyph");});
        ui(()->set(activity,"pagedMode",false));LibraryStore store=(LibraryStore)field(activity,"store");StringBuilder body=new StringBuilder("第一章 雪\n");for(int i=0;i<80;i++)body.append("窗外的雪花落在书页旁，Snow 的故事正在继续。").append(i).append('\n');body.append("第二章 春\n风从山间吹向春天。");LibraryStore.Book a=store.add(body.toString().getBytes(StandardCharsets.UTF_8),"Native scoped A.txt",null),b=store.add((body+"第二本独有结尾").getBytes(StandardCharsets.UTF_8),"Native inherited B.txt",null);a.offset=0;a.anchor=null;a.marks.clear();a.markNotes.clear();a.markAnchors.clear();a.unmappedMarks.clear();b.offset=0;b.typeset=null;a.typeset=new Typeset();a.typeset.font=28;a.typeset.margin=42;a.typeset.palette=1;a.typeset.awake=true;store.save();open(a);
        ui(()->{check((Integer)field(activity,"fontSize")==28,"book A independent font size");check((Integer)field(activity,"sideMargin")==42,"book A independent margin");check((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0,"book A awake policy");ListView reader=(ListView)field(activity,"reader");TextView row=(TextView)reader.getChildAt(0);check(row!=null&&row.getTypeface()==BundledFont.serif(context),"scroll reader uses asset font");invoke("appearance",new Class<?>[0]);AppSheet panel=sheet();TextView sizeLabel=text(panel,"字号 · 28");check(sizeLabel!=null,"typeset settings opened");ViewGroup parent=(ViewGroup)sizeLabel.getParent();((SeekBar)parent.getChildAt(parent.indexOfChild(sizeLabel)+1)).setProgress(14);panel.dismiss();});await(()->field(activity,"previewTypeset")==null);
        ui(()->{check(a.typeset.font==30,"book settings persisted");check(context.getSharedPreferences("reader",0).getInt("font",0)==21,"per-book edit never changes global font");});open(b);ui(()->check((Integer)field(activity,"fontSize")==21,"book B still inherits global"));open(a);ui(()->check((Integer)field(activity,"fontSize")==30,"book A remembers typography"));
        testSettingsGroups(context);
        ui(()->{int at=body.indexOf("Snow");a.marks.add(at);invoke("persist",new Class<?>[0]);invoke("updateProgress",new Class<?>[]{int.class},0);Object bar=field(activity,"progress");check(bar instanceof BookmarkSeekBar,"bookmark tick seek bar installed");check(((List<?>)field(bar,"marks")).contains(at),"bookmark dots reflect data");((View)bar).performAccessibilityAction(0x7f0f0202,null);check(sheet()!=null&&text(sheet(),"跳转到第几章")!=null,"seek long action opens chapter jump");sheet().dismiss();});
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("doubleBookmark",true).putBoolean("verticalPages",true).putBoolean("volumePages",true).putBoolean("volumeRepeat",true).apply();set(activity,"pagedMode",true);invoke("showReader",new Class<?>[]{int.class},0);});await(()->field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing()&&((PagedReaderView)field(activity,"pagedReader")).pageEnd()>0);
        ui(()->{PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");int at=page.position();swipe(page,true,false);check(page.position()==at,"cancelled horizontal drag never turns page");swipe(page,false,false);});await(()->!((PagedReaderView)field(activity,"pagedReader")).isIndexing());ui(()->check(((PagedReaderView)field(activity,"pagedReader")).position()>0,"left drag advances page"));
        Thread.sleep(350);int marks=a.marks.size();ui(()->{PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");long now=SystemClock.uptimeMillis();float x=page.getWidth()/2f,y=page.getHeight()/2f;event(page,now,now,0,x,y);event(page,now,now+30,1,x,y);event(page,now+130,now+130,0,x,y);event(page,now+130,now+160,1,x,y);});ui(()->check(a.marks.size()==marks+1,"center double tap adds bookmark"));
        Thread.sleep(250);AtomicInteger previous=new AtomicInteger();ui(()->{PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");previous.set(page.position());swipe(page,false,true);});await(()->!((PagedReaderView)field(activity,"pagedReader")).isIndexing());ui(()->check(((PagedReaderView)field(activity,"pagedReader")).position()>previous.get(),"vertical swipe advances only paged reader"));
        Thread.sleep(300);ui(()->{previous.set(((PagedReaderView)field(activity,"pagedReader")).position());set(activity,"lastVolumePage",0L);activity.dispatchKeyEvent(new KeyEvent(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN,1));});await(()->!((PagedReaderView)field(activity,"pagedReader")).isIndexing());ui(()->check(((PagedReaderView)field(activity,"pagedReader")).position()>previous.get(),"volume key repeats turn pages"));
        ui(()->{invoke("search",new Class<?>[0]);AppSheet panel=sheet();EditText input=null;Button submit=null;for(View v:views(panel.getWindow().getDecorView())){if(v instanceof EditText)input=(EditText)v;if(v instanceof Button&&((Button)v).getText().toString().equals("搜索"))submit=(Button)v;}check(input!=null&&submit!=null,"search controls present");input.setText("snow");submit.performClick();});await(()->sheet()!=null&&text(sheet(),"共 ")!=null);ui(()->{AppSheet panel=sheet();ListView list=null;for(View v:views(panel.getWindow().getDecorView()))if(v instanceof ListView)list=(ListView)v;check(list!=null&&list.getAdapter().getCount()>1,"case-insensitive search returned grouped results");int count=list.getAdapter().getCount();list.performItemClick(list.getChildAt(0),0,0);check(list.getAdapter().getCount()<count,"chapter results fold");panel.dismiss();});
        testCachedAudio(context);
        ui(()->{
            context.getSharedPreferences("reader",0).edit().putBoolean("twoFingerBrightness",true).putBoolean("followBrightness",false).putFloat("brightness",.4f).apply();View page=(View)field(activity,"pagedReader");int[] origin=new int[2];page.getLocationInWindow(origin);float x=origin[0]+page.getWidth()*.5f,y=origin[1]+page.getHeight()*.65f;long now=SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[2];for(int i=0;i<2;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=x+i*30;coordinates[i].y=y;coordinates[i].pressure=1;coordinates[i].size=1;}
            MotionEvent first=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);activity.dispatchTouchEvent(first);first.recycle();MotionEvent down=MotionEvent.obtain(now,now+10,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);activity.dispatchTouchEvent(down);down.recycle();for(MotionEvent.PointerCoords coordinate:coordinates)coordinate.y-=page.getHeight()*.2f;MotionEvent move=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_MOVE,2,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);activity.dispatchTouchEvent(move);move.recycle();MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,coordinates[0].x,coordinates[0].y,0);activity.dispatchTouchEvent(up);up.recycle();check(context.getSharedPreferences("reader",0).getFloat("brightness",0)>.6f,"two fingers adjust window brightness");check(!context.getSharedPreferences("reader",0).getBoolean("followBrightness",true),"gesture explicitly switches to manual brightness");
        });
        testReleaseContinuity(context);
        testActualSwipeDirections(context);
        ui(()->{invoke("leaveReader",new Class<?>[0]);check((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)==0,"shelf clears book awake override");});
        testShelfDrag(store,a,b);
        testHomeLayout(context,store,a);
        testPolish(context,store);
        testSnowAndDelete(context,store,a,b);
        testSheetHandoff(context);
        testDefaultSwipeSetting(context,store.find(a.id));
        testReviewFixes(context,store.find(a.id));
        testShelfAppearance(context);
        testReadingThemeRefresh(context,store.find(a.id));
        testCustomFontsAndStats(context,store.find(a.id));
        testNotesAndTransferEntry(context,store.find(a.id));
        testPrivacyMigration(context);
        testAtmosphere(context,store.find(a.id));
        testReaderFurniture(context,store.find(a.id));
        testScriptAndCurl(context,store.find(a.id));
        testEpubAndStats(context,store);
    }
    private int currentReadingOffset()throws Exception{Method method=MainActivity.class.getDeclaredMethod("position");method.setAccessible(true);return (Integer)method.invoke(activity);}
    private void testEpubAndStats(Context context,LibraryStore store)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        String[] paths={"mimetype","META-INF/container.xml","book.opf","chapter.xhtml"};
        StringBuilder body=new StringBuilder("<!DOCTYPE html><html xmlns='http://www.w3.org/1999/xhtml'><body><h1>雪融时</h1>");for(int i=0;i<100;i++)body.append("<p>离线 EPUB 正文，故事生长。段落 ").append(i).append("</p>");body.append("</body></html>");
        String[] values={"application/epub+zip","<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>","<package xmlns='http://www.idpf.org/2007/opf'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Native EPUB</dc:title></metadata><manifest><item id='c' href='chapter.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='c'/></spine></package>",body.toString()};
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(bytes)){for(int i=0;i<paths.length;i++){zip.putNextEntry(new java.util.zip.ZipEntry(paths[i]));zip.write(values[i].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        LibraryStore.Book book=store.add(bytes.toByteArray(),"native.epub",null);check(book.epub(),"native EPUB parser and format metadata");
        ui(()->set(activity,"pagedMode",false));open(book);
        ui(()->{TextDocument doc=(TextDocument)field(activity,"document");check(doc.text.contains("离线 EPUB 正文"),"EPUB opens in existing scroll reader");check(doc.chapters.get(0).title.equals("雪融时"),"EPUB heading fallback directory");});
        String today=ReadingStatsData.day(System.currentTimeMillis());context.getSharedPreferences("reading-stats",0).edit().putLong("day:2020-01-01",123000).apply();ReadingStats sample=new ReadingStats(context);sample.enabled(true);sample.pulse(true,SystemClock.elapsedRealtime(),book.id);SystemClock.sleep(60);sample.pulse(false,SystemClock.elapsedRealtime(),book.id);
        check(sample.books(today).containsKey(book.id),"daily per-book duration is recorded");check(sample.day("2020-01-01")==123000&&sample.books("2020-01-01").isEmpty(),"legacy totals preserved without invented book history");
        ui(()->{invoke("showReadingStats",new Class<?>[0]);check((Boolean)field(activity,"statsPage"),"statistics is a page");check(field(activity,"active")==null,"statistics does not count as reading");check(activity.findViewById(android.R.id.content).findViewWithTag("reading-stats-heatmap")!=null,"calendar visible");});
        long before=sample.today();ui(()->invoke("statsPulse",new Class<?>[0]));SystemClock.sleep(60);ui(()->invoke("statsPulse",new Class<?>[0]));check(sample.today()==before,"statistics browsing excluded from reading time");
        ui(()->invoke("leaveStats",new Class<?>[0]));await(()->field(activity,"active")==book&&!(Boolean)field(activity,"busy"));
        ui(()->{
            int w=320,h=640,paper=0xFF172029;android.text.TextPaint ink=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.WHITE);ink.setTextSize(18);ReaderTypography type=new ReaderTypography(context);android.text.StaticLayout page=type.layout("正文",ink,w-48,1);
            for(int direction:new int[]{-1,1}){Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);new PageCurlRenderer().draw(new Canvas(bitmap),page,page,w,h,.5f,direction,paper,24,12);int x=direction>0?12:w-12;check(bitmap.getPixel(x,h/2)!=paper,"paper fold includes side margin instead of a stationary frame");for(int edge:new int[]{0,w-1})check(Color.red(bitmap.getPixel(edge,h-1))<80,"no white screen-edge strip at night");bitmap.recycle();}
        });
    }
    private void testScriptAndCurl(Context context,LibraryStore.Book book)throws Exception{
        book.scriptMode=2;open(book);
        ui(()->{
            TextDocument source=(TextDocument)field(activity,"document");String display=(String)field(activity,"readerText");
            check(source.text.length()==display.length(),"converted text retains UTF-16 offsets");
            check(display.contains("故事正在繼續"),"opening a book prepares traditional display before drawing");
            check(source.text.contains("故事正在继续"),"original document unchanged");
            invoke("appearance",new Class<?>[0]);
            View script=sheet().getWindow().getDecorView().findViewWithTag("reading-script-mode");
            check(script instanceof ChoiceStrip,"script mode in existing font group");
            ((ChoiceStrip)script).getChildAt(1).performClick();
        });
        await(()->book.scriptMode==1);
        ui(()->{check(((String)field(activity,"readerText")).contains("故事正在继续"),"script selection updates reader while panel open");sheet().dismiss();});
        await(()->field(activity,"previewTypeset")==null);
        ui(()->{
            context.getSharedPreferences("reader",0).edit().putInt("pageEffect",1).putBoolean("pageSnowDust",true).putBoolean("reduceMotion",false).putBoolean("rightSwipeNext",false).apply();
            set(activity,"pagedMode",true);set(activity,"pageMotion",true);invoke("showReader",new Class<?>[]{int.class},0);
        });
        await(()->field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing());
        ui(()->{
            PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");check((Boolean)field(page,"curlMode"),"curl effect selected");
            int before=page.position();swipe(page,true,false);check(page.position()==before,"cancel curl never commits offset");
            page.finishMotion();page.next();
        });
        await(()->!((PagedReaderView)field(activity,"pagedReader")).isIndexing()&&field(field(activity,"pagedReader"),"animator")!=null);
        ui(()->{
            PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");
            check(field(page,"dustAnimator")==null,"snow does not start before completed turn");
            ((android.animation.ValueAnimator)field(page,"animator")).end();
            check(field(page,"dustAnimator")!=null,"snow starts after successful completion");
            page.finishMotion();check(field(page,"dustAnimator")==null,"forced completion clears snow");
            context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();page.setAnimationEnabled(false);
            page.next();check(field(page,"animator")==null&&field(page,"dustAnimator")==null,"reduced motion suppresses animation and snow");
        });
        ui(()->{
            int w=320,h=640;android.text.TextPaint ink=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);ink.setColor(Color.WHITE);ink.setTextSize(20);
            ReaderTypography typography=new ReaderTypography(context);android.text.StaticLayout old=typography.layout("Old page",ink,w,1),next=typography.layout("New page",ink,w,1);
            PageCurlRenderer renderer=new PageCurlRenderer();
            for(int direction:new int[]{-1,1})for(float progress:new float[]{0,.25f,.5f,.75f,1}){
                Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);renderer.draw(canvas,old,next,w,h,progress,direction,0xFF172029);
                check(Color.red(bitmap.getPixel(w-1,h-1))<80,"curl paper stays dark across directions and frames");bitmap.recycle();
            }
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLACK);new PageTurnDust().draw(new Canvas(bitmap),.2f,1,w,h,24,24,1,true);
            boolean changed=false;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int color=bitmap.getPixel(x,y);if(x>=24&&x<w-24)check(color==Color.BLACK,"snow never paints body");else if(color!=Color.BLACK)changed=true;}
            check(changed,"snow visible in margin");bitmap.recycle();
            context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();
        });
    }
    private void testReaderFurniture(Context context,LibraryStore.Book book)throws Exception{
        android.content.SharedPreferences prefs=context.getSharedPreferences("reader",0);
        ui(()->{if(sheet()!=null)sheet().dismissNow();if(field(activity,"active")!=null)invoke("leaveReader",new Class<?>[0]);prefs.edit().remove("readerFooter").remove("readerHeader").putBoolean("reduceMotion",true).putInt("appearance",2).putInt("autoNight",0).putString("readerSettingsGroup","display").apply();Motion.invalidateCache(context);book.typeset=new Typeset();book.offset=600;book.anchor=null;((LibraryStore)field(activity,"store")).save();set(activity,"pagedMode",false);});open(book);
        final ReaderFooter[] first={null};final int[] before={0};
        ui(()->{first[0]=(ReaderFooter)field(activity,"readerFooter");check(first[0]!=null&&field(activity,"readerHeader")==null,"footer defaults on and header defaults off");check(((android.graphics.drawable.ColorDrawable)first[0].getBackground()).getColor()==SnowTheme.PAPER,"dark shelf does not turn paper footer dark");check(((TextView)first[0].findViewWithTag("reader-footer-clock")).length()>0,"clock visible immediately");check(((TextView)first[0].findViewWithTag("reader-footer-battery")).getText().toString().startsWith("电量"),"battery has an explicit label");ListView list=(ListView)field(activity,"reader");check(list.getBottom()<=first[0].getTop(),"scroll body ends before footer");ReaderChrome chrome=(ReaderChrome)field(activity,"chrome");if(chrome.isVisible())invoke("toggleChrome",new Class<?>[0]);check(first[0].getVisibility()==View.VISIBLE,"footer survives toolbar hide");before[0]=currentReadingOffset();invoke("appearance",new Class<?>[0]);ToggleRow header=(ToggleRow)sheet().getWindow().getDecorView().findViewWithTag("reading-header-toggle");check(header!=null&&!header.checked(),"header option is inside existing reading settings");header.performClick();});
        await(()->field(activity,"readerHeader")!=null&&!(Boolean)field(activity,"restoring"));
        ui(()->{check(Math.abs(currentReadingOffset()-before[0])<40,"header change retains the current reading line");check(((TextView)field(activity,"readerHeader")).getMaxLines()==1,"long header stays on one line");ToggleRow footer=(ToggleRow)sheet().getWindow().getDecorView().findViewWithTag("reading-footer-toggle");footer.performClick();check(field(activity,"readerFooter")==null,"footer switch applies immediately");footer.performClick();check(field(activity,"readerFooter")!=null,"footer can return without reopening settings");sheet().dismissNow();});await(()->field(activity,"previewTypeset")==null);
        for(boolean paged:new boolean[]{false,true})for(int paper:new int[]{0,1,2}){
            ui(()->{invoke("leaveReader",new Class<?>[0]);prefs.edit().putBoolean("readerFooter",true).putBoolean("readerHeader",true).apply();book.typeset.palette=paper;book.offset=600;book.anchor=null;((LibraryStore)field(activity,"store")).save();set(activity,"pagedMode",paged);});open(book);
            ui(()->{ReaderFooter footer=(ReaderFooter)field(activity,"readerFooter");TextView header=(TextView)field(activity,"readerHeader");int expected=paper==2?SnowTheme.NIGHT_BACKGROUND:paper==1?SnowTheme.WARM_PAPER:SnowTheme.PAPER;check(((android.graphics.drawable.ColorDrawable)footer.getBackground()).getColor()==expected&&((android.graphics.drawable.ColorDrawable)header.getBackground()).getColor()==expected,"header/footer share the actual paper in both modes");for(View v:views(footer))if(v instanceof TextView)check(((TextView)v).getCurrentTextColor()==(paper==2?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED),"all footer text follows reader ink");check(header.getCurrentTextColor()==(paper==2?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED),"header follows reader ink");View body=(View)field(activity,paged?"pagedReader":"reader");check(header.getBottom()<=body.getTop()&&body.getBottom()<=footer.getTop(),"persistent information reserves space above and below text");if(paged)check(((TextView)footer.findViewWithTag("reader-footer-progress")).getText().toString().contains("本节"),"paged footer exposes section page position");else check(!((TextView)footer.findViewWithTag("reader-footer-progress")).getText().toString().contains("本节"),"scroll mode uses percentage without invented pages");});
        }
        ui(()->{ReaderFooter footer=(ReaderFooter)field(activity,"readerFooter");BroadcastReceiver receiver=(BroadcastReceiver)field(footer,"events");receiver.onReceive(context,new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,1).putExtra(BatteryManager.EXTRA_SCALE,2).putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_CHARGING));TextView battery=(TextView)footer.findViewWithTag("reader-footer-battery");check(battery.getText().toString().contains("50%")&&battery.getContentDescription().toString().contains("充电"),"battery percent uses scale and charging has accessible description");footer.resumed(false);check(!(Boolean)field(footer,"registered"),"background state unregisters receiver");footer.resumed(true);check((Boolean)field(footer,"registered"),"resume registers exactly one receiver");ReaderFooter narrow=new ReaderFooter(activity,SnowTheme.PAPER,SnowTheme.MUTED,26);float density=context.getResources().getDisplayMetrics().density;narrow.measure(View.MeasureSpec.makeMeasureSpec(Math.round(160*density),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(300*density),View.MeasureSpec.AT_MOST));check(narrow.getOrientation()==LinearLayout.VERTICAL,"narrow footer wraps to two rows");invoke("leaveReader",new Class<?>[0]);check(!(Boolean)field(footer,"registered"),"leaving reader releases old footer receiver");prefs.edit().remove("readerHeader").remove("readerFooter").apply();});
    }
    private void testAtmosphere(Context context,LibraryStore.Book book)throws Exception{
        android.content.SharedPreferences skyPrefs=context.getSharedPreferences("atmosphere",0);
        ui(()->{if(sheet()!=null)sheet().dismissNow();if(field(activity,"active")!=null)invoke("leaveReader",new Class<?>[0]);skyPrefs.edit().clear().apply();context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();Motion.invalidateCache(context);invoke("showLibrary",new Class<?>[]{String.class},"");});
        await(()->tagged("shelf-atmosphere")!=null&&tagged("shelf-atmosphere").getWidth()>0);
        ui(()->{View sky=tagged("shelf-atmosphere");check(!sky.isClickable()&&!sky.isFocusable(),"shelf ambience never consumes book touches");check(sky.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO,"decoration stays outside accessibility navigation");check(AtmosphereView.weather(context)==AtmosphereView.SNOW,"unconfigured ambience preserves the snow theme");invoke("atmospherePreview",new Class<?>[0]);});
        await(()->sheet()!=null&&sheet().getWindow().getDecorView().findViewWithTag("atmosphere-demo").getWidth()>0);
        ui(()->{
            View decor=sheet().getWindow().getDecorView();ChoiceStrip weather=(ChoiceStrip)decor.findViewWithTag("atmosphere-weather"),time=(ChoiceStrip)decor.findViewWithTag("atmosphere-time");AtmosphereView demo=(AtmosphereView)decor.findViewWithTag("atmosphere-demo");
            check(snow(sheet())==null,"preview avoids a second conflicting snow animation");
            Bitmap bitmap=Bitmap.createBitmap(demo.getWidth(),demo.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            try{for(int w=0;w<4;w++)for(int t=0;t<5;t++){weather.getChildAt(w).performClick();time.getChildAt(t).performClick();check(skyPrefs.getInt("weather",-1)==w&&skyPrefs.getInt("time",-1)==t,"preview saves the selected atmosphere immediately");if(t>0)check(AtmosphereView.period(context)==t-1,"manual light preview ignores the actual clock");demo.draw(canvas);}}finally{bitmap.recycle();}
            Motion.finishTree(demo);check(!demo.animating(),"tree teardown cancels atmospheric frame callbacks");
            context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();Motion.invalidateCache(context);AtmosphereView.refreshAll();check(!demo.animating(),"reduced motion leaves static light without a drawing loop");
            decor.findViewWithTag("atmosphere-enabled").performClick();check(!AtmosphereView.enabled(context)&&!demo.animating(),"atmosphere switch stops the preview immediately");sheet().dismissNow();
            skyPrefs.edit().putBoolean("enabled",true).putInt("weather",AtmosphereView.RAIN).apply();invoke("shelfMenu",new Class<?>[0]);check(sheet().getWindow().getDecorView().findViewWithTag("sheet-atmosphere") instanceof AtmosphereView,"rain replaces snow only on decorated panels");check(snow(sheet())==null,"rain and snow are not rendered together");sheet().dismissNow();
            AppSheet plain=new AppSheet.Builder(activity,false).setTitle("普通确认").show();check(plain.getWindow().getDecorView().findViewWithTag("sheet-atmosphere")==null&&snow(plain)==null,"ordinary confirmation stays undecorated");plain.dismissNow();
        });
        open(book);ui(()->check(tagged("shelf-atmosphere")==null,"reader body has no ambient weather overlay"));
        ui(()->{skyPrefs.edit().clear().apply();invoke("leaveReader",new Class<?>[0]);});
    }
    private void testPrivacyMigration(Context context)throws Exception{
        ui(()->{android.content.SharedPreferences sky=context.getSharedPreferences("atmosphere",0);sky.edit().putBoolean("auto",true).putFloat("lat",31.1f).putFloat("lon",121.1f).putString("city","测试城市").putLong("coordAt",123).putInt("weatherAuto",0).putInt("weather",AtmosphereView.RAIN).putInt("time",2).apply();context.getSharedPreferences("listen",0).edit().putBoolean("allowNetworkVoice",true).apply();invoke("clearLegacyPrivacy",new Class<?>[0]);for(String key:PrivacyPolicy.WEATHER_CACHE)check(!sky.contains(key),"migration removes cached weather field "+key);check(sky.getInt("weather",-1)==AtmosphereView.RAIN&&sky.getInt("time",-1)==2,"manual ambience survives migration");check(!context.getSharedPreferences("listen",0).contains("allowNetworkVoice"),"legacy network voice permission removed");org.json.JSONObject old=new org.json.JSONObject().put("atmosphere",new org.json.JSONObject().put("city","旧备份城市").put("lat",22).put("auto",true).put("weather",AtmosphereView.SNOW)).put("listen",new org.json.JSONObject().put("allowNetworkVoice",true));invoke("applySettings",new Class<?>[]{org.json.JSONObject.class},old);check(!sky.contains("city")&&!sky.contains("lat")&&!sky.contains("auto"),"restore cannot resurrect location cache");check(AtmosphereView.weather(context)==AtmosphereView.SNOW,"manual weather restored normally");check(!context.getSharedPreferences("listen",0).contains("allowNetworkVoice"),"restore cannot enable network voice");});
    }
    private void testNotesAndTransferEntry(Context context,LibraryStore.Book book)throws Exception{
        ui(()->{set(activity,"pagedMode",false);book.annotations.clear();book.offset=0;book.anchor=null;((LibraryStore)field(activity,"store")).save();});open(book);
        ui(()->{invoke("editNote",new Class<?>[]{BookNote.class,int.class,int.class},null,6,16);check(text(sheet(),"样式：柔和高亮（点击切换）")!=null,"compact note style toggle");sheet().getButton(AppSheet.BUTTON_POSITIVE).performClick();});
        await(()->!(Boolean)field(activity,"busy")&&book.annotations.size()==1);waitForIdleSync();
        ui(()->{ListView list=(ListView)field(activity,"reader");TextView first=(TextView)list.getChildAt(0);check(first.getText() instanceof android.text.Spanned,"scroll excerpt retains spans");android.text.Spanned styled=(android.text.Spanned)first.getText();check(styled.getSpans(0,styled.length(),android.text.style.BackgroundColorSpan.class).length>0,"scroll mode renders note highlight");invoke("notes",new Class<?>[0]);check(sheet().getButton(AppSheet.BUTTON_NEUTRAL)!=null,"notes expose export inside manager");sheet().dismissNow();set(activity,"pagedMode",true);});open(book);
        await(()->field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing());
        ui(()->{android.text.StaticLayout layout=(android.text.StaticLayout)field(field(activity,"pagedReader"),"layout");android.text.Spanned styled=(android.text.Spanned)layout.getText();check(styled.getSpans(0,styled.length(),android.text.style.BackgroundColorSpan.class).length>0,"paged mode renders note highlight");invoke("leaveReader",new Class<?>[0]);invoke("lanTransfer",new Class<?>[0]);check(text(sheet(),"开启传书")!=null&&field(activity,"lanSession")==null,"opening transfer panel does not start a server");sheet().dismissNow();check(field(activity,"lanSession")==null,"closing idle transfer panel leaves no receiver");});
    }
    private void testCustomFontsAndStats(Context context,LibraryStore.Book book)throws Exception{
        File sample=new File(context.getCacheDir(),"custom-font-check.otf");try(InputStream in=context.getAssets().open("fonts/RongxueSerif-Regular.otf");OutputStream out=new FileOutputStream(sample)){byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
        String id=FontLibrary.importFont(context,android.net.Uri.fromFile(sample),"自定义测试.otf");check(FontLibrary.ids(context).contains(id),"import persists a private font copy");int count=FontLibrary.ids(context).size();check(FontLibrary.importFont(context,android.net.Uri.fromFile(sample),"重复.otf").equals(id)&&FontLibrary.ids(context).size()==count,"duplicate font imports reuse their identity");
        Typeset selected=new Typeset();selected.customFont=id;check(FontLibrary.face(context,selected)!=Typeface.DEFAULT,"custom font loads on Android");
        File bad=new File(context.getCacheDir(),"bad-font.ttf");try(OutputStream out=new FileOutputStream(bad)){out.write("this is not a font".getBytes(StandardCharsets.UTF_8));}boolean rejected=false;try{FontLibrary.importFont(context,android.net.Uri.fromFile(bad),"坏字体.ttf");}catch(Exception e){rejected=true;}check(rejected&&FontLibrary.ids(context).size()==count,"invalid font does not alter library");
        ui(()->{book.typeset=new Typeset();book.typeset.customFont=id;((LibraryStore)field(activity,"store")).save();});open(book);
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).putString("readerSettingsGroup","font").apply();invoke("appearance",new Class<?>[0]);check(text(sheet(),"自定义字体")!=null,"font controls stay inside existing typography category");sheet().dismissNow();});await(()->field(activity,"previewTypeset")==null);
        FontLibrary.remove(context,id);check(FontLibrary.face(context,selected)==BundledFont.serif(context),"missing custom file falls back safely");
        ui(()->{invoke("leaveReader",new Class<?>[0]);invoke("showReadingStats",new Class<?>[0]);check(activity.findViewById(android.R.id.content).findViewWithTag("reading-stats-heatmap")!=null,"local reading page has a calendar");check(activity.findViewById(android.R.id.content).findViewWithTag("reading-stats-enabled")!=null,"time recording can be disabled");invoke("leaveStats",new Class<?>[0]);});sample.delete();bad.delete();
    }
    private void testReadingThemeRefresh(Context context,LibraryStore.Book book)throws Exception{
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).putInt("appearance",1).putInt("autoNight",0).putString("readerSettingsGroup","theme").apply();book.typeset=new Typeset();book.typeset.palette=0;book.typeset.font=26;((LibraryStore)field(activity,"store")).save();});open(book);
        ui(()->invoke("appearance",new Class<?>[0]));
        for(int paper:new int[]{2,0}){
            ui(()->{ThemeSwatches swatches=(ThemeSwatches)sheet().getWindow().getDecorView().findViewWithTag("reader-theme-swatches");swatches.getChildAt(paper).performClick();});
            await(()->sheet()!=null&&sheet().isDarkSurface()==(paper==2)&&field(activity,"previewTypeset")!=null&&((Typeset)field(activity,"previewTypeset")).palette==paper);
            ui(()->{check((Integer)field(activity,"palette")==paper,"body follows the selected paper immediately");ReaderFooter footer=(ReaderFooter)field(activity,"readerFooter");check(footer!=null&&((android.graphics.drawable.ColorDrawable)footer.getBackground()).getColor()==(Integer)field(activity,"bg"),"footer follows paper immediately while settings remain open");check(((Typeset)field(activity,"previewTypeset")).font==26,"retheming preserves other typography");check(book.typeset!=null&&book.typeset.font==26,"per-book scope survives automatic panel recreation");check(context.getSharedPreferences("reader",0).getString("readerSettingsGroup","").equals("theme"),"theme category stays open");check(context.getSharedPreferences("reader",0).getInt("appearance",0)==1,"reading paper never overwrites shelf appearance");});
        }
        ui(()->sheet().dismissNow());await(()->field(activity,"previewTypeset")==null);
        ui(()->{invoke("leaveReader",new Class<?>[0]);context.getSharedPreferences("reader",0).edit().putInt("appearance",2).apply();invoke("showLibrary",new Class<?>[]{String.class},"");check(tagged("shelf-logo").getForeground() instanceof SoftGlowDrawable,"dark shelf has a static edge glow");check(((SoftGlowDrawable)tagged("shelf-logo").getForeground()).strength()<.4f,"shelf glow stays restrained");context.getSharedPreferences("reader",0).edit().putInt("appearance",1).apply();invoke("showLibrary",new Class<?>[]{String.class},"");check(tagged("shelf-logo").getForeground()==null,"light shelf has no night edge glow");});
    }
    private void testShelfAppearance(Context context)throws Exception{
        final android.content.SharedPreferences prefs=context.getSharedPreferences("reader",0);
        ui(()->{if(sheet()!=null)sheet().dismissNow();if(field(activity,"active")!=null)invoke("leaveReader",new Class<?>[0]);prefs.edit().putInt("palette",2).putInt("autoNight",0).putInt("appearance",2).putBoolean("reduceMotion",true).apply();invoke("showLibrary",new Class<?>[]{String.class},"");invoke("appearanceSettings",new Class<?>[0]);check(sheet().isDarkSurface(),"appearance starts dark");});
        for(int mode:new int[]{1,2,1,0}){
            ui(()->{ChoiceStrip strip=(ChoiceStrip)sheet().getWindow().getDecorView().findViewWithTag("appearance-mode");strip.getChildAt(mode).performClick();});
            await(()->sheet()!=null&&prefs.getInt("appearance",-1)==mode&&((ChoiceStrip)sheet().getWindow().getDecorView().findViewWithTag("appearance-mode")).selected()==mode);
            ui(()->{boolean dark=ReaderPolicy.darkAppearance(mode,(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES,true);check(sheet().isDarkSurface()==dark,"appearance panel follows selected mode");check((Integer)field(activity,"bg")== (dark?SnowTheme.NIGHT_BACKGROUND:SnowTheme.BACKGROUND),"shelf background follows appearance independently of reading paper");ChoiceStrip strip=(ChoiceStrip)sheet().getWindow().getDecorView().findViewWithTag("appearance-mode");check((Integer)field(strip,"accent")==(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK),"segmented buttons are reconstructed with new colors");check(prefs.getInt("palette",-1)==2,"appearance changes preserve reading theme");check((Boolean)field(activity,"appearanceDark")==dark,"resolved appearance cache is current");});
        }
        ui(()->sheet().dismissNow());
    }
    private void testReviewFixes(Context context,LibraryStore.Book book)throws Exception{
        open(book);await(()->activity.hasWindowFocus());
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();int before=(Integer)field(activity,"palette");set(activity,"appearanceDark",!((MainActivity)activity).uiDark());invoke("refreshAutoTheme",new Class<?>[0]);check(field(activity,"active")==book,"chrome-only theme changes preserve the active reader");check((Integer)field(activity,"palette")==before,"chrome-only changes preserve reading palette");});
        ui(()->{TextDocument doc=(TextDocument)field(activity,"document");SearchResults cached=SearchResults.find(doc.text,"Snow",()->false);set(activity,"searchSession",cached);set(activity,"searchSessionDoc",doc);set(activity,"searchSessionTerm","Snow");set(activity,"searchTerm","Snow");set(activity,"searchSessionOnlyChapter",true);set(activity,"searchSessionChapter",0);((Set<Integer>)field(activity,"searchSessionFolded")).add(0);invoke("search",new Class<?>[0]);check(text(sheet(),"共 "+cached.total+" 处 · 已恢复上次结果")!=null,"returning to search reuses cached results");check(field(activity,"searchSession")==cached,"resume does not start a new scan");sheet().dismissNow();});
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();Motion.invalidateCache(context);AppSheet panel=new AppSheet.Builder(activity,false).setSnowflakes(true).setTitle("强制收尾测试").show();View surface=(View)field(panel,"dragSurface");java.lang.reflect.Method emit=AppSheet.class.getDeclaredMethod("emitDecor");emit.setAccessible(true);Motion.sheetIn(surface,panel.getWindow(),()->{try{emit.invoke(panel);}catch(Exception e){throw new RuntimeException(e);}});Motion.finishTree(surface);check(!(Boolean)field(panel,"decorEmitted"),"forced completion never emits opening decoration");panel.dismissNow();});
    }
    private void testSheetHandoff(Context context)throws Exception{
        final AppSheet[] first={null},next={null};AtomicInteger action=new AtomicInteger(),cleanup=new AtomicInteger();
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();first[0]=new AppSheet.Builder(activity,false).setTitle("过渡测试").setItems(new String[]{"下一层"},(d,w)->{check(!first[0].isShowing(),"next menu starts after the outgoing window is removed");action.incrementAndGet();next[0]=new AppSheet.Builder(activity,false).setTitle("下一层").show();}).setOnDismissListener(d->{check(action.get()==1,"menu action precedes dismissal resource cleanup");cleanup.incrementAndGet();}).show();});
        await(()->first[0].isShowing()&&((View)field(first[0],"dragSurface")).getHeight()>0);
        ui(()->{View surface=(View)field(first[0],"dragSurface");Motion.sheetIn(surface,first[0].getWindow(),null);long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,50,50,0);((SheetDragLayout)surface).onInterceptTouchEvent(down);down.recycle();});
        await(()->((View)field(first[0],"dragSurface")).getAlpha()==1);
        ui(()->{check(((View)field(first[0],"dragSurface")).getTranslationY()==0,"a tap during entrance does not freeze the panel halfway");first[0].getListView().performItemClick(null,0,0);check(action.get()==0&&first[0].isShowing(),"menu action waits for the closing transition");check((first[0].getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND)!=0,"animated backdrop is actually enabled on the sheet window");});
        await(()->next[0]!=null&&next[0].isShowing()&&cleanup.get()==1);
        ui(()->{check(action.get()==1&&!first[0].isShowing(),"handoff runs once without overlapping outgoing window");next[0].dismiss();});await(()->!next[0].isShowing());
        AtomicInteger cancelled=new AtomicInteger();ui(()->{AppSheet sheet=new AppSheet.Builder(activity,false).setTitle("取消待执行操作").setPositiveButton("下一步",(d,w)->cancelled.incrementAndGet()).show();sheet.getButton(AppSheet.BUTTON_POSITIVE).performClick();sheet.dismissNow();});waitForIdleSync();ui(()->check(cancelled.get()==0,"forced teardown cancels deferred menu actions"));
    }
    private void testDefaultSwipeSetting(Context context,LibraryStore.Book book)throws Exception{
        open(book);ui(()->{context.getSharedPreferences("reader",0).edit().remove("rightSwipeNext").putBoolean("reduceMotion",true).apply();set(activity,"pagedMode",true);set(activity,"pageMotion",false);invoke("showReader",new Class<?>[]{int.class},0);});
        await(()->field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing());
        ui(()->{check(!(Boolean)field(field(activity,"pagedReader"),"rightSwipeNext"),"missing direction preference defaults to left swipe next");invoke("appearance",new Class<?>[0]);AppSheet panel=sheet();View strip=panel.getWindow().getDecorView().findViewWithTag("reading-swipe-direction");SettingsSection reading=null,gestures=null;for(View view:views(panel.getWindow().getDecorView()))if(view instanceof SettingsSection){SettingsSection section=(SettingsSection)view;if(section.key.equals("reading"))reading=section;if(section.key.equals("gestures"))gestures=section;}check(strip instanceof ChoiceStrip&&((ChoiceStrip)strip).selected()==0,"direction setting shows left as its default");check(views(reading.body).contains(strip)&&!views(gestures.body).contains(strip),"direction setting belongs to reading mode and effects");((ChoiceStrip)strip).select(1);panel.dismiss();});
        await(()->field(activity,"previewTypeset")==null&&sheet()==null);
        ui(()->check(context.getSharedPreferences("reader",0).getBoolean("rightSwipeNext",false),"an explicitly chosen right-swipe setting is preserved"));
    }
    private SnowCrystalView snow(AppSheet sheet){if(sheet==null)return null;for(View view:views(sheet.getWindow().getDecorView()))if(view instanceof SnowCrystalView)return (SnowCrystalView)view;return null;}
    private void testSnowAndDelete(Context context,LibraryStore store,LibraryStore.Book a,LibraryStore.Book b)throws Exception{
        int count=store.books.size();
        ui(()->{set(activity,"selecting",true);((Set<?>)field(activity,"selectedBooks")).clear();invoke("showLibrary",new Class<?>[]{String.class},"");});
        await(()->tagged("shelf-delete")!=null);
        ui(()->{Button delete=(Button)tagged("shelf-delete");check(!delete.isEnabled(),"bulk delete is disabled with no selected books");Set<String> ids=(Set<String>)field(activity,"selectedBooks");ids.add(a.id);ids.add(b.id);invoke("refreshSelection",new Class<?>[0]);check(delete.isEnabled(),"bulk delete enables for selected books");delete.performClick();check(text(sheet(),"删除所选的 2 本书？")!=null,"bulk delete confirms the selected count");check(snow(sheet())==null,"destructive confirmation has no decorative snow");sheet().dismiss();});
        await(()->sheet()==null);
        ui(()->{check(store.find(a.id)!=null&&store.find(b.id)!=null&&((Set<?>)field(activity,"selectedBooks")).size()==2,"cancelled bulk delete preserves books and selection");});
        screenshot("bulk-delete-selection");
        ui(()->{tagged("shelf-delete").performClick();sheet().getButton(AppSheet.BUTTON_POSITIVE).performClick();});
        await(()->!(Boolean)field(activity,"busy")&&store.find(a.id)==null&&store.find(b.id)==null&&sheet()!=null&&text(sheet(),"已移入回收站")!=null);
        ui(()->{check(store.books.size()==count-2,"bulk delete removes only selected books");check(!(Boolean)field(activity,"selecting")&&((Set<?>)field(activity,"selectedBooks")).isEmpty(),"successful bulk delete exits selection mode");LibraryStore disk=new LibraryStore(activity.getFilesDir());check(disk.find(a.id)==null&&disk.find(b.id)==null&&disk.books.size()==count-2,"bulk delete is durable in the real Android index");});
        screenshot("bulk-delete-done");
        ui(()->sheet().getButton(AppSheet.BUTTON_POSITIVE).performClick());
        for(String id:new String[]{a.id,b.id}){
            await(()->sheet()!=null&&text(sheet(),"回收站 · ")!=null);
            ui(()->{RecycleBin bin=(RecycleBin)field(activity,"recycleBin");ArrayList<RecycleBin.Entry> entries=bin.list(System.currentTimeMillis());int at=-1;for(int i=0;i<entries.size();i++)if(entries.get(i).book.id.equals(id))at=i;check(at>=0,"deleted book is available in recycle list");sheet().getListView().performItemClick(null,at,at);});
            await(()->sheet()!=null&&text(sheet(),"恢复正文、封面和阅读记录")!=null);
            ui(()->sheet().getListView().performItemClick(null,0,0));await(()->!(Boolean)field(activity,"busy")&&store.find(id)!=null);
            ui(()->{check(store.find(id)!=null,"recycle UI restores each batch-deleted book");if(id.equals(a.id))invoke("showRecycleBin",new Class<?>[0]);});
        }
        ui(()->check(store.books.size()==count,"restoring the batch returns the original shelf count"));
        open(store.find(a.id));ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();invoke("appearance",new Class<?>[0]);});
        await(()->snow(sheet())!=null&&snow(sheet()).animating()&&(Float)field(snow(sheet()),"phase")>.1f);
        final SnowCrystalView[] crystals={null};
        ui(()->{crystals[0]=snow(sheet());check(!crystals[0].isClickable()&&!crystals[0].isFocusable()&&crystals[0].getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO,"snow crystals do not become a touch or accessibility target");Bitmap image=frame(crystals[0]);int drawn=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(Color.alpha(image.getPixel(x,y))>0)drawn++;check(drawn>0&&drawn<image.getWidth()*image.getHeight()*.18f,"clearer snow crystals remain confined to a small part of the header");image.recycle();Bitmap shot=frame(sheet().getWindow().getDecorView());try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"sheet-snow.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();View anchor=sheet().getWindow().getDecorView().findViewWithTag("sheet-snow");SnowBurst.play(anchor,false);check(SnowBurst.playing(anchor),"local snow burst starts on a visible measured control");SnowBurst.play(anchor,false);Motion.finishTree(anchor);check(!SnowBurst.playing(anchor),"repeated bursts are cancelled by the shared lifecycle cleanup");View pending=new View(activity);SnowBurst.play(pending,false);check(SnowBurst.playing(pending),"unmeasured burst waits for a layout");SnowBurst.stop(pending);check(!SnowBurst.playing(pending),"a pending burst can be cancelled before attach");crystals[0].play();check(crystals[0].animating(),"snow is active before drag-close cleanup");((SheetDragLayout)field(sheet(),"dragSurface")).close();check(crystals[0].animating()&&(Boolean)field(crystals[0],"closing"),"drag close emits a short final ice-dust trail");});
        await(()->field(activity,"previewTypeset")==null&&sheet()==null);
        ui(()->{check(!crystals[0].animating(),"dismissed sheet releases its final dust animation");context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();invoke("appearance",new Class<?>[0]);});
        await(()->snow(sheet())!=null&&snow(sheet()).isAttachedToWindow());
        ui(()->{check(!snow(sheet()).animating()&&(Float)field(snow(sheet()),"phase")==1,"reduced motion skips decorative snow entirely");View anchor=snow(sheet());SnowBurst.play(anchor,false);check(!SnowBurst.playing(anchor),"reduced motion skips local bursts too");sheet().dismiss();});
        await(()->field(activity,"previewTypeset")==null&&sheet()==null);
        ui(()->invoke("leaveReader",new Class<?>[0]));
    }
    private void testPolish(Context context,LibraryStore store)throws Exception{
        final FrameLayout[] stage={null};final ChoiceStrip[] strip={null};final View[] bar={null};final BookCoverView[] cover={null};
        ui(()->{
            context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();
            stage[0]=new FrameLayout(activity);((LinearLayout)field(activity,"root")).addView(stage[0],new LinearLayout.LayoutParams(-1,240));
            strip[0]=new ChoiceStrip(activity,new String[]{"第一","第二","第三"},0,false);stage[0].addView(strip[0],new FrameLayout.LayoutParams(600,180));
            bar[0]=new View(activity);bar[0].setVisibility(View.GONE);stage[0].addView(bar[0],new FrameLayout.LayoutParams(40,40));
            cover[0]=new BookCoverView(activity,"旧书",1);stage[0].addView(cover[0],new FrameLayout.LayoutParams(90,130));
        });waitForIdleSync();
        ui(()->{strip[0].getChildAt(2).performClick();check(strip[0].selected()==2&&strip[0].getChildAt(2).isSelected(),"choice semantics update immediately");});
        Thread.sleep(65);
        ui(()->{RectF pill=(RectF)field(strip[0],"pill"),target=(RectF)field(strip[0],"target");check(pill.left>strip[0].getChildAt(0).getLeft()&&pill.left<target.left,"shared choice pill has a real intermediate position");strip[0].getChildAt(1).performClick();Motion.finishTree(strip[0]);check(((RectF)field(strip[0],"pill")).equals(field(strip[0],"target")),"interrupted choice settles on the newest selection");
            strip[0].setOrientation(LinearLayout.VERTICAL);for(int i=0;i<strip[0].getChildCount();i++)strip[0].getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-1,48));
        });waitForIdleSync();
        ui(()->{strip[0].select(2);Motion.finishTree(strip[0]);check(((RectF)field(strip[0],"pill")).top==strip[0].getChildAt(2).getTop(),"vertical choice pill follows actual child bounds");
            Motion.visible(bar[0],true);Motion.visible(bar[0],false);Motion.visible(bar[0],true);Motion.finishTree(bar[0]);check(bar[0].getVisibility()==View.VISIBLE&&bar[0].getAlpha()==1&&bar[0].getTranslationY()==0,"rapid visibility reversal cannot hide the new playback bar");
            Motion.visible(bar[0],false);Motion.finishTree(bar[0]);check(bar[0].getVisibility()==View.GONE&&bar[0].getAlpha()==1,"playback bar exit releases layout space and resets alpha");
            Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.BLUE);cover[0].setBitmap(image,true);Motion.finishTree(cover[0]);check((Float)field(cover[0],"bitmapAlpha")==1,"cover reveal reaches full opacity");cover[0].bind("新书",2);check(field(cover[0],"bitmap")==null&&field(cover[0],"title").equals("新书"),"rebound cover clears the previous book image");image.recycle();
            AtomicInteger jumped=new AtomicInteger(-1);BookmarkSeekBar seek=new BookmarkSeekBar(activity,new BookmarkSeekBar.Actions(){public void bookmark(int offset){jumped.set(offset);}public void chapters(){}});seek.bookmarks(Arrays.asList(50),101,false);seek.layout(0,0,600,80);float x=300,y=5*context.getResources().getDisplayMetrics().density;long now=SystemClock.uptimeMillis();event(seek,now,now,MotionEvent.ACTION_DOWN,x,y);event(seek,now,now+30,MotionEvent.ACTION_CANCEL,x,y);check(jumped.get()==-1,"cancelled bookmark press never navigates");event(seek,now+100,now+100,MotionEvent.ACTION_DOWN,x,y);event(seek,now+100,now+130,MotionEvent.ACTION_UP,x,y);check(jumped.get()==50,"bookmark feedback preserves exact jump offset");Motion.finishTree(seek);
            context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();strip[0].select(0);check(((RectF)field(strip[0],"pill")).equals(field(strip[0],"target")),"reduced motion applies choice final state immediately");Motion.visible(bar[0],true);check(bar[0].getVisibility()==View.VISIBLE&&bar[0].getAlpha()==1,"reduced motion shows playback controls immediately");
            ((LinearLayout)field(activity,"root")).removeView(stage[0]);invoke("leaveReader",new Class<?>[0]);
        });waitForIdleSync();
        ui(()->{
            GridView grid=(GridView)tagged("shelf-grid");BaseAdapter adapter=(BaseAdapter)grid.getAdapter();View first=adapter.getView(0,null,grid);BookCoverView firstCover=null;for(View v:views(first))if(v instanceof BookCoverView)firstCover=(BookCoverView)v;
            Bitmap stale=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);firstCover.setBitmap(stale);View rebound=adapter.getView(1,first,grid);check(rebound==first,"shelf adapter reuses the complete card tree");
            BookCoverView next=null;for(View v:views(rebound))if(v instanceof BookCoverView)next=(BookCoverView)v;LibraryStore.Book expected=(LibraryStore.Book)adapter.getItem(1);check(next.getTag().equals(expected.id)&&field(next,"bitmap")==null,"recycled card binds the new identity without a stale image");check(rebound.getContentDescription().toString().startsWith(expected.title),"recycled card accessibility identifies the new book");stale.recycle();
        });screenshot("polish-home");
    }
    private void screenshot(String name)throws Exception{waitForIdleSync();Bitmap shot=getUiAutomation().takeScreenshot();if(shot!=null){try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}}
    private void testSettingsGroups(Context context)throws Exception{
        ui(()->{
            View top=(View)field(activity,"readerTopBar"),bottom=(View)field(activity,"readerBottomBar");
            for(View bar:new View[]{top,bottom}){
                android.graphics.drawable.Drawable background=bar.getBackground();Rect bounds=new Rect(background.getBounds());Bitmap sample=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);background.setBounds(0,0,48,48);background.draw(new Canvas(sample));background.setBounds(bounds);
                int alpha=Color.alpha(sample.getPixel(24,24));check(alpha>180&&alpha<255&&bar.getAlpha()==1f,"toolbar surface is translucent while controls stay opaque");sample.recycle();
            }
            invoke("appearance",new Class<?>[0]);AppSheet panel=sheet();ArrayList<SettingsSection> sections=new ArrayList<>();for(View view:views(panel.getWindow().getDecorView()))if(view instanceof SettingsSection)sections.add((SettingsSection)view);
            check(sections.size()==6,"settings grouped into six categories");SettingsSection fonts=null;for(SettingsSection section:sections)if(section.key.equals("font"))fonts=section;
            check(fonts!=null&&fonts.expanded(),"font category opens by default");for(SettingsSection section:sections)if(section!=fonts)check(section.body.getVisibility()==View.GONE,"other category bodies start collapsed");
            TextView size=text(panel,"字号 · ");ViewGroup parent=(ViewGroup)size.getParent();((SeekBar)parent.getChildAt(parent.indexOfChild(size)+1)).setProgress(15);
        });screenshot("ui-font");
        ui(()->{
            AppSheet panel=sheet();SettingsSection fonts=null,paragraphs=null;for(View view:views(panel.getWindow().getDecorView()))if(view instanceof SettingsSection){SettingsSection section=(SettingsSection)view;if(section.key.equals("font"))fonts=section;if(section.key.equals("paragraph"))paragraphs=section;}
            paragraphs.header.performClick();check(paragraphs.expanded()&&fonts.body.getVisibility()==View.GONE,"opening paragraph category collapses font category");
            fonts.header.performClick();TextView size=text(panel,"字号 · 31");check(size!=null,"collapsing and reopening retains unsaved font size");
            fonts.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE,null);check(!fonts.expanded(),"accessibility collapse action works");
            paragraphs.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND,null);check(paragraphs.expanded(),"accessibility expand action works");
        });screenshot("ui-paragraph");
        ui(()->sheet().dismiss());await(()->field(activity,"previewTypeset")==null);
        ui(()->{LibraryStore.Book book=(LibraryStore.Book)field(activity,"active");check(book.typeset!=null&&book.typeset.font==31,"grouped settings persist scoped typography");check(context.getSharedPreferences("reader",0).getInt("font",0)==21,"scoped grouped settings preserve global font");invoke("appearance",new Class<?>[0]);boolean remembered=false;for(View view:views(sheet().getWindow().getDecorView()))if(view instanceof SettingsSection&&((SettingsSection)view).key.equals("paragraph"))remembered=((SettingsSection)view).expanded();check(remembered,"last expanded category is remembered");sheet().dismiss();});
        await(()->field(activity,"previewTypeset")==null);screenshot("ui-reading");
        AtomicInteger palette=new AtomicInteger();
        ui(()->{LibraryStore.Book book=(LibraryStore.Book)field(activity,"active");palette.set(book.typeset.palette);book.typeset.palette=2;invoke("showReader",new Class<?>[]{int.class},0);invoke("appearance",new Class<?>[0]);TextView sample=null;for(View view:views(sheet().getWindow().getDecorView()))if("当前排版预览".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))sample=(TextView)view;check(sample!=null&&sample.getCurrentTextColor()==SnowTheme.NIGHT_INK,"grouped preview respects the night theme");});
        screenshot("ui-night");ui(()->{sheet().dismiss();LibraryStore.Book book=(LibraryStore.Book)field(activity,"active");book.typeset.palette=palette.get();invoke("showReader",new Class<?>[]{int.class},0);});
    }
    private void actualSwipe(View page,boolean right)throws Exception{
        AtomicReference<Point> first=new AtomicReference<>(),last=new AtomicReference<>();
        ui(()->{int[] origin=new int[2];page.getLocationOnScreen(origin);float width=page.getWidth()-page.getPaddingLeft()-page.getPaddingRight();int y=origin[1]+page.getHeight()/2;first.set(new Point(origin[0]+page.getPaddingLeft()+Math.round(width*(right?.15f:.85f)),y));last.set(new Point(origin[0]+page.getPaddingLeft()+Math.round(width*(right?.85f:.15f)),y));});
        long down=SystemClock.uptimeMillis();Point start=first.get(),end=last.get();injectPointer(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,start.x,start.y,0));
        for(int i=1;i<=10;i++){injectPointer(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start.x+(end.x-start.x)*i/10f,start.y,0));Thread.sleep(18);}
        Thread.sleep(100);injectPointer(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,end.x,end.y,0));
    }
    private void testActualSwipeDirections(Context context)throws Exception{
        for(boolean rightNext:new boolean[]{true,false}){
            ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("rightSwipeNext",rightNext).putBoolean("reduceMotion",false).apply();set(activity,"pageMotion",true);invoke("showReader",new Class<?>[]{int.class},0);});
            await(()->field(activity,"pagedReader")!=null&&!((PagedReaderView)field(activity,"pagedReader")).isIndexing()&&((PagedReaderView)field(activity,"pagedReader")).pageEnd()>0);
            PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");AtomicReference<String> firstText=new AtomicReference<>();AtomicInteger expected=new AtomicInteger();
            ui(()->{firstText.set(((android.text.StaticLayout)field(page,"layout")).getText().toString());expected.set(page.pageEnd());});
            actualSwipe(page,rightNext);await(()->!page.isIndexing()&&field(page,"animator")==null);
            ui(()->{String text=((android.text.StaticLayout)field(page,"layout")).getText().toString();check(page.position()==expected.get(),(rightNext?"right":"left")+" finger swipe commits exactly the next page");check(!text.equals(firstText.get()),"displayed text changes after the next-page animation");check((Integer)field(page,"slideDirection")== (rightNext?-1:1),"animation follows physical finger movement independently of content direction");});
            actualSwipe(page,!rightNext);await(()->!page.isIndexing()&&field(page,"animator")==null);
            ui(()->{check(page.position()==0,"opposite swipe returns to the original page");check(((android.text.StaticLayout)field(page,"layout")).getText().toString().equals(firstText.get()),"previous-page animation restores the original text");});
        }
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();((PagedReaderView)field(activity,"pagedReader")).setAnimationEnabled(false);});
    }
    private void testHomeLayout(Context context,LibraryStore store,LibraryStore.Book book)throws Exception{
        store.beginBatch();try{for(int i=0;i<24;i++)store.add(("第一章 测试\n这是书架布局样本 "+i+"。\n雪渐融，故事正生长。").getBytes(StandardCharsets.UTF_8),"版式样本 "+i+".txt",null);}finally{store.endBatch();}
        ui(()->{set(activity,"selecting",false);set(activity,"shelfSearchOpen",false);invoke("showLibrary",new Class<?>[]{String.class},"");});
        await(()->((GridView)tagged("shelf-grid")).getChildCount()>0&&tagged("shelf-resume").getHeight()>0);
        ui(()->{
            boolean name=false,tagline=false,largeImport=false;for(View view:views(activity.getWindow().getDecorView()))if(view instanceof TextView){String text=((TextView)view).getText().toString();name|=text.equals("融雪阅读");tagline|=text.equals("雪渐融，故事正生长。");largeImport|=view instanceof Button&&text.equals("批量导入 TXT")&&view.isShown();}
            check(name&&tagline,"home keeps the brand name and full tagline");check(!largeImport&&tagged("shelf-import")!=null,"import moved to the header without a large permanent footer");
            check(tagged("shelf-search-row").getVisibility()==View.GONE,"home search starts collapsed");
            GridView grid=(GridView)tagged("shelf-grid");View root=activity.findViewById(android.R.id.content);check(grid.getHeight()>root.getHeight()*.65f,"books get most of the available home height");
            check(grid.getChildAt(0) instanceof ShelfTile,"book cards support transform-only lift");
            for(View view:views(grid))if(view instanceof ShelfTitleView)check(((ShelfTitleView)view).getMaxLines()<=2,"shelf title stays within two aligned lines");
            grid.smoothScrollToPosition(15);
        });await(()->((GridView)tagged("shelf-grid")).getFirstVisiblePosition()>0);
        ui(()->{View wrap=tagged("shelf-resume-wrap");check(wrap.getTranslationY()<=-wrap.getHeight()+1,"continue-reading card scrolls out instead of occupying fixed space");tagged("shelf-search-toggle").performClick();EditText input=(EditText)tagged("shelf-search");check(input.getVisibility()==View.VISIBLE&&input.isFocused(),"one tap expands and focuses search");input.setText("Native inherited B");});
        await(()->((GridView)tagged("shelf-grid")).getCount()==1);
        ui(()->{check(tagged("shelf-resume-wrap").getVisibility()==View.GONE,"search results remove the continue-reading inset");activity.onBackPressed();check(!activity.isFinishing()&&!(Boolean)field(activity,"shelfSearchOpen"),"back closes search before exiting the app");});
        await(()->tagged("shelf-search-row").getVisibility()==View.GONE);
        Thread.sleep(250);ui(()->{GridView grid=(GridView)tagged("shelf-grid");View wrap=tagged("shelf-resume-wrap");Bundle report=new Bundle();report.putString("stream","Resume: first="+grid.getFirstVisiblePosition()+" top="+(grid.getChildAt(0)==null?-999:grid.getChildAt(0).getTop())+" padding="+grid.getPaddingTop()+" header="+wrap.getHeight()+" visibility="+wrap.getVisibility()+" y="+wrap.getTranslationY());sendStatus(0,report);});
        await(()->tagged("shelf-resume-wrap").isShown()&&Math.abs(tagged("shelf-resume-wrap").getTranslationY())<1);
        ui(()->check(tagged("shelf-resume").isShown(),"continue-reading returns at the top after closing search"));
        screenshot("home-normal");
        ui(()->{tagged("shelf-select").performClick();GridView grid=(GridView)tagged("shelf-grid");grid.performItemClick(grid.getChildAt(0),0,0);ArrayList<LibraryStore.Book> selected=new ArrayList<>();Set<String> ids=(Set<String>)field(activity,"selectedBooks");for(LibraryStore.Book b:store.books)if(ids.contains(b.id))selected.add(b);store.organize(()->store.organization.pin(selected,true));invoke("refreshSelection",new Class<?>[0]);check(((Button)field(activity,"batchPin")).getText().toString().equals("取消置顶"),"selection toolbar combines pin/unpin into one stateful action");});
        screenshot("home-select");ui(()->{activity.onBackPressed();context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();});waitForIdleSync();
        ui(()->{GlowFrame logo=(GlowFrame)tagged("shelf-logo");logo.pulse();});await(()->((GlowFrame)tagged("shelf-logo")).glowing());
        ui(()->{GlowFrame logo=(GlowFrame)tagged("shelf-logo");Motion.finishTree(logo);check(!logo.glowing(),"one-shot logo light finishes cleanly");((ShelfTile)((GridView)tagged("shelf-grid")).getChildAt(0)).lift(true);});
        await(()->((GridView)tagged("shelf-grid")).getChildAt(0).getTranslationY()<0);
        ui(()->((ShelfTile)((GridView)tagged("shelf-grid")).getChildAt(0)).lift(false));
        await(()->((GridView)tagged("shelf-grid")).getChildAt(0).getTranslationY()==0f);
        ui(()->{
            check(((GridView)tagged("shelf-grid")).getChildAt(0).getTranslationZ()==0,"cover lift restores its elevation on release");
            LinearLayout root=(LinearLayout)field(activity,"root");ListeningBars bars=new ListeningBars(activity,SnowTheme.ACCENT);bars.setTag("test-wave");root.addView(bars,new LinearLayout.LayoutParams(48,48));bars.playing(true);check(bars.animating(),"listening status animates while visible and playing");bars.setVisibility(View.GONE);check(!bars.animating(),"hidden listening status stops animation");bars.setVisibility(View.VISIBLE);bars.playing(false);check(!bars.animating(),"paused listening status stops animation");context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();bars.playing(true);check(!bars.animating(),"reduced motion disables playback animation");root.removeView(bars);GlowFrame logo=(GlowFrame)tagged("shelf-logo");logo.pulse();check(!logo.glowing(),"reduced motion disables decorative light");
        });
        open(book);ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).putString("readerSettingsGroup","font").apply();invoke("appearance",new Class<?>[0]);for(View view:views(sheet().getWindow().getDecorView()))if(view instanceof SettingsSection&&((SettingsSection)view).key.equals("paragraph"))((SettingsSection)view).header.performClick();});
        await(()->{for(View view:views(sheet().getWindow().getDecorView()))if(view instanceof SettingsSection&&((SettingsSection)view).key.equals("paragraph")){SettingsSection s=(SettingsSection)view;return s.expanded()&&s.body.getHeight()>0&&s.body.getLayoutParams().height==ViewGroup.LayoutParams.WRAP_CONTENT;}return false;});
        ui(()->{SettingsSection paragraph=null;for(View view:views(sheet().getWindow().getDecorView()))if(view instanceof SettingsSection&&((SettingsSection)view).key.equals("paragraph"))paragraph=(SettingsSection)view;paragraph.expanded(false);paragraph.expanded(true);Motion.finishTree(sheet().getWindow().getDecorView());check(paragraph.expanded()&&paragraph.body.getVisibility()==View.VISIBLE&&paragraph.body.getLayoutParams().height==ViewGroup.LayoutParams.WRAP_CONTENT,"interrupted accordion animation settles to its final state");sheet().dismiss();context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();});
    }
    private Bitmap frame(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));return bitmap;}
    private void testReleaseContinuity(Context context)throws Exception{
        PagedReaderView page=(PagedReaderView)field(activity,"pagedReader");
        ui(()->{context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",false).apply();Motion.invalidateCache(context);page.setAnimationEnabled(true);page.goTo(0);});
        await(()->!page.isIndexing());
        ExecutorService worker=(ExecutorService)field(page,"worker"),neighbour=(ExecutorService)field(page,"neighbourWorker");
        CountDownLatch gate=new CountDownLatch(1),blocked=new CountDownLatch(2);
        Runnable hold=()->{blocked.countDown();try{gate.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}};
        worker.execute(hold);neighbour.execute(hold);check(blocked.await(5,TimeUnit.SECONDS),"pagination workers controlled for async release regression");
        AtomicReference<Float> released=new AtomicReference<>(),firstFraction=new AtomicReference<>();AtomicInteger destination=new AtomicInteger();
        PagedReaderView.Listener original=(PagedReaderView.Listener)field(page,"listener");
        ui(()->{
            ((Map<?,?>)field(page,"cache")).clear();destination.set(page.pageEnd());
            page.setListener(new PagedReaderView.Listener(){public void changed(){if(!page.isIndexing()&&page.position()==destination.get()&&firstFraction.get()==null)try{firstFraction.set((Float)field(page,"slideFraction"));}catch(Exception e){throw new RuntimeException(e);}original.changed();}public void notice(String m){original.notice(m);}public void toggleControls(){original.toggleControls();}public void userTurn(){original.userTurn();}public void copyAt(int at){original.copyAt(at);}public void bookmarkAt(int at){original.bookmarkAt(at);}});
            float width=page.getWidth()-page.getPaddingLeft()-page.getPaddingRight(),x=page.getPaddingLeft()+width*.94f,end=page.getPaddingLeft()+width*.16f,y=page.getHeight()*.5f;long now=SystemClock.uptimeMillis();
            event(page,now,now,0,x,y);event(page,now,now+500,2,end,y);
            released.set(Math.abs((Float)field(page,"dragOffset"))/width);check(released.get()>.6f&&released.get()<.95f,"page follows most of the finger travel");Bitmap before=frame(page);
            event(page,now,now+650,1,end,y);check(page.isIndexing()&&page.position()==0,"unmeasured destination is not committed early");
            Bitmap after=frame(page);check(before.sameAs(after),"release frame is pixel-identical while next page waits; no snap back");before.recycle();after.recycle();
        });
        gate.countDown();await(()->!page.isIndexing()&&field(page,"animator")==null);
        ui(()->{check(firstFraction.get()!=null&&Math.abs(firstFraction.get()-released.get())<.02f,"remaining animation starts at released fraction instead of zero");check(page.position()==destination.get(),"drag completes exactly one page");page.setListener(original);});
        ui(()->{
            int at=page.position();float width=page.getWidth()-page.getPaddingLeft()-page.getPaddingRight(),x=page.getWidth()*.5f,y=page.getHeight()*.5f;long now=SystemClock.uptimeMillis();
            event(page,now,now,0,x,y);event(page,now,now+500,2,x+width*.12f,y);Bitmap before=frame(page);event(page,now,now+700,1,x+width*.12f,y);Bitmap after=frame(page);
            check(before.sameAs(after),"uncommitted pull springs back from its released frame");check(page.position()==at,"short drag does not commit a page");before.recycle();after.recycle();
        });await(()->field(page,"spring")==null);
        TextDocument doc=(TextDocument)field(activity,"document");int second=doc.chapters.get(1).offset;
        ui(()->page.goTo(second));await(()->!page.isIndexing());
        ui(()->{float width=page.getWidth()-page.getPaddingLeft()-page.getPaddingRight(),x=page.getPaddingLeft()+width*.1f,y=page.getHeight()*.5f;long now=SystemClock.uptimeMillis();event(page,now,now,0,x,y);event(page,now,now+500,2,x+width*.72f,y);});
        await(()->field(page,"dragNeighbour")!=null);
        ui(()->{android.text.StaticLayout preview=(android.text.StaticLayout)field(page,"dragNeighbour");check(preview.getText().length()>0,"previous chapter's last page has a drag preview");long now=SystemClock.uptimeMillis();event(page,now-700,now,1,page.getWidth()*.8f,page.getHeight()*.5f);check((Float)field(page,"slideFraction")>.6f,"backward turn continues from dragged position");});
        await(()->!page.isIndexing()&&field(page,"animator")==null);
        ui(()->{check(page.position()<second,"backward chapter boundary turn commits previous section");page.setAnimationEnabled(false);page.next();});await(()->!page.isIndexing());
        ui(()->{check((Float)field(page,"pendingTurn")==0f&&(Float)field(page,"dragOffset")==0f,"disabled animation clears pending drag state");context.getSharedPreferences("reader",0).edit().putBoolean("reduceMotion",true).apply();});
    }
    private View tagged(String id){for(View view:views(activity.getWindow().getDecorView()))if(id.equals(view.getTag()))return view;return null;}
    private void injectPointer(MotionEvent event){try{event.setSource(InputDevice.SOURCE_TOUCHSCREEN);if(!getUiAutomation().injectInputEvent(event,true))throw new AssertionError("System pointer injection failed");}finally{event.recycle();}}
    private long beginShelfDrag()throws Exception{
        AtomicReference<Point> point=new AtomicReference<>();
        ui(()->{GridView grid=null;for(View v:views(activity.getWindow().getDecorView()))if(v instanceof GridView)grid=(GridView)v;check(grid!=null&&grid.getChildCount()>0,"shelf has a draggable book");Set<?> selected=(Set<?>)field(activity,"selectedBooks");View tile=null;for(int i=0;i<grid.getChildCount()&&tile==null;i++)for(View child:views(grid.getChildAt(i)))if(selected.contains(child.getTag())){tile=grid.getChildAt(i);break;}check(tile!=null,"a selected book is visible for the long-press regression");int[] at=new int[2];tile.getLocationOnScreen(at);point.set(new Point(at[0]+tile.getWidth()/2,at[1]+tile.getHeight()/3));});
        Point p=point.get();long down=SystemClock.uptimeMillis();injectPointer(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,p.x,p.y,0));Thread.sleep(ViewConfiguration.getLongPressTimeout()+250);
        await(()->field(activity,"shelfDrag")!=null&&field(field(activity,"shelfDrag"),"session")!=null);return down;
    }
    private void finishShelfDrag(long down,Point target)throws Exception{
        for(int i=0;i<5;i++){long at=SystemClock.uptimeMillis();injectPointer(MotionEvent.obtain(down,at,MotionEvent.ACTION_MOVE,target.x,target.y,0));Thread.sleep(70);}
        Bitmap shot=getUiAutomation().takeScreenshot();if(shot!=null){try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"drag-hover-test.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
        ui(()->{Object controller=field(activity,"shelfDrag");TextView hint=(TextView)field(controller,"hint");Bundle report=new Bundle();report.putString("stream","Drag target="+target+"; hint="+hint.getText()+"; session="+field(controller,"session"));sendStatus(0,report);});
        injectPointer(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,target.x,target.y,0));
    }
    private void testShelfDrag(LibraryStore store,LibraryStore.Book a,LibraryStore.Book b)throws Exception{
        String suffix=Long.toString(System.currentTimeMillis()%1000000);String old=store.organization.add("拖拽原"+suffix),target=store.organization.add("拖拽目标"+suffix);store.organize(()->store.organization.assign(Arrays.asList(a,b),old));
        AtomicReference<Point> destination=new AtomicReference<>();
        ui(()->{set(activity,"selecting",true);Set<String> selected=(Set<String>)field(activity,"selectedBooks");selected.clear();selected.add(a.id);selected.add(b.id);invoke("showLibrary",new Class<?>[]{String.class},"");});waitForIdleSync();Thread.sleep(350);
        ui(()->{View chip=tagged(target);HorizontalScrollView strip=null;for(View v:views(activity.getWindow().getDecorView()))if(v instanceof HorizontalScrollView)strip=(HorizontalScrollView)v;check(chip!=null&&strip!=null,"real categories are drag targets");strip.scrollTo(chip.getRight()-strip.getWidth()+24,0);});waitForIdleSync();
        ui(()->{View chip=tagged(target);int[] at=new int[2];chip.getLocationOnScreen(at);destination.set(new Point(at[0]+chip.getWidth()/2,at[1]+chip.getHeight()/2));});
        long down=beginShelfDrag();ui(()->check(((Set<?>)field(activity,"selectedBooks")).size()==2,"long press keeps all selected books selected"));finishShelfDrag(down,destination.get());
        await(()->!(Boolean)field(activity,"busy")&&a.categoryIds.contains(target)&&b.categoryIds.contains(target));
        ui(()->{check(a.categoryIds.contains(old)&&b.categoryIds.contains(old),"drop adds a category without removing original memberships");check(((Set<?>)field(activity,"selectedBooks")).size()==2&&(Boolean)field(activity,"selecting"),"selection survives successful drop for further grouping");});
        LibraryStore disk=new LibraryStore(getTargetContext());check(disk.find(a.id).categoryIds.contains(target)&&disk.find(b.id).categoryIds.contains(target),"both dropped books persisted to disk");
        String before=store.metadata().toString();down=beginShelfDrag();AtomicReference<Point> blank=new AtomicReference<>(),edge=new AtomicReference<>();AtomicInteger scrollBefore=new AtomicInteger();
        ui(()->{View root=activity.findViewById(android.R.id.content);int[] at=new int[2];root.getLocationOnScreen(at);blank.set(new Point(at[0]+root.getWidth()/2,at[1]+root.getHeight()*2/3));HorizontalScrollView strip=(HorizontalScrollView)field(field(activity,"shelfDrag"),"strip");strip.getLocationOnScreen(at);scrollBefore.set(strip.getScrollX());edge.set(new Point(at[0]+strip.getWidth()-12,at[1]+strip.getHeight()/2));});
        injectPointer(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,edge.get().x,edge.get().y,0));Thread.sleep(350);
        ui(()->check(((HorizontalScrollView)field(field(activity,"shelfDrag"),"strip")).getScrollX()>scrollBefore.get(),"holding at category strip edge scrolls to offscreen categories"));
        finishShelfDrag(down,blank.get());
        await(()->field(field(activity,"shelfDrag"),"session")==null);check(before.equals(store.metadata().toString()),"drop on blank shelf cancels without editing metadata");
        ui(()->{check(((Set<?>)field(activity,"selectedBooks")).size()==2,"cancel keeps selection for retry");});
    }
    private void testCachedAudio(Context context)throws Exception{
        File home=new File(context.getCacheDir(),"listen-ahead");home.mkdirs();File wave=new File(home,"ahead-native-test.wav");int samples=4410;ByteBuffer data=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);for(int i=0;i<samples;i++)data.putShort((short)(Math.sin(i*.1)*100));try(OutputStream out=new FileOutputStream(wave)){out.write(data.array());}
        SpeechPrecache[] cache={null};CountDownLatch done=new CountDownLatch(1);AtomicInteger started=new AtomicInteger(),failed=new AtomicInteger();ui(()->{cache[0]=new SpeechPrecache(context);Class<?> jobType=Class.forName("com.snowread.app.SpeechPrecache$Job");Constructor<?> constructor=jobType.getDeclaredConstructor(String.class,String.class,float.class);constructor.setAccessible(true);Object job=constructor.newInstance("test","缓存句子",1f);set(job,"file",wave);set(job,"complete",true);set(cache[0],"job",job);TextChunker.SpeechChunk chunk=TextChunker.next("缓存句子",0,4,300);SpeechPrecache.Playback callback=new SpeechPrecache.Playback(){public void started(){started.incrementAndGet();}public void finished(){done.countDown();}public void failed(){failed.incrementAndGet();done.countDown();}};check(!cache[0].play(chunk,1.2f,callback),"cache never reuses wrong speed");check(cache[0].play(chunk,1f,callback),"matching cache file starts native playback");});check(done.await(8,TimeUnit.SECONDS),"cached audio completion");check(started.get()==1&&failed.get()==0,"cached audio starts exactly once without failure");ui(()->{check(!wave.exists(),"played cache file removed");cache[0].stop();cache[0].close();});
    }
}
