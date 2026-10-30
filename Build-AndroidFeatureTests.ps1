param([Parameter(Mandatory=$true)][string]$SdkRoot,[Parameter(Mandatory=$true)][string]$JdkHome,[Parameter(Mandatory=$true)][string]$AppClasses,[Parameter(Mandatory=$true)][string]$BuildRoot)
$ErrorActionPreference='Stop'
if(!(Test-Path -LiteralPath $AppClasses -PathType Leaf)){throw 'AppClasses must point to the production classes.jar, not its directory.'}
$toolsRoot=Join-Path $SdkRoot 'build-tools\35.0.0'
$androidJar=Join-Path $SdkRoot 'platforms\android-35\android.jar'
New-Item -ItemType Directory -Force -Path $BuildRoot,(Join-Path $BuildRoot 'classes'),(Join-Path $BuildRoot 'dex') | Out-Null
$manifest='<?xml version="1.0" encoding="utf-8"?><manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.snowread.app.tests"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><instrumentation android:name="com.snowread.app.FeatureInstrumentation" android:targetPackage="com.snowread.app.sandbox"/><application android:debuggable="true" android:label="SnowRead emulator checks"/></manifest>'
[IO.File]::WriteAllText((Join-Path $BuildRoot 'AndroidManifest.xml'),$manifest,[Text.UTF8Encoding]::new($false))
$relative=[Uri]::UnescapeDataString(([Uri]($PSScriptRoot+[IO.Path]::DirectorySeparatorChar)).MakeRelativeUri([Uri]([IO.Path]::GetFullPath($BuildRoot))).ToString()).Replace('/','\')
Push-Location -LiteralPath $PSScriptRoot
try{& (Join-Path $toolsRoot 'aapt2.exe') link -o (Join-Path $relative 'test-unsigned.apk') -I $androidJar --manifest (Join-Path $relative 'AndroidManifest.xml');if($LASTEXITCODE -ne 0){throw 'Test manifest failed'}}finally{Pop-Location}
$isolatedSource=Join-Path $BuildRoot 'FeatureInstrumentation.java'
$testSource=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'tests\android\FeatureInstrumentation.java')).Replace('"com.snowread.app","com.snowread.app.MainActivity"','"com.snowread.app.sandbox","com.snowread.app.MainActivity"')
[IO.File]::WriteAllText($isolatedSource,$testSource,[Text.UTF8Encoding]::new($false))
& (Join-Path $JdkHome 'bin\javac.exe') -encoding UTF-8 --release 8 -cp "$androidJar;$AppClasses" -d (Join-Path $BuildRoot 'classes') $isolatedSource
if($LASTEXITCODE -ne 0){throw 'Android feature test compilation failed'}
& (Join-Path $JdkHome 'bin\jar.exe') --create --file (Join-Path $BuildRoot 'tests.jar') -C (Join-Path $BuildRoot 'classes') .
if($LASTEXITCODE -ne 0){throw 'Test class packaging failed'}
& (Join-Path $JdkHome 'bin\java.exe') -cp (Join-Path $toolsRoot 'lib\d8.jar') com.android.tools.r8.D8 --lib $androidJar --classpath $AppClasses --min-api 23 --output (Join-Path $BuildRoot 'dex') (Join-Path $BuildRoot 'tests.jar')
if($LASTEXITCODE -ne 0){throw 'Test DEX compilation failed'}
& (Join-Path $JdkHome 'bin\jar.exe') --update --file (Join-Path $BuildRoot 'test-unsigned.apk') -C (Join-Path $BuildRoot 'dex') classes.dex
if($LASTEXITCODE -ne 0){throw 'Test DEX packaging failed'}
Push-Location -LiteralPath $PSScriptRoot
try{& (Join-Path $toolsRoot 'zipalign.exe') -p -f 4 (Join-Path $relative 'test-unsigned.apk') (Join-Path $relative 'test-aligned.apk');if($LASTEXITCODE -ne 0){throw 'Test alignment failed'}}finally{Pop-Location}
$apk=Join-Path $BuildRoot 'SnowRead-feature-tests.apk'
& (Join-Path $JdkHome 'bin\java.exe') -jar (Join-Path $toolsRoot 'lib\apksigner.jar') sign --ks (Join-Path $PSScriptRoot 'dev\debug.keystore') --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --v4-signing-enabled false --out $apk (Join-Path $BuildRoot 'test-aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Test signing failed'}
Write-Output "Dedicated-emulator instrumentation ready: $apk"
