param(
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$TestOutput,
    [int]$Launches=3
)
$ErrorActionPreference='Stop'
# A dedicated emulator only: never clear app data or silently select a connected phone.
if($Serial -notmatch '^emulator-\d+$'){throw 'Use an explicitly selected dedicated emulator.'}
if($Launches -lt 1 -or $Launches -gt 10){throw 'Launches must be 1–10.'}
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
function Invoke-TestAdb([string[]]$Arguments){
    $result=& $Adb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($result -join "`n")}
    return ($result -join "`n")
}
$release=Invoke-TestAdb @('shell','getprop','ro.build.version.release')
$api=Invoke-TestAdb @('shell','getprop','ro.build.version.sdk')
Write-Output "Android $release / API $api / $Serial"
Write-Output (Invoke-TestAdb @('install','-r',([IO.Path]::GetFullPath($Apk))))
for($attempt=1;$attempt -le $Launches;$attempt++){
    Invoke-TestAdb @('logcat','-b','crash','-c') | Out-Null
    $launch=Invoke-TestAdb @('shell','am','start','-S','-W','-n','com.snowread.app/.MainActivity')
    Start-Sleep -Seconds 2
    $crash=Invoke-TestAdb @('logcat','-b','crash','-d')
    $crash | Set-Content -LiteralPath (Join-Path $TestOutput "launch-$attempt-crash.log") -Encoding utf8
    $launch | Set-Content -LiteralPath (Join-Path $TestOutput "launch-$attempt-am.log") -Encoding utf8
    if($crash -match 'Process: com\.snowread\.app'){throw "Launch $attempt crashed; see saved log."}
    $process=Invoke-TestAdb @('shell','pidof','com.snowread.app')
    if(!$process.Trim()){throw "Launch $attempt has no live process."}
    $activities=Invoke-TestAdb @('shell','dumpsys','activity','activities')
    $activities | Set-Content -LiteralPath (Join-Path $TestOutput "launch-$attempt-activities.log") -Encoding utf8
    if($activities -notmatch '(?:mResumedActivity|topResumedActivity)[^\r\n]*com\.snowread\.app/\.MainActivity'){throw "Launch $attempt is not resumed."}
    Write-Output "PASS cold launch $attempt : process $($process.Trim()), MainActivity resumed, no crash."
}
