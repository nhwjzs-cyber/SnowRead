# 融雪阅读 0.11.0 验证记录

2026-10-29；versionCode 17；com.snowread.app。范围见 UPDATE-v0.11.md，旧验证见 docs/VALIDATION-v0.10.2.md。

## 构建与产物

API 35 资源/Java/D8 编译通过，150 个生产 class，minSdk 23 / targetSdk 35。ZIP 4 字节对齐，v1/v2/v3 开发签名检查通过，证书延续，可覆盖安装。

APK 3,074,121 字节；SHA256 `424a297f8a3774e943cc48479f99e5fd9347d33f0660e6ea7608fa15044ca0d6`。

证书 SHA256 `b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。

字体及 OFL/ABOUT 资产与源码一致，原始 ZIP 中央和本地名称没有反斜杠。四项既有听书权限不变，无联网、录音或新增权限。书库 3 / 备份 2 不变，分页生产源码本轮未改。

仍为 debug 测试包；未运行完整 Gradle/lint/release/R8。JDK 21.0.12.1 / Build Tools 35.0.0。沙箱 javac 关闭只读 jar 时有已有 AccessDeniedException 诊断，退出码 0，后续 class/DEX/签名成功。

## 本轮运行

- Test-Core：TextDocument 1997、ReaderBehavior 183、ShelfOrganization 1548、SearchResults 168095、ChapterOrder 20005、ListenCore 55994、ReaderPolicy 5791、DirectoryRows 18，全部通过。断言含逐字符检查，不等于同数量产品场景。
- Android 17 / API 37，独立 SnowRead_Home_Regression AVD / emulator-5556：最终包 115 项原生检查通过。没有安装或清空现有 emulator-5554，没有连接真实手机。
- 沿用内置字体、按书排版/全局隔离、两种真实触摸翻页后的正文核对、松手帧连续性、书签/搜索、缓存 WAV、真实多选拖拽与边缘滚动/取消/读盘检查。
- 新检查：品牌与标语存在、按需搜索和焦点、原大导入按钮移除、书架空间占比、两行标题、继续阅读随滚动离开/关闭搜索恢复、合并置顶状态、一次性光扫结束、封面抬起复位、播放状态动画可见/暂停/减少动效、分组动画中断后高度和状态收敛。
- 最终 APK 连续三次冷启动：MainActivity resumed、进程存活、无新增 reader crash。

最终日志：docs/regressions/0.11-feature-instrumentation.log 与 0.11-startup.log。截图：docs/screenshots/v0.11，采用合成书籍。

中间迭代发现搜索退出后的继续阅读锚定不稳定，改为搜索期间专注结果、收起后重建正常书架；预先测量卡片高度，在 GridView 首次测量前保留占位，避免布局阶段改 padding 与内部缓存不同步；补充了恢复断言。重复测试中，旧夹具的置顶书改变排序，原脚本误拖了未选书；改为按选中 ID 找到拖拽卡片，未改变用户“长按未选书加入选择”的行为。中间失败日志保存在 work/v011-android，未当作通过。

存储/备份算法未改，未重跑整组宿主故障注入，历史结果见旧验证；本轮原生覆盖实际保存与重新读盘。状态音柱仅核验 UI 可见性/暂停/减少动效，不等同真实中文 TTS 连播验收。

## 仍需设备验收

红米 K80 Ultra / Android 16、Android 6/7 等旧机、小屏/横屏/200% 字号/完整 TalkBack 流程、60/120Hz 帧率与低内存。模拟器功能通过不能当作真机性能达标。真实中文 TTS、锁屏/蓝牙/来电和设备磁盘故障沿用之前未测范围。

一次性光扫/淡入的参数与触发可继续按后续动效评审调整；当前没有新增全屏实时模糊或正文粒子。
