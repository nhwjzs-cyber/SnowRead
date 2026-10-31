param([string]$JdkHome=$env:JAVA_HOME,[Parameter(Mandatory=$true)][string]$LayoutlibJar,[string]$TestOutput)
$ErrorActionPreference='Stop'
if(!$TestOutput){$TestOutput=Join-Path $PSScriptRoot '.storage-tests'}
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
$src=Join-Path $PSScriptRoot 'app\src\main\java\com\snowread\app'
$sources=@((Join-Path $src 'BookNote.java'),(Join-Path $src 'LibraryStore.java'),(Join-Path $src 'TextDocument.java'),(Join-Path $src 'ShelfOrganization.java'),(Join-Path $src 'BatchImporter.java'),(Join-Path $src 'BackupArchive.java'),(Join-Path $PSScriptRoot 'tests\host\android\util\AtomicFile.java'),(Join-Path $PSScriptRoot 'tests\LibraryPersistenceTest.java'),(Join-Path $PSScriptRoot 'tests\BackupAndImportTest.java'),(Join-Path $PSScriptRoot 'tests\BatchTransactionTest.java'))
$sources+=@((Join-Path $src 'RecycleBin.java'),(Join-Path $src 'TaskControl.java'),(Join-Path $PSScriptRoot 'tests\SafetyDataTest.java'),(Join-Path $PSScriptRoot 'tests\ScriptModeStorageTest.java'),(Join-Path $PSScriptRoot 'tests\ChapterRuleStorageTest.java'))
$sources+=@('CommonHan.java','EncodingDetector.java','ReadingAnchor.java','PositionMigration.java','Typeset.java','TitleCleaner.java','BookExtras.java','ChapterRule.java','ChineseConverter.java') | ForEach-Object {Join-Path $src $_}
& (Join-Path $JdkHome 'bin\javac.exe') -encoding UTF-8 -cp $LayoutlibJar -d $TestOutput $sources
if($LASTEXITCODE -ne 0){throw 'Storage test compilation failed'}
foreach($testClass in @('com.snowread.app.LibraryPersistenceTest','com.snowread.app.BackupAndImportTest','com.snowread.app.BatchTransactionTest','com.snowread.app.SafetyDataTest','com.snowread.app.ScriptModeStorageTest','com.snowread.app.ChapterRuleStorageTest')) {
    & (Join-Path $JdkHome 'bin\java.exe') -cp "$TestOutput;$LayoutlibJar" $testClass $TestOutput
    if($LASTEXITCODE -ne 0){throw "Storage test failed: $testClass"}
}
