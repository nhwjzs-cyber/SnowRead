# 0.11.7 离线氛围预览验证

本版范围：AtmosphereView、MainActivity 的书架装饰层与预览入口、AppSheet 的装饰选择、Motion 的清理入口。版本 0.11.7 / code 24 / min 23 / target 35，包名 com.snowread.app 和开发证书保持。

## 已完成

- JDK 21 / Android Build Tools 35 完整资源编译、Java 编译、D8、对齐与签名构建。
- APK 资源表 STORED（method 0）、数据起点 4 字节对齐，签名前后门禁通过；ZIP CRC、重复名称/反斜杠、字体载荷、版本与证书独立复核。
- Android FeatureInstrumentation 以本次生产 classes.jar 编译并构建测试 APK，新增 4×5 模式组合绘制、持久化、关闭、减少动效、触摸与无障碍隔离、弹窗范围和正文无天气层检查。仅编译，未执行。
- 与 0.11.6 源码快照比较，现有生产 Java 只改 MainActivity、AppSheet、Motion，新增 AtmosphereView；分页、书库、导入、备份等生产源码保持。Manifest 权限未改。

Java 编译仍有沙箱关闭只读 android.jar/classes.jar 时的已知 AccessDeniedException 诊断；退出码为 0，随后 DEX、签名和资源表检查通过。一次新代码的数值类型编译错误已修正后重新完整构建。

## 未完成 / 不作通过声明

本轮没有在手机或模拟器实际安装、启动或观察新效果；没有真机截图、帧率、耗电或 Redmi K80 Ultra / Android 16 验收结果。测试 APK 编译不能替代上述运行验证。与本次视觉变化无关的核心/存储套件不重复运行。过去版本记录见 docs/VALIDATION-*，不视为本版本结果。

建议手机验收：覆盖安装后从“⋯ → 氛围预览”依次切换四种天气和时段；关闭开关；翻转屏幕；切到后台再回来；打开/下滑关闭装饰面板；打开一本书，检查正文无天气覆盖、点击翻页正常；开启 Aa 中的减少动效，确认无粒子循环。留意效果强度、文字对比度和流畅度。

资源门禁和原安装问题说明见 FIX-v0.11.6-install.md，设计范围见 UPDATE-v0.11.7.md。交付 APK、源码归档与 SHA-256 在 outputs。
