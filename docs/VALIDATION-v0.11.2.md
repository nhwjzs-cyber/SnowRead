# 融雪阅读 0.11.2 验证记录

2026-10-29；versionCode 19；com.snowread.app。范围见 UPDATE-v0.11.2.md，上一版验证单独保存在 docs/VALIDATION-v0.11.1.md。

## 已完成的检查

- API 35 资源、Java、D8 构建与打包通过，152 个生产 class，minSdk 23 / targetSdk 35。
- 4 字节 ZIP 对齐、CRC、v1/v2/v3 开发签名验证通过。原始 ZIP 中央目录及本地头的文件名没有反斜杠；字体、OFL、ABOUT.json 与源码字节一致。
- APK 3,078,217 字节，SHA256 `9b6d9efb804c51d772bcc28fbe64da63b7d66707ee046475adcba9a0a7a323d4`。比 0.11.1 增加 4096 字节。
- 开发证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`，可覆盖同签名旧版。无新权限、依赖或图片资源，书库 v3 / 备份 v2 不变。
- Test-Core：TextDocument 1997、ReaderBehavior 183、ShelfOrganization 1548、SearchResults 168095、ChapterOrder 20005、ListenCore 55994、ReaderPolicy 5791、DirectoryRows 18，通过。逐字符断言不等于同数量产品场景。
- Test-Storage：LibraryPersistence 37、BackupAndImport 52、BatchTransaction 41、SafetyData 43，通过。
- Test-Features：FeatureCore 30、FeatureStorage **57**，通过；新增批量回收的全部副本准备、一次索引提交、索引失败回滚、第二本复制失败时清理前一本副本、重复选择去重、空选择、过期对象拒绝、未选择书保留、原始文件保留、正文/封面/书签/备注/分类/置顶/排版恢复。

存储检查运行真实生产 Java 逻辑；AtomicFile 使用 Windows JVM 测试适配器，不等于安卓真实文件故障测试。日志见 docs/regressions/0.11.2-core.log、0.11.2-storage.log、0.11.2-features.log。

## 未完成的安卓验证

独立 API 37 测试模拟器在 App 安装前反复离线或退出；启动器明确返回过 `-1073741819 / 0xC0000005`。调整图形后端、内存设置与启动会话后仍未获得可用设备，最后也尝试了软件仿真。没有修改主机虚拟化设置，没有向原有 emulator-5554 安装、清空或结束，也未连接真实手机。

新增原生测试源码已编译和打包，包含多选删除禁用/确认/取消/读盘/回收站恢复、雪晶绘制范围、不参与触摸与无障碍、下滑关闭停止、减少动效不播放。但本轮**没有完成原生测试执行、冷启动回归或新增界面截图验收**。不要把 0.11.1 的 130 项原生检查和三次冷启动套用到本版。环境记录见 docs/regressions/0.11.2-emulator-environment.log。

用户 Android 16 / Redmi K80 Ultra 的实际表现、新增弹窗的横屏/大字号/夜读，以及系统动画关闭、TalkBack 与耗电表现仍需设备验收。本版为 debug 测试包，不作帧率承诺。

## 手动验收路径

1. 打开书籍 → Aa 排版，观察顶部边角雪晶只播放一次；关闭、下滑关闭面板时立即停止；开启“减少动效”后重新打开不播放。检查按钮、拖动和滚动仍可用。
2. 书架 → 多选，未选书时“删除”不可用。选择两本，点删除核对数量和书名，关闭确认面板后书籍和选择保留。
3. 再确认移入回收站，只移出所选书籍，完成后退出多选。点“查看回收站”逐本恢复，核对进度、书签、封面、分类及按书排版；手机原始 TXT 保留。
4. 选择多本长书名，确认书名区域可内部滚动、确认按钮可见；补查大字号、横屏和夜读。

## 构建环境

JDK 21.0.12.1 / Build Tools 35.0.0；未运行完整 Gradle、lint、release 或 R8。javac 关闭只读 android.jar / classes.jar 时仍有沙箱 AccessDeniedException 诊断，退出码 0，生产 class、DEX、签名和原生测试包构建均完成。
