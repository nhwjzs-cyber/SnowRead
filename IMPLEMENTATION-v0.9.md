# 0.9：动效规范与听书方案的落地范围

本次依据用户指定的两份 v1 文档；原件保留在 docs/specs。实现 v1 的系统 TTS 听书流程及多数体验项，P2 的神经模型、多角色、音频导出、Auto/Wear、统计和云同步仍是后续内容。

## 动效

| 要求 | 实现 |
|---|---|
| 共享令牌与原语 | Motion：FAST 120 / BASE 180 / SHEET 220 / PAGE 240；enter/exit、press/release、crossFade、progress、sheetIn/out |
| 面板与遮罩 | 32 位移、0.92 alpha 入场，16 位移、160ms 退场；Window dim 0→0.32，取消后归位；不弹跳 |
| 工具栏 | alpha + 上/下 8 的位移，180ms；浮层显隐不改变正文区域 |
| 提示 | 12 入场 / 6 退场，3.2 秒停留；新提示直接替换旧提示 |
| 书架反馈 | Grid 手势观察器调用 press/release，滑动超出阈值释放且不吞掉点击；多选徽标使用短确认动画 |
| 开书/退书和主题 | 有像素上限的旧页快照交叉淡入；分配失败跳过动画，避免为视觉效果导致崩溃 |
| 任务进度 | TaskProgress 合并更新约每 120ms 一次；导入按文件，备份/合并按真实复制字节，未知解压阶段使用细不确定线 |
| 搜索 | 整个结果列表淡入，搜索中细进度线；没有逐行动画 |
| 减少动效 | 系统时长缩放、触摸探索或用户 reduceMotion 任一禁用，所有共享原语立即进入最终状态 |
| 生命周期 | 同 View 旧过渡先取消，暂停/旋转完成当前状态不重播；销毁时立即关闭窗口，避免退出动画持有旧 Activity |
| 翻页 | 保留原 240ms、插值和页面滑动算法；仅增加独立听书高亮、用户操作通知和减少动效入口 |

原规范中的“只动 alpha/translation/scale”用于界面变换；确定进度按规范另用 ValueAnimator 更新 progress。没有 LayoutTransition、Lottie 或第三方动效依赖。两张小型耳机矢量图标仅用于听书入口和通知。

## 听书

| 要求 | 实现 |
|---|---|
| 入口/引导 | 阅读顶栏耳机、更多菜单；语音检查、安装/设置/重试、通知权限和仅前台选择 |
| 系统 TTS | TtsEngine，优先已安装离线中文语音；未安装语音明确提示；联网 Voice 需用户明确允许 |
| 切句 | TextChunker 按中文/英文标点、换行、逗号回退和上限切分，后引号合并，代理对安全 |
| 原文映射 | 压缩空白、过滤网址后保留逐 UTF-16 单元映射；不使用错误的 chunk.start + rangeStart 简单相加 |
| 队列/旧回调 | SpeechQueue，每次只发一段 QUEUE_FLUSH；generation:start 丢弃 stop/seek/变速前回调；缓存下一句 |
| 控制 | 播放/暂停/停止、句/章前后跳、拖动定位；暂停从本句起始恢复 |
| 语速/语音 | 0.5–2.0、步长 0.1、快捷条；引擎/所有语言语音列表、试听 |
| 后台 | ListenService、mediaPlayback 类型前台服务、MediaSession、通知、音频焦点和部分唤醒锁 |
| 音频打断 | 临时焦点丢失/duck 时暂停并按策略续播；永久丢失、手动暂停、拔出耳机不自动恢复 |
| 定时/范围 | 15/30/60 分钟、章末/句末；当前页/本章/全书；章末自动连播可开关 |
| 高亮/跟随 | 句级；引擎提供 onRangeStart 时词级；节流检查范围，不逐词重复跳页；手动浏览临时停止跟随 |
| 小播放条 | 书架 footer 内占据空间，在导入按钮上方；暂停、停止、打开 |
| 断点 | 每句实际开始、暂停/停止与章末保存；重建保持暂停；一次恢复标志防止旧听书断点反复覆盖后续阅读 |
| 隐私 | 默认锁屏隐藏书名，公共通知与媒体元数据都处理；可关闭隐私选项 |
| 错误 | 8 秒初始化超时；当前句重试一次；合成超时保护；引擎、语言、语音包、音频焦点、存储失败有文字反馈 |

## 共享状态与权限

LibraryRepository 只持有一个应用级 LibraryStore、串行 I/O 队列及一份当前 TextDocument 缓存。Activity 与 Service 通过同一 Book 引用共享进度；活动听书会话接管该书进度，Activity 的分页、滚动与暂停回调不会覆盖它。停止后交还阅读页；切换到另一册结束活动会话。恢复备份前停止听书，并替换共享仓库中的实例。

Service/TTS 回调统一回到服务主线程，磁盘进度保存合并提交到共享 I/O 队列，不逐词写索引。LibraryStore 的访问/保存同步，书签集合修改也受相同锁保护。原批量事务与 500 本一次索引提交的规则保留。

动效本身不新增权限；听书按方案新增 FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、WAKE_LOCK、POST_NOTIFICATIONS。没有 INTERNET、电话状态、录音、全盘存储或默认电池豁免权限。

## 与原文档需要区分的地方

1. Android 的媒体会话通知存在通知权限豁免，不能断言拒绝 POST_NOTIFICATIONS 后锁屏控制一定失效。仍提供通知权限申请，以及用户可选择的仅前台模式；拒绝后不反复弹权限框。
2. 清理朗读文本后简单相加偏移会错位，因此保留映射表，并对清理/代理对边界做单测。
3. 声音质量、中文/离线包、词级回调取决于系统引擎。Voice 标记不需要网络不等于数据已经安装，另检查 notInstalled 特征。
4. Service 是唯一播放 owner，进度串行保存和文档共享需要一个仓库，不能只给两个各自的 Store.save 加 synchronized。
5. 睡眠定时只在句末/章末判断，不更改系统音量。v1 不做音频导出；没有采用文档中与本次范围无关的后续路线。
6. “锁屏 30 分钟不中断”“出声 <2 秒”“句间 <200ms”“60/120Hz 流畅”都是设备验收指标，本环境未完成；代码接入和宿主测试不代表已经通过。

## 官方实现依据

- [Android 音频焦点](https://developer.android.com/media/optimize/audio-focus)：target 35 先从可见界面/前台服务取得焦点，朗读被打断时暂停。
- [前台服务类型要求](https://developer.android.com/about/versions/14/changes/fgs-types-required)：mediaPlayback 及对应权限。
- [通知权限与媒体会话豁免](https://developer.android.com/develop/ui/views/notifications/notification-permission)。
- [Voice](https://developer.android.com/reference/android/speech/tts/Voice)、[语音未安装特征](https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine)、[范围回调](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)。
