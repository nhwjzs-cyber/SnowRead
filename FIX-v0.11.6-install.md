# 0.11.6 安装不兼容：原因及修复

## 确定的问题

原包 SHA256 `27f2b54b5ecd58cc048b0215c3548ffc4abf02748020f15431caee61f86db082`。

| 项目 | 原 0.11.6 | 安装修复版 |
|---|---|---|
| 包名 / versionCode | com.snowread.app / 23 | 保持 |
| minSdk / targetSdk | 23 / 35 | 保持 |
| 资源表压缩方法 | DEFLATE（8） | STORED（0） |
| 资源表数据起点 | 11505，非 4 字节对齐 | 11832，4 字节对齐 |
| v1 / v2 / v3 签名 | 通过 | 通过，同一证书 |
| 动效与应用内容 | 原 GLM 版 | 26 个非签名条目逐字节一致 |

Android 官方规则：targetSdk 30 及以上的应用，resources.arsc 必须未压缩且按 4 字节对齐，否则不能安装。当前 targetSdk 为 35，因此原包触发明确的安装阻断条件。官方说明：https://developer.android.com/about/versions/11/behavior-changes-11#app-packaging

没有读取手机上的安装器日志，因此不声称取得了该手机返回的具体错误码；但原包确实违反上述安装要求，足以导致系统拒绝安装。最低安卓版本、CPU 原生库限制或证书冲突未发现异常。

## 为什么原来检查通过

原脚本用 .NET ZipArchive 的 CompressionLevel.NoCompression。复现结果：PowerShell 7.6 / CLR 10 生成 method 0，Windows PowerShell 5.1 / CLR 4 却生成 method 8，即无实际压缩收益的 DEFLATE 流，仍不满足 Android 的 STORED 要求。

zipalign 会把压缩条目标为“OK - compressed”，不会替它检查资源表的强制未压缩规则。签名验证只证明 APK 签名有效，也不证明资源表符合安装要求。因而两个检查都通过仍然可能无法安装。

## 修复方式

修复 APK 直接从用户提供的原包重打 ZIP：显式 STORED 资源表、统一路径、zipalign，再用原开发证书签名。未重新编译或替换 GLM 的 classes.dex；所有 26 个非签名文件，包括 DEX、manifest、资源、字体、图标均与原包字节一致。

构建脚本改为使用项目本来已要求的 JDK，通过 tools/ApkArchive.java 显式设置 ZIP 方法；在对齐后和签名后分别验证资源表压缩方法、原始数据偏移和文件名。任何一项不合规则构建失败，不再仅凭签名/zipalign 报告“APK ready”。不增加应用运行依赖，也不改变应用版本、权限或书库格式。

## 验证

- 原坏包被新检查准确拒绝；0.11.5 好包通过。
- STORED 但未对齐的中间产物也被准确拒绝。
- 安装修复版 CRC、资源表格式/偏移、zipalign、v1/v2/v3 签名通过。
- 修订脚本在 Windows PowerShell 5.1 下完成整套资源、Java、DEX、打包和签名构建；最终资源表再次通过检查。
- Test-ApkArchive.ps1：12 项检查通过，覆盖压缩、未对齐、路径、内容保留、重复条目、缺资源表与禁止覆盖输入。
- 未在真实手机安装或启动，没有把结构校验当成真机安装结果。

## 文件与安装

用 `融雪阅读-v0.11.6-安装修复-debug.apk` 覆盖安装。保留 com.snowread.app、versionCode 23 及相同开发签名，无需为这项修复清除书架。原失败 APK 保持原样，供对照。

修复版 SHA256：`585c0f892fa9d44c784a3c51fbc077e1b82a2e2e25019d957320157e515e6628`。
证书 SHA256：`b6db8f4795d1d1465ecf3bfe7501df337ab17037e722732d00ab86ab5e68d26f`。
