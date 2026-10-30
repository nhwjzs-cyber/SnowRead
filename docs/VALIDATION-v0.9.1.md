# 融雪阅读 0.9.1 验证记录

2026-10-09；versionCode 12；com.snowread.app。本轮范围见 IMPLEMENTATION-v0.9.1.md、TODO-STATUS-v0.9.1.md；0.9 历史记录保留在 docs/VALIDATION-v0.9.md。

## 已运行

| 验证 | 结果 |
|---|---|
| API 35 资源、Java、D8 编译 | 通过，111 个生产 class 文件 |
| APK ZIP 对齐、签名 | 4 字节对齐，v1/v2/v3 通过，沿用开发签名 |
| applicationId / min / target / version | com.snowread.app / 23 / 35 / 0.9.1 (12) |
| TextDocumentTest | 1,997 项 |
| ReaderBehaviorTest | 183 项 |
| ShelfOrganizationTest | 1,548 项 |
| SearchResultsTest | 168,095 项 |
| ChapterOrderTest | 20,005 项 |
| ListenCoreTest | 55,994 项 |
| ReaderPolicyTest | 5,791 项：亮度范围、跨日/全天夜间时间、二十条历史与去重 |
| DirectoryRowsTest | 18 项：搜索/折叠/倒序/定位真实条目，10000 与 10001 标题边界 |
| LibraryPersistenceTest | 37 项：旧数组及 v2/v3 迁移、重新读取、索引失败回滚 |
| BackupAndImportTest | 52 项：完整备份、两种合并、旧备份、损坏拒绝、打开首本 |
| BatchTransactionTest | 41 项：500 本单次索引提交、重复零次提交、嵌套、停止、最终失败回滚与首本引用清除 |
| SafetyDataTest | 43 项：精确及空备份覆盖、取消/提交门闩、复制提前中断、持久回收/分类碰撞/重复恢复/写入失败、非法备注拒绝 |

断言数量包括逐字符/条目检查，不等于同数量独立产品场景或真机测试。500 本宿主导入仅作“一次索引提交”回归，耗时不代表手机性能。

存储测试调用真实生产 LibraryStore、BackupArchive、BatchImporter、RecycleBin 和 TaskControl；org.json 来自 Android Studio layoutlib。Windows 不能执行 Android 本机 AtomicFile，使用 tests/host 下的 JVM 适配器模拟同步写入与原子替换，统计索引提交次数。适配器不进入 APK；不把宿主回滚当作 Android 磁盘满/断电验证。

读取生产 SnowTheme 常量计算小字 #0F5F73 的对比度：书架背景 6.20:1、原纸 6.49:1、暖纸 5.80:1、面板 7.06:1，超过小字 4.5:1 目标。这不是所有颜色组合的完整无障碍审计。

## 包检查

APK SHA256：`2076d91f1466a4f4e78d3a07c440c6516789011e07a51a304e0da97504b953a9`

证书 SHA256：`b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。

权限仍只有 FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、WAKE_LOCK、POST_NOTIFICATIONS，来自上一版系统听书。无 INTERNET、RECORD_AUDIO、READ_PHONE_STATE 或广泛存储权限。外部 TXT 入口、返回回调、SAF 文件夹选择不增加权限。

本包仍为 debuggable=true 的覆盖开发测试包，未宣称完成 Q-01 release/R8/自有发布密钥。包名和签名延续。书库版本 3、备份容器版本 2；可选 markNotes 扩展的读取和恢复校验已测试。

构建环境 JDK 21.0.12.1 / Build Tools 35.0.0。javac 关闭只读 SDK android.jar 时输出环境已有的 AccessDeniedException 诊断，退出码为 0；class、D8、对齐和签名验证完成。完整 Gradle 同步/lint、release 构建和 instrumentation 未运行。

## 尚未运行，不能标为通过

- 真机或模拟器运行、安装/覆盖更新、Android 8/10/12/13/14/15 设备矩阵。
- StaticLayout 真实字体下的逐页连贯性、段距/缩进/对齐与搜索/TTS 高亮，200% 字体、TalkBack、小屏设置布局。
- 窗口亮度、全屏手势/三键、方向切换、夜间模式实际显示和原句位置恢复。
- 文件管理器/微信/QQ 的 URI MIME 和授权，SAF 文件夹 200 本和超 500 本、提供器阻塞取消、导出失败清理。
- 手机恢复中断、磁盘满、强杀和重启回退，真实 Android AtomicFile 与回收站故障路径。
- 锁屏听书 30 分钟、蓝牙/耳机/来电/通知拒绝/OEM 后台、媒体按钮与唤醒锁释放，60Hz/120Hz 帧率。
- 20MB 文本、低端机 OOM、冷启动/开书/翻页耗时；本轮仍限制单本 12MB、目录一万项。

手机步骤在 TODO-STATUS-v0.9.1.md。代码完成和宿主测试不等于设备验收完成。
