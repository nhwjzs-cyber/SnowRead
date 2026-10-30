# 0.11.6 安装打包修复验证

详情见 FIX-v0.11.6-install.md；旧版运行记录独立保留，不作为本版本真机结果。

本轮完成：PowerShell 5.1 / 7 ZIP 方法差异复现、完整 PS5.1 构建、新资源表门禁的正负样本、12 项归档回归、固定包 CRC / zipalign / v1-v3 签名和全部应用载荷字节一致性检查。

资源表必须 STORED 且原始数据偏移 % 4 == 0，普通签名与 zipalign 成功不足以替代这一检查。

安装修复包保持 0.11.6 / code 23 / min 23 / target 35。未修改任何 app/src/main 源码；交付 APK 直接保留原 GLM 包的所有非签名载荷。

未在手机或模拟器实际安装、启动、验证动效；不重复运行与纯打包无关的核心/存储套件。JDK 21 / Build Tools 35；完整构建时仍有已知沙箱关闭只读 android.jar 的 javac AccessDeniedException 诊断，退出码 0，随后 DEX、签名与资源门禁通过。

回归：Test-ApkArchive.ps1 -JdkHome C:\JAVA -TestOutput <workspace-test-folder>。完整构建依旧只需 SDK + JDK，无新增 Python 要求。
