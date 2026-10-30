# 融雪阅读 0.9.0 验证记录

## 本次范围

依据用户提供的《动效规范 v1》《听书模式方案 v1》实现统一 Motion、长任务细进度，以及系统 TTS 听书 v1。原文保留在 docs/specs；逐项对照和实现取舍见 IMPLEMENTATION-v0.9.md。

版本 0.9.0 / versionCode 11，com.snowread.app，沿用开发签名。书库格式仍为 3、备份格式仍为 2，兼容旧版数据和备份。

## 动效与进度

- 共享 Motion 原语与 120/180/220/240ms 令牌：面板/遮罩、工具栏、Toast、卡片按压/多选徽标、开退书、主题快照、搜索结果与确定进度。
- 系统动画关闭、触摸探索、减少动效均进入最终状态；无装饰旋转、逐行动画或回弹过冲。不动画布局尺寸。
- TaskProgress 合并后台进度约每 120ms 更新 UI；批量导入按文件，备份/合并按实际复制字节；总量未知的校验阶段使用细不确定线。没有全屏旋转圈。
- 翻页的 240ms 过渡、插值、边界线及分页算法保留；仅接入独立听书高亮和手动翻页/键盘/辅助功能通知。
- 窗口暂停和旋转结束当前过渡，销毁立即关闭所属面板，避免异步退出持有旧窗口。未在设备验证帧率和视觉效果。

## 听书接入

- 顶部耳机/更多入口，自定义面板、语速/语音/引擎/试听、定时、范围、跟随、标题朗读、可选页码/网址过滤和书架迷你条。
- TextChunker / SpeechQueue / SleepTimer 为纯 Java 模型；朗读清理后保留源字符映射，避免 rangeStart 简单相加错位。generation 拒绝旧回调，逐句 QUEUE_FLUSH，下一句预取。
- ListenService 为唯一播放 owner：主线程接管 TTS 回调；前台媒体服务、MediaSession、通知、音频焦点、noisy 接收器及带超时的部分唤醒锁。暂停/错误/停止释放焦点和唤醒锁，临时焦点丢失按策略恢复。
- LibraryRepository 共享书库实例、串行 I/O 和一份解码文档缓存；听书会话拥有进度，阅读页不覆盖活动听书断点；停止或切换书籍交还。每句实际开始写进度，不逐词写，暂停强制排队保存，进程重建保持暂停。
- 优先已安装的离线中文 Voice，检查 notInstalled 特征；无引擎/中文数据/初始化超时/合成失败提供设置、安装和重试。联网 Voice 要求明确允许。
- Android 13+ 请求通知权限，拒绝可选仅前台；媒体会话存在通知豁免，未采用“拒绝通知权限就必然无法锁屏控制”的错误断言。
- 本版不内置语音模型，不做多角色、音频导出、Auto/Wear、统计或云同步。

## 已执行的自动检查

本轮纯 Java 检查全部通过：

- 文本/解码/卷章/分页：1,997 个断言。
- 工具栏/封面几何：183 个断言。
- 分类/置顶/多分类：1,548 个断言。
- 搜索及 Unicode：168,095 个断言。
- 目录排序与锚点：20,005 个断言。
- 新增 ListenCoreTest 最终定向回归：55,994 个断言。覆盖中文/英文断句、引号、换行、长句硬切、代理对、空白压缩源映射、去网址映射、页码识别、暂停/续读、旧回调、seek、章末连播/停止、标题与页码过滤后的上一句、范围和睡眠定时恢复。大量断言来自逐字符边界检查，不等于同等数量的独立场景。

存储回归全部通过：LibraryPersistenceTest 37、BackupAndImportTest 51、BatchTransactionTest 40。500 本新增仍只发起一次索引提交，最终失败回滚和重复查找保留。备份进度改为字节回调后，往返、损坏拒绝和旧备份恢复检查仍通过。

宿主存储检查使用 layoutlib org.json 和测试 AtomicFile 适配器，不是安卓设备文件系统测试。没有用宿主模型结果代替真实 TTS、音频焦点或前台服务测试。

## 安装包检查

- aapt2 资源编译链接、Java 编译（97 个类）、D8、zipalign、apksigner v1/v2/v3 通过。
- APK 包信息：融雪阅读、com.snowread.app、0.9.0 / code 11、minSdk 23 / targetSdk 35。
- Manifest 实际包含四项权限：FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK、WAKE_LOCK、POST_NOTIFICATIONS。无 INTERNET、录音、电话状态、全盘存储或电池豁免权限。
- ListenService exported=false、stopWithTask=false、foregroundServiceType=mediaPlayback；TTS_SERVICE 可见性查询和 singleTop 入口存在。
- 开发证书与上一版一致，可覆盖更新。

受限 Windows 环境中 javac 关闭 android.jar 仍附带 AccessDeniedException 诊断，但返回 0，97 个类生成，后续 D8/资源/APK 检查通过。未执行完整 Gradle 同步。

## 未执行的设备验收（必须区分）

尚未在真机或模拟器安装运行，没有出声/锁屏/通知/蓝牙/来电或动效截图验收。不能声称已经达到锁屏连续 10/30 分钟、出声 <2 秒、句间 <200ms、60/120Hz 流畅或所有 OEM 兼容。

建议手机顺序检查：

1. 覆盖安装，原分类/封面/书签/进度保留；仅选一本 TXT 仍直接打开，多本仍一次提交。
2. 从阅读中间点击耳机，核对从该位置开始；首次通知允许与拒绝/仅前台分别测试。
3. 当前句高亮与声音对应；多空格、emoji、长句跨页不跳回句首；搜索高亮仍独立。
4. 暂停/恢复最多重复本句，句/章切换、拖动、变速和更换语音后旧回调不跳回。
5. 手动触摸、键盘和 TalkBack 翻页临时退出跟随，回到朗读位置恢复。
6. 原纸/暖纸/夜读、减少动效、系统时长为 0、触摸探索下的界面和播放。
7. 锁屏 30 分钟，通知/媒体面板及蓝牙线控，拔出耳机、来电和其他音频临时占用。
8. 15/30/60 分钟、章末/句末定时，断点进程重建保持暂停，睡眠偏好恢复。
9. 无 TTS 引擎、缺中文包、未安装 Voice、仅联网 Voice、初始化/合成失败及超时的可操作提示。
10. 退到书架、返回阅读、切换书籍、旋转、快速连点、移除/改编码和恢复备份时的所有权与资源释放。
11. 500 文件及大文件导入、备份/恢复各阶段进度持续更新，磁盘失败不会报成成功。
12. 不同 Android 8/10/12/13/14/15 与 Google/讯飞/华为/小米/三星引擎矩阵。

系统 TTS 语音质量、离线能力和词级回调取决于引擎与安装数据。部分厂商后台限制仍需实测；唤醒锁不是系统绝不终止进程的保证。听书配置保存在本地 SharedPreferences，不随整库 ZIP 备份；已提交的共享 Book.offset 会随书库导出。

## 官方依据与文档差异

参见 IMPLEMENTATION-v0.9.md 中的官方链接：[音频焦点](https://developer.android.com/media/optimize/audio-focus)、[前台服务类型](https://developer.android.com/about/versions/14/changes/fgs-types-required)、[媒体通知豁免](https://developer.android.com/develop/ui/views/notifications/notification-permission)、[范围回调](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)。代码落地不等于设备验收通过，未来 P2 内容没有混入 v1。

## 交付

融雪阅读-v0.9-debug.apk；融雪阅读.apk 为相同内容。

融雪阅读-v0.9-source.zip 含源码、测试、原始两份规范、README 和实现对照。

APK SHA-256：31acda56a2e31b03bff886dcb7aab9a61443361571a265e6b6187a781c4d1857

开发证书 SHA-256：b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f