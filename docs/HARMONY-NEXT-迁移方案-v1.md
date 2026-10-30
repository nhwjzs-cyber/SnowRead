# 融雪阅读 → 鸿蒙 NEXT（HarmonyOS 5 / 6）迁移方案

- 版本：v1
- 针对：`融雪阅读-v0.11-debug.apk`（0.11.0，versionCode 17）及同版本源码
- 结论类型：**重写方案**，不是格式转换

---

## 0. 先说结论（三句话）

1. **APK 不能"转成"HAP。** 鸿蒙 NEXT（HarmonyOS 5 起）移除了 AOSP 兼容层和 Android 运行时，APK 在 NEXT 设备上根本无法运行，因此不存在官方的"APK→HAP 转换工具"；HarmonyOS 4 及更早版本能跑 APK，是因为还留着兼容层，与 NEXT 无关。
2. **你的工程规模适合人工重写。** 55 个 Java 文件、4311 行代码、3.0 MB 包体，属于"小而精细"——重写成 ArkTS 是现实可行的，不需要推翻产品。
3. **真正要做的是"重写 + 数据互通"，不是"换个壳"。** 好消息是你没有任何联网依赖、没有账号体系、数据全部落在本地 JSON（书库格式 v3 / 备份格式 v2），这两份格式可以原样被鸿蒙版复用，用户的书架与阅读进度能跨端迁移。

---

## 1. 现状测绘（按代码实测）

| 模块 | 现有实现 | 迁移难度 | 备注 |
|---|---|---|---|
| 界面整体 | 单个 `MainActivity`（1099 行）用代码构建全部 View | 中 | ArkUI 声明式会大幅减少代码量，但要重新组织结构 |
| 分页阅读器 | `PagedReaderView extends View`，`StaticLayout` + Canvas 自绘 + 翻页动画 | **最难** | 核心手感全靠它，也最容易在迁移中走形 |
| 听书 | `ListenService`：前台服务 + MediaSession + 系统 TTS + WakeLock + 通知 | **难** | 鸿蒙要换成长时任务 + AVSession + Core Speech Kit |
| 书库存储 | `LibraryStore`，`AtomicFile` + JSON，格式 v3 | 易 | 格式可直接复用 |
| 备份/恢复 | `BackupArchive`，格式 v2 | 易 | 格式可直接复用 |
| 导入 | SAF（`ACTION_VIEW`/`ACTION_SEND` + `OpenableColumns`）+ 编码探测 GB18030/Big5 | 中 | 换成 picker 与 want 的 skills 声明 |
| 字体 | 内置 `RongxueSerif-Regular.otf`（OFL 授权） | 易 | `font.registerFont` 直接注册 |
| 书架交互 | 多选、拖拽归类、分类标签、置顶 | 中 | 鸿蒙用 PanGesture / dragController 重做 |
| 回收站、搜索、书签、目录 | 纯逻辑 + 列表 | 易 | 逻辑基本可直译 |
| 权限 | 仅 4 项：前台服务、媒体播放前台服务、WakeLock、通知 | 少 | 鸿蒙对应长时任务 + 通知授权，反而更干净 |
| 网络 | **无 INTERNET 权限**，纯本地 | — | 迁移后同样可保持零联网，是加分项 |

源码规模（实测）：

```
55 个 .java，合计 4311 行
MainActivity.java      1099
PagedReaderView.java    506
TextDocument.java       201
AppSheet.java           199
LibraryStore.java       192
ListenService.java      170
BackupArchive.java      156
（其余 48 个文件均 < 140 行）
```

---

## 2. 三条路线，以及为什么选第一条

| | A. 原生 ArkTS 重写 | B. Web 壳（ArkWeb 套壳） | C. 跨平台框架重写 |
|---|---|---|---|
| 能否上架鸿蒙应用市场 | 能 | 能，但审核对纯 Web 壳趋严 | 能 |
| 翻页手感 | 可做到与现在一致 | 差：WebView 内手势延迟、无法自绘分页动画 | 中 |
| 现有 Java 代码的复用度 | 逻辑可直译，接近 100% 思路保留 | 0（要写一遍 Web 版） | 0（等于用新语言重写） |
| 工作量 | 中 | 高（等于再做一版） | 高 |
| 长期维护 | 最好 | 差 | 中 |
| 结论 | **推荐** | 否掉 | 否掉 |

B 否掉的原因很直接：你的阅读器手感来自 `StaticLayout` 精确分页 + Canvas 逐帧拖动，塞进 WebView 这两样都要退化，而工作量并不比 A 少。
C 否掉的原因：源码是纯 Java，Flutter / uni-app x / RN 都帮不上，等于用另一套语言重写，收益低于 A。

> 关于"一键转换"类工具：市面上声称能 APK→HAP 的，绝大多数是把 APK 塞进 HarmonyOS 4 兼容层，或者做的是人工外包改造。华为官方的 **DevEco Studio + CodeGenie** 能把 Java/Android 代码片段辅助翻译成 ArkTS，但那是"片段级辅助"，不是工程级转换，仍需要人逐文件校对。

---

## 3. Android → HarmonyOS NEXT API 映射（对着你的实际调用）

| 你现在用的 | 鸿蒙 NEXT 对应 | 说明 |
|---|---|---|
| `Activity` / `View` 树 | `UIAbility` + ArkUI 声明式页面（`@Entry @Component`） | 单页面方案可继续用；建议拆成 ShelfPage / ReaderPage / SettingsSheet |
| `StaticLayout`（分页核心） | `@ohos.graphics.text` 的 `Paragraph`（ArkGraphics2D），或 `measure.measureTextSize` | 见 §5.1，这是全工程唯一需要做技术验证的点 |
| `Canvas` + `onDraw` | `Canvas` 组件 + `CanvasRenderingContext2D` | 逐帧动画改 `animateTo` / `@ohos.animator` |
| `GestureDetector`（fling/scroll/longPress） | `PanGesture` / `TapGesture` / `LongPressGesture`，或 `Swiper` | 翻页方向可继续自己算 |
| `SharedPreferences` | `@ohos.data.preferences` | 直接对应 |
| `AtomicFile` + JSON 文件 | `@ohos.file.fs`（`fs.writeSync` + 临时文件改名） | 原子写策略可以照搬 |
| `TextToSpeech`（`android.speech.tts`） | `@kit.CoreSpeechKit` 的 `textToSpeech` | 音色/离线包由系统决定，需实测中文断句 |
| `MediaSession` / `MediaStyle` 通知 | `@ohos.multimedia.avsession`（AVSession + AVQueue） | 锁屏/耳机键/通知卡片都由它出 |
| `startForeground` + `foregroundServiceType=mediaPlayback` | `backgroundTaskManager.startBackgroundRunning`（`AUDIO_PLAYBACK`） | 需在 `module.json5` 声明 `backgroundModes` |
| `PowerManager.WakeLock` | 长时任务自带；屏幕常亮用 `window.setWindowKeepScreenOn(true)` | 不必再手动申请锁 |
| `NotificationChannel` + `POST_NOTIFICATIONS` | `@kit.NotificationKit`，`requestEnableNotification` | 首次弹授权 |
| `Intent.ACTION_VIEW` / `ACTION_SEND`（打开方式/分享 TXT） | `module.json5` 里 ability 的 `skills`：`ohos.want.action.viewData` + `uris: [{ type: 'text/plain' }]`，`want.uri` 取值 | 要单独验证第三方文件管理器能否拉起 |
| `ACTION_OPEN_DOCUMENT` + `OpenableColumns` | `@kit.CoreFileKit` 的 `picker.DocumentViewPicker` | 拿不到原文件名时用 `fs.stat` |
| `Typeface.createFromAsset` | `@ohos.font` 的 `font.registerFont({ familyName, familySrc: $rawfile('...') })` | OFL 字体可继续内置 |
| `Vibrator` | `@ohos.vibrator` | 需 `ohos.permission.VIBRATE` |
| `WindowManager.LayoutParams.screenBrightness` | `window.setWindowBrightness()` | 仅作用于本应用窗口 |
| `WindowInsets` / edge-to-edge | `expandSafeArea` + 沉浸式窗口 | 比现在简单 |
| `AccessibilityNodeInfo` | 组件的 `accessibilityText` / `accessibilityLevel` | 需重新标注 |
| `GridView` / `ListView` / `SeekBar` | `Grid` / `List` / `Slider` / `Swiper` | 基本一对一 |

权限对照：Android 4 项权限 → 鸿蒙只需 `ohos.permission.KEEP_BACKGROUND_RUNNING`、`ohos.permission.VIBRATE`，加上通知授权弹窗。**没有一项需要联网权限**。

---

## 4. 分阶段计划

| 阶段 | 目标 | 产出 | 验收标准 | 估时（人日） |
|---|---|---|---|---|
| **M0 环境与骨架** | DevEco 工程跑通真机 | `entry` 模块、`bundleName=com.snowread.app`、空书架页 | 真机能装上、能启动 | 1 |
| **M1 数据层** | 书库 + 备份格式迁移 | `LibraryStore.ets` / `BackupArchive.ets`，读写格式 v3/v2 | **能导入 Android 版导出的备份并正确显示书架** | 1.5 |
| **M2 书架** | 书架、分类、多选、拖拽归类、回收站、搜索 | ShelfPage + 组件 | 与 Android 版逐项对照 | 3 |
| **M3 导入** | picker 导入、打开方式拉起、编码探测 | ImportService | GB18030/Big5 判定与现在一致 | 1.5 |
| **M4 阅读器** | 分页 + 翻页手势 + 进度 + 书签 + 目录 | ReaderPage + `Paginator.ets` | 同字号同屏行数、翻页手感可接受 | 3～4 |
| **M5 Aa 排版** | 字体、字号、行距、留白、三种纸色、夜读、亮度手势 | SettingsSheet | 设置项与现在一一对应 | 2 |
| **M6 听书** | TTS、上一句/下一句/上下章、语速、睡眠定时、通知与锁屏 | ListenAbility + AVSession | 息屏继续朗读、通知可控 | 2.5 |
| **M7 打磨与上架** | 动效、无障碍、图标、隐私说明、签名、软著 | 发布用 `.app` | 通过审核 | 2 |

合计约 **16 人日**（熟手手工节奏）。若由我批量生成 ArkTS 代码、你负责在 DevEco 里编译与真机调试，可以压到 **4～6 轮迭代**：每轮我出一批模块，你回报编译错误与截图，我再修。

---

## 5. 三个真正需要提前验证的点

### 5.1 分页引擎（唯一的技术不确定项）

`StaticLayout` 在鸿蒙没有等价物，有三条候选：

| 方案 | 做法 | 优点 | 风险 |
|---|---|---|---|
| a. ArkGraphics2D `Paragraph` | `ParagraphBuilder` 构建段落，用 `getHeight()` 对前缀文本二分，切出刚好填满一页的长度 | 排版精度最高，最接近 `StaticLayout` | API 版本要求较新（建议 API 12+），调试成本略高 |
| b. `measure.measureTextSize` | 按宽度约束测高，粗粒度分页 | 简单 | 精度差，中英混排/标点悬挂容易出错 |
| c. Canvas 自己算行 | `measureText` 逐字断行，自己维护行高 | 完全可控，与现在的 `fitPage` 逻辑最像 | 工作量最大，需自己处理标点禁则 |

**建议先做 a，失败再退 c。** 你现有的 `fitPage` 已经是一个"取窗口文本→量高→缩窗"的二分循环，这个结构可以直接照搬到 `Paragraph` 上：

```
// 参考结构（未编译验证，需在 DevEco 中补 API 细节）
fitPage(text, start, limit, width, height) {
  let lo = start, hi = Math.min(limit, start + 4000);
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    const h = measureHeight(text.slice(start, mid), width);  // Paragraph 或 measureTextSize
    if (h <= height) lo = mid; else hi = mid - 1;
  }
  return lo;  // 本页应止于的字符偏移
}
```

性能上注意两点：分页必须在 `taskpool` 里跑（对应你现在的 `ExecutorService`），并保留"当前页 + 相邻页"缓存（对应现在的 `LinkedHashMap` LRU）。

### 5.2 听书的后台存活

鸿蒙的做法是：`UIAbility` 申请 **长时任务 `AUDIO_PLAYBACK`** + 建 **AVSession**，两者都齐了，息屏后朗读才不会被冻结。少了任何一项，锁屏 30 秒左右就会被系统掐掉——这是移植期最常见的"突然不念了"故障。

### 5.3 数据互通（建议提前到 M1）

不要等到最后才处理。先让鸿蒙版能读 Android 版**备份格式 v2** 的 JSON，用户就能自己"Android 导出备份 → 传到鸿蒙手机 → 鸿蒙版恢复"，书架、分类、阅读进度、书签全部搬过去。这一步做完，鸿蒙版立刻有实用价值，其余功能可以慢慢补。

---

## 6. 构建与发布

**本机现状**（已检查）：未安装 DevEco Studio，无 `ohpm`、`hvigorw`，无 OpenHarmony/HarmonyOS SDK。所以 HAP 无法在当前环境编译，必须先装工具链。

需要准备：

1. **DevEco Studio 5.0 及以上**（自带 hvigor、ohpm、SDK Manager），装 HarmonyOS SDK **API 12 起，建议 18 / 20**。
2. 新建工程时选 **Empty Ability**，模块名 `entry`，`bundleName` 用 `com.snowread.app`（与现有包名一致便于对照；正式上架时须与 AGC 注册的一致）。
3. **调试签名**：真机装 HAP 必须有签名。DevEco 里登录华为账号 → `File > Project Structure > Signing Configs` → 勾选自动生成签名（会写 `build-profile.json5`）。**没有签名，HAP 装不上任何真机。**
4. 真机需开启"开发者模式 + USB 调试"，或在手机侧用"应用调试助手"安装。
5. 上架：发布物是 `.app`（App Pack，内含 HAP），需要华为开发者账号 + 软件著作权等资质，走应用市场审核。

> 提醒一点：鸿蒙 NEXT 对侧载限制较严，个人分发 HAP 需走调试签名（Profile 有效期有限）或上架。这跟 Android 直接装 APK 的体验不同，产品侧要提前有预期。

---

## 7. 风险清单

| 风险 | 影响 | 对策 |
|---|---|---|
| 分页手感与现在不一致 | 高（产品核心体验） | M4 单独排期，先做技术验证再全量铺 |
| 系统 TTS 中文音色与 Android 不同 | 中 | 用 Core Speech Kit 实测断句与停顿，参数做可调 |
| 字体注册后行高/字重偏差 | 中 | 分页前先按注册字体测量，不要用默认度量 |
| 打开方式/分享拉起不稳定 | 中 | 用多个文件管理器实测，必要时引导用户走应用内导入 |
| 上架审核（纯本地工具类） | 低 | 隐私说明写清"不联网、不收集"，软著提前办 |
| 以为能"一键转" | 最大 | 本文档第 0 节即为答案 |

---

## 8. 建议的第一步

按性价比排序，本周就能动：

1. 装 DevEco Studio + SDK，建空 `entry` 工程，真机跑通"Hello"（半天）。
2. 做 **M1 数据层**：把 `LibraryStore`、`BackupArchive` 直译成 ArkTS，做一个"导入备份并列出书名"的临时页面——这一步做完你就可以验证跨端数据迁移是否成立。
3. 再攻 **M4 分页**：先写一个只有正文、没有工具栏的阅读页，把 `Paragraph` 二分分页跑通，量出"同字号同屏行数是否与 Android 版一致"。
4. 分页过关后，剩余模块基本是体力活，按 M2 → M3 → M5 → M6 顺序补。

---

*本文档中的 API 名称基于 HarmonyOS NEXT 公开 API（API 12+）；代码片段为结构参考，需在 DevEco Studio 中编译验证。*
