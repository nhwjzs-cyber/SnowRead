param([string]$JdkHome=$env:JAVA_HOME,[string]$TestOutput)
$ErrorActionPreference='Stop'
if(!$JdkHome){throw 'Pass -JdkHome or set JAVA_HOME.'}
if(!$TestOutput){$TestOutput=Join-Path $PSScriptRoot '.archive-tests'}
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
& (Join-Path $JdkHome 'bin\javac.exe') -encoding UTF-8 --release 8 -d $TestOutput (Join-Path $PSScriptRoot 'tools\ApkArchive.java') (Join-Path $PSScriptRoot 'tests\ApkArchiveTest.java')
if($LASTEXITCODE -ne 0){throw 'APK archive test compilation failed.'}
& (Join-Path $JdkHome 'bin\java.exe') -cp $TestOutput ApkArchiveTest $TestOutput
if($LASTEXITCODE -ne 0){throw 'APK archive regression failed.'}
