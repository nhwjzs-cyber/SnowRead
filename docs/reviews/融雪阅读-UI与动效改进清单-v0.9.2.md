# 融雪阅读 · UI 与动效专项改进清单

> 复核基线：v0.9.2 / versionCode 13
> 复核对象：`outputs/融雪阅读-v0.9.2-debug.apk`（SHA-256 `360dd473…f910a87`，与 `融雪阅读-v0.9.2-校验.json` 一致）+ `app/src/main/java/com/snowread/app`（36 文件 / 3,279 行）
> 复核时间：2026-10-09
> 定位：**这份清单补的是已有清单没覆盖的那一半**。`docs/reviews/融雪阅读-改进意见清单-v0.9.2.md` 谈的是效率、正确性、功能、工程；那份里涉及 UI / 动效的只有零星几条（E08 的 Handler 往返、E17 的通知重建）。本文只谈**界面本身好不好看、动效对不对、有没有该有却没有的反馈**，不重复那一份的性能与工程条目。

---

## 0. 结论先行

这个应用的视觉语言是完整的：纸色 + 青墨、描边卡片、圆角面板、LinearLayout 手工搭建、零第三方依赖。**动效规范 v1（`docs/specs/融雪阅读-动效规范-v1.md`）写得比大多数独立开发者的动效文档都清楚**——四档时长、五个原语、降级策略、可访问性豁免，一应俱全。

问题恰恰出在**规范很完整，但实现是"打了一半的补丁"**：

1. **规范覆盖的 8 个场景里，实现质量参差**；规范**没覆盖**的高频场景（分类切换、多选切换、开关、选项条、目录跳转）全是硬切；
2. **规范 §8.5 写的"动画期间加硬件层"，代码里一次都没出现**（`grep LAYER_TYPE` 只有 `NONE`）；
3. **规范 §2 列的第三种缓动 `EASE_STD`（FastOutSlowInInterpolator），`Motion.java` 里根本没定义**，全部动效只有两种曲线；
4. **翻页动效与用户手指之间没有任何映射**——这是本清单的第一条；
5. **阅读页的工具栏正在遮挡章节名和进度百分比**，这是本清单里唯一一个"确定是 bug 而不是品味"的界面缺陷。

如果只做三件：**M1（翻页跟手）→ V1（工具栏遮挡）→ M6（分类切换过渡）**。这三件加起来改动量不大，但覆盖了用户每天看到的最显眼的三处。

---

## 第一梯队 · 动效结构问题（M1–M8）

### M1 ★ 翻页不跟手，而且慢速拖动完全无效

**证据**：`PagedReaderView.java:51-67`

```java
new GestureDetector.SimpleOnGestureListener(){
    @Override public boolean onSingleTapUp(MotionEvent event){ ... }      // 点按：翻页
    @Override public boolean onFling(MotionEvent first,MotionEvent last,float vx,float vy){
        if(Math.abs(dx)>48*density && Math.abs(dx)>Math.abs(dy)){ ... next(); ... }
    }
    // 没有 onScroll，没有 onTouchEvent 的位移跟踪
}
```

**问题**：
- 翻页只有两条路径——点按左右 1/3，或**甩动**（`onFling`）。中间没有任何"手指带着页面走"的实现。
- 更糟的是：`onFling` 要求 `|dx| > 48dp`，但 `onScroll` 未实现、位移也不被跟踪。**用户慢慢拖动超过 48dp（手指没甩、速度不够）→ 什么都不会发生**，页面纹丝不动。用户会以为 App 卡了，然后改用点按。
- 现有 240ms 的整页横移动画是在 `apply()` 里、**分页完成之后**才播的（`PagedReaderView.java:133-138`），它与用户手势之间没有任何因果关系——用户无法控制翻页的力度、位置、速度。

**为什么重要**：翻页是阅读器唯一的高频交互。当前实现把它变成了"二选一"的离散操作，用户失去对翻页的掌控感。市面上所有成熟阅读器（Kindle / 多看 / 掌阅 / iBooks）都提供跟手拖动，这是差距最大的一处交互。

**建议**：
1. 实现 `onScroll`：累积横向位移，用 `translationX` 让当前页**跟随手指**实时平移（可复用现有 `drawPage(canvas, layout, x)` 的偏移能力，绘制逻辑已具备，只需把 x 从动画器换成手势值）；
2. 松手时按**位移阈值 + 速度**双条件决定是否翻页（位移 > 40% 页宽 **或** 速度 > 800dp/s），否则用 `Motion` 的 decelerate 曲线弹回；
3. 点按 / 按键翻页路径保留现有 240ms 动画不动，两条路径互不干扰；
4. 边界（已是第一页 / 已到书末）加入**轻微阻尼位移**（最多拖到 24dp 就拉不动）+ 一次轻触感，而不是现在弹一个 Toast（见 M10）。

**收益**：这是把"能用"变成"顺手"的一条。**工作量 M。**

---

### M2 ★ 长任务的进度文字在闪烁

**证据**：`Motion.java:47` + `TaskProgress.java:46`

```java
// Motion.java
static void text(TextView view,String value){
    if(value.contentEquals(view.getText()))return;
    boolean animating=running.containsKey(view);
    view.setText(value);
    if(!animating) enter(view,0,FAST);      // ← 整段文字 alpha 0 → 1
}

// TaskProgress.render()，每 120ms 调一次
if(animate) Motion.text(label,value);
```

**问题**：`Motion.text` 每次都把整个标签重置到 alpha=0 再淡入到 1。而 `TaskProgress` 最多每 120ms 刷新一次，`Motion.FAST` 也是 120ms。两者节奏相同 → **alpha 呈锯齿状反复从 0 爬到 1**。导入 500 个文件时，进度那行字会一直在"半透明↔不透明"之间抖，看起来像一个坏掉的显示屏，而不是一个在正常工作的进度指示。

**为什么重要**：
- 这直接违反自己写的验收标准。动效规范 §9 明确写着"批量导入 500 个文件时，进度条持续前进，**文字切换不闪**"。
- 长任务场景里，闪烁会被用户误读为"出错了 / 卡住了"，反而制造焦虑。而动效规范 §1 的总原则就是"进度表达：让用户看到在动"——现在看到的是"在抽搐"。

**建议**：
1. `Motion.text` 改为**不重置 alpha**：只在文字首次出现时做一次淡入，之后纯 `setText`；
2. 如果要让"数字在变"这件事被感知到，把变化做在**颜色**上：百分比用 `accent` 色，普通描述用 `muted` 色，两者交叉过渡 120ms，而不是整段 alpha 归零；
3. 保留淡入的场景只有一个：文件名从"正在整理书页…"变成具体文件名时，做一次 120ms 交叉淡入——这符合规范 §4 里"文件名 120ms 交叉淡入"的原意。

**收益**：消除一个高频、显眼、且与自身验收标准冲突的缺陷。**工作量 S。**

---

### M3 ★ 系统级动画缩放被完全忽略（无障碍缺陷，不是口味问题）

**证据**：`Motion.java:27`

```java
try{return Settings.Global.getFloat(context.getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE,1)>0
     && Settings.Global.getFloat(context.getContentResolver(),
        Settings.Global.TRANSITION_ANIMATION_SCALE,1)>0;}
catch(Exception ignored){return true;}
```

**问题**：代码只判断"缩放是否 > 0"（用来决定动画开不开），**却从不把缩放值乘进时长**。用户在系统辅助功能里把"动画时长"调到 0.5× 或 2×，这个应用的所有动效时长**纹丝不动**，永远是写死的 120 / 180 / 220 / 240ms。

**为什么重要**：
- 慢速动画（0.5×）是前庭功能敏感用户、演示模式、以及需要看清细节的用户明确表达的需求。忽略它等于剥夺了系统提供的无障碍设置——这是**无障碍缺陷**，不是设计分歧。
- 顺带一提：`Motion.enabled()` 只查了 `ANIMATOR_DURATION_SCALE`，没查 `TRANSITION_ANIMATION_SCALE` 的实际值，两者混在一个布尔判断里，语义混乱。
- 建议同时把 `PagedReaderView.animationsAllowed()`（`:139-146`）里那套重复判断也收敛到 `Motion`，避免两处逻辑漂移。

**建议**：
1. `Motion` 增加 `static float scale(Context)`，读取并缓存 `ANIMATOR_DURATION_SCALE`（见 M4）；
2. 所有时长统一走 `duration(base) = round(base * scale)`，`scale == 0` 时走现有的"直接落到终态"路径；
3. 设定一个上限（如 3×），避免用户把系统调到 10× 时界面变得无法使用；
4. 顺带把"减少动效"开关（prefs `reduceMotion`）与系统缩放的关系写进设置项说明，避免用户困惑。

**收益**：补上一个真实的无障碍缺口，且改动集中在 `Motion` 一处。**工作量 S。**

---

### M4 ★ 每次动画启动都做一次跨进程查询

**证据**：`Motion.java:27` 与 `PagedReaderView.java:141-145`

```java
// Motion.enabled() —— 每一个原语入口都会走到
AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1)
```

**问题**：`Settings.Global.getFloat` 不是读本地内存，它要**通过 ContentResolver 向系统设置服务发一次 binder 调用**。而 `Motion.enabled()` 在 `to()` 内部、`enter/exit/press/release/confirm/badge/text` 的每个入口都会走一遍；`PagedReaderView.animationsAllowed()` 还自己再实现了一份，翻页时也调。

也就是说：**每次翻页、每次面板开合、每次按钮按压的开头，都夹着一次跨进程 IPC**。在低端机上这足以贡献一帧的可感知延迟——而且它恰好发生在"动画开始"这个最需要准时的时间点上，直接体现为**动画起步的顿挫感**。

**为什么重要**：这类抖动不影响功能，但会让"感觉不高级"。而且成本与收益完全不成比例——缩放系数在一分钟内几乎不会变。

**建议**：
1. `Motion` 里加静态缓存：`static volatile float cachedScale = 1f;`；
2. 缓存刷新时机：`MainActivity.onResume()`、`nightTick`（已有 30s 定时器）、以及 `prefs` 变化时；
3. `AccessibilityManager` 同样缓存为静态字段（现有清单 E08 提过 `dispatchTouchEvent` 里的重复查询，**动画路径上的这处是独立的一处，没被那份清单覆盖**）；
4. `PagedReaderView.animationsAllowed()` 改为直接调用 `Motion.enabled(getContext())`，删掉重复实现。

**收益**：去掉所有动画启动时刻的 IPC 抖动。**工作量 S。**

---

### M5 ★ 翻页动画每帧重绘两个整页文本，而且从未启用硬件层

**证据**：`PagedReaderView.java:193-207`

```java
private void drawPage(Canvas canvas,StaticLayout page,float x){
    canvas.save();canvas.translate(x,0);canvas.clipRect(0,0,contentWidth(),contentHeight());
    canvas.drawColor(pageBackground);        // ← 全屏色块填充
    page.draw(canvas);                        // ← 整页文本重绘
    canvas.restore();
}
@Override protected void onDraw(Canvas canvas){
    ...
    if(outgoingLayout!=null){
        drawPage(canvas,outgoingLayout,oldX);   // 第 1 遍
        drawPage(canvas,layout,newX);            // 第 2 遍
        canvas.drawLine(boundary,0,boundary,contentHeight(),edgePaint);
    }
}
```

**问题**：
- 240ms 内约 14 帧，每帧要做 **2 次全屏 `drawColor` + 2 次整页 `StaticLayout.draw`**（在 1080p 上是两块 ~1080×1900 的区域）。
- 更关键的是：`grep LAYER_TYPE` 的结果只有 `LAYER_TYPE_NONE`——**动效规范 §8.5 明确写着"动画期间可给 panel 加 LAYER_TYPE_HARDWARE，结束后恢复 LAYER_TYPE_NONE"，代码里一次都没有实现**。`Motion.cancel()` 里那句 `view.setLayerType(View.LAYER_TYPE_NONE,null)` 是在"收尾"，但从头到尾没有谁把它设成 `HARDWARE`。

**为什么重要**：
- 掉帧会直接毁掉翻页动效本身——这是全 App 唯一一个每帧都发生的大面积重绘。翻页"发飘"、"不跟手"（M1）的观感，有一部分其实是**掉帧**而不是设计问题。
- 这与已有清单 E03（听书时整页重排）属于同一类体感，但成因不同、位置不同，那份清单没覆盖到这里。

**建议**：
1. `Motion.to()` 在启动动画时对目标 View 设 `LAYER_TYPE_HARDWARE`（已有 `cancel()` 会恢复 `NONE`，只需补上开始这一侧）；
2. 翻页动画期间把两个 `StaticLayout` 各渲染一次到离屏缓存（或直接把整个 `PagedReaderView` 提升为硬件层），让每帧只做位图平移；
3. `drawColor` 换成只填充"即将露出的那一侧"，避免整屏填两遍；
4. 边界那条 1px 硬线（`edgePaint`，alpha=24）建议改成**带方向的渐变阴影**（`LinearGradient` 从透明到 12% 黑），同样的成本，观感提升明显。

**收益**：翻页从"可能掉帧"变成"稳定 60fps"，且顺带修好了规范里已经写了但没做的硬件层。**工作量 M。**

---

### M6 ★ 分类切换、多选切换、搜索刷新——全是硬切

**证据**：`MainActivity.java:143`（多选）、`:191`（分类 chip）、`showLibrary()` 全量重建

```java
// 分类 chip 的点击
Button chip=button(name+"  "+count,()->{shelfCategory=id;showLibrary(shelfQuery);});
// showLibrary() 开头
private void showLibrary(String query){
    ...
    frame(false);            // → setContentView(root)，整棵视图树重建
    ...
}
```

**问题**：切换分类 / 进入或退出多选，都会走 `showLibrary()` → `frame()` → **`setContentView(root)` 整棵树重建**。**中间没有任何过渡**——旧书架瞬间消失，新书架瞬间出现。

**为什么重要**：
- 分类 chip 是书架上**最高频的控件**，用户每天点几十次。现在每次点击的观感是"屏幕闪了一下"。
- 硬切还带来副作用：**滚动位置丢失**、展开状态丢失、封面重新解码（配合 B11 的解码去重问题，闪屏期间封面是空白的）。
- 翻遍动效规范 §4 的组件对照表，**"分类切换"根本不在 8 个场景里**——这正是规范与实现之间的空白带。规范 §1 说"只做三类动效：空间关系 / 操作确认 / 进度表达"，分类切换属于**空间关系**（同一空间内的内容替换），本该有过渡。

**建议**：
1. 把书架内容区（GridView + 空状态）包进一个可复用的容器，切分类时对**旧内容**与**新内容**做 `Motion.crossFade`（180ms，规格里已有这个原语，直接复用）；
2. 若担心 GridView 重建成本，则保留 GridView 实例、只换 adapter 与数据源（`shelfAdapter.visible = ...; notifyDataSetChanged()`），用 `Motion.enter(results, CONTENT_NUDGE)` 做新内容入场——**这同时顺带解决了封面重复解码的问题**；
3. 多选模式的底部操作栏（`:181-186`）用 180ms 从下往上滑入（`Motion.enter(bar, 12dp)`），而不是瞬间出现在页脚；
4. 搜索框内容变化导致的网格刷新保持即时（输入需要即时反馈），但空状态的出现/消失应做 180ms 淡入淡出——现在 `sample` 按钮是 `setVisibility` 硬切（`:176`）。

**收益**：把最高频操作从"闪屏"变成"顺滑"，且复用的是已有原语，不新增动画代码。**工作量 S–M。**

---

### M7 ★ 开关、选项条、色板——三个选择控件零过渡

**证据**：`ReaderControls.java`（`ChoiceStrip` / `ToggleRow` / `ThemeSwatches`）

```java
// ToggleRow：整个应用唯一完全自绘的控件
toggle=new View(context) {
    @Override protected void onDraw(Canvas c){
        paint.setColor(ToggleRow.this.checked?accent:soft);
        c.drawRoundRect(...);
        paint.setColor(dark?NIGHT_SURFACE:Color.WHITE);
        c.drawCircle(ToggleRow.this.checked?getWidth()-dp(13):dp(13),getHeight()/2f,dp(9),paint);
    }
};
toggle.setOnClickListener(v->{this.checked=!this.checked;toggle.invalidate(); ...});   // ← 直接跳变
```

**问题**：
- 开关的滑块位置**瞬移**，轨道颜色**瞬变**（`invalidate()` 重绘，没有插值）；
- `ChoiceStrip.update()`（`:28`）切换时直接 `setBackground(新drawable)` + `setTextColor`，背景与文字色**瞬间跳变**；
- `ThemeSwatches`（`:63`）选中环的描边宽度从 1dp 瞬间变 3dp，中心的 "Aa" 瞬间变成 "✓"。

**为什么重要**：
- 这三个控件是这个应用里**唯一"完全自绘但完全不动"**的地方。用户已经建立了"这个 App 的东西会平滑变化"的预期（面板会滑入、工具栏会淡出、进度条会补间），唯独点开关时旋钮瞬移——这种**静止的控件**在一套有动效语言的产品里，比"没有控件"更显眼。
- 这与规范 §4 里"主题切换：旧阅读页截图覆盖，180ms 淡出"是矛盾的：外层转场做了 180ms 交叉淡入，**内层被点的那个色块本身却瞬间跳变**，两层动效互相打架。
- 顺带：`ToggleRow` 的 `checked` 是自绘状态，不会触发 `Motion` 的任何原语——说明规范里的"复用优先"在这块被绕过了。

**建议**：
1. `ToggleRow`：把 `checked` 从 boolean 改成 `float 0→1` 的动画值，用 `ValueAnimator`（180ms，decelerate）插值滑块位置与轨道色；这是自绘控件，改造成本极低；
2. `ChoiceStrip`：选中态的背景与文字色用 `Motion.crossFade` 或对两个 TextView 交叉淡入；
3. `ThemeSwatches`：环的粗细用 `Paint` 的 stroke 宽度做 180ms 插值，"Aa"↔"✓" 切换用一次 120ms 淡出淡入；
4. 这三条都可以在 `Motion` 里加两个新原语（`interpolate(view, float)` / `crossFadeColor`），供所有自绘控件复用。

**收益**：把"半张脸"的产品补齐。**工作量 S。**

---

### M8 ★ 面板回弹没有物理感；规范里的第三种缓动从未存在

**证据**：`Motion.java:17-18` 只有两种曲线；`SheetDragLayout.java:34,38`

```java
// Motion.java —— 规范 §2 列了三种，实际只有两种
private static final DecelerateInterpolator IN=new DecelerateInterpolator(1.4f);
private static final AccelerateInterpolator OUT=new AccelerateInterpolator(1.2f);
// 规范 §2 的 EASE_STD（FastOutSlowInInterpolator）没有定义、没有使用

// SheetDragLayout —— 松手回弹
if(close)close(); else Motion.settleSheet(this,window);   // → to(panel,1,0,1,BASE,true,null)，纯减速
```

**问题**：
- 拖动面板松手后的回位，是全 App 手感最强的一个瞬间。现在用一条纯减速曲线收尾，面板像被"吸"回原位，而不是被"放"回原位。缺一点点过冲，物理感就少一半。
- 拖拽跟手是 1:1 硬跟随（`setTranslationY`），没有阻尼；松手与拖动之间是两种截然不同的手感。

**为什么重要**：规范 §1 写"安静：不弹跳、不旋转、不闪烁"，这条原则本身是对的——但"安静"指的是**不要装饰性弹跳**，不是"不许有过冲"。**0.5% 量级的过冲**属于物理反馈，不是装饰。业界标准（Material 3、Flutter 的 `Curves.easeOutBack`）都在手势回弹上使用极小的过冲。

**建议**：
1. `Motion` 补齐规范 §2 承诺的第三种曲线 `EASE_STD = FastOutSlowInInterpolator`，用于交叉淡入与面板回弹；
2. 新增 `EASE_BACK = OvershootInterpolator(1.06f)`（**过冲量必须小，1.06 就够**），仅用于 `SheetDragLayout.settleSheet()` 与 `SwipeActionRow.settle()` 两个手势回弹场景；
3. 拖拽过程中给 `translationY` 加 0.85 的阻尼系数（`start + dy * 0.85`），让面板"沉"一点而不是死板地跟着走；
4. **在动效规范 §2 的表格里把每种曲线的适用场景写清楚**，避免下一个版本又出现"规范有、实现没有"的漂移——这是本文档反复出现的一个模式。

**收益**：两处关键手感的质感提升。**工作量 S。**

---

### M9 面板遮罩在部分 ROM 上有黑闪风险

**证据**：`AppSheet.java:115` + `Motion.java:49`

```java
// AppSheet.onCreate
window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
window.setDimAmount(0);
// Motion.sheetIn —— 在 show() 之后才 addFlags
window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
window.setDimAmount(0);
dim(window,SCRIM,BASE,panel.getContext());
```

**问题**：窗口已经显示后才 `addFlags(FLAG_DIM_BEHIND)`，再从 dimAmount=0 开始动画。在部分 ROM 上，"加 dim 标志"这一帧系统会先用默认 dim 值合成一帧，再被我们改成 0 —— 视觉上就是**"先黑一下再淡入"**。

**为什么重要**：动效规范 §9 的验收清单第一条就是"遮罩同步渐变，**不先黑一下**"。这条验收项目前很可能在部分机型上不通过。

**建议**：`FLAG_DIM_BEHIND` 在 `onCreate` 就设好、`dimAmount` 初值就是 0，全程只对 `dimAmount` 做动画，不再 add/clear 标志。

---

### M10 边界与确认反馈的层次不对

**证据**：`PagedReaderView.java:180,189`

```java
if(endOffset>=doc.text.length()){notice("已经到书末了");return;}
...
else notice("已经是第一页");
```

**问题**：翻到书末 / 书首时弹一个 Toast。Toast 是全屏浮层，会盖住正文，还会打断阅读节奏——而这里要传达的只是"这一下没生效"。

**建议**：改成**位移阻尼 + 一次轻触感**（`performHapticFeedback(VIRTUAL_KEY)`），Toast 留给真正的信息事件（"已导出"、"已复制"）。注意：全项目目前 **0 处触觉反馈**（`grep performHapticFeedback` 无结果），而长按管理、开关切换、拖拽面板、到书边界这四类场景都适合加一次轻震——这是投入产出比很高的一项。

---

## 第二梯队 · 视觉与布局问题（V1–V11）

### V1 ★★ 工具栏正在遮挡章节名和进度百分比（确定是缺陷）

**证据**：`MainActivity.java:441-500`

```java
readerStage=new FrameLayout(this);
LinearLayout content=column(); readerStage.addView(content,new FrameLayout.LayoutParams(-1,-1));
chapterLabel = text("",11,muted);
content.addView(chapterLabel, new LinearLayout.LayoutParams(-1,dp(32)));   // ← 内容区顶部 32dp
...
content.addView(view,    new LinearLayout.LayoutParams(-1,0,1));          // ← 正文
progressLabel = text("",11,muted);
content.addView(progressLabel,new LinearLayout.LayoutParams(-1,dp(30)));   // ← 内容区底部 30dp

// 工具栏是 readerStage 的**兄弟节点**，后添加 → 绘制在上面
readerTopBar=top;   topPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
topPosition.setMargins(dp(12),dp(8),dp(12),0);
readerStage.addView(top,topPosition);        // 高 48 + 上下 padding 12 = 60dp
...
readerBottomBar=bottom;  bottomPosition=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
readerStage.addView(bottom,bottomPosition);   // 高约 44+32+60+16 ≈ 152dp
```

**几何结论**（假设状态栏内边距 24dp）：
- `chapterLabel` 占内容区 y = 24–56；顶栏占 y = 32–92 → **重叠 24dp，章节名被压住**；
- `progressLabel` 占内容区底部 30dp；底栏高约 152dp → **完全被覆盖，一行都看不到**。

**为什么重要**：阅读时用户最想看的两件事——"我在第几章"和"读到百分之几"——恰恰在工具栏显示时被挡住。而工具栏默认显示 4 秒（`ReaderChrome.IDLE_MS=4000`）才自动收起。**用户进书后的前 4 秒，看不到章节和进度**。滚动模式下顶栏还额外压着章节名。

**建议（注意不能引入重排）**：
- 代码注释明确写了 "Reader content keeps the same geometry whether overlay controls are visible or hidden"——**这个决策是对的**（避免工具栏显隐触发重新分页），所以修复方案必须保持正文几何不变；
- 方案 A（推荐）：**把 `chapterLabel` 移进顶栏**作为书名下方的一行副标题（顶栏高度 48→64dp），**把 `progressLabel` 移进底栏**放在 SeekBar 上方。正文区域一行代码都不用改 → 不触发重新分页，冲突自然消失；
- 方案 B：给 `content` 加固定的 64dp / 152dp 上下 padding —— **会改变正文高度 → 触发重新分页和页码变化**，不可取；
- 顺带：底栏已有 SeekBar，章节与百分比信息应该与它成组，而不是散落在正文的上下边缘。

**收益**：修掉全 App 最明显的界面缺陷，且不动分页逻辑。**工作量 S。**

---

### V2 ★ 书架头部占掉约 250dp，且永远不收起

**证据**：`MainActivity.java:135-160`

| 区块 | 高度 |
|---|---|
| header padding | 18 + 8 |
| 品牌行（logo 40 + 标题 + tagline + 两个按钮 48） | 48 |
| 标题行（"全部书架" 24sp + "分类管理" 48）+ topMargin 12 | 60 |
| 分类 chips 滚动条（44 + 10） | 54 |
| 搜索框（48 + 12） | 60 |
| "继续阅读"卡片（有书时） | ~50 + 10 |
| **合计** | **约 250dp（有继续阅读卡时接近 310dp）** |

**问题**：这些区块全部在 `GridView` 之外，是**固定不动的**。在 5.5" 屏（可用高度约 560dp）上，250dp 的头部吃掉 45%，**封面网格只剩约 1.5 行**。用户滚动时，这 250dp 也一直占着。

**为什么重要**：
- 书架是这个 App 的门面，但首屏几乎看不到书。
- "融雪阅读 / 雪渐融，故事正生长。" 这块品牌区在用户看过三次之后就纯粹是占位了。
- 规范 §4 只关心"页面之间切换"，没提**同一页面内的滚动响应**——这是又一处规范空白。

**建议**：
1. 滚动时**折叠头部**：监听 GridView 的 `onScroll`，超过 60dp 后把品牌行与标题行收起来（`Motion.exit`，向上位移），只保留 chips 与搜索框；回滚时展开；
2. 更轻量的版本：只让**品牌行**（48dp）随滚动淡出，标题行与 chips 保留；
3. 搜索框与 chips 合并成一行（chips 在上、搜索框用图标按钮打开），再省 48dp。

**收益**：书架首屏可见书量提升一倍以上。**工作量 M。**

---

### V3 ★ 书架卡片完全没有水波纹，按压反馈还会让文字发虚

**证据**：`MainActivity.java:163,214,170`

```java
shelf.setSelector(new ColorDrawable(Color.TRANSPARENT));            // GridView 选择器也关了
...
tile.setBackground(SnowTheme.rounded(checked? ... :Color.TRANSPARENT, dp(14), checked?accent:Color.TRANSPARENT, dp(2)));
// ↑ GradientDrawable，不是 RippleDrawable

shelf.setOnTouchListener((v,event)->{ ... if(pressedTile!=null)Motion.press(pressedTile); ... });
// ↑ 唯一的反馈 = 整张卡片 scale 到 0.97
```

**问题**：
1. **卡片没有 ripple**。整个应用其他所有可点元素（按钮、面板行、列表项）都用 `SnowTheme.ripple(...)`，唯独**书架上最主要的内容卡片**没有。
2. 唯一的反馈是整卡 `scale 0.97`。缩放一个含文字的 ViewGroup 会触发子树重新栅格化，**120ms 内封面和标题文字都是模糊的**，抬起时再糊一次。

**为什么重要**：书架卡片是用户点击频次最高的控件，却是**整个 App 触感最弱的一个**——没有涟漪、只有一次整体缩小。相较于同 App 内其他按钮都有水波纹，这个对比很刺眼。

**建议**：
1. 卡片背景改为 `SnowTheme.ripple(底色, 14dp, rippleColor)`，保留描边；
2. 按压反馈从"整卡缩放"改为**"封面轻微缩放 0.96 + 叠加一层 8% 蒙层"**（`Motion` 加一个 `pressOverlay` 原语），文字不参与缩放 → 不糊；
3. 保留 `Motion.press` 给真正的图标按钮；卡片改用 overlay 反馈；
4. 顺带：卡片按压时**同时禁用水波纹**是错的——水波纹正是"这一下点到了"的直接证据，两者应该并存。

**收益**：最高频控件的触感升级。**工作量 S。**

---

### V4 ★ 默认封面的配色太接近，书架难以区分

**证据**：`BookCoverView.java:29`

```java
int[] colors={0xFF43839A,0xFF5C929D,0xFF638BA6,0xFF6C9492,0xFF507C8D};
// ↑ 五个都是同一青绿色系，差异仅在明度
paint.setColor(colors[(seed&Integer.MAX_VALUE)%colors.length]);
```

**问题**：五个封面色同属一个青绿色区间，饱和度与明度几乎一致。**一排默认封面看起来几乎是同一个色块**，用户只能靠读标题来分辨——封面的存在意义被浪费了。而新用户（还没有自定义封面）的书库里，**全部都是默认封面**。

**为什么重要**：书架的核心任务是"快速找到那本书"。视觉区分度不足时，用户被迫退回读文字，这正是封面该替用户做的事。

**建议**：
1. **在纸雪调色板内扩展色相**：青 → 墨绿 → 赭石 → 靛蓝 → 灰紫，保持低饱和与高明度，不要引入跳脱的颜色；
2. 分配依据从 `book.id.hashCode()` 改成**书名 hash**，这样同一本书在任何设备上颜色一致（id 依赖文件路径，换设备就变）；
3. 同时给封面加**极轻的斜向渐变**（同色相 92% → 108% 亮度），让相邻封面之间有微弱的"纸纹"分隔；
4. 现有的白色标题 + 底部 "融雪阅读" 水印保留，但水印的 6sp（`:48-49`）小到几乎不可读，建议改成 7sp 并降低到 50% 透明度，或直接去掉（留白更干净）。

**收益**：书架第一眼的可辨识度提升明显，且不增加任何资源文件。**工作量 S。**

---

### V5 ★ "选中"这个语义有六种视觉表达

**证据**：同一个 App 里，"选中"用了六套语言——

| 位置 | 表达方式 | 代码 |
|---|---|---|
| 分类 chip | accent 实心填充 + 白字 | `MainActivity:192` |
| 书架卡片 | ACCENT_SOFT 底 + accent 描边 + 右上角 ✓ 徽标 | `MainActivity:214-216` |
| 面板单选 | 右侧 "✓" / "›" 字符 | `AppSheet:76` |
| 分类勾选 | 文字前缀 "✓ 名称" / "— 名称" / "○ 名称" | `MainActivity:232` |
| 开关 | 圆角轨道 + 圆点 | `ReaderControls:43-46` |
| 面板行 | accent 底色 + accent 文字 | `AppSheet:74-76` |

**问题**：六种视觉都在表达"选中"，用户却学不到一个统一的心智模型，每遇到一个新控件都要重新读一遍。动效规范 §1 强调"复用优先：8 个场景只新增 5 个原语"，**视觉层却完全没做复用**。

**建议**：收敛为两个基元，全 App 统一：
- **基元 A（强选中，用于 chip / 主选）**：accent 实心填充 + 白字；
- **基元 B（弱选中，用于列表项 / 卡片）**：accent 描边 + accent 勾选标记（勾放在固定位置：右侧或左上角，**位置必须统一**）；
- 开关保持独立（它的"选中"语义是"开启"，本来就是另一类）；
- 分类勾选的 "✓/—/○" 前缀文字改为**左侧固定位置的勾选图标**，避免和标题文字混在一起导致文字起始位置跳动。

---

### V6 ★ 开关的关闭态几乎不可见

**证据**：`ReaderControls.java:40,45`

```java
soft = dark ? 0xFF42616B : 0xFFD0DEDE;      // 关闭态轨道色
paint.setColor(checked?accent:soft);
c.drawRoundRect(0,0,getWidth(),getHeight(),dp(14),dp(14),paint);
```

**对比度核算**：关闭态轨道 `#D0DEDE` 落在面板底色 `#FFFCF5` 上，亮度比约 **1.34 : 1**。

**问题**：1.34:1 的对比度低于任何可读性阈值。用户看到的是一个"几乎和背景同色的小圆角块 + 一个白圆点"，**分不清它是关着还是根本没画出来**。Material 规范的未选中轨道通常有 1px 描边或明显更深的灰。

**建议**：
1. 关闭态加 1px 描边（`0x33000000` 或 `0xFF9EB2B5`），轨道色提到 `#C2D3D5` 左右；
2. 滑块（thumb）尺寸随状态变化：关闭 12dp、开启 18dp —— 这既是 Material 的做法，也让"开/关"在形状上可区分（不依赖颜色）；
3. 轨道尺寸从 46×26dp 调到 Material 3 的 52×32dp，thumb 24dp；触摸区域已经由整行 `ToggleRow` 承担（`setOnClickListener` 在整行上），不受影响。

---

### V7 同一个 App 里出现了三种 SeekBar

**证据**：

| 位置 | 外观 |
|---|---|
| `AppSheet.styleInputs:147` | 24dp **圆角方块** thumb，accent 填充 + paper 描边，`setThumbOffset(dp(12))` |
| `MainActivity:486`（阅读进度条） | 系统默认圆形 thumb，仅设了 tint |
| `ListenUi:57`（听书进度条） | 系统默认圆形 thumb，无 tint 设置 |

**问题**：三个外观不同的滑块分布在三个功能区。`setThumbOffset(dp(12))` 让 thumb 超出轨道 12dp，在部分 ROM 上会被裁切或与轨道重叠。

**建议**：抽一个 `SeekBar styleSeek(SeekBar)` 统一：thumb 用 20dp 圆形（accent 填充 + paper 描边）、offset 归零、轨道高 4dp、进度色 accent / 底色 soft。三个调用点全部改用。

---

### V8 输入框有两套皮肤

**证据**：`MainActivity:155`（书架搜索框）用 `shape(card,16)` = surface 底 + 1px 描边；`AppSheet.styleInputs:142`（面板输入框）用 `SnowTheme.rounded(soft,14,0,0)` = **青色底、无描边**。

**建议**：统一为"surface 底 + 1px border + 14dp 圆角"。顺带给搜索框补一个**清除按钮**（有内容时显示 ✕），这是 Material 输入框的标准配置，现在的搜索框右侧是空的。

---

### V9 目录里"已读 / 当前"的区分只靠颜色

**证据**：`MainActivity:592`

```java
int color = current ? accent
           : index<anchor ? (isNight()?0xFF8A9A9F:0xFF697474)
           : ink;
```

**问题**：只有颜色一个通道。`#697474` 在 `#FFFCF5` 上约 4.4:1，勉强达标但偏弱；**色弱用户无法区分"已读"和"未读"**。

**建议**：加一个非颜色通道——已读章节**左缩进 8dp**（或加一条 2dp 竖条），并保持"已读 = 更靠左"这个物理直觉。规范里"用非颜色的第二通道"是通用的可访问性做法。

---

### V10 全 App 唯一的绿色是那个 logo

**证据**：`ic_leaf.xml` 用 `#528F80` / `#6F9E7C` / `#356E58` / `#B4D2A5`（绿色系），被 `MainActivity:137` 放在书架左上角 40dp 处；而 `SnowTheme` 全部是青墨系（`#248FA9` / `#0F5F73` / `#193E4B`）。

**问题**：书架顶部那一小块饱和绿色，是整个界面唯一的暖色块，出现在所有青墨元素中间。它不像"品牌标识"，更像"一个可以点的状态提示"。

**建议**：要么把应用内 logo 改成青墨系（launcher 图标可以保留绿色，因为 launcher 是独立语境），要么明确它的品牌语义（不响应点击、不加涟漪），并在视觉上与可点元素区分开。

---

### V11 面板顶部占用过多，且 eyebrow 与拖拽条冗余

**证据**：`AppSheet.java:49-60`

```java
View handle=new View(...);              // 34×4dp 拖拽条，bottomMargin 14
panel.addView(handle, hp);
TextView eyebrow=text("融雪阅读",11,accent); eyebrow.setLetterSpacing(.12f);
panel.addView(eyebrow);
TextView title=text(spec.title,21,ink);
```

**问题**：面板顶部依次是拖拽条 + eyebrow + 标题，合计约 80dp。面板总高又被限制在屏幕 48%（`:81`），在手机上本就紧张。**拖拽条与右上角 "×" 是两套冗余的关闭方式**，而 eyebrow "融雪阅读" 在一个已经明确是本 App 的面板里是冗余信息。

**建议**：去掉 eyebrow（品牌已在启动页与 launcher 出现）；标题与内容之间的间距从 12dp 收到 8dp；拖拽条保留（它是唯一的"可拖"暗示）但把 "×" 改为更克制的图标按钮（现在是 26sp 的 "×" 字符，与其他 22dp 线性图标不统一）。

---

## 第三梯队 · 细节打磨（D1–D8）

| 编号 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **D1** | **矢量图标的描边色硬编码且不统一**（`ic_more`/`ic_toc` 是 `#248FA9`，`ic_listen` 是 `#19768E`）。运行时被 `setTint` 覆盖所以看不出问题，但复用这些资源、导出给其他用途、或做夜间模式时会踩坑 | `res/drawable/ic_*.xml` | 统一改为 `#FF000000` + `android:tint` 由代码控制，避免同一套图标两种蓝 |
| **D2** | 书架列宽固定 104dp（`fontScale>1.15` 时 134dp），不随屏幕宽度自适应。平板上会出现 5 列小封面 + 大片空白 | `MainActivity:163` | 按可用宽度算列数，目标列宽 112–140dp |
| **D3** | 面板列表行同时设了 `contentDescription` 又保留了子 TextView 文本，TalkBack 可能重复朗读 | `AppSheet:75-77` | 行上设 `IMPORTANT_FOR_ACCESSIBILITY_YES` 并把子 TextView 标 `NO`，或直接去掉行上的 contentDescription |
| **D4** | 分类 chip 的**数量**与名称同字号同色，数字不突出 | `MainActivity:191` | 数量改用 muted 色并缩小一号，或加细分隔符 |
| **D5** | 底部导航的 "Aa" 用 22sp serif 粗体，与旁边三个 22dp 线性图标视觉重量不一致 | `MainActivity:933` | "Aa" 字号降到 18sp，或换成一个与其他图标同风格的矢量图标 |
| **D6** | `ToggleRow` 切换时发 `TYPE_WINDOW_CONTENT_CHANGED` 而不是 `TYPE_VIEW_CLICKED` / `announceForAccessibility`，TalkBack 不会明确播报"已开启/已关闭" | `ReaderControls:47` | 改发 `TYPE_VIEW_CLICKED` 并附上状态文案 |
| **D7** | 默认封面底部 6sp 的 "融雪阅读" 水印小到几乎不可读，只是一层噪点 | `BookCoverView:48-49` | 提到 7sp 且降到 50% 透明度，或去掉 |
| **D8** | 自动夜间每 30s 轮询一次，**可能在阅读过程中**触发整页重建 + 全屏截图（`refreshAutoTheme` → `capturePage` → `showReader` → `revealPage`）。虽然有截图交叉淡入兜底，但一次 8MB 的位图分配（`:887`）仍可能触发 `OutOfMemoryError`，而 catch 之后**会静默降级成硬切闪屏** | `MainActivity:737,887` | 主题切换避开用户正在操作的时刻；`capturePage` 失败时给出更平滑的降级路径（纯 crossFade 而非硬切） |

---

## 附：与已有清单的关系

| 已有清单条目 | 本文是否重复 | 说明 |
|---|---|---|
| E03（词级高亮整页重排） | 否 | E03 讲性能开销；M2 讲的是同一条链路上**视觉反馈本身是闪烁的** |
| E08（`dispatchTouchEvent` 的 Handler 往返） | 部分 | E08 讲触摸事件路径；M4 讲的是**动画启动路径**上的跨进程查询，是另一处 |
| E17（通知重建） | 否 | 通知不在本文范围 |
| B07（面板打开时自动夜间不生效） | 否 | 逻辑 bug，非视觉 |
| §6「不建议仿真翻页/卷曲」 | **同意** | 本文 M1 建议的是**跟手拖动**，不是卷曲模拟；M5 只优化现有平移的成本。仿真卷曲在低端机上必然掉帧，这条判断不变 |

---

## 建议排期

| 版本 | 内容 | 理由 |
|---|---|---|
| **0.9.3** | **V1**（工具栏遮挡）· **M2**（进度闪烁）· **M3**（动画缩放）· **M4**（去掉每次 IPC）· **M6**（分类切换过渡）· **M7**（开关/选项条过渡）· **M9**（遮罩黑闪） | 都是小改动、高可见度；V1 是唯一确定的界面缺陷 |
| **0.10** | **M1**（翻页跟手）· **M5**（翻页硬件层）· **V3**（卡片水波纹）· **V4**（封面色板）· **M8**（回弹曲线） | 翻页是核心交互，这一组做完阅读体验会有质变 |
| **0.11** | **V2**（书架头部折叠）· **V5**（选中语言统一）· **V6**（开关可见性）· **V7/V8**（SeekBar 与输入框统一）· **D1–D8** | 一致性收尾 |
| 随时 | **M10**（触觉反馈） | 加一次 `performHapticFeedback` 即可，建议跟着 M1 的翻页一起做 |

---

## 验收口径（补进下版 VALIDATION）

- 打开任意书，**工具栏显示期间**能看到章节名与进度百分比；
- 导入 500 个文件，进度文字**不出现明暗闪烁**，只有进度条在动；
- 系统把动画时长调到 0.5× / 2×，应用内所有动效时长**随之变化**；
- 分页模式下**慢速拖动页面**能翻页（不需要甩）；
- 翻页动画全程无掉帧（用 `Choreographer` 统计，单帧 < 16ms）；
- 切换分类、多选进出，**无闪屏**，有 180ms 过渡；
- 点击开关，滑块**平滑移动**而非瞬移；
- 面板遮罩出现时**不先黑一下**；
- TalkBack 朗读 AppSheet 列表项不重复；
- 开关关闭态与背景的对比度 ≥ 3:1。

---

**一句话总结**：这个 App 的动效**规范写得比实现好**——问题不在于"缺动画"，而在于**动画只覆盖了一部分界面，剩下的部分连硬切都没做好**。先把 M1（翻页跟手）和 V1（工具栏遮挡）做了，这两个改动的用户可感知收益，超过再补二十个缓动。
