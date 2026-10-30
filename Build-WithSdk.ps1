param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$BuildRoot,
    [switch]$Release,
    [ValidateSet("com.snowread.app","com.snowread.app.sandbox")][string]$ApplicationId
)
$ErrorActionPreference = 'Stop'
if (!$SdkRoot -or !$JdkHome) { throw 'Provide -SdkRoot and -JdkHome, or set ANDROID_HOME and JAVA_HOME.' }
$projectRoot = $PSScriptRoot
# Resolve build-tools and platform from whatever the local SDK actually ships, newest first.
# Hard-coding a version breaks as soon as the SDK is updated.
$buildToolsRoot = Join-Path $SdkRoot 'build-tools'
$platformsRoot = Join-Path $SdkRoot 'platforms'
$toolRoot = $null
$androidJar = $null
$availableTools = @(Get-ChildItem -LiteralPath $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') } |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending)
foreach ($candidate in $availableTools) {
    if (Test-Path -LiteralPath (Join-Path $candidate.FullName 'lib\d8.jar')) { $toolRoot = $candidate.FullName; break }
}
if (!$toolRoot) { throw "No usable build-tools found under $buildToolsRoot" }
$availablePlatforms = @(Get-ChildItem -LiteralPath $platformsRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'android.jar') } |
    Sort-Object { try { [version]($_.Name -replace '^android-','') } catch { [version]'0.0' } } -Descending)
foreach ($candidate in $availablePlatforms) {
    $jar = Join-Path $candidate.FullName 'android.jar'
    if (Test-Path -LiteralPath $jar) { $androidJar = $jar; break }
}
if (!$androidJar) { throw "No platform with android.jar found under $platformsRoot" }
Write-Output "Using build-tools: $toolRoot"
Write-Output "Using platform jar: $androidJar"
$java = Join-Path $JdkHome 'bin\java.exe'
$javac = Join-Path $JdkHome 'bin\javac.exe'
$jar = Join-Path $JdkHome 'bin\jar.exe'
foreach ($required in @($androidJar,$java,$javac,$jar,(Join-Path $toolRoot 'aapt2.exe'),(Join-Path $toolRoot 'lib\d8.jar'),(Join-Path $toolRoot 'lib\apksigner.jar'),(Join-Path $toolRoot 'zipalign.exe'))) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required build tool missing: $required" }
}
function Check-BuildExit([string]$Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE" } }
if (!$BuildRoot) { $BuildRoot = Join-Path $projectRoot '.sdk-build' }
$classesRoot=Join-Path $buildRoot ('classes-'+[guid]::NewGuid().ToString('N'))
foreach ($folder in @($buildRoot,$classesRoot,(Join-Path $buildRoot 'dex'))) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }
$sourceRoot = Join-Path $projectRoot 'app\src\main'
$gradleConfig = [IO.File]::ReadAllText((Join-Path $projectRoot 'app\build.gradle'))
$appVersionCode = [regex]::Match($gradleConfig,'(?m)^\s*versionCode\s+(\d+)').Groups[1].Value
$appVersionName = [regex]::Match($gradleConfig,"(?m)^\s*versionName\s+'([^']+)'").Groups[1].Value
$appIdentifier = [regex]::Match($gradleConfig,"(?m)^\s*applicationId\s+'([^']+)'").Groups[1].Value
if ($ApplicationId) { $appIdentifier = $ApplicationId }
$targetApi = [regex]::Match($gradleConfig,'(?m)^\s*targetSdk\s+(\d+)').Groups[1].Value
if (!$targetApi) { throw 'targetSdk is missing from app/build.gradle.' }
if (!$appIdentifier) { throw 'Application identifier is missing from app/build.gradle.' }
if (!$appVersionCode -or !$appVersionName) { throw 'App version is missing from app/build.gradle.' }
$utf8 = New-Object Text.UTF8Encoding($false)
$debugFlag=if($Release){"false"}else{"true"}
$manifest = [IO.File]::ReadAllText((Join-Path $sourceRoot 'AndroidManifest.xml')).Replace('<manifest xmlns:',('<manifest package="'+$appIdentifier+'" xmlns:')).Replace('<application',('<application android:debuggable="'+$debugFlag+'"'))
if ($appIdentifier -eq 'com.snowread.app.sandbox') { $manifest=$manifest.Replace('android:label="融雪阅读"','android:label="融雪阅读·测试沙箱"') }
[IO.File]::WriteAllText((Join-Path $buildRoot 'AndroidManifest.xml'),$manifest,$utf8)
$projectUri = [Uri]($projectRoot + [IO.Path]::DirectorySeparatorChar)
$buildRelative = [Uri]::UnescapeDataString($projectUri.MakeRelativeUri([Uri]([IO.Path]::GetFullPath($buildRoot))).ToString()).Replace('/','\')
# Windows native SDK tools mishandle absolute Chinese paths; relative paths preserve them.
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $toolRoot 'aapt2.exe') compile --dir 'app\src\main\res' -o (Join-Path $buildRelative 'resources.zip')
    Check-BuildExit 'Resource compilation'
    & (Join-Path $toolRoot 'aapt2.exe') link -o (Join-Path $buildRelative 'unsigned.apk') -I $androidJar --manifest (Join-Path $buildRelative 'AndroidManifest.xml') --min-sdk-version 23 --target-sdk-version $targetApi --version-code $appVersionCode --version-name $appVersionName -A 'app\src\main\assets' (Join-Path $buildRelative 'resources.zip')
    Check-BuildExit 'Resource linking'
} finally { Pop-Location }
$sources = Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'java') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 --release 8 --class-path $androidJar -d $classesRoot $sources
Check-BuildExit 'Java compilation'
& $jar --create --file (Join-Path $buildRoot 'classes.jar') -C $classesRoot .
Check-BuildExit 'Class packaging'
& $java -cp (Join-Path $toolRoot 'lib\d8.jar') com.android.tools.r8.D8 --lib $androidJar --min-api 23 --output (Join-Path $buildRoot 'dex') (Join-Path $buildRoot 'classes.jar')
Check-BuildExit 'DEX compilation'
& $jar --update --file (Join-Path $buildRoot 'unsigned.apk') -C (Join-Path $buildRoot 'dex') classes.dex
Check-BuildExit 'DEX packaging'
# ZIP NoCompression is runtime-dependent: Windows PowerShell 5.1 still emits
# DEFLATE method 8. Android 11+ requires resources.arsc to use STORED method 0.
# Use the already-required JDK for explicit methods, then check the signed bytes.
$archiveTools=Join-Path $buildRoot 'archive-tools'
New-Item -ItemType Directory -Force -Path $archiveTools | Out-Null
& $javac -encoding UTF-8 --release 8 -d $archiveTools (Join-Path $projectRoot 'tools\ApkArchive.java')
Check-BuildExit 'APK archive helper compilation'
$normalizedApk=Join-Path $buildRoot 'normalized.apk'
& $java -cp $archiveTools ApkArchive normalize (Join-Path $buildRoot 'unsigned.apk') $normalizedApk
Check-BuildExit 'APK archive normalization'
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $toolRoot 'zipalign.exe') -p -f 4 (Join-Path $buildRelative 'normalized.apk') (Join-Path $buildRelative 'aligned.apk')
    Check-BuildExit 'ZIP alignment'
    & $java -cp $archiveTools ApkArchive verify (Join-Path $buildRoot 'aligned.apk')
    Check-BuildExit 'Android resource table requirements'
} finally { Pop-Location }
$apkName=if($Release){'RongxueReader-release.apk'}else{'RongxueReader-debug.apk'}
$output = Join-Path $projectRoot $apkName
& $java -jar (Join-Path $toolRoot 'lib\apksigner.jar') sign --ks (Join-Path $projectRoot 'dev\debug.keystore') --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $output (Join-Path $buildRoot 'aligned.apk')
Check-BuildExit 'APK signing'
& $java -jar (Join-Path $toolRoot 'lib\apksigner.jar') verify --verbose $output
Check-BuildExit 'Signature verification'
& $java -cp $archiveTools ApkArchive verify $output
Check-BuildExit 'Signed APK resource table requirements'
Write-Output "APK ready: $output"
