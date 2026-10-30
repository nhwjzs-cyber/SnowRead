# 融雪阅读 0.11.1 验证记录

2026-10-29；versionCode 18；com.snowread.app。取舍见 UPDATE-v0.11.1.md，上一版验证见 docs/VALIDATION-v0.11.md。

## 产物

- API 35 资源 / Java / D8 构建通过，151 个生产 class，minSdk 23 / targetSdk 35。
- ZIP 4 字节对齐、CRC 与 v1/v2/v3 开发签名检查通过。原始 ZIP 中央目录及本地头文件名均无反斜杠，字体 / OFL / ABOUT.json 与源码逐字节一致。
- APK 3,074,121 字节；SHA256 `aa9d407e75777dd04284c04d0b1d0d2db88033fc3c3f334689c603cb19bc395c`。
- 证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`，与旧版相同。
- 仅修改 8 个 UI / 动效生产文件；LibraryStore、BackupArchive、正文解析与手势方向代码未改。PagedReaderView 的改动仅在绘制字段与 drawBoundary()。四项既有听书权限、字体、数据格式和备份格式不变。
- 使用 JDK 21.0.12.1 / Build Tools 35.0.0；未执行完整 Gradle / lint / release / R8。沙箱 javac 关闭只读 jar 时出现已知 AccessDeniedException 诊断，退出码 0；class、DEX、签名及安卓运行通过。

## 自动检查

- Test-Core：TextDocument 1997、ReaderBehavior 183、ShelfOrganization 1548、SearchResults 168095、ChapterOrder 20005、ListenCore 55994、ReaderPolicy 5791、DirectoryRows 18，全部通过。逐字符断言不等于同数量的产品场景。
- Test-Storage：LibraryPersistence 37、BackupAndImport 52、BatchTransaction 41、SafetyData 43，通过；包含 500 本导入一次提交及失败回滚。
- Test-Features：FeatureCore 30、FeatureStorage 34，通过。首次长输出路径触发 Windows 回收副本 renameTo 失败，改用同工作区短测试目录 work/f111 后通过；未改变存储生产代码。
- 独立 Android 17 / API 37 AVD：最终 APK **130 项原生集成检查通过**，包括保留的字体、排版隔离、书签、搜索、缓存 WAV、实际触摸翻页 / 正文比对 / 松手连续性、多选拖拽 / 取消 / 边缘滚动 / 读盘。
- 新检查：胶囊中间帧与中断终态、竖排实际边界；听书显隐快速反向与隐藏释放空间；封面渐入终态和换书清空；书签取消不跳转及精确偏移；减少动效立即终态；完整卡片复用、封面 ID 和无障碍说明更新。
- 最终 APK 连续 3 次冷启动均有进程、MainActivity resumed 且无应用崩溃。

原生日志：docs/regressions/0.11.1-feature-instrumentation.log；冷启动：0.11.1-startup.log。首次模拟器启动有 System UI 无响应弹窗抢焦点，两次运行在音量键检查处失败；关闭系统弹窗、恢复焦点后完整重跑通过。未绕过应用的窗口焦点判断。

## 视觉检查与边界

- 已查看书架、排版胶囊和夜读截图；另在专用模拟器设系统字体 1.3 倍、横屏，实际打开阅读与排版面板，展开字体并滚动到三个竖排选项，核对显示和可达性。截图见 docs/screenshots/v0.11.1/。横屏高度有限，设置内容依靠面板内部滚动。
- 新过渡使用 Motion.enabled() 的减少动效 / 系统动画关闭 / 触摸探索统一判断；本轮原生用例检查减少动效路径，未逐项开关 TalkBack 或系统动画缩放做设备矩阵。
- 未做前后对照 gfxinfo 帧率基准，不把消除逐帧渐变分配等同于“无掉帧”或固定帧率承诺。
- 未连接用户 Android 16 / Redmi K80 Ultra；真实离线中文语音连续播放、ROM 行为、耗电和低端机表现需真机验收。听书新增显隐检查覆盖共享原语，不等于完整中文 TTS 会话测试。
- 使用专用 SnowRead_Visual_Regression / emulator-5556，未向原有 emulator-5554 安装、清空或结束。测试后关闭专用 AVD 并清理本次临时系统镜像与路径别名。
