package com.snowread.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native, offline reader. No WebView, third-party runtime, or tracking; networking is limited to the explicitly enabled local TXT receiver. */
public final class MainActivity extends Activity {
    private static final int CREATE_EPUB=51,PICK_REPLACE=47,PICK_FOLDER=46,PICK_TXT=41,PICK_COVER=42,CREATE_BACKUP=43,PICK_BACKUP=44,EXPORT_TXT=45,CREATE_EXPORT=48,PICK_FONT=49,EXPORT_NOTES=50;
    private interface FontChoice {void choose(String id);}
    private FontChoice pendingFontChoice;
    private ArrayList<BookNote> pendingNoteExport;private String pendingNoteTitle;
    private volatile LanTransferServer lanSession;private int lanGeneration;private boolean lanStarting;private volatile boolean lanLibraryDirty;

    private ReadingStats readingStats;private boolean statsResumed;private long lastReadingInteraction=SystemClock.elapsedRealtime();
    private final Runnable statsTick=new Runnable(){public void run(){statsPulse();if(statsResumed)chromeHandler.postDelayed(this,15000);}};
    private ExecutorService io;
    private LibraryRepository repository;
    private ListenUi listenUi;
    private int listenStart=-1,listenEnd=-1;
    private boolean listenFollowing=true,followingJump,openListenAfterLoad;
    private long lastListenFollow;
    private View followButton,pressedTile;
    private float pressX,pressY;
    private String notificationBook;
    private Bitmap pendingFrame;
    private TaskProgress taskProgress;
    private TaskControl taskControl;
    private String taskTitle="";
    private AppSheet cancellationSheet;
    private android.window.OnBackInvokedCallback systemBack;
    private RecycleBin recycleBin;
    private final ReaderPolicy.History navigation=new ReaderPolicy.History();
    private Uri externalFile;
    private int manualPalette;
    private Typeset previewTypeset;private Integer previewNightMode;
    private boolean appearanceDark;
    private final BrightnessGesture brightnessGesture=new BrightnessGesture();private long lastVolumePage;
    private final Runnable nightTick=new Runnable(){public void run(){refreshAutoTheme();chromeHandler.postDelayed(this,30000);}};
    private LibraryStore store;
    private CoverStore covers;
    private final android.util.LruCache<String,Bitmap> coverCache=new android.util.LruCache<String,Bitmap>(4*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) {return value.getAllocationByteCount();}
    };
    private String pendingCoverId,shelfQuery="";
    private String shelfCategory=ShelfOrganization.ALL;
    private boolean selecting;
    private final LinkedHashSet<String> selectedBooks=new LinkedHashSet<>();
    private final HashSet<String> selectionAnimations=new HashSet<>();
    private ShelfAdapter shelfAdapter;
    private EditText shelfSearch;private boolean shelfSearchOpen,brandLightShown;private Button batchPin,batchDelete;private String pendingCategoryGlow;
    private final HashSet<String> freshBooks=new HashSet<>();
    private ShelfCategoryDrag shelfDrag;
    private TextView selectionTitle;
    private FrameLayout readerStage;
    private View readerTopBar,readerBottomBar;
    private final ReaderChrome chrome=new ReaderChrome();
    private final Handler chromeHandler=new Handler(Looper.getMainLooper());
    private final Runnable chromeTimeout=()-> {
        if(chrome.hideIfDue(SystemClock.elapsedRealtime())) applyChromeVisibility();
        scheduleChrome();
    };
    private LibraryStore.Book active;
    private TextDocument document;
    private LinearLayout root;
    private ListView reader;
    private PagedReaderView pagedReader;
    private boolean pagedMode=false,pageMotion=true;
    private String readerText="";
    private boolean statsPage;private String statsReturnBook="",statsSelectedDay="";
    private TextView chapterLabel, progressLabel,readerHeader;
    private ReaderFooter readerFooter;
    private SeekBar progress;
    private ReaderAdapter readerAdapter;
    private android.content.SharedPreferences prefs;
    private int fontSize=21, palette=0, generation=0, highlight=-1, sideMargin=26;
    private float spacing=1.65f;
    private String searchTerm="";
    private boolean restoring=false, failed=false, busy=false,tearingDown;
    private int bg, ink, muted, card;
    private int accent=SnowTheme.ACCENT_DARK;
    private AppSheet loading;
    private String pendingExportBookId;private String pendingReplaceId;
    private volatile BackupArchive.Prepared preparedBackup;
    private volatile int searchGeneration;
    private SearchResults searchSession;private TextDocument searchSessionDoc;private String searchSessionTerm="";
    private boolean searchSessionOnlyChapter,searchSessionIgnoreCase=true;
    private int searchSessionChapter,searchSessionRow,searchSessionTop;
    private final HashSet<Integer> searchSessionFolded=new HashSet<>();
    private final java.util.concurrent.atomic.AtomicBoolean importStop=new java.util.concurrent.atomic.AtomicBoolean();
    private View notice;
    private final Runnable clearNotice=()->{View old=notice;if(old!=null)Motion.exit(old,Motion.TOAST_OUT,Motion.FAST,()->{if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);if(notice==old)notice=null;});};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);clearLegacyPrivacy();
        if(Build.VERSION.SDK_INT>=33){systemBack=()->onBackPressed();getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBack);}
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        pagedMode=prefs.getBoolean("pagedMode",false);pageMotion=prefs.getBoolean("pageMotion",true);
        sideMargin=Math.max(12,Math.min(56,prefs.getInt("sideMargin",26)));fontSize=prefs.getInt("font",21); spacing=prefs.getFloat("spacing",1.65f); manualPalette=prefs.getInt("palette",0);palette=effectivePalette();appearanceDark=uiDark();
        try { repository=LibraryRepository.get(this);store=repository.store();io=repository.io;covers=new CoverStore(this);recycleBin=new RecycleBin(getFilesDir());listenUi=new ListenUi(this,state); }
        catch (Exception e) {
            failed=true;
            dialog().setTitle("本地书库暂时无法读取")
                .setMessage("为保留已有数据，本次不会覆盖书库。请重启应用重试。\n"+message(e))
                .setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show();
            return;
        }
        if(state!=null){shelfSearchOpen=state.getBoolean("shelfSearchOpen");shelfCategory=state.getString("shelfCategory",ShelfOrganization.ALL);selecting=state.getBoolean("selecting");ArrayList<String> ids=state.getStringArrayList("selectedBooks");if(ids!=null)selectedBooks.addAll(ids);}
        showLibrary(state==null?"":state.getString("shelfQuery",""));
        receiveExternal(getIntent());
        if(state!=null&&externalFile==null&&state.getBoolean("statsPage",false)){statsPage=true;statsReturnBook=state.getString("statsReturnBook","");statsSelectedDay=state.getString("statsSelectedDay",ReadingStatsData.day(System.currentTimeMillis()));showReadingStats();return;}
        if (state!=null&&externalFile==null) {
            pendingReplaceId=state.getString("pendingReplaceId");pendingEncoding=state.getString("pendingEncoding");pendingCoverId=state.getString("pendingCoverId");pendingExportBookId=state.getString("pendingExportBookId");pendingEpubBookId=state.getString("pendingEpubBookId");
            String id=state.getString("book");
            for (LibraryStore.Book b:store.books) if (b.id.equals(id)) { openBook(b); break; }
        }
    }
    boolean isNight(){return palette==2;}
    /** Shelf chrome and dialog darkness from the appearance setting (0 follow system, 1 light, 2 dark). */
    boolean uiDark(){int pref=prefs==null?0:prefs.getInt("appearance",0);return ReaderPolicy.darkAppearance(pref,(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES,palette==2);}
    private Context uiContext(){return themedContext(uiDark());}
    private Context themedContext(boolean dark){
        String name=dark?"SnowDialogDark":"SnowDialogLight";
        int style=getResources().getIdentifier(name,"style",getPackageName());
        return style==0?this:new ContextThemeWrapper(this,style);
    }
    private AppSheet.Builder dialog(){return new AppSheet.Builder(uiContext(),uiDark());}
    private void colors(boolean reading){
        int p=reading?(isNight()?2:palette):(uiDark()?2:0);
        bg=p==2?SnowTheme.NIGHT_BACKGROUND:reading?(p==1?SnowTheme.WARM_PAPER:SnowTheme.PAPER):SnowTheme.BACKGROUND;
        ink=p==2?SnowTheme.NIGHT_INK:p==1?0xFF4B4238:SnowTheme.INK;
        muted=p==2?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED;
        card=p==2?SnowTheme.NIGHT_SURFACE:SnowTheme.SURFACE;accent=p==2?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;
        EdgeToEdge.apply(getWindow(),p==2);
    }
    private void frame(boolean reading) {
        if(shelfDrag!=null){shelfDrag.dispose();shelfDrag=null;}
        colors(reading);getWindow().setBackgroundDrawable(new ColorDrawable(bg));findViewById(android.R.id.content).setBackgroundColor(bg);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        if(reading){root.setBackgroundColor(bg);setContentView(root);}
        else{FrameLayout scene=new FrameLayout(this);scene.setBackgroundColor(bg);AtmosphereView sky=new AtmosphereView(this,uiDark(),false);sky.setTag("shelf-atmosphere");scene.addView(sky,new FrameLayout.LayoutParams(-1,-1));scene.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(scene);}
        final LinearLayout surface=root;EdgeToEdge.listen(surface,reading,(left,top,right,navigation,keyboard)->surface.setPadding(left,top,right,Math.max(navigation,keyboard)));
    }
    private void showLibrary(String query) {
        statsPage=false;statsReturnBook="";
        if(shelfDrag!=null){shelfDrag.dispose();shelfDrag=null;}
        boolean listMode=!selecting&&prefs!=null&&prefs.getBoolean("shelfList",false);
        if(readerFooter!=null)readerFooter.resumed(false);readerFooter=null;readerHeader=null;
        chrome.leave();chromeHandler.removeCallbacks(chromeTimeout);readerTopBar=null;readerBottomBar=null;readerStage=null;shelfQuery=query;
        if(readingStats!=null)readingStats.pulse(false,lastReadingInteraction);active=null;document=null;readerText="";searchSession=null;reader=null;pagedReader=null;navigation.clear();previewTypeset=null;previewNightMode=null;brightnessGesture.reset();applyTypeset();palette=effectivePalette();appearanceDark=uiDark();
        if(!ShelfOrganization.ALL.equals(shelfCategory)&&!ShelfOrganization.PINNED.equals(shelfCategory)&&!ShelfOrganization.NONE.equals(shelfCategory)&&!store.organization.categories.containsKey(shelfCategory))shelfCategory=ShelfOrganization.ALL;
        HashSet<String> available=new HashSet<>();for(LibraryStore.Book b:store.books)available.add(b.id);selectedBooks.retainAll(available);
        frame(false);applyReaderWindow();selectionTitle=null;batchPin=null;batchDelete=null;
        LinearLayout header=column();header.setPadding(dp(20),dp(12),dp(16),dp(8));
        LinearLayout brand=row();GlowFrame markFrame=new GlowFrame(this,9);markFrame.setTag("shelf-logo");shelfGlow(markFrame,9,.30f);
        ImageView mark=new ImageView(this);mark.setImageResource(getResources().getIdentifier("ic_leaf","drawable",getPackageName()));mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);markFrame.addView(mark,new FrameLayout.LayoutParams(-1,-1));brand.addView(markFrame,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout words=column();words.setPadding(dp(9),0,dp(4),0);
        TextView name=text("融雪阅读",20,ink);name.setMaxLines(2);name.setTypeface(Typeface.create("sans-serif-medium",0));words.addView(name);
        TextView tagline=text("雪渐融，故事正生长。",11,muted);tagline.setMaxLines(2);words.addView(tagline);brand.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        View searchButton=iconButton("搜索书名","ic_search",this::toggleShelfSearch);searchButton.setTag("shelf-search-toggle");brand.addView(searchButton,new LinearLayout.LayoutParams(dp(44),dp(48)));
        if(selecting)brand.addView(button("完成",()->{selecting=false;selectedBooks.clear();showLibrary(shelfQuery);}),new LinearLayout.LayoutParams(dp(56),dp(48)));
        else{View add=iconButton("导入书籍","ic_add",()->chooseFile(null));add.setTag("shelf-import");brand.addView(add,new LinearLayout.LayoutParams(dp(44),dp(48)));View viewToggle=iconButton(listMode?"切换为网格视图":"切换为列表视图","ic_toc",()->{prefs.edit().putBoolean("shelfList",!listMode).apply();showLibrary(shelfQuery);});viewToggle.setTag("shelf-view-toggle");brand.addView(viewToggle,new LinearLayout.LayoutParams(dp(44),dp(48)));brand.addView(iconButton("书架更多操作","ic_more",this::shelfMenu),new LinearLayout.LayoutParams(dp(44),dp(48)));}
        header.addView(brand);
        if(selecting){LinearLayout selection=row();selectionTitle=text("",16,ink);selection.addView(selectionTitle,new LinearLayout.LayoutParams(0,-2,1));selection.addView(button("全选",()->{for(LibraryStore.Book b:shelfAdapter.visible)selectedBooks.add(b.id);refreshSelection();}),new LinearLayout.LayoutParams(dp(64),dp(44)));header.addView(selection);}
        root.addView(header);
        if(!brandLightShown){brandLightShown=true;markFrame.pulse();}

        LinearLayout categoryRow=row();categoryRow.setPadding(dp(12),0,dp(12),0);
        HorizontalScrollView categories=new HorizontalScrollView(this);categories.setHorizontalScrollBarEnabled(false);categories.setTag("shelf-categories");
        if(selecting)shelfDrag=new ShelfCategoryDrag(this,root,categories,uiDark(),this::dropSelectedBooks);
        LinearLayout chips=row();chips.setPadding(dp(4),0,dp(4),dp(4));
        addCategoryChip(chips,ShelfOrganization.ALL,"全部");addCategoryChip(chips,ShelfOrganization.PINNED,"置顶");
        if(selecting||ShelfOrganization.NONE.equals(shelfCategory))addCategoryChip(chips,ShelfOrganization.NONE,"未分类");
        int shown=0;for(Map.Entry<String,String> category:store.organization.categories.entrySet())if(selecting||shown++<4||category.getKey().equals(shelfCategory)||category.getKey().equals(pendingCategoryGlow))addCategoryChip(chips,category.getKey(),category.getValue());
        Button categoryMenu=button(selecting?"＋ 分类":"更多分类 ▾",()->{if(selecting)editCategory(null);else categoryFilter();});categoryMenu.setTextSize(12);categoryMenu.setStateListAnimator(null);categoryMenu.setElevation(0);chips.addView(categoryMenu,new LinearLayout.LayoutParams(-2,dp(48)));
        categories.addView(chips);categoryRow.addView(categories,new LinearLayout.LayoutParams(0,-2,1));
        if(!selecting){Button select=button("多选",()->{selecting=true;selectedBooks.clear();hideShelfKeyboard();showLibrary(shelfQuery);});select.setTag("shelf-select");select.setTextSize(13);select.setStateListAnimator(null);select.setElevation(0);categoryRow.addView(select,new LinearLayout.LayoutParams(dp(56),dp(48)));}
        root.addView(categoryRow);
        for(int i=0;i<chips.getChildCount();i++)if(shelfCategory.equals(chips.getChildAt(i).getTag())){final View target=chips.getChildAt(i);categories.post(()->categories.scrollTo(Math.max(0,target.getLeft()-dp(12)),0));break;}
        if(pendingCategoryGlow!=null){View target=chips.findViewWithTag(pendingCategoryGlow);pendingCategoryGlow=null;if(target!=null)target.post(()->{categories.scrollTo(Math.max(0,target.getLeft()-(categories.getWidth()-target.getWidth())/2),0);Motion.flash(target);SnowBurst.play(target,uiDark());});}

        LinearLayout searchRow=row();searchRow.setPadding(dp(12),0,dp(6),0);searchRow.setBackground(shape(card,16));searchRow.setTag("shelf-search-row");
        EditText filter=new EditText(uiContext());shelfSearch=filter;filter.setTag("shelf-search");filter.setSingleLine(true);filter.setTextSize(14);filter.setTextColor(ink);filter.setHintTextColor(muted);filter.setHint("搜索书名");filter.setPadding(dp(4),0,dp(4),0);filter.setBackgroundColor(Color.TRANSPARENT);filter.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);searchRow.addView(filter,new LinearLayout.LayoutParams(0,dp(48),1));
        searchRow.addView(button("收起",this::closeShelfSearch),new LinearLayout.LayoutParams(dp(52),dp(48)));
        shelfSearchOpen=shelfSearchOpen||!query.isEmpty();searchRow.setVisibility(shelfSearchOpen?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.setMargins(dp(20),dp(4),dp(20),dp(8));root.addView(searchRow,fp);
        filter.setOnEditorActionListener((view,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){hideShelfKeyboard();return true;}return false;});

        FrameLayout stage=new FrameLayout(this);stage.setClipChildren(true);
        ShelfGridView shelf=new ShelfGridView(this,uiDark());shelf.setTag("shelf-grid");if(listMode){shelf.setNumColumns(1);shelf.setColumnWidth(dp(600));shelf.setVerticalSpacing(dp(4));}else{shelf.setNumColumns(GridView.AUTO_FIT);shelf.setColumnWidth(dp(getResources().getConfiguration().fontScale>1.15f?134:104));shelf.setVerticalSpacing(dp(14));}shelf.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);shelf.setHorizontalSpacing(dp(12));shelf.setPadding(dp(20),dp(4),dp(20),dp(18));shelf.setClipToPadding(false);shelf.setSelector(new ColorDrawable(Color.TRANSPARENT));
        shelfAdapter=new ShelfAdapter(listMode);shelf.setAdapter(shelfAdapter);stage.addView(shelf,new FrameLayout.LayoutParams(-1,-1));
        LibraryStore.Book last=store.find(prefs.getString("lastBook",""));if(last==null)for(LibraryStore.Book b:store.books)if(b.offset>0&&(last==null||b.opened>last.opened))last=b;
        final View resume=!selecting&&last!=null?resumeCard(last):null;
        if(resume!=null){FrameLayout wrap=new FrameLayout(this);wrap.setTag("shelf-resume-wrap");wrap.setPadding(dp(20),dp(4),dp(20),dp(12));wrap.addView(resume,new FrameLayout.LayoutParams(-1,-2));stage.addView(wrap,new FrameLayout.LayoutParams(-1,-2));wrap.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));shelf.setPadding(dp(20),wrap.getMeasuredHeight()+dp(4),dp(20),dp(18));
            wrap.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->wrap.post(()->{if(!shelfSearchOpen&&shelfQuery.isEmpty()&&wrap.getVisibility()!=View.GONE){int top=Math.max(wrap.getHeight(),wrap.getMeasuredHeight())+dp(4);if(shelf.getPaddingTop()!=top){shelf.setPadding(dp(20),top,dp(20),dp(18));shelf.setSelection(0);}}}));
            shelf.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){}public void onScroll(AbsListView view,int first,int visible,int total){if(wrap.getVisibility()==View.GONE)return;View child=shelf.getChildAt(0);float shift=first==0&&child!=null?child.getTop()-shelf.getPaddingTop():-wrap.getHeight();wrap.setTranslationY(Math.max(-wrap.getHeight(),Math.min(0,shift)));wrap.setVisibility(shift<=-wrap.getHeight()&&wrap.getHeight()>0?View.INVISIBLE:View.VISIBLE);}});
        }
        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(28),dp(16),dp(28),dp(16));TextView emptyTitle=text("",20,ink);emptyTitle.setGravity(Gravity.CENTER);empty.addView(emptyTitle);
        TextView emptyHint=text("",13,muted);emptyHint.setGravity(Gravity.CENTER);emptyHint.setLineSpacing(dp(5),1);emptyHint.setPadding(0,dp(12),0,dp(12));empty.addView(emptyHint);
        Button firstImport=button("＋ 导入第一本书",()->chooseFile(null));firstImport.setTextColor(Color.WHITE);firstImport.setBackground(SnowTheme.ripple(SnowTheme.ACCENT_DARK,dp(16),0x30FFFFFF));empty.addView(firstImport,new LinearLayout.LayoutParams(-1,dp(52)));
        Button sample=button("先读一段示例 →",this::importSample);empty.addView(sample);stage.addView(empty,new FrameLayout.LayoutParams(-1,-1));shelf.setEmptyView(empty);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        shelf.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){pressX=event.getX();pressY=event.getY();int at=shelf.pointToPosition((int)pressX,(int)pressY);pressedTile=at<0?null:shelf.getChildAt(at-shelf.getFirstVisiblePosition());if(pressedTile instanceof ShelfTile)((ShelfTile)pressedTile).lift(true);}else if(action==MotionEvent.ACTION_MOVE&&Math.hypot(event.getX()-pressX,event.getY()-pressY)>ViewConfiguration.get(this).getScaledTouchSlop()){if(pressedTile instanceof ShelfTile)((ShelfTile)pressedTile).lift(false);pressedTile=null;}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(pressedTile instanceof ShelfTile)((ShelfTile)pressedTile).lift(false);pressedTile=null;}return false;});
        shelf.setOnItemClickListener((parent,v,p,id)->{LibraryStore.Book book=shelfAdapter.visible.get(p);if(selecting){if(!selectedBooks.remove(book.id))selectedBooks.add(book.id);refreshSelection();}else openBook(book);});
        shelf.setOnItemLongClickListener((parent,v,p,id)->{if(busy)return true;LibraryStore.Book book=shelfAdapter.visible.get(p);if(selecting){if(store.organization.categories.isEmpty()){toast("请先点击“＋ 分类”创建一个分类");return true;}selectedBooks.add(book.id);refreshSelection();if(pressedTile instanceof ShelfTile)((ShelfTile)pressedTile).lift(false);pressedTile=null;if(shelfDrag==null||!shelfDrag.start(v,selectedBooks))toast("暂时无法拖动，可使用底部“分类”按钮");}else bookOptions(book);return true;});
        Runnable render=()->{
            shelfQuery=filter.getText().toString();shelf.smoothScrollBy(0,0);shelfAdapter.visible=store.organization.visible(store.books,shelfCategory,shelfQuery);
            shelfAdapter.visible=ShelfOrganization.filterProgress(shelfAdapter.visible,Math.max(0,Math.min(3,prefs.getInt("shelfShow",0))),b->(float)ratio((LibraryStore.Book)b));
            shelfAdapter.visible=ShelfOrganization.arrange(shelfAdapter.visible,Math.max(0,Math.min(3,prefs.getInt("shelfSort",0))));
            if(resume!=null){View wrap=(View)resume.getParent();boolean show=!shelfSearchOpen&&shelfQuery.isEmpty();wrap.setVisibility(show?View.VISIBLE:View.GONE);int top=show?Math.max(wrap.getHeight(),wrap.getMeasuredHeight())+dp(4):dp(4);if(shelf.getPaddingTop()!=top){shelf.setPadding(dp(20),top,dp(20),dp(18));shelf.setSelection(0);}}
            boolean first=store.books.isEmpty();emptyTitle.setText(first?"书架，等一本好书":"这里还没有书");emptyHint.setText(first?"导入手机里的 TXT / EPUB 书籍\n下次打开，接着上次的故事读":!shelfQuery.trim().isEmpty()?"试试更短的书名，或切换分类查找":"可切换分类，或从顶部 ＋ 导入书籍");firstImport.setVisibility(first?View.VISIBLE:View.GONE);sample.setVisibility(first?View.VISIBLE:View.GONE);refreshSelection();
        };
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){render.run();}});filter.setText(query);render.run();
        LinearLayout footer=column();footer.setPadding(dp(20),0,dp(20),0);if(listenUi!=null)footer.addView(listenUi.miniBar());
        if(selecting){LinearLayout actions=row();batchPin=button("置顶",()->{ArrayList<LibraryStore.Book> books=selected();if(books.isEmpty())return;boolean all=true;for(LibraryStore.Book b:books)all&=b.pinned;final boolean pin=!all;organize(()->store.organization.pin(books,pin),true);});Button classify=button("分类",()->{ArrayList<LibraryStore.Book> books=selected();if(books.isEmpty())toast("请先选择书籍");else chooseCategory(books);});Button exportZip=button("导出",()->{ArrayList<LibraryStore.Book> books=selected();if(books.isEmpty())toast("请先选择书籍");else exportSelectedZip(books);});batchDelete=button("删除",()->confirmDeleteSelected());batchDelete.setTag("shelf-delete");batchDelete.setTextColor(uiDark()?0xFFFFB9AB:0xFF9A493E);for(Button button:new Button[]{batchPin,classify,exportZip,batchDelete}){button.setBackground(SnowTheme.ripple(card,dp(14),0x20248FA9));button.setStateListAnimator(null);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(4),dp(4),dp(4),dp(6));actions.addView(button,p);}footer.addView(actions);
            TextView note=text("长按已选书籍，拖到顶部分类",11,muted);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(4),0,dp(8));note.setVisibility(prefs.getBoolean("shelfDragTipSeen",false)?View.GONE:View.VISIBLE);footer.addView(note);if(shelfDrag!=null)shelfDrag.hint(note);
        }root.addView(footer);refreshSelection();root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void toggleShelfSearch(){if(busy)return;if(shelfSearchOpen){closeShelfSearch();return;}shelfSearchOpen=true;if(shelfSearch==null)return;View row=(View)shelfSearch.getParent();row.setVisibility(View.VISIBLE);Motion.enter(row,Motion.CONTENT_NUDGE);View resume=root.findViewWithTag("shelf-resume-wrap");if(resume!=null){resume.setVisibility(View.GONE);GridView grid=(GridView)root.findViewWithTag("shelf-grid");grid.smoothScrollBy(0,0);grid.setPadding(dp(20),dp(4),dp(20),dp(18));grid.setSelection(0);}shelfSearch.requestFocus();shelfSearch.post(()->((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(shelfSearch,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT));}
    private void closeShelfSearch(){shelfSearchOpen=false;hideShelfKeyboard();if(shelfSearch!=null){shelfSearch.clearFocus();View row=(View)shelfSearch.getParent();Motion.exit(row,-Motion.CONTENT_NUDGE,Motion.FAST,()->{if(!shelfSearchOpen&&active==null&&!isDestroyed())showLibrary("");});}}
    private void hideShelfKeyboard(){if(root!=null)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private void shelfMenu(){if(busy)return;dialog().setSnowflakes(true).setTitle("书架 · "+store.books.size()+" 本").setItems(new String[]{"多选书籍","分类管理","排序与筛选","数据与备份","氛围预览","外观模式","阅读统计"},(d,w)->{if(w==0){selecting=true;selectedBooks.clear();showLibrary(shelfQuery);}else if(w==1)manageCategories();else if(w==2)shelfArrange();else if(w==3)dataOptions();else if(w==4)atmospherePreview();else if(w==5)appearanceSettings();else showReadingStats();}).setItemHints(new String[]{"批量置顶、分类或移出所选书籍","新建、重命名或删除分类","按最近阅读或书名排序，筛选在读、未读或已读","导出、恢复、批量导入与回收站","手选天气与本地时间光线","浅色 / 深色 / 跟随系统","阅读日历、每日明细与按书时长"}).show();}
    private void shelfArrange(){
        LinearLayout panel=column();
        panel.addView(sectionLabel("排序方式"));
        ChoiceStrip sort=new ChoiceStrip(uiContext(),new String[]{"智能（置顶优先）","最近阅读","最近导入","书名"},Math.max(0,Math.min(3,prefs.getInt("shelfSort",0))),uiDark());sort.setTag("shelf-sort");panel.addView(sort);
        panel.addView(sectionLabel("只看"));
        ChoiceStrip show=new ChoiceStrip(uiContext(),new String[]{"全部","在读","未读","已读"},Math.max(0,Math.min(3,prefs.getInt("shelfShow",0))),uiDark());show.setTag("shelf-show");panel.addView(show);
        TextView hint=text("排序和筛选只改变当前显示，不影响书籍数据；分类与置顶的规则仍然生效。",12,muted);hint.setPadding(0,dp(12),0,dp(4));panel.addView(hint);
        Runnable apply=()->{prefs.edit().putInt("shelfSort",sort.selected()).putInt("shelfShow",show.selected()).apply();showLibrary(shelfQuery);};
        sort.onChanged(apply);show.onChanged(apply);
        dialog().setSnowflakes(true).setTitle("排序与筛选").setView(panel).show();
    }
    private String shelfProgress(LibraryStore.Book b){float r=(float)ratio(b);int state=ReadingStatus.of(b,r);return state==ReadingStatus.UNREAD?"未读":state==ReadingStatus.FINISHED?"已读":String.format(Locale.CHINA,"在读 · %.0f%%",r*100);}
    private void chooseCustomFont(String currentId,FontChoice choice){
        if(busy){toast("正在处理，请稍候");return;}
        ArrayList<String> ids=FontLibrary.ids(this);ArrayList<String> labels=new ArrayList<>();labels.add("导入字体文件…");labels.add("使用上方所选内置字体");for(String id:ids)labels.add(FontLibrary.name(this,id));
        dialog().setTitle("自定义字体").setItems(labels.toArray(new String[0]),(d,w)->{if(w==0){pendingFontChoice=choice;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_LOCAL_ONLY,true);launchPicker(pick,PICK_FONT);}else {String id=w==1?"":ids.get(w-2);io.execute(()->{Typeset selected=new Typeset();selected.customFont=id;FontLibrary.face(getApplicationContext(),selected);runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())choice.choose(id);});});}}).setNeutralButton("移除字体文件",(d,w)->manageCustomFonts(currentId,choice)).show();
    }
    private void manageCustomFonts(String currentId,FontChoice choice){
        ArrayList<String> ids=FontLibrary.ids(this);if(ids.isEmpty()){toast("还没有导入字体");return;}String[] names=new String[ids.size()];for(int i=0;i<ids.size();i++)names[i]=FontLibrary.name(this,ids.get(i));
        dialog().setTitle("移除字体文件").setItems(names,(d,w)->dialog().setTitle("移除这款字体？").setMessage("引用它的书籍会回退到内置字形，阅读记录保留。当前选用此字体时会自动切回内置字形。").setPositiveButton("移除",(a,b)->{io.execute(()->{try{FontLibrary.remove(getApplicationContext(),ids.get(w));runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){if(ids.get(w).equals(currentId))choice.choose("");toast("字体文件已移除");}});}catch(Exception e){runOnUiThread(()->error("移除失败",e));}});}).show()).show();
    }
    private void finishFontImport(int result,Intent data){
        final FontChoice choice=pendingFontChoice;pendingFontChoice=null;if(result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();if(busy)return;busy=true;toast("正在导入字体…");
        io.execute(()->{try{String id=FontLibrary.importFont(getApplicationContext(),uri,displayName(uri));runOnUiThread(()->{busy=false;if(isDestroyed()||isFinishing())return;if(choice!=null)choice.choose(id);toast("字体已导入");});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed())error("字体无法导入，原设置保留",e);});}});
    }
    private void statsPulse(){
        if(readingStats==null)readingStats=new ReadingStats(this);
        boolean listening=listenOwns()&&listenUi!=null&&listenUi.snapshot()!=null&&(listenUi.snapshot().playing()||listenUi.snapshot().state==SpeechQueue.State.PREPARING);
        readingStats.pulse(statsResumed&&hasWindowFocus()&&active!=null&&document!=null&&!busy&&!listening&&!statsPage,lastReadingInteraction,active==null?"":active.id);
    }
    @Override public void onUserInteraction(){super.onUserInteraction();statsPulse();lastReadingInteraction=SystemClock.elapsedRealtime();statsPulse();}
    private void showReadingStats(){
        if(busy)return;
        if(!statsPage){savePosition();statsPulse();statsReturnBook=active==null?"":active.id;readingStats.pulse(false,lastReadingInteraction);statsSelectedDay=ReadingStatsData.day(System.currentTimeMillis());}
        statsPage=true;if(readerFooter!=null)readerFooter.resumed(false);readerFooter=null;chrome.leave();chromeHandler.removeCallbacks(chromeTimeout);
        active=null;document=null;readerText="";reader=null;pagedReader=null;readerAdapter=null;applyTypeset();palette=effectivePalette();appearanceDark=uiDark();frame(false);
        LinearLayout top=row();top.setPadding(dp(12),dp(8),dp(12),dp(8));top.addView(button("返回",()->leaveStats()));TextView title=text("阅读统计",21,ink);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));top.addView(button("今天",()->{statsSelectedDay=ReadingStatsData.day(System.currentTimeMillis());showReadingStats();}));root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout panel=column();panel.setPadding(dp(20),dp(10),dp(20),dp(28));scroll.addView(panel);
        panel.addView(text("雪渐融，故事留下足迹。",13,muted));
        LinearLayout totals=row();totals.setPadding(0,dp(18),0,dp(18));String[] labels={"今日","本周","累计"};long[] values={readingStats.today(),readingStats.week(),readingStats.total()};for(int i=0;i<3;i++){LinearLayout tile=column();tile.setPadding(dp(8),dp(10),dp(8),dp(10));tile.addView(text(labels[i],12,muted));tile.addView(text(ReadingStats.duration(values[i]),15,ink));totals.addView(tile,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(totals);
        panel.addView(sectionLabel("阅读日历 · 近16周"));
        final LinearLayout detail=column();final Button date=button(statsSelectedDay+"  ▾",()->{});final ReadingHeatmap[] calendar={null};
        java.util.List<String> days=ReadingStatsData.days(16,System.currentTimeMillis());
        ReadingHeatmap.Selected choose=value->{statsSelectedDay=value;date.setText(value+"  ▾");calendar[0].select(value);showStatsDay(detail,value);};
        calendar[0]=new ReadingHeatmap(uiContext(),readingStats,days,statsSelectedDay,uiDark(),choose::select);calendar[0].setTag("reading-stats-heatmap");panel.addView(calendar[0],new LinearLayout.LayoutParams(-1,dp(196)));
        date.setTag("reading-stats-date");date.setOnClickListener(v->{ArrayList<String> options=new ArrayList<>();String today=ReadingStatsData.day(System.currentTimeMillis());for(int i=days.size()-1;i>=0;i--)if(days.get(i).compareTo(today)<=0)options.add(days.get(i));dialog().setTitle("选择统计日期").setItems(options.toArray(new String[0]),(d,w)->choose.select(options.get(w))).show();});panel.addView(date);panel.addView(detail);showStatsDay(detail,statsSelectedDay);
        ToggleRow enabled=new ToggleRow(uiContext(),"记录本地阅读时长",readingStats.enabled(),uiDark());enabled.setTag("reading-stats-enabled");enabled.onChanged(()->readingStats.enabled(enabled.checked()));panel.addView(enabled);
        panel.addView(text("仅记录前台正文阅读。后台、听书和90秒无操作后的停留不累计；每15秒采样，时长为近似值。按书明细从新版开始记录，旧每日总时长保留。仅存本机，不随书库备份。",12,muted));
        panel.addView(button("清空统计记录",()->dialog().setTitle("清空阅读统计？").setMessage("仅清除本机统计，书籍、进度、笔记和书签不受影响。").setPositiveButton("清空",(d,w)->{readingStats.clear();showReadingStats();}).setNegativeButton("取消",null).show()));applyReaderWindow();
    }
    private void showStatsDay(LinearLayout panel,String day){
        panel.removeAllViews();panel.addView(sectionLabel(day+" · 阅读明细"));panel.addView(text(ReadingStats.duration(readingStats.day(day)),24,ink));
        long first=readingStats.first(day),last=readingStats.last(day);if(first>0&&last>=first){java.text.DateFormat time=android.text.format.DateFormat.getTimeFormat(this);panel.addView(text("首次 "+time.format(new Date(first))+" · 最近 "+time.format(new Date(last)),12,muted));}
        ArrayList<Map.Entry<String,Long>> books=new ArrayList<>(readingStats.books(day).entrySet());Collections.sort(books,(a,b)->Long.compare(b.getValue(),a.getValue()));long known=0;
        for(Map.Entry<String,Long> record:books){LibraryStore.Book book=store.find(record.getKey());LinearLayout line=row();line.setPadding(0,dp(12),0,dp(12));TextView name=text(book==null?"已移出书架的书籍":book.title,14,ink);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));line.addView(text(ReadingStats.duration(record.getValue()),13,muted));panel.addView(line);known+=record.getValue();}
        long unknown=Math.max(0,readingStats.day(day)-known);if(unknown>0)panel.addView(text("未按书归属的历史时长 · "+ReadingStats.duration(unknown),12,muted));if(readingStats.day(day)==0)panel.addView(text("这一天还没有阅读记录。",14,muted));
    }
    private void leaveStats(){String id=statsReturnBook;statsPage=false;statsReturnBook="";LibraryStore.Book book=store.find(id);if(book!=null)openBook(book);else showLibrary(shelfQuery);}
    private void appearanceSettings(){
        final AppSheet[] appearanceSheet={null};
        LinearLayout panel=column();
        ChoiceStrip mode=new ChoiceStrip(uiContext(),new String[]{"跟随系统","浅色","深色"},Math.max(0,Math.min(2,prefs.getInt("appearance",0))),uiDark());mode.setTag("appearance-mode");panel.addView(mode);
        TextView hint=text("跟随系统时，主页与弹窗颜色随手机深色模式变化。深色会覆盖氛围中的光线时段（一律按夜间渲染），不改变天气选择；阅读页主题仍由排版设置控制。",12,muted);hint.setPadding(0,dp(12),0,dp(4));panel.addView(hint);
        mode.onChanged(()->{
            final int selected=mode.selected();if(selected==prefs.getInt("appearance",0))return;
            // A themed Context and custom controls capture colors at construction. Recreate
            // this panel after its window closes, together with the shelf's new palette.
            appearanceSheet[0].dismissThen(()->{
                prefs.edit().putInt("appearance",selected).apply();
                if(active==null)showLibrary(shelfQuery);
                appearanceDark=uiDark();AtmosphereView.refreshAll();appearanceSettings();
            });
        });
        appearanceSheet[0]=dialog().setTitle("外观模式").setView(panel).show();
    }
    private void atmospherePreview(){
        android.content.SharedPreferences settings=getSharedPreferences("atmosphere",0);
        LinearLayout panel=column();
        ToggleRow enabled=new ToggleRow(uiContext(),"主界面氛围效果",AtmosphereView.enabled(this),uiDark());enabled.setTag("atmosphere-enabled");panel.addView(enabled);
        panel.addView(sectionLabel("天气氛围 · 手动选择"));
        ChoiceStrip weather=new ChoiceStrip(uiContext(),new String[]{"晴","阴","雨","雪"},AtmosphereView.manual(this),uiDark());weather.setTag("atmosphere-weather");panel.addView(weather);
        panel.addView(sectionLabel("光线 · 随时间或手动预览"));
        ChoiceStrip time=new ChoiceStrip(uiContext(),new String[]{"随时间","清晨","白昼","黄昏","夜间"},Math.max(0,Math.min(4,settings.getInt("time",0))),uiDark());time.setTag("atmosphere-time");panel.addView(time);
        // Day-cycle strip: the four lighting stops with a marker on the active period, plus a one-line caption.
        final int[] lightStops={0xFFF2D4A0,0xFFE8D9B1,0xFFE4B191,0xFF395379};
        final String[] lightNotes={"清晨 · 薄光微暖，纸面染一层杏色","白昼 · 中性亮光，封面与文字颜色最准","黄昏 · 斜阳偏橙，暖意渐深","夜间 · 青灰低光，最安静护眼"};
        final boolean stripDark=uiDark();
        TextView lightNote=text("",12,muted);lightNote.setPadding(0,dp(8),0,0);panel.addView(lightNote);
        View lightTimeline=new View(uiContext()){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.Shader ramp;
            @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);ramp=w>0?new android.graphics.LinearGradient(0,0,w,0,lightStops,new float[]{0,.34f,.67f,1},android.graphics.Shader.TileMode.CLAMP):null;invalidate();}
            @Override protected void onDraw(Canvas c){
                if(ramp==null)return;float h=getHeight();
                paint.setShader(ramp);c.drawRoundRect(0,h*.30f,getWidth(),h*.70f,h*.20f,h*.20f,paint);paint.setShader(null);
                int active=time.selected()==0?AtmosphereView.period(MainActivity.this):time.selected()-1;
                float cx=getWidth()*(active+.5f)/4f,cy=h/2f;
                paint.setStyle(Paint.Style.FILL);paint.setColor(stripDark?SnowTheme.NIGHT_INK:Color.WHITE);c.drawCircle(cx,cy,h*.26f,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(stripDark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK);c.drawCircle(cx,cy,h*.26f,paint);
                paint.setStyle(Paint.Style.FILL);
            }
        };
        lightTimeline.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,dp(22));tl.topMargin=dp(10);tl.leftMargin=dp(6);tl.rightMargin=dp(6);panel.addView(lightTimeline,tl);
        Runnable lightRefresh=()->{lightNote.setText(lightNotes[Math.max(0,Math.min(3,activeLightPeriod(settings)))]);lightTimeline.invalidate();};
        FrameLayout preview=new FrameLayout(uiContext());preview.setBackground(SnowTheme.rounded(bg,dp(18),uiDark()?0xFF294853:SnowTheme.BORDER,dp(1)));preview.setClipToOutline(true);
        AtmosphereView sky=new AtmosphereView(uiContext(),uiDark(),false);sky.setTag("atmosphere-demo");preview.addView(sky,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout words=column();words.setGravity(Gravity.CENTER);words.addView(text("雪渐融，故事正生长。",17,ink));TextView status=text("",12,muted);words.addView(status);preview.addView(words,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(180));pp.topMargin=dp(16);panel.addView(preview,pp);
        TextView hint=text("天气由你手动选择，光线可随本机时间变化，不联网、不推测位置。氛围只影响书架背景；弹窗顶部的融雪雪花与底部新芽保留，正文保持安静。",12,muted);hint.setPadding(0,dp(12),0,dp(8));panel.addView(hint);
        Runnable update=()->{
            settings.edit().putBoolean("enabled",enabled.checked()).putInt("weather",weather.selected()).putInt("time",time.selected()).apply();
            AtmosphereView.refreshAll();
            status.setText(!enabled.checked()?"氛围已关闭":AtmosphereView.label(this,uiDark())+(uiDark()?" · 深色覆盖光线":"")+(Motion.enabled(this)?"":" · 已减少动态效果"));
            lightRefresh.run();

        };
        enabled.onChanged(update);weather.onChanged(update);time.onChanged(update);update.run();
        dialog().setTitle("氛围预览").setContentFraction(.62f).setView(panel).show();
    }
    private int activeLightPeriod(android.content.SharedPreferences settings){int sel=Math.max(0,Math.min(4,settings.getInt("time",0)));return sel==0?AtmosphereView.period(this):sel-1;}
    private void clearLegacyPrivacy(){android.content.SharedPreferences.Editor weather=getSharedPreferences("atmosphere",0).edit();for(String key:PrivacyPolicy.WEATHER_CACHE)weather.remove(key);weather.apply();getSharedPreferences("listen",0).edit().remove("allowNetworkVoice").apply();}
    /** Global preferences snapshot embedded in backups: reader, atmosphere and listen settings. */
    private byte[] captureSettingsBytes(){
        try{
            org.json.JSONObject root=new org.json.JSONObject();
            for(String name:new String[]{"reader","atmosphere","listen"}){
                org.json.JSONObject group=new org.json.JSONObject();
                for(Map.Entry<String,?> entry:getSharedPreferences(name,MODE_PRIVATE).getAll().entrySet()){
                    if(!PrivacyPolicy.keepSetting(name,entry.getKey()))continue;Object value=entry.getValue();
                    try{
                        if(value instanceof Integer)group.put(entry.getKey(),(Integer)value);else if(value instanceof Long)group.put(entry.getKey(),(Long)value);
                        else if(value instanceof Boolean)group.put(entry.getKey(),(Boolean)value);else if(value instanceof Float)group.put(entry.getKey(),(Float)value);
                        else if(value instanceof String)group.put(entry.getKey(),(String)value);else if(value instanceof Set)group.put(entry.getKey(),new org.json.JSONArray((java.util.Set<String>)value));
                    }catch(Exception ignored){}
                }
                root.put(name,group);
            }
            return root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }catch(Exception e){return null;}
    }
    private void applySettings(org.json.JSONObject root){
        try{
            for(String name:new String[]{"reader","atmosphere","listen"}){
                org.json.JSONObject group=root.optJSONObject(name);if(group==null)continue;
                android.content.SharedPreferences.Editor editor=getSharedPreferences(name,MODE_PRIVATE).edit();
                java.util.Iterator<String> keys=group.keys();
                while(keys.hasNext()){String key=keys.next();if(!PrivacyPolicy.keepSetting(name,key))continue;Object value=group.opt(key);
                    if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
                    else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);
                    else if(value instanceof String)editor.putString(key,(String)value);}
                editor.apply();
            }
            clearLegacyPrivacy();Motion.invalidateCache(this);AtmosphereView.refreshAll();applyTypeset();palette=effectivePalette();appearanceDark=uiDark();manualPalette=prefs.getInt("palette",0);
        }catch(Exception ignored){}
    }
    private void categoryFilter(){
        ArrayList<String> ids=new ArrayList<>(Arrays.asList(ShelfOrganization.ALL,ShelfOrganization.PINNED,ShelfOrganization.NONE));ids.addAll(store.organization.categories.keySet());String[] labels=new String[ids.size()];for(int i=0;i<ids.size();i++)labels[i]=store.organization.label(ids.get(i))+" · "+store.organization.visible(store.books,ids.get(i),"").size()+" 本";
        final AppSheet[] sheet={null};LinearLayout tools=row();tools.addView(button("管理分类",()->{sheet[0].dismissThen(()->manageCategories());}),new LinearLayout.LayoutParams(0,dp(48),1));tools.addView(button("＋ 新建分类",()->{sheet[0].dismissThen(()->editCategory(null));}),new LinearLayout.LayoutParams(0,dp(48),1));
        sheet[0]=dialog().setTitle("全部分类").setHeaderView(tools).setItems(labels,(d,w)->{shelfCategory=ids.get(w);showLibrary(shelfQuery);}).show();
    }
    private void shelfGlow(View view,float radius,float strength){
        if(!uiDark())return;
        SoftGlowDrawable glow=new SoftGlowDrawable(SnowTheme.NIGHT_ACCENT,getResources().getDisplayMetrics().density,radius);
        glow.strength(strength);view.setForeground(glow);
    }
    private View resumeCard(LibraryStore.Book book){
        GlowFrame cardView=new GlowFrame(this,16);cardView.setTag("shelf-resume");shelfGlow(cardView,16,.22f);cardView.setBackground(shape(card,16));cardView.setFocusable(true);cardView.setClickable(true);cardView.setContentDescription("继续阅读《"+book.title+"》");cardView.setOnClickListener(v->openBook(book));
        LinearLayout content=row();content.setPadding(dp(10),dp(10),dp(12),dp(10));BookCoverView cover=new BookCoverView(this,book.title,book.id.hashCode());cover.setTag(book.id);content.addView(cover,new LinearLayout.LayoutParams(dp(34),dp(48)));bindCover(cover,book);
        LinearLayout text=column();text.setPadding(dp(12),0,dp(8),0);TextView title=text(book.title,15,ink);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);text.addView(title);String chapter=book.id.equals(prefs.getString("lastBook",""))?prefs.getString("lastChapter",""):"继续上次阅读";TextView detail=text(chapter+String.format(Locale.CHINA," · %.1f%%",100*ratio(book)),11,muted);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);detail.setPadding(0,dp(5),0,0);text.addView(detail);content.addView(text,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("继续 ›",12,accent);content.addView(arrow);cardView.addView(content,new FrameLayout.LayoutParams(-1,-2));return cardView;
    }
    private void addCategoryChip(LinearLayout row,String id,String name) {
        boolean checked=id.equals(shelfCategory);int count=store.organization.visible(store.books,id,"").size();
        Button chip=button(name+(checked||selecting?" "+count:""),()->{if(busy)return;shelfCategory=id;showLibrary(shelfQuery);});chip.setTag(id);chip.setTextSize(13);chip.setMaxLines(1);chip.setEllipsize(TextUtils.TruncateAt.END);chip.setMaxWidth(dp(190));chip.setPadding(dp(12),0,dp(12),0);chip.setStateListAnimator(null);chip.setElevation(0);chip.setTextColor(accent);chip.setBackground(SnowTheme.ripple(checked?(uiDark()?SnowTheme.NIGHT_SURFACE:SnowTheme.ACCENT_SOFT):Color.TRANSPARENT,dp(14),0x20248FA9));
        if(checked)shelfGlow(chip,14,.22f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.rightMargin=dp(2);row.addView(chip,p);if(shelfDrag!=null&&store.organization.categories.containsKey(id))shelfDrag.category(chip,id,name);
    }

    private boolean dropSelectedBooks(List<String> ids,String category){
        if(busy||active!=null||!selecting||!store.organization.categories.containsKey(category))return false;
        final LibraryStore library=store;final ArrayList<String> dragged=new ArrayList<>(ids);
        final String name=store.organization.categories.get(category);
        busy=true;
        root.post(()->{
            if(isDestroyed()||isFinishing()){busy=false;return;}
            showLoading("正在将 "+dragged.size()+" 本书加入「"+name+"」…");
            io.execute(()->{
                try{
                    synchronized(library){
                        ArrayList<LibraryStore.Book> books=new ArrayList<>();boolean changed=false;
                        for(String id:dragged){LibraryStore.Book book=library.find(id);if(book==null)throw new IOException("拖动的书籍已不在书架，请重新选择");books.add(book);if(!book.categoryIds.contains(category))changed=true;}
                        if(changed)library.organize(()->library.organization.updateMembership(books,Collections.singleton(category),Collections.emptySet()));
                    }
                    runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed()||isFinishing())return;pendingCategoryGlow=category;showLibrary(shelfQuery);toast("已将 "+dragged.size()+" 本书加入「"+name+"」，原有分类保留");});
                }catch(Exception error){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()&&!isFinishing())error("分类未保存，原有分类保留",error);});}
            });
        });
        return true;
    }
    private void refreshSelection() {
        selectionAnimations.retainAll(selectedBooks);
        if(selectionTitle!=null)selectionTitle.setText(selecting?"已选 "+selectedBooks.size()+" 本":store.organization.label(shelfCategory)+"书架");
        if(batchPin!=null){boolean all=!selectedBooks.isEmpty();for(LibraryStore.Book b:selected())all&=b.pinned;batchPin.setText(all?"取消置顶":"置顶");batchPin.setEnabled(!selectedBooks.isEmpty());}
        if(batchDelete!=null){batchDelete.setEnabled(!selectedBooks.isEmpty()&&!busy);batchDelete.setAlpha(batchDelete.isEnabled()?1:.45f);}
        if(shelfAdapter!=null)shelfAdapter.notifyDataSetChanged();
    }
    private ArrayList<LibraryStore.Book> selected() {
        ArrayList<LibraryStore.Book> books=new ArrayList<>();for(LibraryStore.Book b:store.books)if(selectedBooks.contains(b.id))books.add(b);return books;
    }
    private static final class ShelfHolder {BookCoverView cover;TextView badge,status;ShelfTitleView title;String boundTitle;}
    private class ShelfAdapter extends BaseAdapter {
        final boolean compact;
        ArrayList<LibraryStore.Book> visible=new ArrayList<>();
        ShelfAdapter(boolean compact){this.compact=compact;}
        public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View reused,ViewGroup parent) {
            LibraryStore.Book book=visible.get(p);boolean checked=selecting&&selectedBooks.contains(book.id);
            ShelfTile tile;ShelfHolder holder;
            if(reused instanceof ShelfTile&&reused.getTag() instanceof ShelfHolder){tile=(ShelfTile)reused;holder=(ShelfHolder)tile.getTag();}
            else if(compact){
                tile=new ShelfTile(MainActivity.this,uiDark(),true);holder=new ShelfHolder();tile.setTag(holder);tile.setOrientation(LinearLayout.HORIZONTAL);tile.setPadding(dp(12),dp(8),dp(12),dp(8));tile.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout image=new FrameLayout(MainActivity.this);holder.cover=new BookCoverView(MainActivity.this,book.title,book.id.hashCode());image.addView(holder.cover,new FrameLayout.LayoutParams(-1,-1));tile.addView(image,new LinearLayout.LayoutParams(dp(36),dp(50)));
                holder.badge=text("",12,accent);holder.badge.setGravity(Gravity.CENTER);holder.badge.setPadding(dp(7),dp(4),dp(7),dp(4));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);bp.setMargins(dp(4),dp(3),dp(3),0);image.addView(holder.badge,bp);
                LinearLayout words=column();holder.title=new ShelfTitleView(MainActivity.this);words.addView(holder.title,new LinearLayout.LayoutParams(-1,-2));
                holder.status=text("",11,muted);holder.status.setSingleLine(true);holder.status.setEllipsize(TextUtils.TruncateAt.END);words.addView(holder.status,new LinearLayout.LayoutParams(-1,-2));
                tile.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            }
            else{
                tile=new ShelfTile(MainActivity.this,uiDark());holder=new ShelfHolder();tile.setTag(holder);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(5),dp(5),dp(5),dp(8));
                FrameLayout image=new FrameLayout(MainActivity.this);holder.cover=new BookCoverView(MainActivity.this,book.title,book.id.hashCode());image.addView(holder.cover,new FrameLayout.LayoutParams(-1,-1));tile.addView(image,new LinearLayout.LayoutParams(-1,dp(156)));
                holder.badge=text("",12,accent);holder.badge.setGravity(Gravity.CENTER);holder.badge.setPadding(dp(7),dp(4),dp(7),dp(4));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);bp.setMargins(dp(4),dp(5),dp(5),0);image.addView(holder.badge,bp);
                holder.title=new ShelfTitleView(MainActivity.this);holder.title.setPadding(0,dp(10),0,0);tile.addView(holder.title,new LinearLayout.LayoutParams(-1,dp(48*Math.max(1,getResources().getConfiguration().fontScale))));
                holder.status=text("",11,muted);holder.status.setSingleLine(true);holder.status.setEllipsize(TextUtils.TruncateAt.END);tile.addView(holder.status,new LinearLayout.LayoutParams(-1,dp(24)));
            }
            tile.resetLift();Motion.reset(tile);tile.setBackground(SnowTheme.rounded(checked?(uiDark()?SnowTheme.NIGHT_SURFACE:SnowTheme.ACCENT_SOFT):Color.TRANSPARENT,dp(14),checked?accent:Color.TRANSPARENT,dp(2)));
            if(!book.id.equals(holder.cover.getTag())||!book.title.equals(holder.boundTitle)){
                holder.boundTitle=book.title;holder.cover.bind(book.title,book.id.hashCode());holder.cover.setTag(book.id);bindCover(holder.cover,book);
            }
            Motion.reset(holder.badge);holder.badge.setVisibility(book.pinned||selecting?View.VISIBLE:View.GONE);holder.badge.setText(selecting?(checked?"✓":"○"):"置顶");holder.badge.setTextColor(selecting&&checked?Color.WHITE:accent);holder.badge.setBackground(SnowTheme.rounded(checked?SnowTheme.ACCENT_DARK:card,dp(10),0,0));if(checked&&selectionAnimations.add(book.id))Motion.badge(holder.badge);
            holder.title.setText(book.title.replaceAll("\\s+"," ").trim());holder.title.setTextColor(ink);
            holder.status.setText(shelfProgress(book));holder.status.setTextColor(muted);
            tile.setContentDescription(book.title+(book.pinned?"，置顶":"")+"，"+holder.status.getText()+(selecting?(checked?"，已选":"，未选"):"，长按管理"));
            if(freshBooks.remove(book.id)&&Motion.enabled(MainActivity.this)){final ShelfTile entering=tile;final String id=book.id;tile.setAlpha(0);tile.postDelayed(()->{if(entering.isAttachedToWindow()&&id.equals(((ShelfHolder)entering.getTag()).cover.getTag()))Motion.enter(entering,Motion.CONTENT_NUDGE);},Math.min(p,5)*25L);}return tile;
        }
    }
    private boolean organize(Runnable edit,boolean finishSelection) {
        try {store.organize(edit);if(finishSelection){selectedBooks.clear();selecting=false;}showLibrary(shelfQuery);return true;}
        catch(Exception e){error("没有保存成功",e);return false;}
    }
    private void chooseCategory(ArrayList<LibraryStore.Book> books) {
        ArrayList<String> ids=new ArrayList<>(store.organization.categories.keySet());int[] initial=new int[ids.size()],state=new int[ids.size()];
        LinearLayout panel=column();TextView hint=text(books.size()>1?"✓ 全部属于 · — 部分属于\n未修改的分类保持原样，可同时归入多个分类。":"可同时勾选多个分类，修改后点击保存。",12,muted);hint.setPadding(0,0,0,dp(10));panel.addView(hint);
        ArrayList<Button> choices=new ArrayList<>();
        for(int i=0;i<ids.size();i++){final int index=i;initial[i]=state[i]=store.organization.membership(books,ids.get(i));Button choice=button("",()->{});choice.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);choice.setPadding(dp(14),dp(8),dp(14),dp(8));choices.add(choice);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);panel.addView(choice,p);}
        Runnable paint=()->{for(int i=0;i<choices.size();i++){Button choice=choices.get(i);choice.setText((state[i]==1?"✓  ":state[i]==2?"—  ":"○  ")+store.organization.categories.get(ids.get(i)));choice.setSelected(state[i]==1);choice.setContentDescription(store.organization.categories.get(ids.get(i))+(state[i]==1?"，全部已选":state[i]==2?"，部分已选":"，未选"));choice.setBackground(SnowTheme.ripple(state[i]>0?(uiDark()?SnowTheme.NIGHT_BACKGROUND:SnowTheme.ACCENT_SOFT):card,dp(14),0x20248FA9));}};
        for(int i=0;i<choices.size();i++){final int index=i;choices.get(i).setOnClickListener(v->{state[index]=state[index]==1?0:1;paint.run();});}paint.run();
        Button clear=button("清空所选书籍的全部分类",()->{Arrays.fill(state,0);paint.run();});panel.addView(clear);
        Button add=button("保存选择并新建分类",()->{});panel.addView(add);
        AppSheet sheet=dialog().setSnowflakes(true).setTitle("分类 · "+books.size()+" 本书").setView(panel).setPositiveButton("保存分类",null).show();
        add.setOnClickListener(v->{try{saveCategoryChoices(books,ids,initial,state);sheet.dismissThen(()->{selectedBooks.clear();selecting=false;showLibrary(shelfQuery);editCategory(null,books);});}catch(Exception e){error("没有保存成功",e);}});
        sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{
            try{saveCategoryChoices(books,ids,initial,state);sheet.dismissThen(()->{selectedBooks.clear();selecting=false;showLibrary(shelfQuery);});}catch(Exception e){error("没有保存成功",e);}
        });
    }
    private void saveCategoryChoices(ArrayList<LibraryStore.Book> books,ArrayList<String> ids,int[] initial,int[] state)throws Exception {
        HashSet<String> added=new HashSet<>(),removed=new HashSet<>();for(int i=0;i<ids.size();i++)if(state[i]!=initial[i]){if(state[i]==1)added.add(ids.get(i));else if(state[i]==0)removed.add(ids.get(i));}
        store.organize(()->store.organization.updateMembership(books,added,removed));
        if(!added.isEmpty())for(String id:ids)if(added.contains(id)){pendingCategoryGlow=id;break;}
    }
    private void manageCategories() {
        LinearLayout panel=column();Button add=button("＋ 新建分类",()->{});panel.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView hint=text("左滑或点击分类，直接重命名 / 删除。\n一本书可属于多个分类，数量可能重复计入。",12,muted);hint.setPadding(0,dp(4),0,dp(10));panel.addView(hint);
        ListView list=new ListView(this);list.setDivider(null);list.setSelector(new ColorDrawable(Color.TRANSPARENT));panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<String> ids=new ArrayList<>(store.organization.categories.keySet());final SwipeActionRow[] opened={null};final Runnable[] refresh={null};
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return ids.size();}public Object getItem(int p){return ids.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){String id=ids.get(p);final SwipeActionRow[] current={null};
                current[0]=new SwipeActionRow(uiContext(),store.organization.categories.get(id),store.organization.visible(store.books,id,"").size(),uiDark(),()->editCategory(id,null,refresh[0]),()->{
                    dialog().setTitle("删除「"+store.organization.categories.get(id)+"」？").setMessage("仅解除这个分类的关联，其他分类、正文、封面与阅读记录均保留。没有其他分类的书会显示在“未分类”。").setPositiveButton("删除分类",(d,w)->{if(organize(()->store.organization.delete(id,store.books),false))refresh[0].run();}).show();
                },()->{if(opened[0]!=null&&opened[0]!=current[0])opened[0].close();opened[0]=current[0];});
                LinearLayout wrapper=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(8);wrapper.addView(current[0],cp);return wrapper;
            }
        };list.setAdapter(adapter);
        refresh[0]=()->{if(opened[0]!=null)opened[0].close();opened[0]=null;ids.clear();ids.addAll(store.organization.categories.keySet());adapter.notifyDataSetChanged();};
        add.setOnClickListener(v->editCategory(null,null,refresh[0]));dialog().setSnowflakes(true).setTitle("分类管理").setFixedView(panel).show();
    }
    private void editCategory(String id){editCategory(id,null,null);}
    private void editCategory(String id,ArrayList<LibraryStore.Book> books){editCategory(id,books,null);}
    private void editCategory(String id,ArrayList<LibraryStore.Book> books,Runnable after) {
        LinearLayout panel=column();EditText input=new EditText(uiContext());input.setSingleLine(true);input.setHint("例如：玄幻、正在追、想读");if(id!=null)input.setText(store.organization.categories.get(id));panel.addView(input);
        TextView hint=text("名称最多 20 个字",12,muted);hint.setPadding(0,dp(10),0,0);panel.addView(hint);
        AppSheet sheet=dialog().setSnowflakes(true).setTitle(id==null?"新建分类":"重命名分类").setView(panel).setPositiveButton("保存",null).show();
        sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{
            try {store.organize(()->{if(id!=null)store.organization.rename(id,input.getText().toString());else {String created=store.organization.add(input.getText().toString());if(books!=null)store.organization.updateMembership(books,Collections.singleton(created),Collections.emptySet());}});sheet.dismissThen(()->{if(books!=null){selecting=false;selectedBooks.clear();}showLibrary(shelfQuery);if(after!=null)after.run();});}
            catch(IllegalArgumentException e){hint.setText(e.getMessage());}catch(Exception e){error("没有保存成功",e);}
        });
    }
    private void bindCover(BookCoverView view,LibraryStore.Book book) {
        Bitmap cached=coverCache.get(book.id);
        if(cached!=null){view.setBitmap(cached);return;}
        if(!covers.has(book.id)) return;
        io.execute(()-> {
            Bitmap thumbnail=covers.thumbnail(book.id);
            runOnUiThread(()-> {
                if(isDestroyed()||thumbnail==null) return;
                coverCache.put(book.id,thumbnail);
                if(book.id.equals(view.getTag())) view.setBitmap(thumbnail,true);
            });
        });
    }
    private double ratio(LibraryStore.Book b) { return Math.min(1.0,b.offset/(double)Math.max(1,b.length-1)); }
    private void chooseFile(String encoding) {
        if(busy)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);pendingEncoding=encoding;
        try{startActivityForResult(intent,PICK_TXT);}catch(ActivityNotFoundException e){error("没有可用的文件选择器",e);}
    }
    private String pendingEncoding,pendingEpubBookId;
    private ArrayList<LibraryStore.Book> zipSelection;
    private String displayName(Uri uri)throws Exception {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getString(0);}
        return "未命名小说.txt";
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==CREATE_EPUB){if(result==RESULT_OK&&data!=null&&data.getData()!=null)exportEpub(data.getData());else pendingEpubBookId=null;return;}
        if(request==EXPORT_NOTES){finishNoteExport(result,data);return;}
        if(request==PICK_FONT){finishFontImport(result,data);return;}
        if(request==PICK_REPLACE){if(result==RESULT_OK&&data!=null&&data.getData()!=null)readReplacement(data.getData());return;}
        if(request==PICK_FOLDER){if(result==RESULT_OK&&data!=null&&data.getData()!=null)importFolder(data.getData());return;}
        if(request==PICK_COVER){finishCoverSelection(result,data);return;}
        if(result!=RESULT_OK||data==null)return;
        if(request==CREATE_BACKUP&&data.getData()!=null){exportBackup(data.getData());return;}
        if(request==PICK_BACKUP&&data.getData()!=null){inspectBackup(data.getData());return;}
        if(request==EXPORT_TXT&&data.getData()!=null){exportText(data.getData());return;}
        if(request==CREATE_EXPORT&&data.getData()!=null){writeSelectedZip(data.getData());return;}
        if(request!=PICK_TXT)return;
        LinkedHashSet<Uri> unique=new LinkedHashSet<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++){Uri uri=data.getClipData().getItemAt(i).getUri();if(uri!=null)unique.add(uri);}if(data.getData()!=null)unique.add(data.getData());
        if(unique.isEmpty())return;if(unique.size()>500){toast("每批最多选择 500 个文件，请分批导入");return;}
        final String encoding=pendingEncoding;pendingEncoding=null;ArrayList<Uri> uris=new ArrayList<>(unique);
        if(uris.size()==1){importSingle(uris.get(0),encoding);return;}
        chooseImportCategory(uris,encoding);
    }
    /** Multi-select export: pack the selected books' original TXT files into one ZIP at the chosen location. */
    private void exportSelectedZip(ArrayList<LibraryStore.Book> books){
        if(busy)return;zipSelection=new ArrayList<>(books);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"融雪阅读-导出-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.CHINA).format(new Date())+".zip");
        launchPicker(intent,CREATE_EXPORT);
    }
    private void writeSelectedZip(Uri uri){
        if(busy||zipSelection==null||zipSelection.isEmpty())return;busy=true;
        final ArrayList<LibraryStore.Book> books=new ArrayList<>(zipSelection);zipSelection=null;
        TaskControl task=showCancellableTask("导出书籍","正在打包所选的 "+books.size()+" 本书籍…");
        io.execute(()->{
            int exported=0,skipped=0;
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt");java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new BufferedOutputStream(out))){
                zip.setLevel(1);
                HashSet<String> used=new HashSet<>();
                for(int i=0;i<books.size();i++){
                    task.checkCancelled();
                    LibraryStore.Book book=books.get(i);
                    task.update(i,books.size(),book.title);
                    File file=store.file(book);
                    if(!file.isFile()){skipped++;continue;}
                    String base=book.title.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_").trim();
                    if(base.isEmpty())base="未命名";
                    String ext=book.epub()?".epub":".txt";String name=base+ext;int n=2;while(!used.add(name))name=base+" ("+(n++)+")"+ext;
                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1)zip.write(buffer,0,read);}
                    zip.closeEntry();exported++;
                }
                task.beginCommit();
                final int done=exported,missed=skipped;
                runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;successSheet("导出完成","已导出 "+done+" 本原始书籍"+(missed>0?"，跳过 "+missed+" 本（文件缺失）":"")+"。\nZIP 内保留 TXT / EPUB 原始文件，不包含应用内封面与进度。");});
            }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("导出未完成，请重试",e,uri);});}
        });
    }
    private void chooseImportCategory(ArrayList<Uri> uris,String encoding){
        ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();ids.add("");labels.add("未分类");ids.addAll(store.organization.categories.keySet());labels.addAll(store.organization.categories.values());
        int checked=ids.indexOf(shelfCategory);if(checked<0)checked=0;
        dialog().setTitle("导入 "+uris.size()+" 个文件到…").setMessage("选择分类后开始。重复书籍会跳过，已有分类和进度保留。").setSingleChoiceItems(labels.toArray(new String[0]),checked,(d,w)->startBatchImport(uris,ids.get(w),encoding)).setNegativeButton("取消",null).show();
    }
    private void chooseFolder(){if(busy)return;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);launchPicker(intent,PICK_FOLDER);}
    private void importFolder(Uri tree){
        if(busy)return;busy=true;TaskControl task=showCancellableTask("扫描书籍文件夹","扫描所选文件夹中的 TXT / EPUB，不包含子文件夹…");
        io.execute(()->{try{
            String parent=android.provider.DocumentsContract.getTreeDocumentId(tree);Uri children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);ArrayList<Uri> uris=new ArrayList<>();
            try(Cursor c=getContentResolver().query(children,new String[]{android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
                if(c==null)throw new IOException("无法读取这个文件夹");int scanned=0;while(c.moveToNext()){task.checkCancelled();if(++scanned%32==0)task.update(scanned,0,"已扫描 "+scanned+" 项，找到 "+uris.size()+" 个书籍文件");String name=c.getString(1);if(name!=null&&!android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))&&EpubDocument.supported(name)){uris.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)));if(uris.size()>500)throw new IOException("此文件夹超过 500 个书籍文件，请分为较小的文件夹后导入");}}
            }task.beginCommit();runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;if(uris.isEmpty())toast("所选文件夹中没有 TXT / EPUB 文件");else if(uris.size()==1)importSingle(uris.get(0),null);else chooseImportCategory(uris,null);});
        }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("文件夹扫描失败",e,null);});}});
    }
    private void startBatchImport(ArrayList<Uri> uris,String category,String encoding) {
        if(busy)return;final HashSet<String> known=new HashSet<>();for(LibraryStore.Book b:store.books)known.add(b.id);busy=true;importStop.set(false);
        LinearLayout panel=column();TaskProgress task=new TaskProgress(uiContext(),uiDark());taskProgress=task;panel.addView(task);
        Button stop=button("停止导入",()->{});stop.setOnClickListener(v->{importStop.set(true);stop.setEnabled(false);stop.setText("正在停止，请稍候…");});panel.addView(stop);
        loading=dialog().setTitle("批量导入").setView(panel).setCancelable(false).show();
        ArrayList<BatchImporter.Source> sources=new ArrayList<>();for(Uri uri:uris)sources.add(new BatchImporter.Source(){public String name()throws Exception{return displayName(uri);}public InputStream open()throws Exception{return getContentResolver().openInputStream(uri);}});
        io.execute(()->{
            BatchImporter.Result report=BatchImporter.run(store,sources,category,encoding,(done,total,name)->task.report(name.equals("正在保存书架…")?0:done,name.equals("正在保存书架…")?0:total,(total>0?"已处理 "+done+" / "+total+"\n":"")+name),()->importStop.get());
            runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;if(report.commitError==null)for(LibraryStore.Book b:store.books)if(!known.contains(b.id))freshBooks.add(b.id);selecting=false;selectedBooks.clear();shelfCategory=category.isEmpty()?ShelfOrganization.NONE:category;showLibrary("");
                AppSheet.Builder summary=dialog().setTitle(report.commitError!=null?"未能保存导入":report.processed<report.total?"导入已停止":"导入完成").setMessage(report.summary()).setPositiveButton("回到书架",null);
                if(!report.failures.isEmpty())summary.setItems(report.failures.toArray(new String[0]),(d,w)->dialog().setTitle("未能导入").setMessage(report.failures.get(w)).setPositiveButton("知道了",null).show()).setNeutralButton("指定编码重试",(d,w)->pickEncoding(enc->chooseFile(enc)));
                if(report.commitError==null&&!report.failedIndexes.isEmpty()){ArrayList<Uri> retry=new ArrayList<>();for(int index:report.failedIndexes)if(index>=0&&index<uris.size())retry.add(uris.get(index));if(!retry.isEmpty())summary.setNegativeButton("重试失败的 "+retry.size()+" 个文件",(d,w)->startBatchImport(retry,category,null));}
                if(report.firstImported!=null&&report.commitError==null)summary.setPositiveButton("打开第一本",(d,w)->openBook(report.firstImported));
                if(report.commitError==null&&report.imported>0&&report.failures.isEmpty()){TextView check=text("✓ 已保存至书架",18,accent);GlowFrame light=new GlowFrame(this,14);light.addView(check);summary.setView(light).setSnowflakes(report.processed==report.total);summary.show();Motion.confirm(check);light.pulse();}else summary.show();
            });
        });
    }
    private void dataOptions() {
        if(busy)return;
        AppSheet.Builder builder=dialog().setSnowflakes(true).setTitle("备份与数据管理").setMessage("备份包含全部书籍、封面、分类、置顶、书签和进度，保存在你选择的位置。备份同时包含全局设置，恢复时可以选择是否应用。");
        AppSheet[] handle=new AppSheet[1];
        LinearLayout panel=column();
        SettingsSection backup=new SettingsSection(uiContext(),"data-backup","备份与恢复","导出 ZIP · 恢复或合并",uiDark());
        backup.body.addView(button("导出整库备份 ZIP",first(handle,()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");intent.putExtra(Intent.EXTRA_TITLE,"融雪阅读-备份-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.CHINA).format(new Date())+".zip");launchPicker(intent,CREATE_BACKUP);})));
        backup.body.addView(button("从备份恢复 / 合并",first(handle,()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");launchPicker(intent,PICK_BACKUP);})));
        SettingsSection importing=new SettingsSection(uiContext(),"data-import","批量导入","文件选择器 · 整个文件夹",uiDark());
        importing.body.addView(button("Wi-Fi 局域网传书",first(handle,()->lanTransfer())));
        importing.body.addView(button("批量导入 TXT / EPUB",first(handle,()->chooseFile(null))));
        importing.body.addView(button("从文件夹导入 TXT / EPUB",first(handle,()->chooseFolder())));
        SettingsSection[] sections={backup,importing};
        for(SettingsSection section:sections){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);panel.addView(section,p);
            section.onToggle(()->{boolean expand=!section.expanded();for(SettingsSection other:sections)other.expanded(other==section&&expand);});
        }
        LinearLayout.LayoutParams binParams=new LinearLayout.LayoutParams(-1,-2);binParams.topMargin=dp(10);panel.addView(button("回收站（保留 7 天）",first(handle,()->showRecycleBin())),binParams);
        panel.addView(button("导入预览设置",first(handle,()->{ToggleRow preview=new ToggleRow(uiContext(),"单本总是先预览书名与编码",prefs.getBoolean("previewImport",false),uiDark());dialog().setTitle("导入预览").setMessage("默认保留单本快捷打开；书名被清洗或编码置信度不高时会自动预览。开启后，所有单本都先预览。").setView(preview).setOnDismissListener(dismissed->prefs.edit().putBoolean("previewImport",preview.checked()).apply()).show();})));
        handle[0]=builder.setView(panel).setContentFraction(.62f).setNegativeButton("关闭",null).show();
    }
    private void launchPicker(Intent intent,int request){try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){error("没有可用的文件选择器",e);}}
    private void exportBackup(Uri uri) {
        if(busy)return;busy=true;TaskControl task=showCancellableTask("导出备份","正在整理完整备份…");
        io.execute(()->{File temporary=null;
            try{
                temporary=File.createTempFile("snowread-backup-",".zip",getCacheDir());
                byte[] settings=captureSettingsBytes();
                try(OutputStream out=new FileOutputStream(temporary)){BackupArchive.export(store,settings,out,task);}
                try(InputStream in=new FileInputStream(temporary);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){BackupArchive.copyTracked(in,out,BackupArchive.MAX_TOTAL+16L*1024*1024,task,0,(int)temporary.length(),"正在写入所选位置…");}
                task.beginCommit();runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())successSheet("备份已导出","书籍、封面、分类、置顶、进度和书签已保存在所选位置。\n备份同时包含全局设置，恢复时可以选择是否应用。");});
            }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("未能完整导出，请重新备份",e,uri);});}
            finally{if(temporary!=null)temporary.delete();}
        });
    }
    private void inspectBackup(Uri uri) {
        if(busy)return;busy=true;TaskControl task=showCancellableTask("检查备份","正在检查备份完整性…");
        io.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null)throw new IOException("无法读取这个备份");
                BackupArchive.Prepared prepared=BackupArchive.prepare(in,getFilesDir(),task);try{task.beginCommit();}catch(Exception e){prepared.close();throw e;}
                runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed()){prepared.close();return;}preparedBackup=prepared;
                    int duplicates=0;HashSet<String> ids=new HashSet<>();for(LibraryStore.Book b:store.books)ids.add(b.id);for(LibraryStore.Book b:prepared.store.books)if(ids.contains(b.id))duplicates++;
                    final boolean[] chosen={false};
                    dialog().setTitle("恢复这份备份").setMessage("检查通过："+prepared.store.books.size()+" 本书 · "+prepared.store.organization.categories.size()+" 个分类\n其中 "+duplicates+" 本已在当前书架。合并会保留当前独有的书籍。")
                        .setItems(new String[]{"合并，保留当前已有书籍的记录","合并，同书采用备份中的记录","覆盖恢复，与备份完全一致…"},(d,w)->{chosen[0]=true;if(w==2)confirmReplace(prepared);else restoreBackup(prepared,true,w==1);}).setNegativeButton("取消",null)
                        .setOnDismissListener(d->chromeHandler.post(()->{if(!chosen[0]&&preparedBackup==prepared){prepared.close();preparedBackup=null;}})).show();
                });
            }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("备份检查失败，书架未更改",e,null);});}
        });
    }
    private void restoreBackup(BackupArchive.Prepared prepared,boolean merge,boolean useBackup) {
        if(busy)return;if(listenUi!=null)listenUi.stopPlayback();busy=true;TaskControl task=showCancellableTask(merge?"合并备份":"覆盖恢复","正在准备书库副本，原书库暂不更改…");
        final org.json.JSONObject incomingSettings=prepared.settings;
        io.execute(()->{
            try{LibraryStore restored=BackupArchive.restore(prepared,store,merge,useBackup,task);
                repository.replace(restored);runOnUiThread(()->{store=restored;hideLoading();busy=false;if(isDestroyed())return;coverCache.evictAll();selectedBooks.clear();selecting=false;shelfCategory=ShelfOrganization.ALL;showLibrary("");successSheet("恢复完成","当前书架共 "+store.books.size()+" 本书，阅读记录已恢复。");
                    if(incomingSettings!=null)dialog().setTitle("应用备份中的全局设置？").setMessage("这份备份包含全局设置（排版、外观、氛围、听书偏好等）。应用后会覆盖当前的同名设置。").setPositiveButton("应用",(a,b)->{applySettings(incomingSettings);toast("全局设置已应用");}).setNegativeButton("暂不",null).show();});
            }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("恢复未完成，请查看原因",e,null);});}
            finally{prepared.close();if(preparedBackup==prepared)preparedBackup=null;}
        });
    }
    private void chooseEpubExport(LibraryStore.Book book){
        if(busy)return;savePosition();pendingEpubBookId=book.id;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/epub+zip");
        String name=book.title.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_");if(name.length()>80)name=name.substring(0,80);intent.putExtra(Intent.EXTRA_TITLE,name+".epub");launchPicker(intent,CREATE_EPUB);
    }
    private void exportEpub(Uri uri){
        LibraryStore.Book book=store.find(pendingEpubBookId);pendingEpubBookId=null;if(book==null||busy)return;busy=true;TaskControl task=showCancellableTask("导出 EPUB","正在准备正文与目录…");
        io.execute(()->{try{TextDocument doc=repository.load(book);task.checkCancelled();try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
            EpubExport.write(out,doc,book.title,(done,total)->{task.checkCancelled();task.update(done,total,"正在导出 "+done+" / "+total+" 节");});
        }task.beginCommit();runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())toast("EPUB 已导出，包含原文与目录");});}
        catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("EPUB 导出未完成",e,uri);});}});
    }
    private void chooseTextExport(LibraryStore.Book book) {
        pendingExportBookId=book.id;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(book.epub()?"application/epub+zip":"text/plain");
        String filename=book.title.replaceAll("[\\\\/:*?\"<>|\\r\\n]","_");if(filename.length()>80)filename=filename.substring(0,80);intent.putExtra(Intent.EXTRA_TITLE,filename+(book.epub()?".epub":".txt"));launchPicker(intent,EXPORT_TXT);
    }
    private void exportText(Uri uri) {
        LibraryStore.Book chosen=null;for(LibraryStore.Book b:store.books)if(b.id.equals(pendingExportBookId))chosen=b;pendingExportBookId=null;if(chosen==null){toast("这本书已不在书架中");return;}
        final LibraryStore.Book book=chosen;if(busy)return;busy=true;final TaskControl task=showCancellableTask("导出原始书籍","正在导出「"+book.title+"」…");
        io.execute(()->{try{try(InputStream in=new FileInputStream(store.file(book));OutputStream out=getContentResolver().openOutputStream(uri,"wt")){BackupArchive.copyTracked(in,out,TextDocument.MAX_BYTES,task,0,(int)store.file(book).length(),book.title);}task.beginCommit();runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())toast(book.epub()?"原始 EPUB 已导出":"TXT 已导出，保留原始文本编码");});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())taskFailure("书籍导出失败",e,uri);});}});
    }
    private interface BookTask { LibraryStore.Book run() throws Exception; }
    private void importSingle(Uri uri,String encoding){
        if(busy)return;final String category=store.organization.categories.containsKey(shelfCategory)?shelfCategory:"";busy=true;showLoading("正在分析书籍内容与书名…");final boolean[] epubAttempt={false};
        io.execute(()->{try{String filename=displayName(uri);epubAttempt[0]=EpubDocument.name(filename);if(!EpubDocument.supported(filename))throw new IOException("请选择 TXT 或 EPUB 文件");byte[] bytes=TextDocument.readLimited(getContentResolver().openInputStream(uri));if(EpubDocument.name(filename)){LibraryStore.Book imported=store.add(bytes,filename,null,category);runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()){freshBooks.add(imported.id);openBook(imported);}});return;}TextDocument decoded=TextDocument.decode(bytes,encoding);String title=TitleCleaner.clean(filename);runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;if(prefs.getBoolean("previewImport",false)||!"高".equals(decoded.encodingConfidence)||!title.equals(TitleCleaner.stem(filename)))importPreview(bytes,filename,title,category,decoded);else commitImport(bytes,filename,title,category,decoded);});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())dialog().setTitle("无法导入").setMessage(message(e)).setPositiveButton(epubAttempt[0]?"知道了":"指定编码重试",(d,w)->{if(!epubAttempt[0])pickEncoding(value->importSingle(uri,value));}).show();});}});
    }
    private void importing(BookTask task,Runnable retry) {
        if (busy) return;final HashSet<String> known=new HashSet<>();for(LibraryStore.Book b:store.books)known.add(b.id);busy=true;showLoading("正在整理书页…");
        io.execute(()-> {
            try {
                LibraryStore.Book b=task.run();
                runOnUiThread(()-> { hideLoading();busy=false;if (!isDestroyed()){if(known.contains(b.id))toast("已在书架，已为你打开");else freshBooks.add(b.id);openBook(b);} });
            } catch (Exception e) { runOnUiThread(()-> { hideLoading();busy=false;if (!isDestroyed()) {
                dialog().setTitle("无法导入").setMessage(message(e)).setNegativeButton("关闭",null)
                    .setPositiveButton(retry==null?"知道了":"更多选项",(d,w)-> { if (retry!=null) retry.run(); }).show();
            }}); }
        });
    }
    private void importSample() {
        importing(()->store.add(TextDocument.readLimited(getAssets().open("sample.txt")),"山间来信 · 示例",null),null);
    }
    private void openBook(LibraryStore.Book book) {
        if (busy) return;statsPage=false;
        if(readingStats!=null)readingStats.pulse(false,lastReadingInteraction);savePosition();if(repository!=null&&!repository.listenOwner.isEmpty()&&!repository.owns(book.id)&&listenUi!=null)listenUi.stopPlayback(); active=null; reader=null; pagedReader=null;
        int token=++generation; busy=true;showLoading("正在翻开「"+book.title+"」…");
        io.execute(()-> {
            try {
                FontLibrary.face(getApplicationContext(),book.typeset==null?ReaderTypography.global(this):book.typeset);
                TextDocument loaded=repository.load(book);
                String converted=ChineseText.convert(getApplicationContext(),loaded.text,book.scriptMode);
                runOnUiThread(()-> {
                    hideLoading();busy=false;if (isDestroyed()||token!=generation) return;
                    navigation.clear();capturePage();active=book;book.readingStarted=true;if(book.readingState==ReadingStatus.UNREAD)book.readingState=ReadingStatus.AUTO; document=loaded;readerText=converted; active.length=loaded.text.length(); active.opened=System.currentTimeMillis();prefs.edit().putString("lastBook",book.id).apply();if(loaded.chaptersTruncated)toast("标题超过 1 万个，仅索引前 1 万项；后续正文仍可阅读和搜索");
                    searchTerm="";highlight=-1;listenFollowing=true;showReader(active.offset);revealPage();applyAwake();if(openListenAfterLoad){openListenAfterLoad=false;listenUi.open();}
                });
            } catch (Exception e) { runOnUiThread(()-> { hideLoading();busy=false;if (!isDestroyed()) {showLibrary("");error("无法打开这本书",e);} }); }
        });
    }
    private void showReader(int offset) {
        if(readerFooter!=null)readerFooter.resumed(false);readerFooter=null;readerHeader=null;
        applyTypeset();palette=effectivePalette();brightnessGesture.reset();
        chromeHandler.removeCallbacks(chromeTimeout);
        reader=null;pagedReader=null;readerAdapter=null;progress=null;progressLabel=null;restoring=false;
        frame(true);
        readerStage=new FrameLayout(this);root.addView(readerStage,new LinearLayout.LayoutParams(-1,-1));
        // Persistent header/footer reserve real space. Overlay controls never resize the text viewport.
        LinearLayout content=column();readerStage.addView(content,new FrameLayout.LayoutParams(-1,-1));
        if(prefs.getBoolean("readerHeader",false)){
            readerHeader=new TextView(themedContext(isNight()));readerHeader.setTag("reader-page-header");readerHeader.setTextSize(11);readerHeader.setTextColor(muted);readerHeader.setBackgroundColor(bg);readerHeader.setSingleLine(true);readerHeader.setEllipsize(TextUtils.TruncateAt.END);readerHeader.setIncludeFontPadding(false);readerHeader.setPadding(dp(sideMargin),dp(6),dp(sideMargin),dp(4));readerHeader.setMinimumHeight(dp(26));content.addView(readerHeader,new LinearLayout.LayoutParams(-1,-2));
        }
        chapterLabel=text("",11,muted);chapterLabel.setSingleLine(true);chapterLabel.setEllipsize(TextUtils.TruncateAt.END);
        if(pagedMode) {
            PagedReaderView view=new PagedReaderView(this,document,fontSize,spacing,ink,bg,palette==2?0xFF71612D:0xFFE7D69D,sideMargin,effectiveTypeset(),readerText);
            view.setNotes(active.annotations);pagedReader=view;view.setAnimationEnabled(pageMotion&&Motion.enabled(this));view.setHighlight(highlight,searchTerm);
            view.setListener(new PagedReaderView.Listener() {
                public void changed() {
                    if(pagedReader!=view||active==null||isDestroyed()) return;
                    if(!listenOwns())active.offset=view.position();updateProgress(view.position());
                    if(!view.isIndexing()&&!restoring&&!listenOwns()) persist();
                }
                public void notice(String message) {if(pagedReader==view&&!isDestroyed()) toast(message);}
                public void toggleControls() {if(pagedReader==view) toggleChrome();}
                public void userTurn(){suspendListenFollow();}
                public void copyAt(int offset){copySentence(offset);}
                public void bookmarkAt(int offset){quickBookmark(offset);}
            });
            content.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        } else {
        int verticalMargin=Math.max(4,Math.min(40,prefs.getInt("verticalMargin",4)));
        reader=new ReadingListView(this,()->toggleChrome(),()->suspendListenFollow(),()->quickBookmark(position()),prefs.getBoolean("doubleBookmark",false));reader.setDivider(null);reader.setCacheColorHint(Color.TRANSPARENT);
        reader.setPadding(dp(sideMargin),dp(verticalMargin),dp(sideMargin),dp(verticalMargin+4));reader.setClipToPadding(false);reader.setVerticalScrollBarEnabled(false);
            readerAdapter=new ReaderAdapter();reader.setAdapter(readerAdapter);content.addView(reader,new LinearLayout.LayoutParams(-1,0,1));
            reader.setOnScrollListener(new AbsListView.OnScrollListener() {
                public void onScrollStateChanged(AbsListView v,int state) {if(state==SCROLL_STATE_TOUCH_SCROLL||state==SCROLL_STATE_FLING)suspendListenFollow();if(state==SCROLL_STATE_IDLE&&!restoring) savePosition();}
                public void onScroll(AbsListView v,int first,int visible,int total) {if(!restoring&&active!=null) updateProgress(position());}
            });
        }
        if(prefs.getBoolean("readerFooter",true)){
            readerFooter=new ReaderFooter(themedContext(isNight()),bg,muted,sideMargin);content.addView(readerFooter,new LinearLayout.LayoutParams(-1,-2));readerFooter.resumed(statsResumed);
        }
        progressLabel=text("",11,muted);progressLabel.setSingleLine(true);progressLabel.setGravity(Gravity.CENTER);
        progressLabel.setPadding(dp(2),dp(2),dp(2),dp(2));
        LinearLayout top=row();top.setPadding(dp(6),dp(4),dp(8),dp(4));top.setBackground(SnowTheme.readerToolbar(palette==1?SnowTheme.WARM_PAPER:card,isNight(),dp(22),dp(1)));top.setElevation(dp(2));
        top.addView(iconButton("返回书架","ic_back",()->leaveReader()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        // Book title plus the chapter name as a subtitle, so the toolbar never hides it.
        LinearLayout titles=column();titles.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(active.title,15,ink);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name,new LinearLayout.LayoutParams(-1,-2));
        titles.addView(chapterLabel,new LinearLayout.LayoutParams(-1,-2));
        top.addView(titles,new LinearLayout.LayoutParams(0,dp(48),1));
        top.addView(iconButton("听书","ic_listen",()->listenUi.open()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(iconButton("更多选项","ic_more",()->readerOptions()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        readerTopBar=top;FrameLayout.LayoutParams topPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        topPosition.setMargins(dp(12),dp(8),dp(12),0);readerStage.addView(top,topPosition);
        LinearLayout bottom=column();bottom.setPadding(dp(14),dp(8),dp(14),dp(8));bottom.setBackground(SnowTheme.readerToolbar(palette==1?SnowTheme.WARM_PAPER:card,isNight(),dp(24),dp(1)));bottom.setElevation(dp(2));
        if(pagedMode) {
            LinearLayout turns=row();
            turns.addView(button("‹ 上一页",()->{suspendListenFollow();pagedReader.previous();}),new LinearLayout.LayoutParams(0,dp(44),1));
            turns.addView(button("下一页 ›",()->{suspendListenFollow();pagedReader.next();}),new LinearLayout.LayoutParams(0,dp(44),1));bottom.addView(turns);
        }
        // Progress text sits with the scrubber it describes, inside the overlay bar.
        bottom.addView(progressLabel,new LinearLayout.LayoutParams(-1,dp(18)));
        progress=new BookmarkSeekBar(uiContext(),new BookmarkSeekBar.Actions(){public void bookmark(int offset){navigateTo(offset);}public void chapters(){restoring=false;chapterJump();}});progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));bottom.addView(progress,new LinearLayout.LayoutParams(-1,dp(44)));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) {restoring=true;}
            public void onStopTrackingTouch(SeekBar bar) {jumpTo((int)((document.text.length()-1)*(bar.getProgress()/1000.0)));}
            public void onProgressChanged(SeekBar bar,int value,boolean user) {if(user) {int at=(int)((document.text.length()-1)*value/1000.0);progressLabel.setText(document.readingTitle(at)+String.format(Locale.CHINA," · %.1f%%",value/10.0));}}
        });
        LinearLayout actions=row();
        for(String action:new String[]{"目录","书签","搜索","Aa"}) {
            Runnable run=action.equals("目录")?()->chapters():action.equals("书签")?()->bookmarks():action.equals("搜索")?()->search():()->appearance();
            String icon=action.equals("目录")?"ic_toc":action.equals("书签")?"ic_bookmark":action.equals("搜索")?"ic_search":"ic_settings";
            View item=navButton(action,icon,run);if(action.equals("书签"))item.setTag("reader-bookmark-action");actions.addView(item,new LinearLayout.LayoutParams(0,dp(60),1));
        }
        bottom.addView(actions);readerBottomBar=bottom;FrameLayout.LayoutParams bottomPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottomPosition.setMargins(dp(12),0,dp(12),dp(8));readerStage.addView(bottom,bottomPosition);
                followButton=button("回到朗读位置",()->listenRestoreFollow());followButton.setBackground(shape(card,18));FrameLayout.LayoutParams followPosition=new FrameLayout.LayoutParams(-2,dp(44),Gravity.RIGHT|Gravity.TOP);followPosition.setMargins(0,dp(76),dp(16),0);readerStage.addView(followButton,followPosition);followButton.setVisibility(View.GONE);
        lastReadingInteraction=SystemClock.elapsedRealtime();statsPulse();chrome.enter(SystemClock.elapsedRealtime(),hasWindowFocus());scheduleChrome();followingJump=true;jumpTo(offset);followingJump=false;if(listenUi!=null&&listenUi.snapshot()!=null)onListeningChanged(listenUi.snapshot());
        applyReaderWindow();
        if(!prefs.getBoolean("controlsTipShown",false)) {
            toast("点击正文中间显示或收起工具栏，停留 4 秒后自动收起");
            prefs.edit().putBoolean("controlsTipShown",true).apply();
        }
    }
    private void toggleChrome() {
        if(active==null||readerStage==null) return;
        chrome.toggle(SystemClock.elapsedRealtime());applyChromeVisibility();scheduleChrome();
    }
    private void applyChromeVisibility() {
        applyReaderWindow();
        View[] bars={readerTopBar,readerBottomBar};for(int i=0;i<bars.length;i++){View bar=bars[i];if(bar==null)continue;float nudge=i==0?-Motion.NUDGE:Motion.NUDGE;
            if(chrome.isVisible()){boolean hidden=bar.getVisibility()!=View.VISIBLE||bar.getAlpha()<1;bar.setVisibility(View.VISIBLE);if(hidden)Motion.enter(bar,nudge);else Motion.reset(bar);}
            else Motion.exit(bar,nudge,Motion.BASE,()->{if(!chrome.isVisible())bar.setVisibility(View.GONE);});
        }
    }
    private void scheduleChrome() {
        chromeHandler.removeCallbacks(chromeTimeout);
        if(active==null||readerStage==null||isDestroyed()) return;
        android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        if(accessibility!=null&&accessibility.isTouchExplorationEnabled()) return;
        long remaining=chrome.remaining(SystemClock.elapsedRealtime());
        if(remaining!=Long.MAX_VALUE) chromeHandler.postDelayed(chromeTimeout,remaining);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(active!=null&&readerStage!=null&&prefs.getBoolean("twoFingerBrightness",false)){
            View body=pagedReader!=null?pagedReader:reader;float level=prefs.getFloat("brightness",.5f);if(prefs.getBoolean("followBrightness",true))try{level=android.provider.Settings.System.getInt(getContentResolver(),android.provider.Settings.System.SCREEN_BRIGHTNESS)/255f;}catch(Exception ignored){}
            if(brightnessGesture.handle(event,body,level)){if(brightnessGesture.started){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();chrome.touchUp(SystemClock.elapsedRealtime());}WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.screenBrightness=brightnessGesture.value;getWindow().setAttributes(attrs);if(brightnessGesture.finished){prefs.edit().putBoolean("followBrightness",false).putFloat("brightness",brightnessGesture.value).apply();toast("阅读亮度 "+Math.round(brightnessGesture.value*100)+"%");}scheduleChrome();return true;}
        }
        if(active!=null&&readerStage!=null) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN) chrome.touchDown(SystemClock.elapsedRealtime());
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                chrome.touchUp(SystemClock.elapsedRealtime());
            scheduleChrome();
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);if(readingStats!=null&&!focused)readingStats.pulse(false,lastReadingInteraction);else statsPulse();chrome.focus(focused,SystemClock.elapsedRealtime());if(focused&&prefs!=null&&!failed){refreshAutoTheme();applyReaderWindow();}scheduleChrome();
    }
    private int position() {
        if(pagedReader!=null) return pagedReader.position();
        if (reader==null||document==null||reader.getChildCount()==0) return active==null?0:active.offset;
        int index=reader.getFirstVisiblePosition();TextView view=(TextView)reader.getChildAt(0);
        Layout layout=view.getLayout(); int local=0;
        if (layout!=null) local=layout.getLineStart(layout.getLineForVertical(Math.max(0,-view.getTop()-view.getTotalPaddingTop())));
        int offset=document.starts.get(index)+local;
        if (reader.getLastVisiblePosition()==document.starts.size()-1) {
            View last=reader.getChildAt(reader.getChildCount()-1);
            if (last.getBottom()<=reader.getHeight()-reader.getPaddingBottom()) offset=document.text.length()-1;
        }
        return Math.max(0,Math.min(offset,document.text.length()-1));
    }
    private void jumpTo(int requested) {
        if(!followingJump)suspendListenFollow();
        if(pagedReader!=null&&document!=null) {restoring=false;pagedReader.goTo(requested);return;}
        if (reader==null||document==null) return;
        int offset=Math.max(0,Math.min(requested,document.text.length()-1));
        if(!listenOwns())active.offset=offset;int index=document.rowAt(offset);restoring=true;
        final ListView target=reader;
        target.setSelectionFromTop(index,0);
        target.post(()-> {
            if (reader!=target||isDestroyed()) return;
            TextView probe=(TextView)readerAdapter.getView(index,null,target);
            probe.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,target.getWidth()-target.getPaddingLeft()-target.getPaddingRight()),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            Layout layout=probe.getLayout();int y=0;
            if (layout!=null) y=layout.getLineTop(layout.getLineForOffset(offset-document.starts.get(index)));
            target.setSelectionFromTop(index,-y);
            target.post(()-> { if (reader==target) {restoring=false;updateProgress(offset);if(!listenOwns())persist();} });
        });
        updateProgress(offset);
    }
    private void updateProgress(int offset) {
        if(document==null||chapterLabel==null) return;if(progress instanceof BookmarkSeekBar)((BookmarkSeekBar)progress).bookmarks(active.marks,document.text.length(),isNight());
        String currentTitle=ChineseText.label(document.readingTitle(offset),active.scriptMode);chapterLabel.setText(currentTitle);if(readerHeader!=null)readerHeader.setText(currentTitle);if(active!=null&&(!currentTitle.equals(prefs.getString("lastChapter",""))||!active.id.equals(prefs.getString("lastBook",""))))prefs.edit().putString("lastBook",active.id).putString("lastChapter",currentTitle).apply();
        if(progress!=null && !restoring) progress.setProgress((int)(1000.0*offset/Math.max(1,document.text.length()-1)));
        String where=document.numberedChapters.isEmpty()?"正文":String.format(Locale.CHINA,"章节 %d/%d",document.numberedOrdinalAt(offset),document.numberedChapters.size());
        String pageInfo=pagedReader==null?"":pagedReader.pageLabel()+" · ";
        if(readerFooter!=null&&!restoring)readerFooter.progress(ReaderStatusText.progress(offset,document.text.length(),pagedReader==null?"":pagedReader.pageLabel()));
        if(progressLabel!=null && !restoring) progressLabel.setText(pageInfo+where+String.format(Locale.CHINA," · %.1f%%",100.0*offset/Math.max(1,document.text.length()-1)));
    }
    private void refreshHighlights() {
        if(pagedReader!=null) pagedReader.setHighlight(highlight,searchTerm);
        if(pagedReader!=null)pagedReader.setListenHighlight(listenStart,listenEnd);if(readerAdapter!=null) readerAdapter.notifyDataSetChanged();
    }
    private void savePosition() { if (active!=null&&document!=null&&!restoring&&!listenOwns()) { active.offset=position();persist(); } }
    private void persist(){
        if(store==null||failed)return;
        try{
            LibraryStore.Book book=active;
            if(book!=null&&document!=null){
                ReadingAnchor location=repository.anchor(book,book.offset);
                java.util.HashMap<Integer,ReadingAnchor> anchors=new java.util.HashMap<>();
                for(Integer pos:new ArrayList<>(book.marks))if(!book.markAnchors.containsKey(pos)){ReadingAnchor mark=repository.anchor(book,pos);if(mark!=null)anchors.put(pos,mark);}
                synchronized(store){if(store.find(book.id)==book){if(location!=null)book.anchor=location;for(java.util.Map.Entry<Integer,ReadingAnchor> e:anchors.entrySet())if(book.marks.contains(e.getKey()))book.markAnchors.put(e.getKey(),e.getValue());}}
            }
            repository.requestProgressSave(false,e->toast("保存失败："+message(e)));
        }catch(Exception e){toast("保存失败："+message(e));}
    }

    private void leaveReader() {savePosition();capturePage();++generation;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);showLibrary("");revealPage();}
    private void chapters() {
        final TextDocument doc=document;int anchor=doc.chapterAt(position());DirectoryRows order=new DirectoryRows(doc,prefs.getBoolean("tocDescending",false));
        LinearLayout panel=column();EditText filter=new EditText(uiContext());filter.setSingleLine(true);filter.setHint("搜索章节名 / 卷名");panel.addView(filter);TextView legend=text((doc.chaptersTruncated?"超过 1 万项，仅显示前 1 万项。\n":"")+"浅灰：当前进度之前 · 点击卷名折叠 / 展开",12,muted);legend.setPadding(0,0,0,dp(10));panel.addView(legend);
        FrameLayout stage=new FrameLayout(this);panel.addView(stage,new LinearLayout.LayoutParams(-1,0,1));ListView list=new ListView(this);list.setDivider(null);list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setClipToPadding(false);list.setPadding(0,0,0,dp(72));stage.addView(list,new FrameLayout.LayoutParams(-1,-1));
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return order.rows.size();}public Object getItem(int p){return order.chapter(p);}public long getItemId(int p){return order.chapter(p);}
            public View getView(int p,View reuse,ViewGroup parent){int index=order.chapter(p);TextDocument.Chapter chapter=doc.chapters.get(index);boolean current=index==anchor;int color=current?accent:index<anchor?(isNight()?0xFF8A9A9F:0xFF697474):ink;
                LinearLayout item=row();item.setPadding(dp(chapter.kind==TextDocument.Chapter.VOLUME?12:chapter.volumeIndex>=0?24:12),dp(14),dp(12),dp(14));item.setMinimumHeight(dp(58));item.setBackground(SnowTheme.ripple(current?(isNight()?SnowTheme.NIGHT_BACKGROUND:SnowTheme.ACCENT_SOFT):Color.TRANSPARENT,dp(14),0x20248FA9));
                TextView title=text((chapter.kind==TextDocument.Chapter.VOLUME?(order.folded.contains(chapter.volumeIndex)?"▸ ":"▾ "):"")+chapter.title,15,color);title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);if(chapter.kind==TextDocument.Chapter.VOLUME)title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);item.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                if(current){TextView mark=text("当前",11,accent);mark.setPadding(dp(8),0,0,0);item.addView(mark);}item.setContentDescription(chapter.title+(current?"，当前章":index<anchor?"，当前进度之前":"，尚未到达"));return item;
            }
        };list.setAdapter(adapter);
        final AppSheet[] self={null};
        Button sort=button(order.descending?"倒序 ↓":"正序 ↑",()->{});sort.setTextSize(12);sort.setContentDescription("切换目录正序或倒序");
        Button ruleEntry=button("目录识别",()->chapterRuleSheet(doc,active,self));ruleEntry.setTextSize(12);ruleEntry.setContentDescription("预览并选择分章规则");
        LinearLayout headerRow=row();headerRow.addView(sort,new LinearLayout.LayoutParams(0,-2,1));if(!active.epub())headerRow.addView(ruleEntry,new LinearLayout.LayoutParams(0,-2,1));
        Runnable locate=()->{filter.setText("");int row=order.locate(anchor);adapter.notifyDataSetChanged();if(row>=0)list.smoothScrollToPositionFromTop(row,Math.max(0,list.getHeight()/3),Motion.enabled(this)?240:0);};
        sort.setOnClickListener(v->{order.descending=!order.descending;prefs.edit().putBoolean("tocDescending",order.descending).apply();sort.setText(order.descending?"倒序 ↓":"正序 ↑");order.rebuild();adapter.notifyDataSetChanged();});
        Button current=button("当前章",locate);current.setTextColor(isNight()?SnowTheme.NIGHT_BACKGROUND:Color.WHITE);current.setCompoundDrawablesRelative(iconDrawable("ic_locate",isNight()?SnowTheme.NIGHT_BACKGROUND:Color.WHITE,20),null,null,null);current.setCompoundDrawablePadding(dp(5));current.setPadding(dp(12),0,dp(12),0);current.setBackground(SnowTheme.ripple(accent,dp(24),0x30FFFFFF));current.setElevation(dp(4));current.setContentDescription("回到当前章");
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM|Gravity.RIGHT);cp.setMargins(0,0,dp(4),dp(10));stage.addView(current,cp);
        AppSheet sheet=dialog().setSnowflakes(true).setTitle("目录 · "+doc.volumes.size()+" 卷 · "+doc.numberedChapters.size()+" 章").setHeaderView(headerRow).setFixedView(panel).setNeutralButton("输入章号",(d,w)->chapterJump()).show();
        self[0]=sheet;
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){order.query=s.toString();order.rebuild();adapter.notifyDataSetChanged();list.setSelection(0);}});
        list.setOnItemClickListener((parent,v,p,id)->{int index=order.chapter(p);if(doc.chapters.get(index).kind==TextDocument.Chapter.VOLUME){order.toggle(index);adapter.notifyDataSetChanged();}else{sheet.dismissThen(()->navigateTo(doc.chapters.get(index).offset));}});list.post(locate);
    }
    /** F01: preview chapter recognition with a selectable rule, then apply it without touching the text. */
    private void chapterRuleSheet(TextDocument doc,LibraryStore.Book book,AppSheet[] toc){
        if(active!=null&&active.epub()){toast("EPUB 使用书内目录，不需要 TXT 分章规则");return;}
        if(busy||doc!=document)return;
        AppSheet.Builder builder=dialog().setSnowflakes(true).setTitle("目录识别设置");
        AppSheet[] handle=new AppSheet[1];
        LinearLayout panel=column();
        TextView intro=text("分章不正确时，可换一种识别规则。正文、书签和笔记的位置都不会改变。",12,muted);intro.setPadding(0,0,0,dp(10));panel.addView(intro);
        ChoiceStrip rules=new ChoiceStrip(uiContext(),new String[]{ChapterRule.label(0),ChapterRule.label(1),ChapterRule.label(2),ChapterRule.label(3)},book.chapterRule,isNight());rules.setTag("chapter-rule");panel.addView(rules);
        TextView describe=text(ChapterRule.describe(book.chapterRule),12,muted);describe.setPadding(0,dp(8),0,dp(4));panel.addView(describe);
        TextView status=text("",13,ink);status.setPadding(0,dp(4),0,dp(4));panel.addView(status);
        LinearLayout previewArea=column();panel.addView(previewArea);
        Button previewButton=button("重新预览",()->{});panel.addView(previewButton);
        Button apply=button("应用此规则",()->{});apply.setEnabled(false);panel.addView(apply);
        TextView note=text("预览只是临时结果，未应用前目录保持原样。",11,muted);note.setPadding(0,dp(8),0,0);panel.addView(note);
        final int[] generation={0};final int[] chosen={book.chapterRule};final TextDocument[] sample={null};
        final Runnable[] runPreview={()->{}};
        runPreview[0]=()->{
            int token=++generation[0];int rule=chosen[0];
            sample[0]=null;apply.setEnabled(false);previewArea.removeAllViews();status.setText("正在识别…");
            io.execute(()->{
                try{
                    TextDocument sampleDoc=new TextDocument(doc.text,doc.encoding,rule);
                    runOnUiThread(()->{
                        if(generation[0]!=token||isDestroyed()||handle[0]==null||!handle[0].isShowing())return;
                        sample[0]=sampleDoc;
                        boolean fallback=sampleDoc.chapters.size()==1&&"开始阅读".equals(sampleDoc.chapters.get(0).title);
                        int real=fallback?0:sampleDoc.chapters.size();
                        status.setText(real<=0?"未匹配到标题，可换其他规则或返回原目录":"识别到 "+real+" 章 · "+sampleDoc.volumes.size()+" 卷"+(sampleDoc.chaptersTruncated?"（已截断至 1 万项）":""));
                        int shown=Math.min(8,sampleDoc.chapters.size());
                        for(int i=0;i<shown;i++){TextDocument.Chapter c=sampleDoc.chapters.get(i);TextView row=text((i+1)+". "+c.title,13,ink);row.setMaxLines(2);row.setEllipsize(TextUtils.TruncateAt.END);row.setPadding(dp(4),dp(6),dp(4),dp(6));previewArea.addView(row);}
                        apply.setEnabled(real>0||rule==ChapterRule.AUTO);
                    });
                }catch(Exception e){runOnUiThread(()->{if(generation[0]==token&&handle[0]!=null&&handle[0].isShowing())status.setText("识别失败："+e.getMessage());});}
            });
        };
        rules.onChanged(()->{chosen[0]=rules.selected();describe.setText(ChapterRule.describe(chosen[0]));runPreview[0].run();});
        previewButton.setOnClickListener(v->runPreview[0].run());
        apply.setOnClickListener(v->{
            TextDocument sampleDoc=sample[0];
            if(sampleDoc==null){toast("请先预览识别结果");return;}
            if(busy)return;busy=true;
            final TextDocument ruled=sampleDoc;final int rule=chosen[0];
            if(handle[0]!=null)handle[0].dismissThen(()->applyChapterRule(book,ruled,rule,toc));
            else applyChapterRule(book,ruled,rule,toc);
        });
        handle[0]=builder.setView(panel).setContentFraction(.62f).setNegativeButton("取消",null).show();
        runPreview[0].run();
    }
    /** Same text, new chapter list: swap the document in place, re-capture anchors at their offsets, persist. */
    private void applyChapterRule(LibraryStore.Book book,TextDocument ruled,int rule,AppSheet[] toc){
        repository.adopt(book,ruled);document=ruled;book.chapterRule=rule;
        if(book.anchor!=null)book.anchor=repository.anchor(book,position());
        ArrayList<Integer> anchored=new ArrayList<>(book.markAnchors.keySet());
        for(Integer at:anchored){ReadingAnchor recaptured=repository.anchor(book,at);if(recaptured!=null)book.markAnchors.put(at,recaptured);}
        try{synchronized(store){store.save();}}catch(Exception e){error("规则已应用但未保存",e);}
        capturePage();showReader(position());revealPage();
        if(toc[0]!=null){if(toc[0].isShowing())toc[0].dismiss();chapters();}
        toast("目录已按「"+ChapterRule.label(rule)+"」重新识别");
        busy=false;
    }
    private void chapterJump() {
        if(document.numberedChapters.isEmpty()){toast("没有识别到带章号的标题，可从目录或进度条定位");return;}
        LinearLayout panel=column();
        ArrayList<String> names=new ArrayList<>();names.add("全书（所有卷）");for(TextDocument.Chapter v:document.volumes)names.add(v.title);
        int current=document.chapters.get(document.chapterAt(position())).volumeIndex;final int[] selected={current>=0?current+1:0};
        if(!document.volumes.isEmpty()) {
            Button volume=button(names.get(selected[0])+"  ▾",()->{});volume.setBackground(shape(isNight()?SnowTheme.NIGHT_BACKGROUND:SnowTheme.ACCENT_SOFT,14));panel.addView(volume,new LinearLayout.LayoutParams(-1,-2));
            volume.setOnClickListener(v->dialog().setTitle("选择卷").setSingleChoiceItems(names.toArray(new String[0]),selected[0],(d,w)->{selected[0]=w;volume.setText(names.get(w)+"  ▾");}).setNegativeButton("取消",null).show());
        }
        EditText number=new EditText(uiContext());number.setSingleLine(true);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);number.setHint("章号，例如 12");LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(12);panel.addView(number,np);
        TextView hint=text("按标题章号查找：第十二章输入 12。\n序章和卷标题不计入章数。",12,muted);hint.setPadding(0,dp(10),0,dp(6));panel.addView(hint);
        AppSheet sheet=dialog().setSnowflakes(true).setTitle("跳转到第几章").setView(panel).setPositiveButton("跳转",null).setNegativeButton("取消",null).show();
        sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{
            int requested=TextDocument.parseNumber(number.getText().toString().trim());
            if(requested<0){hint.setText("请输入有效的章号");return;}
            boolean filter=!document.volumes.isEmpty()&&selected[0]>0;int volume=filter?selected[0]-1:-1;
            List<TextDocument.Chapter> matches=document.findChapters(requested,volume,filter);
            if(matches.isEmpty()){hint.setText("未找到第 "+requested+" 章，请检查章号或选择其他卷");return;}
            sheet.dismissThen(()->{if(matches.size()==1){navigateTo(matches.get(0).offset);return;}
                String[] choices=new String[matches.size()];for(int i=0;i<choices.length;i++)choices[i]=document.volumeTitle(matches.get(i))+" · "+matches.get(i).title;
                dialog().setTitle("找到多个同章号，请选择").setItems(choices,(d,w)->navigateTo(matches.get(w).offset)).setNegativeButton("取消",null).show();});
        });
    }
    private void bookmarks() {
        ArrayList<Integer> marks=new ArrayList<>(active.marks);Collections.sort(marks);String[] items=new String[marks.size()+1+(active.unmappedMarks.isEmpty()?0:1)];items[0]="＋ 添加当前位置为书签 / 备注";
        for(int i=0;i<marks.size();i++){int pos=Math.min(marks.get(i),document.text.length()-1);String note=active.markNotes.get(pos);items[i+1]=document.readingTitle(pos)+String.format(Locale.CHINA," · %.1f%%",100.0*pos/Math.max(1,document.text.length()-1))+"\n"+(note==null?document.text.substring(pos,Math.min(pos+24,document.text.length())).replace('\n',' '):note);}
        if(!active.unmappedMarks.isEmpty())items[items.length-1]="待定位记录 · "+active.unmappedMarks.size()+" 条（原文与备注保留）";
        AppSheet sheet=dialog().setSnowflakes(true).setTitle("书签 · 按章节位置排序").setNeutralButton("笔记与划线",(d,w)->notes()).setMessage("点击跳转，长按修改备注或删除。").setItems(items,(d,w)->{if(w==0)editBookmark(position());else if(w<=marks.size())navigateTo(marks.get(w-1));else unresolvedBookmarks();}).show();
        sheet.getListView().setOnItemLongClickListener((parent,v,index,id)->{if(index==0||index>marks.size())return false;int pos=marks.get(index-1);sheet.dismissThen(()->dialog().setTitle("管理书签").setItems(new String[]{"编辑备注","删除书签"},(d,w)->{if(w==0)editBookmark(pos);else synchronized(store){String old=active.markNotes.remove(pos);active.marks.remove(Integer.valueOf(pos));try{store.save();toast("书签已删除");}catch(Exception e){active.marks.add(pos);Collections.sort(active.marks);if(old!=null)active.markNotes.put(pos,old);error("删除失败",e);}}}).show());return true;});
    }
    private void search(){
        final TextDocument searchedDocument=document;final LibraryStore.Book searchedBook=active;final boolean resumeSearch=searchSession!=null&&searchSessionDoc==searchedDocument&&searchTerm.equals(searchSessionTerm);
        final int chapterAtOpen=resumeSearch?searchSessionChapter:searchedDocument.chapterAt(position());
        LinearLayout panel=column();LinearLayout queryRow=row();EditText input=new EditText(uiContext());input.setSingleLine(true);input.setHint("输入正文关键词");input.setText(searchTerm);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);queryRow.addView(input,new LinearLayout.LayoutParams(0,-2,1));Button submit=button("搜索",()->{});queryRow.addView(submit,new LinearLayout.LayoutParams(dp(60),dp(48)));panel.addView(queryRow);
        ToggleRow ignoreCase=new ToggleRow(uiContext(),"英文忽略大小写",resumeSearch?searchSessionIgnoreCase:prefs.getBoolean("searchIgnoreCase",true),isNight()),onlyChapter=new ToggleRow(uiContext(),"仅打开搜索时的当前章",resumeSearch&&searchSessionOnlyChapter,isNight());panel.addView(ignoreCase);panel.addView(onlyChapter);Button history=button("最近搜索（最多 10 条）",()->{});panel.addView(history);
        TextView status=text("结果按章节分组，点击章节名可折叠。",12,muted);panel.addView(status);ProgressBar searchBusy=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);searchBusy.setIndeterminate(Motion.enabled(this));searchBusy.setVisibility(View.GONE);panel.addView(searchBusy,new LinearLayout.LayoutParams(-1,dp(2)));ListView results=new ListView(this);results.setDivider(null);results.setSelector(new ColorDrawable(Color.TRANSPARENT));panel.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        final SearchResults[] found={null};final String[] termUsed={""};SearchGroups grouped=new SearchGroups();
        BaseAdapter adapter=new BaseAdapter(){public int getCount(){return grouped.rows.size();}public Object getItem(int p){return grouped.rows.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View reuse,ViewGroup parent){SearchGroups.Row item=grouped.rows.get(p);if(item.hit<0){TextView label=text((grouped.folded.contains(item.chapter)?"▸ ":"▾ ")+searchedDocument.chapters.get(item.chapter).title+" · "+item.count+" 处",14,accent);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);label.setPadding(dp(10),dp(14),dp(10),dp(14));label.setContentDescription(label.getText()+"，点击折叠或展开");return label;}
                SearchResults.Hit hit=found[0].hits.get(item.hit);LinearLayout cell=column();cell.setPadding(dp(12),dp(10),dp(12),dp(10));cell.setBackground(SnowTheme.ripple(isNight()?SnowTheme.NIGHT_BACKGROUND:SnowTheme.BACKGROUND,dp(14),0x20248FA9));TextView where=text(String.format(Locale.CHINA,"全书 %.1f%%",100.0*hit.offset/Math.max(1,searchedDocument.text.length())),11,accent);cell.addView(where);TextView preview=text("",14,ink);preview.setLineSpacing(dp(3),1);preview.setPadding(0,dp(7),0,dp(6));SpannableString snippet=new SpannableString(hit.preview);snippet.setSpan(new BackgroundColorSpan(isNight()?0xFF71612D:0xFFE7D69D),hit.matchStart,hit.matchEnd,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);preview.setText(snippet);cell.addView(preview);return cell;
            }
        };results.setAdapter(adapter);AppSheet sheet=dialog().setSnowflakes(true).setTitle("正文搜索").setFixedView(panel).setNeutralButton("清除阅读高亮",(d,w)->{searchTerm="";highlight=-1;refreshHighlights();}).setOnDismissListener(d->{++searchGeneration;if(found[0]!=null&&found[0]==searchSession&&searchSessionDoc==searchedDocument){searchSessionFolded.clear();searchSessionFolded.addAll(grouped.folded);searchSessionRow=results.getFirstVisiblePosition();View first=results.getChildAt(0);searchSessionTop=first==null?0:first.getTop();}}).show();
        Runnable invalidate=()->{++searchGeneration;searchBusy.setVisibility(View.GONE);found[0]=null;searchSession=null;grouped.rebuild(searchedDocument,null);adapter.notifyDataSetChanged();status.setText("点击搜索，查看所在章节与上下文");};
        Runnable execute=()->{String term=input.getText().toString();int token=++searchGeneration;found[0]=null;grouped.folded.clear();grouped.rebuild(searchedDocument,null);adapter.notifyDataSetChanged();if(term.isEmpty()){status.setText("请输入关键词");searchBusy.setVisibility(View.GONE);return;}status.setText("正在搜索…");searchBusy.setVisibility(View.VISIBLE);termUsed[0]=term;rememberSearch(term);boolean insensitive=ignoreCase.checked();prefs.edit().putBoolean("searchIgnoreCase",insensitive).apply();int from=onlyChapter.checked()?searchedDocument.chapters.get(chapterAtOpen).offset:0,end=onlyChapter.checked()?searchedDocument.sectionEnd(chapterAtOpen):searchedDocument.text.length();
            io.execute(()->{SearchResults matches=SearchResults.find(searchedDocument.text,term,from,end,insensitive,()->searchGeneration!=token);runOnUiThread(()->{if(matches==null||isDestroyed()||!sheet.isShowing()||token!=searchGeneration||active!=searchedBook)return;searchBusy.setVisibility(View.GONE);found[0]=matches;searchSession=matches;searchSessionDoc=searchedDocument;searchSessionTerm=termUsed[0];searchSessionOnlyChapter=onlyChapter.checked();searchSessionIgnoreCase=insensitive;searchSessionChapter=chapterAtOpen;searchSessionRow=0;searchSessionTop=0;searchSessionFolded.clear();grouped.rebuild(searchedDocument,matches);adapter.notifyDataSetChanged();status.setText("共 "+matches.total+" 处"+(matches.total>SearchResults.LIMIT?"，显示前 1000 处":"")+" · 点击章节折叠");Motion.enter(results,Motion.CONTENT_NUDGE);results.setSelection(0);});});
        };
        submit.setOnClickListener(v->execute.run());ignoreCase.onChanged(invalidate);onlyChapter.onChanged(invalidate);input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){execute.run();return true;}return false;});input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){invalidate.run();}});
        history.setOnClickListener(v->{ArrayList<String> terms=searchHistory();if(terms.isEmpty()){toast("还没有搜索记录");return;}dialog().setTitle("最近搜索").setItems(terms.toArray(new String[0]),(d,w)->{input.setText(terms.get(w));execute.run();}).setNeutralButton("清空搜索历史",(d,w)->prefs.edit().remove("searchHistory").apply()).show();});
        results.setOnItemClickListener((parent,v,p,id)->{SearchGroups.Row item=grouped.rows.get(p);if(item.hit<0){if(!grouped.folded.remove(item.chapter))grouped.folded.add(item.chapter);grouped.rebuild(searchedDocument,found[0]);adapter.notifyDataSetChanged();return;}SearchResults.Hit hit=found[0].hits.get(item.hit);final String chosenTerm=termUsed[0];sheet.dismissThen(()->{searchTerm=chosenTerm;highlight=hit.offset;refreshHighlights();navigateTo(hit.offset);});});if(resumeSearch){found[0]=searchSession;termUsed[0]=searchSessionTerm;grouped.folded.addAll(searchSessionFolded);grouped.rebuild(searchedDocument,found[0]);adapter.notifyDataSetChanged();status.setText("共 "+found[0].total+" 处 · 已恢复上次结果");final int row=searchSessionRow,top=searchSessionTop;results.post(()->results.setSelectionFromTop(row,top));}else if(!searchTerm.isEmpty())execute.run();
    }
    private ArrayList<String> searchHistory(){ArrayList<String> values=new ArrayList<>();try{org.json.JSONArray list=new org.json.JSONArray(prefs.getString("searchHistory","[]"));for(int i=0;i<Math.min(10,list.length());i++){String value=list.optString(i,"");if(!value.isEmpty()&&value.length()<=120)values.add(value);}}catch(Exception ignored){}return values;}
    private void rememberSearch(String term){ArrayList<String> history=searchHistory();history.remove(term);history.add(0,term);while(history.size()>10)history.remove(history.size()-1);prefs.edit().putString("searchHistory",new org.json.JSONArray(history).toString()).apply();}
    private Typeset effectiveTypeset(){return previewTypeset!=null?previewTypeset:active!=null&&active.typeset!=null?active.typeset:ReaderTypography.global(this);}
    private void applyTypeset(){Typeset t=effectiveTypeset();fontSize=t.font;spacing=t.spacing;sideMargin=t.margin;manualPalette=t.palette;}
    private int linePitchDp(int font,int family,float spacing){
        android.graphics.Paint paint=new android.graphics.Paint();
        paint.setTypeface(FontLibrary.face(this,effectiveTypeset()));
        paint.setTextSize(font*getResources().getDisplayMetrics().scaledDensity);
        android.graphics.Paint.FontMetrics metrics=paint.getFontMetrics();
        return Math.round((metrics.bottom-metrics.top)*spacing/getResources().getDisplayMetrics().density);
    }
    private void saveGlobalTypeset(Typeset t){prefs.edit().putInt("font",t.font).putFloat("spacing",t.spacing).putInt("sideMargin",t.margin).putInt("palette",t.palette).putInt("fontFamily",t.fontFamily).putString("customFont",t.customFont).putInt("paragraphGap",t.paragraphGap).putBoolean("awake",t.awake).putBoolean("justify",t.justify).putBoolean("indent",t.indent).apply();}
    private void appearance(){appearance(0);}
    private void appearance(final int restoreScroll){
        final Context typesetContext=themedContext(isNight());
        final boolean[] recolor={false};final int[] savedScroll={0};
        savePosition();
        final int offset=position();final LibraryStore.Book book=active;
        final Typeset draft=effectiveTypeset().copy();previewTypeset=draft;
        LinearLayout panel=column();
        final Runnable[] pitch={()->{}};
        ToggleRow scope=new ToggleRow(typesetContext,"仅对《"+book.title+"》生效",book.typeset!=null,isNight());panel.addView(scope);
        TextView scopeHint=text("字体、段落、纸色与常亮可按书保存；其余设置为全局。",12,muted);scopeHint.setPadding(0,0,0,dp(12));panel.addView(scopeHint);
        TextView sample=text("雪融，故事生长",draft.font,ink);sample.setMaxLines(1);sample.setMinimumHeight(dp(64));sample.setEllipsize(TextUtils.TruncateAt.END);sample.setContentDescription("当前排版预览");panel.addView(sample,new LinearLayout.LayoutParams(-1,-2));

        SettingsSection fonts=new SettingsSection(typesetContext,"font","字体","字形、字号与字体授权",isNight());
        SettingsSection paragraphs=new SettingsSection(typesetContext,"paragraph","段落与留白","行距、段距、页边距与对齐",isNight());
        SettingsSection themesGroup=new SettingsSection(typesetContext,"theme","主题与夜读","纸色与自动夜间",isNight());
        SettingsSection reading=new SettingsSection(typesetContext,"reading","阅读方式与动效","滚动、分页与翻页动画",isNight());
        SettingsSection display=new SettingsSection(typesetContext,"display","屏幕与阅读信息","页眉页脚、亮度、常亮与方向",isNight());
        SettingsSection gestures=new SettingsSection(typesetContext,"gestures","手势与按键","触摸操作与音量翻页",isNight());
        SettingsSection[] sections={fonts,paragraphs,themesGroup,reading,display,gestures};
        for(SettingsSection section:sections){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);panel.addView(section,p);section.onToggle(()->{
            boolean expand=!section.expanded();for(SettingsSection other:sections)other.expanded(other==section&&expand);
            prefs.edit().putString("readerSettingsGroup",expand?section.key:"").apply();if(expand)section.reveal();
        });}
        String openGroup=prefs.getString("readerSettingsGroup","font");boolean known=openGroup.isEmpty();for(SettingsSection section:sections)if(section.key.equals(openGroup))known=true;
        if(!known)openGroup="font";for(SettingsSection section:sections)section.expanded(section.key.equals(openGroup));
        final AppSheet[] settings={null};final Runnable[] summaries={()->{}};final View[] nightTimes={null};
        ChoiceStrip autoNight=new ChoiceStrip(typesetContext,new String[]{"关闭","跟随系统","按时间"},prefs.getInt("autoNight",0),isNight());previewNightMode=autoNight.selected();
        Runnable preview=()->{
            summaries[0].run();if(nightTimes[0]!=null)nightTimes[0].setVisibility(autoNight.selected()==2?View.VISIBLE:View.GONE);previewNightMode=autoNight.selected();applyTypeset();palette=effectivePalette();
            ReaderTypography typography=new ReaderTypography(this,draft);typography.apply(sample);sample.setTextSize(draft.font);sample.setLineSpacing(0,draft.spacing);
            String value=ChineseText.label("雪融，故事生长",book.scriptMode);sample.setText(typography.style(value,0,value.length(),sample.getTextSize(),getResources().getDisplayMetrics().density));
            sample.setTextColor(isNight()?SnowTheme.NIGHT_INK:draft.palette==1?0xFF4B4238:SnowTheme.INK);sample.setPadding(dp(draft.margin),dp(12),dp(draft.margin),dp(12));
            sample.setBackground(SnowTheme.rounded(isNight()?SnowTheme.NIGHT_BACKGROUND:draft.palette==1?SnowTheme.WARM_PAPER:SnowTheme.PAPER,dp(16),SnowTheme.BORDER,dp(1)));
            if(!isDestroyed()&&active==book){capturePage();showReader(offset);revealPage();}
            if(settings[0]!=null&&!recolor[0]&&settings[0].isDarkSurface()!=isNight()){
                recolor[0]=true;View parent=(View)panel.getParent();savedScroll[0]=parent==null?0:parent.getScrollY();
                settings[0].dismiss();
            }
        };

        ChoiceStrip faces=new ChoiceStrip(typesetContext,new String[]{"内置宋体","黑体","系统"},draft.fontFamily,isNight());fonts.body.addView(faces);
        fonts.body.addView(sectionLabel("简繁显示（仅本书）"));
        ChoiceStrip script=new ChoiceStrip(typesetContext,new String[]{"原文","简体","繁體"},book.scriptMode,isNight());script.setTag("reading-script-mode");fonts.body.addView(script);
        TextView scriptHint=text("离线转换正文和页眉，保留原文件。搜索、摘录面板、导出与听书沿用原文；正文系统复制取得显示文字。",12,muted);fonts.body.addView(scriptHint);
        final int[] scriptTicket={0};
        script.onChanged(()->{
            final int mode=script.selected(),token=++scriptTicket[0];final String source=document.text;
            if(mode==book.scriptMode){scriptHint.setText("已选择当前显示方式。");return;}
            scriptHint.setText("正在准备简繁显示…");
            io.execute(()->{
                try {String converted=ChineseText.convert(getApplicationContext(),source,mode);
                    runOnUiThread(()->{
                        if(token!=scriptTicket[0]||isDestroyed()||active!=book||settings[0]==null||!settings[0].isShowing()||document.text!=source)return;
                        int previous=book.scriptMode;
                        synchronized(store){book.scriptMode=mode;try{store.save();}catch(Exception e){book.scriptMode=previous;script.select(previous);scriptHint.setText("未能保存，保留原显示："+message(e));return;}}
                        readerText=converted;scriptHint.setText("已切换；原文、书签和笔记位置保持不变。");preview.run();
                    });
                }catch(Exception e){runOnUiThread(()->{if(token==scriptTicket[0]&&active==book&&settings[0]!=null&&settings[0].isShowing()){script.select(book.scriptMode);scriptHint.setText("转换失败，保留原显示："+message(e));}});}
            });
        });
        Button customFont=button("自定义字体 · "+(draft.customFont.isEmpty()?"未选用":FontLibrary.name(this,draft.customFont)),()->chooseCustomFont(draft.customFont,id->{if(active!=book||settings[0]==null||!settings[0].isShowing())return;draft.customFont=id;pitch[0].run();preview.run();}));customFont.setMaxLines(1);customFont.setEllipsize(TextUtils.TruncateAt.END);fonts.body.addView(customFont);
        fonts.body.addView(text("导入本机 TTF / OTF 字体（最多20 MB）。字体文件不随书库备份，换机需重导入；缺失时回退内置字形。",12,muted));
        TextView font=sectionLabel("字号 · "+draft.font);fonts.body.addView(font);
        SeekBar size=new SeekBar(typesetContext);size.setMax(20);size.setProgress(draft.font-16);fonts.body.addView(size,new LinearLayout.LayoutParams(-1,dp(48)));
        size.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){draft.font=16+value;font.setText("字号 · "+draft.font);pitch[0].run();sample.setTextSize(draft.font);summaries[0].run();}public void onStopTrackingTouch(SeekBar s){preview.run();}});
        fonts.body.addView(button("宋体来源与开源授权",()->fontLicense()));

        TextView line=sectionLabel(String.format(Locale.CHINA,"行距 · %.2f · 行高约 %d dp",draft.spacing,linePitchDp(draft.font,draft.fontFamily,draft.spacing)));paragraphs.body.addView(line);
        pitch[0]=()->line.setText(String.format(Locale.CHINA,"行距 · %.2f · 行高约 %d dp",draft.spacing,linePitchDp(draft.font,draft.fontFamily,draft.spacing)));
        SeekBar lines=new SeekBar(typesetContext);lines.setMax(10);lines.setProgress(Math.round((draft.spacing-1.2f)*10));paragraphs.body.addView(lines,new LinearLayout.LayoutParams(-1,dp(48)));
        lines.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){draft.spacing=1.2f+value/10f;line.setText(String.format(Locale.CHINA,"行距 · %.2f · 行高约 %d dp",draft.spacing,linePitchDp(draft.font,draft.fontFamily,draft.spacing)));sample.setLineSpacing(0,draft.spacing);summaries[0].run();}public void onStopTrackingTouch(SeekBar s){preview.run();}});
        TextView margin=sectionLabel("页边距 · "+draft.margin);paragraphs.body.addView(margin);
        SeekBar margins=new SeekBar(typesetContext);margins.setMax(44);margins.setProgress(draft.margin-12);paragraphs.body.addView(margins,new LinearLayout.LayoutParams(-1,dp(48)));
        margins.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){draft.margin=12+value;margin.setText("页边距 · "+draft.margin);summaries[0].run();}public void onStopTrackingTouch(SeekBar s){preview.run();}});
        paragraphs.body.addView(sectionLabel("正文对齐"));ChoiceStrip alignment=new ChoiceStrip(typesetContext,new String[]{"左对齐","两端对齐"},draft.justify?1:0,isNight());paragraphs.body.addView(alignment);
        if(Build.VERSION.SDK_INT<26)paragraphs.body.addView(text("Android 8 以下使用左对齐。",12,muted));
        TextView paragraphLabel=sectionLabel("段距 · "+draft.paragraphGap);paragraphs.body.addView(paragraphLabel);
        SeekBar paragraph=new SeekBar(typesetContext);paragraph.setMax(24);paragraph.setProgress(draft.paragraphGap);paragraphs.body.addView(paragraph,new LinearLayout.LayoutParams(-1,dp(48)));
        ToggleRow indent=new ToggleRow(typesetContext,"首行缩进两字",draft.indent,isNight());paragraphs.body.addView(indent);
        TextView verticalLabel=sectionLabel("上下边距 · "+Math.max(4,Math.min(40,prefs.getInt("verticalMargin",4)))+" dp（全局）");paragraphs.body.addView(verticalLabel);
        SeekBar verticalSeek=new SeekBar(typesetContext);verticalSeek.setMax(36);verticalSeek.setProgress(Math.max(0,Math.min(40,prefs.getInt("verticalMargin",4)))-4);paragraphs.body.addView(verticalSeek,new LinearLayout.LayoutParams(-1,dp(48)));
        verticalSeek.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){if(!user)return;int vm=4+value;prefs.edit().putInt("verticalMargin",vm).apply();verticalLabel.setText("上下边距 · "+vm+" dp（全局）");}public void onStopTrackingTouch(SeekBar s){if(active==book){capturePage();showReader(offset);revealPage();}}});

        ThemeSwatches themes=new ThemeSwatches(typesetContext,draft.palette,isNight(),index->{draft.palette=index;autoNight.select(0);preview.run();});themes.setTag("reader-theme-swatches");themesGroup.body.addView(themes);
        themesGroup.body.addView(sectionLabel("全局 · 自动夜间"));themesGroup.body.addView(autoNight);autoNight.onChanged(preview);
        final int[] times={prefs.getInt("nightStart",1320),prefs.getInt("nightEnd",420)};
        LinearLayout timeRow=row();Button from=button("开始 "+clockText(times[0]),()->{}),until=button("结束 "+clockText(times[1]),()->{});
        timeRow.addView(from,new LinearLayout.LayoutParams(0,-2,1));timeRow.addView(until,new LinearLayout.LayoutParams(0,-2,1));themesGroup.body.addView(timeRow);nightTimes[0]=timeRow;
        timeRow.setVisibility(autoNight.selected()==2?View.VISIBLE:View.GONE);autoNight.onChanged(()->{timeRow.setVisibility(autoNight.selected()==2?View.VISIBLE:View.GONE);preview.run();});
        from.setOnClickListener(v->editNightTime(times,0,from,"夜读开始"));until.setOnClickListener(v->editNightTime(times,1,until,"夜读结束"));

        ChoiceStrip modes=new ChoiceStrip(typesetContext,new String[]{"上下滚动","逐页翻阅"},pagedMode?1:0,isNight());reading.body.addView(modes);
        ToggleRow motion=new ToggleRow(typesetContext,"翻页动画",pageMotion,isNight());reading.body.addView(motion);
        reading.body.addView(sectionLabel("分页效果（全局）"));
        ChoiceStrip pageEffect=new ChoiceStrip(typesetContext,new String[]{"平移滑动","仿真卷页"},prefs.getInt("pageEffect",1)==1?1:0,isNight());pageEffect.setTag("reading-page-effect");reading.body.addView(pageEffect);
        ToggleRow turnDust=new ToggleRow(typesetContext,"翻页完成 · 边框雪尘",prefs.getBoolean("pageSnowDust",true),isNight());turnDust.setTag("reading-page-snow");reading.body.addView(turnDust);
        reading.body.addView(text("仅分页且开启动画时生效；减少动效时关闭卷页与雪尘。",12,muted));
        reading.body.addView(sectionLabel("左右滑动方向"));ChoiceStrip swipeDirection=new ChoiceStrip(typesetContext,new String[]{"左滑下一页","右滑下一页"},prefs.getBoolean("rightSwipeNext",false)?1:0,isNight());swipeDirection.setTag("reading-swipe-direction");reading.body.addView(swipeDirection);
        ToggleRow reduce=new ToggleRow(typesetContext,"减少动效",prefs.getBoolean("reduceMotion",false),isNight());reading.body.addView(reduce);

        display.body.addView(sectionLabel("页眉与页脚（全局）"));
        ToggleRow footerInfo=new ToggleRow(typesetContext,"页脚 · 进度、时间与电量",prefs.getBoolean("readerFooter",true),isNight()),headerInfo=new ToggleRow(typesetContext,"页眉 · 当前章节标题",prefs.getBoolean("readerHeader",false),isNight());footerInfo.setTag("reading-footer-toggle");headerInfo.setTag("reading-header-toggle");display.body.addView(footerInfo);display.body.addView(headerInfo);
        display.body.addView(text("随阅读纸色自动配色，并为正文预留空间。关闭两项即可纯净阅读。",12,muted));
        Runnable furniture=()->{prefs.edit().putBoolean("readerFooter",footerInfo.checked()).putBoolean("readerHeader",headerInfo.checked()).apply();preview.run();};footerInfo.onChanged(furniture);headerInfo.onChanged(furniture);
        display.body.addView(sectionLabel("亮度与屏幕"));
        ToggleRow followBrightness=new ToggleRow(typesetContext,"跟随系统亮度",prefs.getBoolean("followBrightness",true),isNight());display.body.addView(followBrightness);
        TextView lightLabel=sectionLabel("阅读亮度 · "+Math.round(prefs.getFloat("brightness",.5f)*100)+"%");display.body.addView(lightLabel);
        SeekBar light=new SeekBar(typesetContext);light.setMax(99);light.setProgress(Math.max(0,Math.round(prefs.getFloat("brightness",.5f)*100)-1));light.setEnabled(!followBrightness.checked());display.body.addView(light,new LinearLayout.LayoutParams(-1,dp(48)));
        ToggleRow awake=new ToggleRow(typesetContext,"阅读时保持屏幕常亮",draft.awake,isNight());display.body.addView(awake);
        ToggleRow fullscreen=new ToggleRow(typesetContext,"工具栏收起时全屏",prefs.getBoolean("fullscreen",false),isNight());display.body.addView(fullscreen);
        display.body.addView(sectionLabel("全局 · 屏幕方向"));ChoiceStrip direction=new ChoiceStrip(typesetContext,new String[]{"跟随系统","锁定竖屏","锁定横屏"},prefs.getInt("orientation",0),isNight());display.body.addView(direction);
        Runnable lightPreview=()->{
            float value=(light.getProgress()+1)/100f;prefs.edit().putBoolean("followBrightness",followBrightness.checked()).putFloat("brightness",value).apply();light.setEnabled(!followBrightness.checked());lightLabel.setText("阅读亮度 · "+Math.round(value*100)+"%");summaries[0].run();applyReaderWindow();
            if(settings[0]!=null){WindowManager.LayoutParams a=settings[0].getWindow().getAttributes();a.screenBrightness=ReaderPolicy.brightness(followBrightness.checked(),value);settings[0].getWindow().setAttributes(a);}
        };followBrightness.onChanged(lightPreview);light.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user)lightPreview.run();}});

        ToggleRow twoFinger=new ToggleRow(typesetContext,"双指上下滑调亮度",prefs.getBoolean("twoFingerBrightness",false),isNight()),doubleMark=new ToggleRow(typesetContext,"双击正文中间添加书签",prefs.getBoolean("doubleBookmark",false),isNight()),vertical=new ToggleRow(typesetContext,"上下滑动翻页（仅分页）",prefs.getBoolean("verticalPages",false),isNight()),volume=new ToggleRow(typesetContext,"音量键翻页（仅分页）",prefs.getBoolean("volumePages",false),isNight()),repeat=new ToggleRow(typesetContext,"长按音量键连续翻页",prefs.getBoolean("volumeRepeat",true),isNight()),leftHand=new ToggleRow(typesetContext,"左手模式（互换左右点击翻页）",prefs.getBoolean("leftHand",false),isNight());
        
        gestures.body.addView(twoFinger);gestures.body.addView(doubleMark);gestures.body.addView(vertical);gestures.body.addView(leftHand);gestures.body.addView(volume);gestures.body.addView(repeat);

        summaries[0]=()->{
            String[] family={"内置宋体","黑体","系统"},paper={"原纸","暖纸","夜读"};
            customFont.setText("自定义字体 · "+(draft.customFont.isEmpty()?"未选用":FontLibrary.name(this,draft.customFont)));fonts.summary((draft.customFont.isEmpty()?family[Math.max(0,Math.min(2,draft.fontFamily))]:FontLibrary.name(this,draft.customFont))+" · "+draft.font+" 号");
            paragraphs.summary(String.format(Locale.CHINA,"行距 %.2f · 留白 %d · 段距 %d · 上下 %d dp",draft.spacing,draft.margin,paragraph.getProgress(),Math.max(4,Math.min(40,prefs.getInt("verticalMargin",4)))));
            themesGroup.summary(paper[Math.max(0,Math.min(2,draft.palette))]+(autoNight.selected()==0?" · 手动":autoNight.selected()==1?" · 跟随系统":" · 定时夜读"));
            reading.summary((modes.selected()==0?"上下滚动":"逐页翻阅")+" · "+(swipeDirection.selected()==1?"右滑下一页":"左滑下一页")+(reduce.checked()?" · 减少动效":motion.checked()?" · 翻页动画开启":" · 翻页动画关闭"));
            display.summary((footerInfo.checked()?"页脚开":"页脚关")+" · "+(headerInfo.checked()?"页眉开":"页眉关")+" · "+(followBrightness.checked()?"亮度跟随系统":"亮度 "+(light.getProgress()+1)+"%")+(awake.checked()?" · 常亮开启":""));
            gestures.summary((doubleMark.checked()?"双击书签开启":"双击书签关闭")+" · "+(volume.checked()?"音量翻页开启":"音量翻页关闭")+(leftHand.checked()?" · 左手模式（左右点击已互换）":""));
        };
        Runnable typography=()->{draft.fontFamily=faces.selected();draft.justify=alignment.selected()==1&&Build.VERSION.SDK_INT>=26;draft.paragraphGap=paragraph.getProgress();draft.indent=indent.checked();draft.awake=awake.checked();paragraphLabel.setText("段距 · "+draft.paragraphGap);line.setText(String.format(Locale.CHINA,"行距 · %.2f · 行高约 %d dp",draft.spacing,linePitchDp(draft.font,draft.fontFamily,draft.spacing)));preview.run();};
        faces.onChanged(()->{draft.customFont="";typography.run();});alignment.onChanged(typography);indent.onChanged(typography);awake.onChanged(typography);
        paragraph.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar s,int value,boolean user){paragraphLabel.setText("段距 · "+value);summaries[0].run();}public void onStopTrackingTouch(SeekBar s){typography.run();}});
        swipeDirection.onChanged(summaries[0]);modes.onChanged(summaries[0]);motion.onChanged(summaries[0]);reduce.onChanged(()->{prefs.edit().putBoolean("reduceMotion",reduce.checked()).apply();if(reduce.checked()&&settings[0]!=null)Motion.finishTree(settings[0].getWindow().getDecorView());summaries[0].run();});volume.onChanged(summaries[0]);
        leftHand.onChanged(()->{summaries[0].run();toast(leftHand.checked()?"左手模式已开启：左右点击翻页已互换，关闭回到这里即可":"左手模式已关闭：点击右侧为下一页");});
        scope.onChanged(()->{
            if(!scope.checked()){
                Typeset global=ReaderTypography.global(this);draft.font=global.font;draft.margin=global.margin;draft.spacing=global.spacing;draft.palette=global.palette;draft.fontFamily=global.fontFamily;draft.customFont=global.customFont;draft.paragraphGap=global.paragraphGap;draft.indent=global.indent;draft.justify=global.justify;draft.awake=global.awake;
                size.setProgress(global.font-16);margins.setProgress(global.margin-12);lines.setProgress(Math.round((global.spacing-1.2f)*10));themes.select(global.palette);faces.select(global.fontFamily);alignment.select(global.justify?1:0);paragraph.setProgress(global.paragraphGap);indent.checked(global.indent);awake.checked(global.awake);
            }preview.run();
        });
        settings[0]=new AppSheet.Builder(typesetContext,isNight()).setSnowflakes(true).setTitle("阅读排版").setContentFraction(.62f).setView(panel).setOnDismissListener(d->{
            draft.awake=awake.checked();draft.indent=indent.checked();draft.paragraphGap=paragraph.getProgress();Typeset before=book.typeset;
            synchronized(store){book.typeset=scope.checked()?draft.copy():null;try{store.save();if(!scope.checked())saveGlobalTypeset(draft);}catch(Exception e){book.typeset=before;toast("排版未保存："+message(e));}}
            pagedMode=modes.selected()==1;pageMotion=motion.checked();
            prefs.edit().putBoolean("pagedMode",pagedMode).putBoolean("pageMotion",pageMotion).putInt("pageEffect",pageEffect.selected()).putBoolean("pageSnowDust",turnDust.checked()).putBoolean("rightSwipeNext",swipeDirection.selected()==1).putBoolean("twoFingerBrightness",twoFinger.checked()).putBoolean("doubleBookmark",doubleMark.checked()).putBoolean("verticalPages",vertical.checked()).putBoolean("volumePages",volume.checked()).putBoolean("volumeRepeat",repeat.checked()).putBoolean("fullscreen",fullscreen.checked()).putBoolean("leftHand",leftHand.checked()).putBoolean("reduceMotion",reduce.checked()).putInt("orientation",direction.selected()).putInt("autoNight",autoNight.selected()).putInt("nightStart",times[0]).putInt("nightEnd",times[1]).apply();previewTypeset=null;previewNightMode=null;
            if(!tearingDown&&!isDestroyed()&&!isFinishing()&&active==book){showReader(offset);applyReaderWindow();if(recolor[0])appearance(savedScroll[0]);}
        }).show();
        if(restoreScroll>0)panel.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){panel.removeOnLayoutChangeListener(this);if(panel.getParent() instanceof ScrollView)((ScrollView)panel.getParent()).scrollTo(0,restoreScroll);}});
        preview.run();lightPreview.run();
    }

    private void fontLicense(){try(InputStream in=getAssets().open("fonts/OFL.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()){BackupArchive.copy(in,out,65536);TextView text=text("融雪宋体子集：基于 Adobe 思源宋体 CN Regular 2.003，精简为 7,812 字符并改名，遵循 SIL OFL 1.1。缺字由系统字体补充。\n\n"+new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),13,ink);text.setTextIsSelectable(true);dialog().setTitle("字体来源与授权").setView(text).show();}catch(Exception e){error("无法读取字体授权",e);}}
    private void applyAwake() {
        if (effectiveTypeset().awake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void readerOptions() {
        boolean searchBack=searchSession!=null&&document==searchSessionDoc&&!searchSession.hits.isEmpty();int s=searchBack?1:0;
        ArrayList<String> options=new ArrayList<>(Arrays.asList("上一章","下一章","输入章号跳转"));ArrayList<String> hints=new ArrayList<>(Arrays.asList("跳到上一章开头","跳到下一章开头","直接输入编号跳到任意一章"));
        if(searchBack){options.add("回到搜索结果");hints.add("重新打开上次搜索 · "+searchSession.hits.size()+" 处命中");}
        options.addAll(Arrays.asList("阅读设置","切换编码","听书","返回上一位置","选段摘录／笔记","笔记与划线"));hints.addAll(Arrays.asList("字号、行距、边距与阅读主题","乱码时换一种编码，进度自动映射","朗读本书，支持后台与锁屏控制","回到刚才离开的位置","选择文字、复制、划线或写笔记","查看、修改和导出本书笔记"));
        dialog().setSnowflakes(true).setTitle(active.title).setItemHints(hints.toArray(new String[0])).setItems(options.toArray(new String[0]),(d,w)-> {
            int offset=position();
            if(w==0) {
                int ordinal=document.numberedOrdinalAt(offset);
                TextDocument.Chapter current=document.chapters.get(document.chapterAt(offset));
                int previous=current.kind==TextDocument.Chapter.CHAPTER?ordinal-2:ordinal-1;
                jumpTo(previous<0?0:document.numberedChapters.get(previous).offset);
            }
            if(w==1) {
                for(TextDocument.Chapter c:document.numberedChapters) if(c.offset>offset) {jumpTo(c.offset);return;}
                toast("已经是最后一章");
            }
            if(w==2) chapterJump();
            if(w==2+s&&searchBack){searchTerm=searchSessionTerm;search();return;}
            if(w==3+s) appearance();
            if(w==4+s){if(active.epub())toast("EPUB 编码由书籍定义，无需切换");else pickEncoding(enc->changeEncoding(active,enc));}if(w==5+s)listenUi.open();if(w==6+s){int previous=navigation.pop();if(previous>=0)jumpTo(previous);else toast("还没有跳转历史");}if(w==7+s)copySentence(position());if(w==8+s)notes();
        }).setNegativeButton("关闭",null).show();
    }
    private String clockText(int minutes){return String.format(Locale.CHINA,"%02d:%02d",minutes/60,minutes%60);}
    private void editNightTime(int[] times,int index,Button button,String label){
        LinearLayout panel=column();EditText input=new EditText(uiContext());input.setSingleLine(true);input.setHint("HH:mm，例如 22:00");input.setText(clockText(times[index]));panel.addView(input);TextView hint=text("使用 24 小时制。",12,muted);panel.addView(hint);
        AppSheet sheet=dialog().setTitle(label).setView(panel).setPositiveButton("保存时间",null).show();sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{String value=input.getText().toString().trim();if(!value.matches("(?:[01]?[0-9]|2[0-3]):[0-5][0-9]")){hint.setText("请输入 00:00 至 23:59 的时间");return;}String[] parts=value.split(":");times[index]=Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);button.setText(label+" "+clockText(times[index]));sheet.dismiss();});
    }
    private int effectivePalette(){java.util.Calendar now=java.util.Calendar.getInstance();boolean night=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;return ReaderPolicy.palette(manualPalette,previewNightMode==null?prefs.getInt("autoNight",0):previewNightMode,night,now.get(java.util.Calendar.HOUR_OF_DAY)*60+now.get(java.util.Calendar.MINUTE),prefs.getInt("nightStart",22*60),prefs.getInt("nightEnd",7*60));}
    private void refreshAutoTheme(){if(prefs==null||failed||busy||!hasWindowFocus())return;int p=effectivePalette();boolean dark=uiDark();if(p==palette&&dark==appearanceDark)return;int offset=position();boolean readerChanged=p!=palette;palette=p;appearanceDark=dark;if(statsPage){showReadingStats();}else if(active!=null&&document!=null){capturePage();showReader(offset);revealPage();}else if(store!=null)showLibrary(shelfQuery);}
    private void applyReaderWindow(){
        if(prefs==null)return;WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=active==null?-1:ReaderPolicy.brightness(prefs.getBoolean("followBrightness",true),prefs.getFloat("brightness",.5f));getWindow().setAttributes(a);
        int direction=active==null?0:prefs.getInt("orientation",0);int orientation=direction==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:direction==2?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;if(getRequestedOrientation()!=orientation)setRequestedOrientation(orientation);
        if(active==null)getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else applyAwake();
        EdgeToEdge.immersive(getWindow(),active!=null&&prefs.getBoolean("fullscreen",false)&&!chrome.isVisible());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int code=e.getKeyCode();if(active!=null&&pagedReader!=null&&hasWindowFocus()&&prefs.getBoolean("volumePages",false)&&(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN)){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&(e.getRepeatCount()==0||prefs.getBoolean("volumeRepeat",true))&&!pagedReader.isIndexing()&&(e.getRepeatCount()==0||SystemClock.elapsedRealtime()-lastVolumePage>=250)){lastVolumePage=SystemClock.elapsedRealtime();if(code==KeyEvent.KEYCODE_VOLUME_UP)pagedReader.previous();else pagedReader.next();}return true;
        }return super.dispatchKeyEvent(e);
    }
    private void navigateTo(int target){navigation.push(position());jumpTo(target);}
    private void receiveExternal(Intent intent){
        if(intent==null)return;String action=intent.getAction();if(!Intent.ACTION_VIEW.equals(action)&&!Intent.ACTION_SEND.equals(action))return;
        Uri uri=Intent.ACTION_VIEW.equals(action)?intent.getData():intent.getParcelableExtra(Intent.EXTRA_STREAM);intent.setAction(null);
        if(uri!=null&&("content".equals(uri.getScheme())||"file".equals(uri.getScheme())))externalFile=uri;else toast("请分享或打开 TXT / EPUB 文件附件");
    }
    private void consumeExternal(){if(externalFile==null||busy||failed||tearingDown)return;Uri uri=externalFile;externalFile=null;savePosition();importSingle(uri,null);}
    private TaskControl showCancellableTask(String title,String msg){
        taskTitle=title;TaskProgress task=new TaskProgress(uiContext(),uiDark());taskProgress=task;task.report(0,0,msg);
        TaskControl control=new TaskControl((done,total,text)->task.report(done,total,text));taskControl=control;
        LinearLayout panel=column();panel.addView(task);panel.addView(button("取消操作",()->requestTaskCancel()),new LinearLayout.LayoutParams(-1,dp(48)));
        loading=dialog().setTitle(title).setView(panel).setCancelable(false).setOnBack(()->requestTaskCancel()).show();return control;
    }
    private void requestTaskCancel(){
        TaskControl control=taskControl;if(control==null){if(busy)toast("请稍候，当前操作即将完成");return;}
        if(control.committing()){toast("正在完成最后的书库切换，请稍候");return;}
        if(cancellationSheet!=null&&cancellationSheet.isShowing())return;
        cancellationSheet=dialog().setTitle("取消"+taskTitle+"？").setMessage("取消恢复会保留原书库。导出中断产生的文件会尝试清理，无法清理时请手动删除。").setPositiveButton("取消本次操作",(d,w)->{if(control==taskControl){if(control.cancel()){if(taskProgress!=null)taskProgress.report(0,0,"正在取消并清理临时文件…");}else toast("正在完成最后的书库切换");}}).show();
    }
    private void taskFailure(String title,Exception e,Uri output){
        if(output!=null)io.execute(()->{try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),output);}catch(Exception ignored){}});
        if(e instanceof TaskControl.Cancelled)toast("操作已取消，书库保留；导出文件若仍存在请删除后重试");else error(title,e);
    }
    private void releasePrepared(BackupArchive.Prepared p){if(preparedBackup==p)preparedBackup=null;io.execute(()->p.close());}
    private void confirmReplace(BackupArchive.Prepared prepared){
        ArrayList<String> removed=new ArrayList<>(),cats=new ArrayList<>();for(LibraryStore.Book b:store.books)if(prepared.store.find(b.id)==null)removed.add(b.title);
        for(Map.Entry<String,String> entry:store.organization.categories.entrySet())if(!entry.getValue().equals(prepared.store.organization.categories.get(entry.getKey())))cats.add(entry.getValue());
        ArrayList<String> labels=new ArrayList<>();labels.add("将删除 "+removed.size()+" 本当前独有的书");labels.addAll(removed);labels.add("将删除或替换 "+cats.size()+" 个分类");labels.addAll(cats);
        ListView inventory=new ListView(this);inventory.setDivider(null);inventory.setAdapter(new BaseAdapter(){public int getCount(){return labels.size();}public Object getItem(int p){return labels.get(p);}public long getItemId(int p){return p;}public View getView(int p,View reuse,ViewGroup parent){TextView label=reuse instanceof TextView?(TextView)reuse:text("",14,ink);label.setText(labels.get(p));label.setPadding(dp(10),dp(10),dp(10),dp(10));return label;}});
        final boolean[] accepted={false};dialog().setTitle("确认覆盖当前书架").setMessage("覆盖后仅保留备份的书籍与分类，同书的封面、置顶、进度和书签也采用备份记录。此操作不经过回收站，建议先导出当前书库。").setFixedView(inventory).setPositiveButton("确认覆盖恢复",(d,w)->{accepted[0]=true;restoreBackup(prepared,false,true);}).setOnDismissListener(d->{if(!accepted[0])releasePrepared(prepared);}).show();
    }
    private void confirmDeleteSelected(){
        if(busy)return;ArrayList<LibraryStore.Book> books=selected();if(books.isEmpty())return;
        StringBuilder names=new StringBuilder();for(int i=0;i<Math.min(4,books.size());i++)names.append("《").append(books.get(i).title).append("》\n");if(books.size()>4)names.append("等 ").append(books.size()).append(" 本\n");
        LinearLayout preview=column();TextView titles=text(names.toString().trim(),14,ink);titles.setLineSpacing(dp(4),1);preview.addView(titles);TextView hint=text("将从书架移入回收站，保留 7 天，可恢复正文、封面、进度和书签。手机里的原始书籍文件会保留。",13,muted);hint.setPadding(0,dp(12),0,0);preview.addView(hint);
        dialog().setTitle("删除所选的 "+books.size()+" 本书？").setView(preview).setPositiveButton("移入回收站",(d,w)->removeSelectedToRecycle(books)).show();
    }
    private void removeSelectedToRecycle(ArrayList<LibraryStore.Book> books){
        if(busy)return;if(listenUi!=null)for(LibraryStore.Book book:books)listenUi.stopIfBook(book.id);
        busy=true;showLoading("正在保存 "+books.size()+" 本书的回收副本…");
        io.execute(()->{try{
            ArrayList<RecycleBin.Entry> entries=recycleBin.removeAll(store,books,System.currentTimeMillis());repository.invalidate();
            runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;for(LibraryStore.Book book:books)coverCache.remove(book.id);selectedBooks.clear();selecting=false;showLibrary(shelfQuery);
                dialog().setTitle("已移入回收站").setMessage(entries.size()+" 本书已从书架移出，保留 7 天。可在“顶部更多 → 数据与备份 → 回收站”恢复。").setPositiveButton("查看回收站",(d,w)->showRecycleBin()).setNeutralButton("撤销，放回书架",(d,w)->restoreBatch(entries)).show();});
        }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()){refreshSelection();error("删除未完成，所选书籍保留",e);}});}});
    }
    private void removeToRecycle(LibraryStore.Book book){
        if(busy)return;if(listenUi!=null)listenUi.stopIfBook(book.id);busy=true;showLoading("正在保存可撤销的副本…");
        io.execute(()->{try{RecycleBin.Entry entry=recycleBin.remove(store,book,System.currentTimeMillis());repository.invalidate();
                runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;coverCache.remove(book.id);showLibrary(shelfQuery);dialog().setTitle("已移入回收站").setMessage("「"+book.title+"」保留 7 天，可在“更多 → 回收站”恢复。").setPositiveButton("撤销移出",(d,w)->restoreRecycled(entry)).show();});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("移出失败，书籍保留",e);});}});
    }
    private void showRecycleBin(){
        if(busy)return;busy=true;showLoading("正在读取回收站…");io.execute(()->{ArrayList<RecycleBin.Entry> entries=recycleBin.list(System.currentTimeMillis());long recycleBytes=recycleBin.bytes();runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;String[] labels=new String[entries.size()];for(int i=0;i<labels.length;i++){RecycleBin.Entry e=entries.get(i);long days=Math.max(1,(RecycleBin.RETENTION-(System.currentTimeMillis()-e.removedAt)+86400000-1)/86400000);labels[i]=e.book.title+" · 约 "+days+" 天后清理";}
            AppSheet.Builder bin=dialog().setTitle("回收站 · "+entries.size()+" 本 · "+String.format(Locale.CHINA,"%.2f MB",recycleBytes/(1024.0*1024))).setMessage(entries.isEmpty()?"回收站是空的。过期记录会在打开回收站时清理。":"点击选择恢复或永久删除。回收站不包含在整库备份中。").setItems(labels,(d,w)->{RecycleBin.Entry entry=entries.get(w);dialog().setTitle(entry.book.title).setItems(new String[]{"恢复正文、封面和阅读记录","永久删除…"},(choice,index)->{if(index==0)restoreRecycled(entry);else dialog().setTitle("永久删除这本书？").setMessage("此操作无法撤销。").setPositiveButton("永久删除",(confirm,which)->{try{recycleBin.delete(entry);showRecycleBin();}catch(Exception e){error("清理失败",e);}}).show();}).show();});if(recycleBytes>0)bin.setNeutralButton("全部清空回收站…",(d,w)->dialog().setTitle("永久清空回收站？").setMessage("将删除回收站全部正文、封面和阅读记录，此操作无法撤销。当前书架保留。").setPositiveButton("永久清空",(a,b)->{if(busy)return;busy=true;showLoading("正在清理回收站…");io.execute(()->{try{recycleBin.clear();runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())showRecycleBin();});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("部分记录未能清理",e);});}});}).show());bin.show();});});
    }
    private void restoreRecycled(RecycleBin.Entry entry){
        if(busy)return;busy=true;showLoading("正在恢复书籍…");io.execute(()->{try{recycleBin.restore(store,entry);repository.invalidate();runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()){shelfCategory=ShelfOrganization.ALL;coverCache.remove(entry.book.id);showLibrary("");toast("书籍及阅读记录已恢复");}});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("恢复未完成，回收记录保留",e);});}});
    }
    /** One-tap undo for a batch delete: put every recycle copy straight back onto the shelf. */
    private void restoreBatch(ArrayList<RecycleBin.Entry> entries){
        if(busy||entries.isEmpty())return;busy=true;showLoading("正在把 "+entries.size()+" 本书放回书架…");
        io.execute(()->{try{
                for(RecycleBin.Entry entry:entries)recycleBin.restore(store,entry);repository.invalidate();runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;for(RecycleBin.Entry entry:entries)coverCache.remove(entry.book.id);showLibrary(shelfQuery);toast("已放回书架");});
            }catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()){showLibrary(shelfQuery);error("部分书籍未能恢复，其余已放回，可在回收站查看",e);}});}});
    }
    private void copySentence(int requested){
        if(active!=null&&document!=null&&!BookNote.overlapping(active.annotations,requested,requested+1).isEmpty()){manageRangeNotes(requested,requested+1,false);return;}
        if(document==null)return;int at=Math.max(0,Math.min(requested,document.text.length()-1)),start=at;String body=document.text;
        while(start>0&&at-start<5000&&body.charAt(start-1)!='\n')start--;if(start>0&&Character.isLowSurrogate(body.charAt(start)))start--;
        TextChunker.SpeechChunk chunk=TextChunker.next(body,start,body.length(),2400);while(chunk!=null&&chunk.end<=at)chunk=TextChunker.next(body,chunk.end,body.length(),2400);if(chunk==null)return;
        final int offset=chunk.start;final String value=body.substring(chunk.start,chunk.end);TextView preview=text(value,16,ink);preview.setTextIsSelectable(true);
        LinearLayout excerptPanel=column();excerptPanel.addView(text("长按文字选择范围；未选中时使用整段。",12,muted));excerptPanel.addView(preview);Button annotate=button("划线／写笔记",()->{});excerptPanel.addView(annotate);
        AppSheet excerpt=dialog().setTitle("选段摘录").setView(excerptPanel).setPositiveButton("复制句子",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("融雪阅读摘录",value));toast("已复制");}).setNeutralButton("加书签备注",(d,w)->editBookmark(offset)).setNegativeButton("从选中处开始朗读",(d,w)->{}).show();annotate.setOnClickListener(v->{int a=preview.getSelectionStart(),b=preview.getSelectionEnd();int from=a>=0&&b>=0&&a!=b?Math.min(a,b):0,to=a>=0&&b>=0&&a!=b?Math.max(a,b):value.length();excerpt.dismissThen(()->editNote(null,offset+from,offset+to));});excerpt.getButton(AppSheet.BUTTON_NEGATIVE).setOnClickListener(v->{int selectionStart=android.text.Selection.getSelectionStart(preview.getText()),selectionEnd=android.text.Selection.getSelectionEnd(preview.getText());int begin=offset+Math.max(0,Math.min(selectionStart,selectionEnd));excerpt.dismissThen(()->listenUi.startAt(begin));});
    }
    private void notes(){
        if(active==null||document==null)return;final LibraryStore.Book book=active;ArrayList<BookNote> list=new ArrayList<>(book.annotations);Collections.sort(list,(a,b)->{if(a.located()!=b.located())return a.located()?-1:1;return a.located()?Integer.compare(a.start,b.start):Long.compare(a.created,b.created);});
        String[] labels=new String[list.size()];for(int i=0;i<list.size();i++){BookNote n=list.get(i);labels[i]=(n.located()?document.readingTitle(n.start):"待定位")+" · "+(n.style==0?"高亮":"划线")+"\n"+n.quote.substring(0,Math.min(90,n.quote.length())).replace('\n',' ')+(n.note.isEmpty()?"":"\n"+n.note.substring(0,Math.min(80,n.note.length())));}
        dialog().setTitle("笔记与划线 · "+list.size()).setMessage(list.isEmpty()?"长按正文打开摘录，选择文字后添加高亮、划线或笔记。":"待定位的原文与笔记会保留，可重新指定位置。").setItems(labels,(d,w)->{if(active==book)noteDetail(list.get(w));}).setPositiveButton("选段添加",(d,w)->copySentence(position())).setNeutralButton("导出笔记",(d,w)->{if(list.isEmpty()){toast("本书还没有笔记");return;}pendingNoteExport=list;pendingNoteTitle=book.title;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/markdown").putExtra(Intent.EXTRA_TITLE,book.title.replaceAll("[\\\\/:*?\"<>|]","_")+"-笔记.md");launchPicker(intent,EXPORT_NOTES);}).show();
    }
    private void noteDetail(BookNote n){
        if(active==null||document==null)return;final LibraryStore.Book book=active;
        LinearLayout panel=column();TextView quote=text(n.quote,15,ink);quote.setMaxLines(5);quote.setEllipsize(TextUtils.TruncateAt.END);panel.addView(quote);
        if(!n.note.isEmpty()){TextView memo=text(n.note,13,muted);memo.setMaxLines(3);memo.setEllipsize(TextUtils.TruncateAt.END);panel.addView(memo);}
        final AppSheet[] holder={null};
        panel.addView(button("修改备注与样式",()->holder[0].dismissThen(()->{if(active==book)editNoteForm(n,n.start,n.end);} )));
        Button remove=button("删除划线／高亮与笔记",()->holder[0].dismissThen(()->confirmNoteDelete(book,n)));remove.setTag("note-delete");remove.setTextColor(uiDark()?0xFFFFB9AB:0xFF9A493E);panel.addView(remove);
        if(n.located())panel.addView(button("跳转到原文",()->holder[0].dismissThen(()->{if(active==book)navigateTo(n.start);} )));
        panel.addView(button("查看完整摘录与备注",()->holder[0].dismissThen(()->{TextView all=text(n.quote+(n.note.isEmpty()?"":"\n\n备注："+n.note),15,ink);all.setTextIsSelectable(true);dialog().setTitle("完整摘录与备注").setView(all).setContentFraction(.58f).show();})));
        panel.addView(button("复制摘录",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("融雪阅读摘录",n.quote));toast("已复制摘录");}));
        if(!n.located())panel.addView(button("重新选择原文位置",()->holder[0].dismissThen(()->dialog().setTitle("重新定位标记").setMessage("先浏览到正确位置，再使用当前位置的句子替换原摘录，备注会保留。").setPositiveButton("使用当前位置",(d,w)->{if(active!=book)return;int at=position();TextChunker.SpeechChunk chunk=TextChunker.next(document.text,at,document.text.length(),2400);if(chunk!=null)saveNote(book,n,n.rebind(document,chunk.start,chunk.end),null);}).show())));
        holder[0]=dialog().setTitle(n.located()?"划线与笔记":"待定位标记").setView(panel).setContentFraction(.58f).show();
    }
    private void confirmNoteDelete(LibraryStore.Book book,BookNote note){
        if(active!=book)return;dialog().setTitle("删除这条标记？").setMessage("将移除这条划线／高亮和附带备注，正文不会改变。重叠的其他标记会保留。").setPositiveButton("删除",(d,w)->{if(active==book)saveNote(book,note,null,null);}).setNegativeButton("取消",null).show();
    }
    private void manageRangeNotes(int from,int until,boolean deleting){
        if(active==null)return;final LibraryStore.Book book=active;ArrayList<BookNote> list=BookNote.overlapping(book.annotations,from,until);
        if(list.isEmpty()){toast("选中范围内没有已保存的划线或高亮");return;}
        if(list.size()==1){if(deleting)confirmNoteDelete(book,list.get(0));else noteDetail(list.get(0));return;}
        String[] labels=new String[list.size()];for(int i=0;i<list.size();i++){BookNote n=list.get(i);labels[i]=(n.style==0?"高亮 · ":"下划线 · ")+n.quote.substring(0,Math.min(70,n.quote.length())).replace('\n',' ');}
        dialog().setTitle("此处有 "+list.size()+" 条标记").setItems(labels,(d,w)->{if(active!=book)return;if(deleting)confirmNoteDelete(book,list.get(w));else noteDetail(list.get(w));}).show();
    }
    private void editNote(BookNote existing,int from,int to){
        if(active==null||document==null)return;
        if(existing==null&&!BookNote.overlapping(active.annotations,from,to).isEmpty()){
            final LibraryStore.Book book=active;dialog().setTitle("选段中已有标记").setMessage("可以管理或删除已有划线，也可以另外新增一条。").setPositiveButton("管理已有标记",(d,w)->{if(active==book)manageRangeNotes(from,to,false);}).setNeutralButton("另外新增",(d,w)->{if(active==book)editNoteForm(null,from,to);}).show();return;
        }editNoteForm(existing,from,to);
    }
    private void editNoteForm(BookNote existing,int from,int to){
        if(active==null||document==null)return;final LibraryStore.Book book=active;final BookNote base;try{base=existing==null?BookNote.create(document,from,to,"",0):existing;}catch(Exception e){error("无法创建笔记",e);return;}
        LinearLayout panel=column();TextView quote=text(base.quote,15,ink);quote.setMaxLines(6);panel.addView(quote);final int[] kind={base.style};Button style=button(kind[0]==0?"样式：柔和高亮（点击切换）":"样式：下划线（点击切换）",()->{});style.setOnClickListener(v->{kind[0]=1-kind[0];style.setText(kind[0]==0?"样式：柔和高亮（点击切换）":"样式：下划线（点击切换）");});panel.addView(style);EditText note=new EditText(uiContext());note.setHint("写下想法（可留空，最多4000字）");note.setText(base.note);note.setMinLines(3);note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(BookNote.MAX_NOTE)});panel.addView(note);
        AppSheet sheet=dialog().setTitle(existing==null?"添加笔记":"修改笔记").setView(panel).setPositiveButton("保存",null).show();sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->saveNote(book,existing,base.edit(note.getText().toString(),kind[0]),sheet));
    }
    private void saveNote(LibraryStore.Book book,BookNote previous,BookNote replacement,AppSheet sheet){
        if(busy)return;busy=true;if(sheet!=null)sheet.getButton(AppSheet.BUTTON_POSITIVE).setEnabled(false);final int offset=position();
        io.execute(()->{try{store.updateNote(book,previous,replacement);
            runOnUiThread(()->{busy=false;if(isDestroyed())return;Runnable finish=()->{if(active==book){capturePage();showReader(offset);revealPage();}toast(replacement==null?"划线／高亮与笔记已删除":"笔记已保存");};if(sheet!=null&&sheet.isShowing())sheet.dismissThen(finish);else finish.run();});
        }catch(Exception e){runOnUiThread(()->{busy=false;if(isDestroyed())return;if(sheet!=null&&sheet.isShowing())sheet.getButton(AppSheet.BUTTON_POSITIVE).setEnabled(true);error("笔记未保存，原记录保留",e);});}});
    }
    private void finishNoteExport(int result,Intent data){
        final ArrayList<BookNote> list=pendingNoteExport;final String title=pendingNoteTitle;pendingNoteExport=null;pendingNoteTitle=null;if(result!=RESULT_OK||data==null||data.getData()==null||list==null)return;final Uri uri=data.getData();io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入所选位置");Writer writer=new BufferedWriter(new OutputStreamWriter(out,"UTF-8"));NoteExport.write(writer,title,list);writer.flush();runOnUiThread(()->{if(!isDestroyed())toast("笔记已导出");});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error("导出未完成，所选位置可能含不完整文件，请重新导出",e);});}});
    }
    private java.net.InetAddress lanAddress()throws IOException{
        android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);android.net.Network network=cm.getActiveNetwork();android.net.NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);if(caps==null||caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)||(!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)&&!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)))throw new IOException("请连接与电脑相同的 Wi-Fi，并暂时关闭 VPN");android.net.LinkProperties links=cm.getLinkProperties(network);if(links!=null)for(android.net.LinkAddress a:links.getLinkAddresses())if(a.getAddress() instanceof java.net.Inet4Address&&a.getAddress().isSiteLocalAddress())return a.getAddress();throw new IOException("未找到可用的局域网 IPv4 地址，请检查 Wi-Fi");
    }
    private void stopLan(){++lanGeneration;lanStarting=false;LanTransferServer server=lanSession;lanSession=null;if(server!=null)server.close();}
    private void lanTransfer(){
        if(busy)return;LinearLayout panel=column();panel.addView(text("电脑和手机连接同一 Wi-Fi，在电脑浏览器打开地址并输入口令。仅在本面板前台开启时接收，10分钟后自动关闭。\n仅限可信局域网：HTTP 明文传输，口令不等于加密。支持 TXT，每本12MB，一次100本、合计128MB。",13,muted));TextView status=text("尚未开启",15,ink);status.setTextIsSelectable(true);panel.addView(status);Button toggle=button("开启传书",()->{}),copy=button("复制地址与口令",()->{});copy.setEnabled(false);panel.addView(toggle);panel.addView(copy);final AppSheet[] holder={null};
        holder[0]=dialog().setTitle("Wi-Fi 局域网传书").setView(panel).setOnDismissListener(d->{stopLan();if(lanLibraryDirty&&active==null&&!busy){lanLibraryDirty=false;showLibrary(shelfQuery);}}).show();
        copy.setOnClickListener(v->{LanTransferServer server=lanSession;if(server!=null&&server.isOpen()){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("融雪阅读传书",server.url()+"\n口令："+server.code));toast("地址与口令已复制");}});
        Runnable poll=new Runnable(){public void run(){if(isDestroyed()||!holder[0].isShowing())return;LanTransferServer server=lanSession;if(server!=null){try{if(!server.isOpen()||!server.address.equals(lanAddress())){stopLan();status.setText("传书已停止，已导入的书籍保留。");}else status.setText(server.url()+"\n口令："+server.code+"\n"+server.status());}catch(Exception e){stopLan();status.setText("网络已变化，传书已停止。");}}toggle.setEnabled(!lanStarting);toggle.setText(lanStarting?"正在开启…":lanSession==null?"开启传书":"停止传书");copy.setEnabled(lanSession!=null&&lanSession.isOpen());chromeHandler.postDelayed(this,1000);}};chromeHandler.post(poll);
        toggle.setOnClickListener(v->{if(lanSession!=null){stopLan();status.setText("传书已停止");return;}if(lanStarting)return;lanStarting=true;toggle.setEnabled(false);final int token=++lanGeneration;io.execute(()->{try{java.net.InetAddress address=lanAddress();final LanTransferServer[] owner={null};LanTransferServer server=new LanTransferServer(address,getCacheDir(),files->{FutureTask<Boolean> gate=new FutureTask<>(()->{if(busy||!owner[0].isOpen()||isDestroyed())return false;busy=true;return true;});runOnUiThread(gate);if(!gate.get())throw new IOException("当前有其他任务，请稍后重试");Future<BatchImporter.Result> job=io.submit(()->{ArrayList<BatchImporter.Source> sources=new ArrayList<>();for(LanTransferServer.Incoming file:files)sources.add(new BatchImporter.Source(){public String name(){return file.name;}public InputStream open()throws Exception{return new FileInputStream(file.file);}});BatchImporter.Result result; synchronized(store){result=BatchImporter.run(store,sources,"",null,(done,total,name)->{},()->!owner[0].isOpen());}if(result.imported>0)lanLibraryDirty=true;return result;});BatchImporter.Result result;try{result=job.get();}finally{runOnUiThread(()->{busy=false;if(!isDestroyed()&&statsResumed&&active==null&&lanLibraryDirty){lanLibraryDirty=false;showLibrary(shelfQuery);}});}if(result.commitError!=null)throw new IOException("书架未能保存");return result.summary();});owner[0]=server;
                    runOnUiThread(()->{if(token!=lanGeneration||isDestroyed()||!statsResumed||!holder[0].isShowing()){server.close();return;}lanStarting=false;lanSession=server;server.start();status.setText(server.url()+"\n口令："+server.code);toggle.setEnabled(true);});
                }catch(Exception e){runOnUiThread(()->{if(token!=lanGeneration||isDestroyed())return;lanStarting=false;toggle.setEnabled(true);status.setText("无法开启："+message(e));});}});});
    }

    private void editBookmark(int requested){
        final LibraryStore.Book book=active;final int offset=Math.max(0,Math.min(requested,document.text.length()-1));LinearLayout panel=column();panel.addView(text(document.readingTitle(offset),13,muted));EditText note=new EditText(uiContext());note.setHint("备注（可留空）");note.setText(book.markNotes.get(offset));note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});panel.addView(note);
        AppSheet sheet=dialog().setTitle("书签备注").setView(panel).setPositiveButton("保存书签",null).show();sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{synchronized(store){ArrayList<Integer> before=new ArrayList<>(book.marks);String old=book.markNotes.get(offset);if(!book.marks.contains(offset))book.marks.add(offset);Collections.sort(book.marks);String value=note.getText().toString().trim();if(value.isEmpty())book.markNotes.remove(offset);else book.markNotes.put(offset,value);try{ReadingAnchor mark=repository.anchor(book,offset);if(mark!=null)book.markAnchors.put(offset,mark);store.save();sheet.dismissThen(()->{toast("书签已保存");updateProgress(position());bookmarkSnow();});}catch(Exception e){book.marks.clear();book.marks.addAll(before);if(old==null)book.markNotes.remove(offset);else book.markNotes.put(offset,old);error("书签保存失败",e);}}});
    }

    private void quickBookmark(int requested){
        if(active==null||document==null)return;int at=ReadingAnchor.safe(document.text,requested);synchronized(store){if(active.marks.contains(at)){toast("当前位置已有书签");return;}active.marks.add(at);Collections.sort(active.marks);ReadingAnchor anchor=repository.anchor(active,at);if(anchor!=null)active.markAnchors.put(at,anchor);try{store.save();toast("已添加书签");updateProgress(position());bookmarkSnow();}catch(Exception e){active.marks.remove(Integer.valueOf(at));active.markAnchors.remove(at);error("书签未保存",e);}}
    }
    private void bookmarkSnow(){if(readerBottomBar!=null&&readerBottomBar.isShown()){View target=readerBottomBar.findViewWithTag("reader-bookmark-action");if(target!=null)SnowBurst.play(target,isNight());}}
    private void showMigration(LibraryStore.Book book,TextDocument decoded,PositionMigration plan,byte[] replacement,String filename){
        String message="编码："+book.encoding+" → "+decoded.encoding+"\n可定位书签 "+plan.marks.size()+" 个 · 待定位 "+plan.unresolved.size()+" 个\n"+(plan.approximate?"阅读进度未精确匹配，将暂按全书比例定位。":"阅读进度已按正文上下文或章节位置匹配。")+"\n待定位书签及备注保留在书签面板。"+(replacement==null?"原始 TXT 不变。":"替换前的正文与记录保留在回收站 7 天。");
        TextView sample=text(decoded.text.substring(0,Math.min(200,decoded.text.length())),14,ink);sample.setTextIsSelectable(true);
        dialog().setTitle(replacement==null?"确认切换编码":"确认替换正文").setMessage(message).setView(sample).setPositiveButton("应用并保留阅读记录",(d,w)->{
            if(busy)return;busy=true;showLoading("正在保存阅读位置与书签…");io.execute(()->{RecycleBin.Entry archived=null;try{
                String converted=ChineseText.convert(getApplicationContext(),decoded.text,book.scriptMode);
                LibraryStore.Book updated=book;if(replacement==null)store.reencode(book,decoded,plan);else{archived=recycleBin.archive(store,book,System.currentTimeMillis());updated=store.replaceContent(book,replacement,filename,decoded,plan);}final LibraryStore.Book result=updated;repository.adopt(result,decoded);
                runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;coverCache.remove(book.id);if(active==book){active=result;document=decoded;readerText=converted;highlight=-1;searchTerm="";navigation.clear();showReader(plan.offset);}else showLibrary(shelfQuery);toast(plan.unresolved.isEmpty()?"阅读记录已保留":"部分书签待定位，可在书签面板处理");});
            }catch(Exception e){if(archived!=null)try{recycleBin.delete(archived);}catch(Exception ignored){}runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("没有保存成功，原记录保留",e);});}});
        }).show();
    }
    private void chooseReplacement(LibraryStore.Book book){if(book.epub()){toast("EPUB 暂不支持替换正文，请导入为新书");return;}if(busy)return;pendingReplaceId=book.id;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_LOCAL_ONLY,true);launchPicker(pick,PICK_REPLACE);}
    private void readReplacement(Uri uri){String id=pendingReplaceId;pendingReplaceId=null;LibraryStore.Book book=store.find(id);if(book==null||busy)return;if(listenUi!=null)listenUi.stopIfBook(book.id);savePosition();busy=true;showLoading("正在比较新旧正文…");io.execute(()->{try{String filename=displayName(uri);if(!filename.toLowerCase(Locale.ROOT).endsWith(".txt"))throw new IOException("请选择 TXT 文件");byte[] bytes=TextDocument.readLimited(getContentResolver().openInputStream(uri));TextDocument old=repository.load(book),decoded=TextDocument.decode(bytes,null);PositionMigration plan=new PositionMigration(old,decoded,book.offset,book.anchor,book.marks,book.markNotes,book.markAnchors,book.unmappedMarks);runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())showMigration(book,decoded,plan,bytes,filename);});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("新正文无法读取，原书未变更",e);});}});}
    private void commitImport(byte[] bytes,String filename,String title,String category,TextDocument doc){importing(()->{LibraryStore.Book book=store.addPrepared(bytes,filename,title,doc.encoding,category,doc);if(book.encoding.equals(doc.encoding)&&book.length==doc.text.length())repository.adopt(book,doc);return book;},null);}
    private void importPreview(byte[] bytes,String filename,String title,String category,TextDocument doc){
        LinearLayout body=column();EditText name=new EditText(uiContext());name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});name.setText(title);body.addView(name);body.addView(button("恢复为文件名",()->name.setText(TitleCleaner.stem(filename))));TextView encoding=text("检测："+doc.encoding+"（"+("手动指定".equals(doc.encodingConfidence)?"手动指定":"置信度"+doc.encodingConfidence)+"）\n启发式检测不能保证准确，请检查正文。",12,muted);body.addView(encoding);TextView sample=text(doc.text.substring(0,Math.min(200,doc.text.length())),15,ink);sample.setTextIsSelectable(true);sample.setPadding(0,dp(10),0,dp(10));body.addView(sample);
        final AppSheet[] holder={null};body.addView(button("切换编码并预览…",()->pickEncoding(value->{String edited=name.getText().toString();if(busy)return;busy=true;showLoading("正在预览编码…");io.execute(()->{try{TextDocument decoded=TextDocument.decode(bytes,value);runOnUiThread(()->{hideLoading();busy=false;if(isDestroyed())return;holder[0].dismiss();importPreview(bytes,filename,edited,category,decoded);});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("该编码无法解码",e);});}});})));holder[0]=dialog().setTitle("导入预览 · 前 200 字").setView(body).setPositiveButton("确认导入并打开",null).show();holder[0].getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入书名");return;}holder[0].dismiss();commitImport(bytes,filename,value,category,doc);});
    }
    private void unresolvedBookmarks(){
        final LibraryStore.Book book=active;ArrayList<ReadingAnchor.Unmapped> missing=new ArrayList<>(book.unmappedMarks);String[] labels=new String[missing.size()];for(int i=0;i<labels.length;i++){ReadingAnchor.Unmapped m=missing.get(i);labels[i]=m.anchor.chapter+String.format(Locale.CHINA," · 原章内 %.1f%%",m.anchor.ratio*100)+(m.note.isEmpty()?"":"\n"+m.note);}
        dialog().setTitle("待定位书签 · "+missing.size()).setItems(labels,(d,w)->{ReadingAnchor.Unmapped m=missing.get(w);if(active!=book){toast("请先重新打开这本书再定位");return;}dialog().setTitle(m.anchor.chapter).setMessage("原文片段："+m.anchor.context+"\n备注："+m.note+"\n可先关闭面板，浏览到正确位置，再将这条记录放到当前位置。").setPositiveButton("放到当前阅读位置",(a,b)->{if(active!=book){toast("请先重新打开这本书再定位");return;}int at=position();synchronized(store){String old=book.markNotes.get(at);if(old!=null&&!old.isEmpty()&&!m.note.isEmpty()&&!old.equals(m.note)){toast("此处已有另一条备注，请选择其他位置");return;}boolean added=!book.marks.contains(at);if(added)book.marks.add(at);Collections.sort(book.marks);if(!m.note.isEmpty())book.markNotes.put(at,m.note);book.unmappedMarks.remove(m);ReadingAnchor anchor=repository.anchor(book,at);if(anchor!=null)book.markAnchors.put(at,anchor);try{store.save();toast("待定位书签已恢复");updateProgress(at);}catch(Exception e){if(added)book.marks.remove(Integer.valueOf(at));if(old==null)book.markNotes.remove(at);else book.markNotes.put(at,old);book.unmappedMarks.add(m);error("没有保存成功",e);}}}).show();}).show();
    }

    private interface EncodingAction {void choose(String value);}
    private void pickEncoding(EncodingAction action) {
        String[] encodings={"UTF-8","GB18030","UTF-16LE","UTF-16BE","Big5"};
        dialog().setTitle("选择文本编码（GB18030 兼容 GBK）")
            .setItems(encodings,(d,w)->action.choose(encodings[w])).setNegativeButton("取消",null).show();
    }
    private void changeEncoding(LibraryStore.Book book,String encoding){
        if(book.epub()){toast("EPUB 使用内置编码，不需要手动切换");return;}
        savePosition();if(busy)return;if(listenUi!=null)listenUi.stopIfBook(book.id);busy=true;showLoading("正在映射原阅读位置和书签…");io.execute(()->{try{TextDocument old=repository.load(book);TextDocument decoded=TextDocument.decode(TextDocument.readLimited(new FileInputStream(store.file(book))),encoding);if(book.chapterRule!=ChapterRule.AUTO){TextDocument ruled=new TextDocument(decoded.text,decoded.encoding,book.chapterRule);ruled.encodingConfidence=decoded.encodingConfidence;decoded=ruled;}final TextDocument mapped=decoded;PositionMigration plan=new PositionMigration(old,mapped,book.offset,book.anchor,book.marks,book.markNotes,book.markAnchors,book.unmappedMarks);runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())showMigration(book,mapped,plan,null,null);});}catch(Exception e){runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed())error("编码切换失败，原记录保留",e);});}});
    }
    private void chooseCover(LibraryStore.Book book) {
        pendingCoverId=book.id;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
        try {startActivityForResult(intent,PICK_COVER);}
        catch(ActivityNotFoundException error){pendingCoverId=null;error("没有可用的图片选择器",error);}
    }
    private void finishCoverSelection(int result,Intent data) {
        String id=pendingCoverId;pendingCoverId=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||id==null) return;
        LibraryStore.Book selected=null;
        for(LibraryStore.Book candidate:store.books) if(candidate.id.equals(id)){selected=candidate;break;}
        if(selected==null){toast("这本书已不在书架中");return;}
        if(busy)return;busy=true;showLoading("正在保存封面…");Uri uri=data.getData();
        io.execute(()-> {
            try {
                covers.importImage(id,uri);
                runOnUiThread(()-> {
                    hideLoading();busy=false;if(isDestroyed())return;
                    coverCache.remove(id);showLibrary(shelfQuery);toast("封面已更新");
                });
            }catch(Exception error) {runOnUiThread(()->{hideLoading();busy=false;if(!isDestroyed()) error("封面设置失败",error);});}
        });
    }
    /** Menu action that closes its sheet first when the sheet has been built (handle[0] set after show()). */
    private Runnable first(AppSheet[] handle,Runnable run){
        return ()->{if(handle[0]==null)run.run();else handle[0].dismissThen(run);};
    }
    private void bookOptions(LibraryStore.Book b) {
        AppSheet.Builder builder=dialog().setSnowflakes(true).setTitle(b.title);
        AppSheet[] handle=new AppSheet[1];
        LinearLayout panel=column();
        SettingsSection organize=new SettingsSection(uiContext(),"book-organize","整理与阅读","状态 · 书籍备注 · 分类",uiDark());
        organize.body.addView(button("书籍备注与状态 · "+ReadingStatus.label(ReadingStatus.of(b,(float)ratio(b))),first(handle,()->editBookInfo(b))));
        organize.body.addView(button(b.pinned?"取消置顶":"置顶这本书",first(handle,()->organize(()->store.organization.pin(Collections.singletonList(b),!b.pinned),false))));
        organize.body.addView(button("设置分类（可多选）",first(handle,()->chooseCategory(new ArrayList<>(Collections.singletonList(b))))));
        organize.body.addView(button("选择多本书",first(handle,()->{selecting=true;selectedBooks.clear();selectedBooks.add(b.id);showLibrary(shelfQuery);})));
        SettingsSection coverInfo=new SettingsSection(uiContext(),"book-info","书名与封面","修改书名 · 封面 · 文件名还原",uiDark());
        coverInfo.body.addView(button("修改书名",first(handle,()->renameBook(b))));
        coverInfo.body.addView(button("自定义封面",first(handle,()->chooseCover(b))));
        coverInfo.body.addView(button("恢复默认封面",first(handle,()->{covers.remove(b.id);coverCache.remove(b.id);showLibrary(shelfQuery);toast("已恢复默认封面");})));
        coverInfo.body.addView(button("恢复为导入文件名",first(handle,()->{if(b.sourceName.isEmpty()){toast("这本旧书未记录导入文件名");return;}synchronized(store){String old=b.title;b.title=TitleCleaner.stem(b.sourceName);if(b.title.length()>240)b.title=b.title.substring(0,240);try{store.save();showLibrary(shelfQuery);}catch(Exception e){b.title=old;error("书名未保存",e);}}})));
        SettingsSection contentInfo=new SettingsSection(uiContext(),"book-content","正文与编码","切换编码 · 替换正文 · 导出",uiDark());
        if(!b.epub())contentInfo.body.addView(button("切换编码",first(handle,()->pickEncoding(enc->changeEncoding(b,enc)))));
        if(!b.epub())contentInfo.body.addView(button("替换正文（保留阅读记录）",first(handle,()->chooseReplacement(b))));
        contentInfo.body.addView(button((b.epub()?"导出原始 EPUB":"导出原始 TXT"),first(handle,()->chooseTextExport(b))));
        if(!b.epub())contentInfo.body.addView(button("导出为 EPUB（正文与目录）",first(handle,()->chooseEpubExport(b))));
        organize.expanded(true);SettingsSection[] sections={organize,coverInfo,contentInfo};
        for(SettingsSection section:sections){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);panel.addView(section,p);
            section.onToggle(()->{boolean expand=!section.expanded();for(SettingsSection other:sections)other.expanded(other==section&&expand);});
        }
        Button remove=button("移出书架",first(handle,()->dialog().setTitle("移出「"+b.title+"」？").setMessage("移入回收站并保留 7 天，期间可恢复正文、封面、进度和书签。手机里的原始文件会保留。").setNegativeButton("取消",null).setPositiveButton("移出",(confirm,which)->removeToRecycle(b)).show()));
        remove.setTextColor(isNight()?0xFFFFB9AB:0xFF9A493E);remove.setContentDescription("移出书架，移入回收站，7 天内可恢复");
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);panel.addView(remove,rp);
        handle[0]=builder.setView(panel).setContentFraction(.62f).setNegativeButton("取消",null).show();
    }
    private void editBookInfo(LibraryStore.Book book){
        if(busy)return;LinearLayout panel=column();panel.addView(sectionLabel("阅读状态"));
        ChoiceStrip state=new ChoiceStrip(uiContext(),new String[]{"跟随进度","未读","在读","已读"},book.readingState,uiDark());state.setTag("book-reading-state");panel.addView(state);
        panel.addView(text("状态不会改动阅读位置。标为未读后，再次打开会进入在读；已读状态会保留，方便重读。",12,muted));panel.addView(sectionLabel("书籍备注"));
        EditText memo=new EditText(uiContext());memo.setTag("book-memo");memo.setHint("记下推荐理由、阅读计划或自己的想法（最多2000字）");memo.setText(book.memo);memo.setMinLines(4);memo.setMaxLines(8);memo.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ReadingStatus.MAX_MEMO)});panel.addView(memo);
        TextView hint=text("备注与状态随书库备份及回收站保留。",12,muted);panel.addView(hint);
        AppSheet sheet=dialog().setTitle("书籍备注与状态").setView(panel).setContentFraction(.58f).setPositiveButton("保存",null).show();
        sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{if(busy)return;busy=true;sheet.getButton(AppSheet.BUTTON_POSITIVE).setEnabled(false);String value=memo.getText().toString().trim();int selected=state.selected();
            io.execute(()->{try{store.updateBookInfo(book,value,selected);runOnUiThread(()->{busy=false;if(isDestroyed())return;sheet.dismissThen(()->{if(active==book)updateProgress(position());else showLibrary(shelfQuery);toast("书籍备注与状态已保存");});});}
                catch(Exception e){runOnUiThread(()->{busy=false;if(isDestroyed())return;if(sheet.isShowing()){sheet.getButton(AppSheet.BUTTON_POSITIVE).setEnabled(true);hint.setText("未能保存，原记录保留："+message(e));}});}});
        });
    }
    private void renameBook(LibraryStore.Book b) {
        LinearLayout panel=column();EditText input=new EditText(uiContext());input.setSingleLine(true);input.setText(b.title);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)});panel.addView(input);
        TextView hint=text("仅修改书架书名，原始文件名保留",12,muted);hint.setPadding(0,dp(10),0,0);panel.addView(hint);
        AppSheet sheet=dialog().setTitle("修改书名").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).show();
        sheet.getButton(AppSheet.BUTTON_POSITIVE).setOnClickListener(v->{String value=input.getText().toString().replaceAll("\\s+"," ").trim();if(value.isEmpty()){hint.setText("请输入书名");return;}synchronized(store){String before=b.title;b.title=value;try{store.save();sheet.dismissThen(()->showLibrary(shelfQuery));}catch(Exception e){b.title=before;error("没有保存成功",e);}}});
    }
    private void removeNoticeNow(){if(notice!=null){Motion.cancel(notice);if(notice.getParent() instanceof ViewGroup)((ViewGroup)notice.getParent()).removeView(notice);notice=null;}}
    private boolean listenOwns(){return active!=null&&repository!=null&&repository.owns(active.id);}
    LibraryStore.Book listenBook(){return active;}
    TextDocument listenDocument(){return document;}
    int listenOffset(){return position();}
    int listenWindowEnd(){if(document==null)return 0;if(pagedReader!=null)return pagedReader.pageEnd();if(reader==null||reader.getChildCount()==0)return Math.min(document.text.length(),position()+600);int row=reader.getLastVisiblePosition();TextView view=(TextView)reader.getChildAt(reader.getChildCount()-1);Layout layout=view.getLayout();if(layout==null)return Math.min(document.text.length(),document.starts.get(row)+view.length());int y=Math.max(0,reader.getHeight()-reader.getPaddingBottom()-view.getTop()-view.getTotalPaddingTop());return Math.min(document.text.length(),document.starts.get(row)+layout.getLineEnd(layout.getLineForVertical(y)));}
    void listenSaveReading(){savePosition();}
    void listenToast(String value){toast(value);}
    void listenOpenBook(String id){LibraryStore.Book book=store.find(id);if(book==null)return;if(active==book)listenUi.open();else{openListenAfterLoad=true;openBook(book);}}
    void clearListeningHighlight(){listenStart=listenEnd=-1;refreshHighlights();if(followButton!=null)Motion.visible(followButton,false);}
    private void suspendListenFollow(){if(listenOwns()){listenFollowing=false;if(followButton!=null)Motion.visible(followButton,true);}}
    void listenRestoreFollow(){getSharedPreferences("listen",0).edit().putBoolean("follow",true).apply();listenFollowing=true;lastListenFollow=0;if(listenUi!=null&&listenUi.snapshot()!=null){followingJump=true;ListenService.Snapshot s=listenUi.snapshot();if(active!=null&&s.owns(active.id))jumpTo(s.start>=0?s.start:s.offset);followingJump=false;if(followButton!=null)Motion.visible(followButton,false);listenUi.refreshState();}}
    void listenDisableFollow(){listenFollowing=false;if(followButton!=null)Motion.visible(followButton,true);}
    void onListeningChanged(ListenService.Snapshot value){
        if(readingStats!=null&&(value.playing()||value.state==SpeechQueue.State.PREPARING))readingStats.pulse(false,lastReadingInteraction);else statsPulse();
        if(active==null||document==null)return;if(!value.owns(active.id)){if(listenStart>=0)clearListeningHighlight();return;}
        listenStart=value.start;listenEnd=value.end;refreshHighlights();
        if(followButton!=null)Motion.visible(followButton,!(value.follow&&listenFollowing));
        long now=SystemClock.uptimeMillis();if(value.playing()&&value.follow&&listenFollowing&&now-lastListenFollow>=150&&(pagedReader==null||!pagedReader.isIndexing())){lastListenFollow=now;int at=Math.max(0,value.start>=0?value.start:value.offset);boolean visible=pagedReader!=null?pagedReader.containsOffset(at):at>=position()&&at<listenWindowEnd();if(!visible){followingJump=true;jumpTo(at);followingJump=false;}}
    }
    private void capturePage(){
        pendingFrame=null;if(root==null||!Motion.enabled(this)||root.getWidth()==0||root.getHeight()==0)return;
        Motion.finishTree(findViewById(android.R.id.content));
        View scene=root.getParent() instanceof View?(View)root.getParent():root;
        try{float scale=Math.min(1,(float)Math.sqrt(2000000.0/(scene.getWidth()*(double)scene.getHeight())));
            Bitmap image=Bitmap.createBitmap(Math.max(1,(int)(scene.getWidth()*scale)),Math.max(1,(int)(scene.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image);canvas.drawColor(bg);canvas.scale(scale,scale);scene.draw(canvas);pendingFrame=image;
        }catch(OutOfMemoryError|RuntimeException ignored){pendingFrame=null;}
    }
    private void revealPage(){
        if(pendingFrame==null)return;Bitmap image=pendingFrame;pendingFrame=null;
        final View target=root;FrameLayout host=findViewById(android.R.id.content);
        ImageView old=new ImageView(this);old.setImageBitmap(image);old.setScaleType(ImageView.ScaleType.FIT_XY);old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        host.addView(old,new FrameLayout.LayoutParams(-1,-1));target.setAlpha(1);
        final ViewTreeObserver tree=target.getViewTreeObserver();
        final boolean[] released={false};final ViewTreeObserver.OnPreDrawListener[] observer={null};final Runnable[] timeout={null};
        Runnable cleanup=()->{if(tree.isAlive()&&observer[0]!=null)tree.removeOnPreDrawListener(observer[0]);if(timeout[0]!=null)old.removeCallbacks(timeout[0]);};
        Runnable release=()->{if(released[0])return;released[0]=true;cleanup.run();if(target!=root||!old.isAttachedToWindow())return;Motion.exit(old,0,Motion.BASE,()->{host.removeView(old);old.setImageDrawable(null);});};
        observer[0]=()->{boolean ready=target.getWidth()>0&&target.getHeight()>0&&!restoring;
            if(target==root&&active!=null){if(pagedReader!=null)ready&=!pagedReader.isIndexing()&&pagedReader.pageEnd()>0;else if(reader!=null)ready&=reader.getChildCount()>0;}
            if(ready)release.run();return true;};
        timeout[0]=release;tree.addOnPreDrawListener(observer[0]);old.postDelayed(timeout[0],2000);
        old.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){released[0]=true;cleanup.run();old.setImageDrawable(null);}});
    }
    @Override protected void onStart(){super.onStart();chromeHandler.removeCallbacks(nightTick);chromeHandler.post(nightTick);consumeExternal();if(listenUi!=null)listenUi.start();if(getIntent().getBooleanExtra("showListen",false)){notificationBook=getIntent().getStringExtra("listenBook");getIntent().removeExtra("showListen");consumeListenIntent();}}
    @Override protected void onStop(){if(shelfDrag!=null)shelfDrag.cancel();chromeHandler.removeCallbacks(nightTick);if(listenUi!=null)listenUi.stop();super.onStop();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(listenUi!=null)listenUi.permission(request,grants);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);receiveExternal(intent);consumeExternal();if(intent.getBooleanExtra("showListen",false)){notificationBook=intent.getStringExtra("listenBook");consumeListenIntent();}}
    private void consumeListenIntent(){if(tearingDown||isDestroyed()||notificationBook==null||busy||listenUi==null)return;String id=notificationBook;notificationBook=null;listenOpenBook(id);}
    private class ReaderAdapter extends BaseAdapter {
        public int getCount() {return document.starts.size();}
        public Object getItem(int p) {return p;}
        public long getItemId(int p) {return p;}
        public View getView(int p,View reuse,ViewGroup parent) {
            TextView view=reuse instanceof TextView?(TextView)reuse:new TextView(MainActivity.this);
            view.setTextSize(fontSize);view.setTextColor(ink);new ReaderTypography(MainActivity.this,effectiveTypeset()).apply(view);view.setLineSpacing(0,spacing);
            view.setIncludeFontPadding(false);view.setPadding(0,dp(4),0,dp(4));view.setTextIsSelectable(true);
            final LibraryStore.Book selectedBook=active;final TextDocument selectedDocument=document;final int selectionBase=document.starts.get(p);
            view.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
                public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,0x534e01,10,"划线／笔记");menu.add(0,0x534e02,11,"从这里朗读");menu.add(0,0x534e03,12,"删除已有标记");return true;}
                public boolean onPrepareActionMode(ActionMode mode,Menu menu){MenuItem remove=menu.findItem(0x534e03);if(remove!=null){int a=view.getSelectionStart(),b=view.getSelectionEnd();remove.setVisible(active==selectedBook&&a>=0&&b>=0&&!BookNote.overlapping(selectedBook.annotations,selectionBase+Math.min(a,b),selectionBase+Math.max(a,b)).isEmpty());}return true;}
                public void onDestroyActionMode(ActionMode mode){}
                public boolean onActionItemClicked(ActionMode mode,MenuItem item){if(item.getItemId()!=0x534e01&&item.getItemId()!=0x534e02&&item.getItemId()!=0x534e03)return false;int a=view.getSelectionStart(),b=view.getSelectionEnd();if(a<0||b<0||a==b)return true;int from=selectionBase+Math.min(a,b),until=selectionBase+Math.max(a,b);mode.finish();chromeHandler.post(()->{if(active!=selectedBook||document!=selectedDocument)return;if(item.getItemId()==0x534e01)editNote(null,from,until);else if(item.getItemId()==0x534e03)manageRangeNotes(from,until,true);else listenUi.startAt(from);});return true;}
            });
            String body=document.row(p);int start=document.starts.get(p);SpannableString styled=new ReaderTypography(MainActivity.this,effectiveTypeset(),active.annotations).style(readerText,start,start+body.length(),view.getTextSize(),getResources().getDisplayMetrics().density);
            if (highlight>=0 && !searchTerm.isEmpty()) {
                int a=Math.max(0,highlight-start),b=Math.min(body.length(),highlight-start+searchTerm.length());
                if (b>a && highlight<start+body.length() && highlight+searchTerm.length()>start) styled.setSpan(new BackgroundColorSpan(palette==2?0xFF71612D:0xFFE7D69D),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if(listenStart>=0&&listenEnd>listenStart){int a=Math.max(0,listenStart-start),b=Math.min(body.length(),listenEnd-start);if(b>a)styled.setSpan(new BackgroundColorSpan(palette==2?0xFF51664A:0xFFDCEBE0),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);}view.setText(styled);return view;
        }
    }
    private abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,float size,int color){
        TextView t=new TextView(uiContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView sectionLabel(String title){
        TextView label=text(title,14,ink);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setPadding(0,dp(12),0,dp(8));return label;
    }
    private Drawable iconDrawable(String name,int color,int size){
        int id=getResources().getIdentifier(name,"drawable",getPackageName());if(id==0)return null;
        Drawable icon=getResources().getDrawable(id,uiContext().getTheme()).mutate();icon.setTint(color);icon.setBounds(0,0,dp(size),dp(size));return icon;
    }
    private View iconButton(String label,String name,Runnable action){
        ImageButton button=new ImageButton(uiContext());button.setImageDrawable(iconDrawable(name,accent,24));
        button.setPadding(dp(12),dp(12),dp(12),dp(12));button.setBackgroundTintList(null);
        button.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(14),isNight()?0x308CD6E5:0x20248FA9));
        button.setContentDescription(label);button.setOnClickListener(v->action.run());return button;
    }
    private View navButton(String label,String icon,Runnable action){
        LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(14),isNight()?0x308CD6E5:0x20248FA9));
                if(label.equals("Aa")){TextView letters=text("Aa",22,accent);letters.setTypeface(Typeface.create("serif",Typeface.BOLD));letters.setGravity(Gravity.CENTER);letters.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(letters,new LinearLayout.LayoutParams(dp(32),dp(26)));}
        else {ImageView image=new ImageView(this);image.setImageDrawable(iconDrawable(icon,accent,22));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(image,new LinearLayout.LayoutParams(dp(22),dp(22)));}
        TextView name=text(label.equals("Aa")?"排版":label,11,accent);name.setPadding(0,dp(5),0,0);name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(name);
        cell.setContentDescription(label.equals("Aa")?"Aa 阅读排版设置":label);cell.setFocusable(true);cell.setOnClickListener(v->action.run());return cell;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(uiContext());b.setText(label);b.setTextSize(14);b.setTextColor(accent);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setPadding(dp(8),0,dp(8),0);b.setBackgroundTintList(null);
        b.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(14),isNight()?0x308CD6E5:0x20248FA9));b.setOnClickListener(v->action.run());return b;
    }
    private GradientDrawable shape(int color,int radius){
        boolean surface=color==card;return SnowTheme.rounded(color,dp(radius),isNight()?0xFF294853:SnowTheme.BORDER,surface?dp(1):0);
    }
    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private void toast(String s) {
        chromeHandler.removeCallbacks(clearNotice);removeNoticeNow();
        FrameLayout host=findViewById(android.R.id.content);if(host==null)return;
        TextView label=text(s,13,ink);label.setPadding(dp(16),dp(12),dp(16),dp(12));label.setGravity(Gravity.CENTER);label.setBackground(shape(card,16));label.setElevation(dp(6));
        label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);p.setMargins(dp(24),0,dp(24),dp(112)+(root==null?0:root.getPaddingBottom()));host.addView(label,p);notice=label;Motion.enter(label,Motion.TOAST_IN);chromeHandler.postDelayed(clearNotice,3200);
    }
    private void successSheet(String title,String message){TextView check=text("✓",28,accent);check.setContentDescription("操作完成");GlowFrame light=new GlowFrame(this,14);light.addView(check);dialog().setSnowflakes(true).setTitle(title).setMessage(message).setView(light).show();Motion.confirm(check);light.pulse();}
    private String message(Exception e) {return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void error(String title,Exception e) {dialog().setTitle(title).setMessage(message(e)).setPositiveButton("知道了",null).show();}
    private TaskProgress showTaskLoading(String title,String msg){TaskProgress task=new TaskProgress(uiContext(),uiDark());taskProgress=task;task.report(0,0,msg);loading=dialog().setTitle(title).setView(task).setCancelable(false).show();return task;}
    private void showLoading(String msg) {showTaskLoading("请稍候",msg);}
    private void hideLoading() {taskControl=null;if(cancellationSheet!=null){cancellationSheet.dismissNow();cancellationSheet=null;}if(taskProgress!=null){taskProgress.dispose();taskProgress=null;}if(externalFile!=null)chromeHandler.post(()->consumeExternal());if(notificationBook!=null)chromeHandler.post(()->consumeListenIntent());if(loading!=null) {if(loading.isShowing()) loading.dismiss();loading=null;}}
    @Override public void onBackPressed() {if(statsPage){leaveStats();return;}if(busy){requestTaskCancel();return;}if(active!=null) leaveReader();else if(selecting){selecting=false;selectedBooks.clear();showLibrary(shelfQuery);}else if(shelfSearchOpen)closeShelfSearch();else super.onBackPressed();}

    @Override protected void onResume(){super.onResume();statsResumed=true;if(readerFooter!=null)readerFooter.resumed(true);if(lanLibraryDirty&&store!=null&&active==null&&!statsPage&&!busy){lanLibraryDirty=false;showLibrary(shelfQuery);}lastReadingInteraction=SystemClock.elapsedRealtime();chromeHandler.removeCallbacks(statsTick);chromeHandler.post(statsTick);Motion.invalidateCache(this);AtmosphereView.refreshAll();if(prefs!=null){refreshAutoTheme();applyReaderWindow();}if(pagedReader!=null)pagedReader.setAnimationEnabled(pageMotion&&Motion.enabled(this));}
    @Override protected void onPause() {if(readerFooter!=null)readerFooter.resumed(false);stopLan();if(readingStats!=null)readingStats.pulse(false,lastReadingInteraction);statsResumed=false;chromeHandler.removeCallbacks(statsTick);if(pressedTile instanceof ShelfTile)((ShelfTile)pressedTile).resetLift();pressedTile=null;if(pagedReader!=null)pagedReader.setAnimationEnabled(false);Motion.finishTree(findViewById(android.R.id.content));AppSheet.finishFor(this);savePosition();if(repository!=null)repository.requestProgressSave(true,e->toast("保存失败："+message(e)));chrome.focus(false,SystemClock.elapsedRealtime());chromeHandler.removeCallbacks(chromeTimeout);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out) {
        savePosition();out.putBoolean("statsPage",statsPage);out.putString("statsReturnBook",statsReturnBook);out.putString("statsSelectedDay",statsSelectedDay);if(listenUi!=null)listenUi.save(out);if(active!=null) {out.putString("book",active.id);out.putInt("offset",active.offset);}out.putString("pendingReplaceId",pendingReplaceId);out.putString("pendingEncoding",pendingEncoding);out.putString("pendingCoverId",pendingCoverId);out.putString("pendingExportBookId",pendingExportBookId);out.putString("pendingEpubBookId",pendingEpubBookId);out.putBoolean("shelfSearchOpen",shelfSearchOpen);out.putString("shelfQuery",shelfQuery);out.putString("shelfCategory",shelfCategory);out.putBoolean("selecting",selecting);out.putStringArrayList("selectedBooks",new ArrayList<>(selectedBooks));super.onSaveInstanceState(out);
    }
    @Override public void onConfigurationChanged(Configuration config) {super.onConfigurationChanged(config);Motion.finishTree(findViewById(android.R.id.content));AppSheet.finishFor(this);if(busy)return;if(statsPage){showReadingStats();return;}palette=effectivePalette();if(active!=null) {int pos=position();showReader(pos);applyAwake();}else if(!failed)showLibrary(shelfQuery);}
    @Override protected void onDestroy() {if(readerFooter!=null)readerFooter.resumed(false);stopLan();pendingNoteExport=null;pendingFontChoice=null;tearingDown=true;++generation;++searchGeneration;importStop.set(true);if(taskControl!=null)taskControl.cancel();if(preparedBackup!=null&&!busy){preparedBackup.close();preparedBackup=null;}chrome.leave();chromeHandler.removeCallbacks(chromeTimeout);chromeHandler.removeCallbacks(clearNotice);removeNoticeNow();hideLoading();if(listenUi!=null)listenUi.destroy();AppSheet.destroyFor(this);chromeHandler.removeCallbacksAndMessages(null);coverCache.evictAll();if(Build.VERSION.SDK_INT>=33&&systemBack!=null)getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);super.onDestroy();}
}
