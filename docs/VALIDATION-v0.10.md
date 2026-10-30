# 融雪阅读 0.10.0 验证记录

2026-10-09；versionCode 14；com.snowread.app。范围和剩余事项见 IMPLEMENTATION-v0.10.md。保留 0.9.2 启动修复；其对照崩溃、升级数据保留记录见 docs/VALIDATION-v0.9.2.md。

## 构建与包检查

- API 35 资源/Java/D8 构建通过，137 个生产 class；最低 API 23、目标 35。
- ZIP 4 字节对齐、开发签名 v1/v2/v3 通过；证书延续，可覆盖升级，不需要卸载。
- APK 3,049,476 字节；SHA256 `25a6464c04e3490be228d76610e2956f9d2a077ba3912105df1057249ad23f35`。
- 证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。
- 字体 2,815,080 字节、7,812 映射字符，APK 内字节与本地 OFL 子集一致；SHA256 `72a155136dab216aeb4c3b20ff88ad13cac947faff69d4cbce0af00e39aed7da`。检查 ZIP 原始中央/本地文件名无反斜杠，避免 Windows ZIP API 名称规范化掩盖错误。
- 权限只有 FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、WAKE_LOCK、POST_NOTIFICATIONS；没有 INTERNET、录音、电话或广泛存储权限。
- 沿用书库 3、备份 2，可选元数据通过往返测试。测试适配器与独立 instrumentation 不进入生产 APK。

JDK 21.0.12.1 / Build Tools 35.0.0。沙箱 javac 关闭只读 android.jar/classes.jar 时有 AccessDeniedException 环境诊断，退出码 0；生产 class、DEX 与签名通过。未运行完整 Gradle 同步/lint、release/R8；交付仍为开发测试包。

## 宿主回归

| 测试 | 断言数 |
|---|---:|
| TextDocumentTest | 1997 |
| ReaderBehaviorTest | 183 |
| ShelfOrganizationTest | 1548 |
| SearchResultsTest | 168095 |
| ChapterOrderTest | 20005 |
| ListenCoreTest | 55994 |
| ReaderPolicyTest | 5791 |
| DirectoryRowsTest | 18 |
| LibraryPersistenceTest | 37 |
| BackupAndImportTest | 52 |
| BatchTransactionTest | 41 |
| SafetyDataTest | 43 |
| FeatureCoreTest | 30 |
| FeatureStorageTest | 24 |

全部通过。新增覆盖书名/编码、同名章节与卷/上下文锚点、未定位保留、Unicode、章内忽略大小写搜索与分组、排版/锚点备份、非法元数据拒绝、编码与正文替换失败回滚、正文 ID 缓存、回收站容量及清空。500 本仍只提交一次索引。断言数含逐字符检查，不代表同数量产品场景；宿主耗时不代表手机性能。存储使用真实生产逻辑、layoutlib org.json 与 tests/host AtomicFile 适配器，不能替代 Android 磁盘满/断电测试。

## Android 运行回归

专用 Android 17 / API 37 模拟器 emulator-5556，最终生产包与独立测试 APK：

- 36 项集成检查通过，包括提前调用沉浸式安全创建 DecorView、AssetManager 读取 OTF、Typeface 真正加载内置字体与汉字、阅读 TextView 字体引用。
- 两本书排版隔离/持久化与全局值保留、退出书架清除本书常亮覆盖。
- 进度书签数据/章节跳转入口，横滑翻页与取消不翻页、中心双击书签、垂直翻页、音量重复事件、双指滑动改变窗口亮度并保存。
- 英文大小写搜索返回分组，点击章标题折叠。
- 合成 PCM WAV 模拟已完成的预备文件，真实 MediaPlayer 播放、语速不匹配拒用、一次回调、播放后清理。此项不等于真实中文 TTS 预合成验收。
- 最终包连续三次冷启动通过：进程存活、MainActivity resumed、无新增 crash。

原始功能日志保存在 docs/regressions/0.10-feature-instrumentation.log，冷启动简要记录保存在 docs/regressions/0.10-startup.log。测试仅在本次专用模拟器运行，未卸载或清空真实手机数据。

## 尚需设备验证

- 用户 Android 16 / 红米 K80 Ultra 与 Android 6–16 设备矩阵；API 37 模拟器不能等同这些设备。
- 真实中文 TTS 双客户端预合成、句间间隔、语速/音色切换、锁屏持续播放、OEM 后台、蓝牙/来电、通知拒绝。
- 长书分页边界/字体补字/字距、选区朗读、TalkBack、200% 字体、小屏/横屏、三键与手势导航、手指拖动视觉与 60/120Hz 帧率。
- SAF/微信/QQ 文件提供器、编码歧义、多本导入与外部替换实际文件，磁盘满/强杀/重启恢复及低内存。

本轮没有宣称 F08/F10/F11 已实现，也没有将代码接入等同于完整真机验收。
