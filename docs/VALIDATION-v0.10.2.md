# 融雪阅读 0.10.2 验证记录

2026-10-29；versionCode 16；com.snowread.app。变更与默认手势见 UPDATE-v0.10.2.md，旧验证记录保留在 docs/VALIDATION-v0.10.1.md。

## 构建与产物

- API 35 资源、Java、D8 编译完成，145 个生产 class；minSdk 23 / targetSdk 35。
- APK ZIP 4 字节对齐、v1/v2/v3 签名通过，包名和证书延续，可覆盖安装。
- APK 3,061,764 字节；SHA256 `f99f28711f4950094f33dd28ee3e346944d939d5e567c1f8e84293de3fcb1794`。
- 证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。
- ZIP 原始中央目录和本地文件头名称无反斜杠；字体/OFL/ABOUT 资产与源码字节一致。
- 四项既有听书权限不变，无 INTERNET 或新增权限，书库 3、备份 2 不变。新的分组状态和滑动方向属于全局偏好。
- 交付仍为开发测试包，未运行完整 Gradle/lint/release/R8。JDK 21.0.12.1 / Build Tools 35.0.0；沙箱 javac 关闭只读 jar 有已有 AccessDeniedException 诊断，退出码 0，class/DEX/签名均成功。

## 本轮运行的核心回归

TextDocumentTest 1,997；ReaderBehaviorTest 183；ShelfOrganizationTest 1,548；SearchResultsTest 168,095；ChapterOrderTest 20,005；ListenCoreTest 55,994；ReaderPolicyTest 5,791；DirectoryRowsTest 18。全部通过，断言数含逐字符检查，不等于同数量产品场景。

存储/备份生产代码本轮未改，未重新运行整组宿主故障注入；其上版结果见 docs/VALIDATION-v0.10.1.md。本轮的按书保存、全局隔离和分类重新读盘通过原生回归。

## 最终包 Android 原生回归

专用 SnowRead_UI_Regression AVD，Android 17 / API 37，emulator-5556。原有 emulator-5554 未被安装、清空或关闭；没有连接真实手机。

最终包 85 项检查通过，日志 docs/regressions/0.10.2-feature-instrumentation.log：

- 六类设置、默认字体展开、其他控件隐藏；切类收起前类、再展开保留未保存值，辅助功能展开/收起动作，记住上次类别。
- 修改后按书字体持久化且全局字体不变；夜读预览使用夜读字色。截图人工检查后，将大字号预览收紧为实际字号的单行，并对最终包重新执行检查。
- 两条工具栏底板绘制为半透明，整体 alpha 保持 1，避免文字与图标一起淡化。
- 完整系统触摸事件分别执行手指向右/向左作为下一页：恰好前进一页、画面中真实正文变化、物理动画方向跟手；反向动作回到 offset 0 且原文一致。
- 继续通过松手前后像素一致、等待分页不提前提交、剩余比例动画、回弹和跨章预览检查。
- 继续通过内置字体加载、书签/手势/搜索、缓存 WAV 播放、多选真实拖拽、边缘滚动、取消不改元数据及重新读盘。

Test-AndroidStartup.ps1 对最终 APK 连续三次冷启动通过：进程存活、MainActivity resumed、无新增 reader crash。日志 docs/regressions/0.10.2-startup.log。

截图位于 docs/screenshots/v0.10.2，使用合成书籍。UI 自动检查不代替全部视觉和设备验收。

## 未验收范围

- 红米 K80 Ultra / Android 16 真机的方向使用习惯、跟手感和帧率；本版明确默认右滑下一页，可在 Aa 的手势组改为左滑。
- 小屏/横屏/200% 字号和 TalkBack 的完整操作矩阵；本轮核验了展开/收起语义动作，未运行完整读屏器旅程。
- 60/120Hz 性能、超长章节/低内存，以及真实中文 TTS、蓝牙/来电/锁屏持续播放和真机磁盘故障；沿用上一版待验证项，不宣称本轮已覆盖。
