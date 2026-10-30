# 融雪阅读 0.10.1 验证记录

2026-10-29；versionCode 15；com.snowread.app。变更见 FIX-v0.10.1.md。前版完整记录保留在 docs/VALIDATION-v0.10.md；字体、锚点、备份等实现说明仍见 IMPLEMENTATION-v0.10.md。

## 最终构建与包检查

- API 35 资源/Java/D8 构建成功，143 个生产 class；minSdk 23、targetSdk 35。
- APK ZIP 4 字节对齐、v1/v2/v3 签名检查通过，开发证书与旧版一致。可覆盖更新，无需卸载。
- APK 大小 3,057,668 字节；SHA256 `714f0b7984b35c91cde0e4a7de7eff53142d716124888e5f4c66f18dd80131ec`。
- 证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。
- 检查 APK 的 ZIP 原始中央/本地条目名称无反斜杠；内置宋体与源资产逐字节一致，OFL 授权继续随包提供。
- 权限仍为 FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、WAKE_LOCK、POST_NOTIFICATIONS；无新增权限。数据格式未变。
- 仍为开发测试包；未执行完整 Gradle/lint/release/R8 验收。JDK 21.0.12.1 / Build Tools 35.0.0；沙箱 javac 关闭只读 android.jar 时有已有的 AccessDeniedException 诊断，退出码为 0，后续 class/DEX/签名均通过。

## 宿主测试

| 测试 | 通过断言数 |
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
| FeatureStorageTest | 34 |

新增批量分类断言覆盖：两本书只增加目标分类、不影响第三本，统一一次索引提交；原进度、书签备注与置顶保留，重新读盘一致；最终索引写入失败时整批分类与原索引字节恢复。

宿主调用真实生产存储逻辑，使用 layoutlib org.json 和 tests/host AtomicFile 适配器。断言含逐字符检查，不等同同数量产品场景；宿主耗时不能当手机性能指标。

## Android 原生检查

在本次工作目录建立独立 SnowRead_Regression AVD（Android 17 / API 37，emulator-5556），没有使用或清空原先运行的 emulator-5554，也没有连接真实手机。

最终包通过 58 项原生检查，新增重点：

- 控制后台分页延迟，对比松手前后位图完全相同，等待期间不归位；正式动画起始比例等于松手比例，完成后只前进一页。
- 短拖回弹第一帧不跳回；跨章向前翻有上一章最后一页预览，动画从拖动比例继续；关闭动画后清除残留位移。
- 使用系统真实触摸事件完成长按、移动、松手：两本书都加入指定目标，原分类保留，重新读盘一致；成功后仍保留多选。
- 分类栏边缘停留确实滚动；拖到空白处取消，元数据与选择保持。
- 原有字体、按书排版、书签、手势、搜索和缓存 WAV 播放检查继续通过。

最终原生日志：docs/regressions/0.10.1-feature-instrumentation.log。首次测试的输入注入在 Android 17 系统拖拽窗口被目标 UID 限制，调整为 UiAutomation 真实触摸输入并结束遗留按下状态后完成；中间失败日志保存在 work/v0101-android。随后发现分类栏初始化滚动动画与长按落点竞争，改为直接定位后通过最终检查。没有将中间失败计为通过。

Test-AndroidStartup.ps1 对最终 APK 连续 3 次冷启动通过：进程存活、MainActivity resumed、无新增 reader crash。记录见 docs/regressions/0.10.1-startup.log。

## 尚未验收

- 用户红米 K80 Ultra / Android 16 的实际跟手感受和帧率；Android 6/7 等旧设备兼容。模拟器通过不能代替这些设备。
- 小屏/横屏/200% 字号/TalkBack 的完整拖拽可用性；底部分类按钮保留作为替代操作。
- 大章节分页耗时、低内存、大书架滚动性能的量化；本轮不宣称达到固定 60/120Hz 帧率。
- 真实中文 TTS 双客户端、蓝牙/来电/锁屏长时间播放、真实设备磁盘满/断电/强杀；沿用前版未测试项，不因本轮修改而宣称通过。
