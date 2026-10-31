param([string]$JdkHome=$env:JAVA_HOME,[Parameter(Mandatory=$true)][string]$LayoutlibJar,[string]$TestOutput)
$ErrorActionPreference='Stop'
if(!$TestOutput){$TestOutput=Join-Path $PSScriptRoot '.feature-tests'}
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
$src=Join-Path $PSScriptRoot 'app\src\main\java\com\snowread\app'
$sources=@('ReadingStats.java','ReadingTimeCounter.java','PortableFonts.java','TextCleanup.java','ReadingStatus.java','EpubExport.java','EpubDocument.java','ReadingStatsData.java','ChineseConverter.java','ChapterRule.java','BookNote.java','CommonHan.java','EncodingDetector.java','TitleCleaner.java','TextDocument.java','ReadingAnchor.java','PositionMigration.java','Typeset.java','BookExtras.java','LibraryStore.java','ShelfOrganization.java','BackupArchive.java','BatchImporter.java','RecycleBin.java','SearchResults.java','SearchGroups.java','TaskControl.java') | ForEach-Object {Join-Path $src $_}
$sources+=@((Join-Path $PSScriptRoot 'tests\host\android\util\AtomicFile.java'),(Join-Path $PSScriptRoot 'tests\BackupAndImportTest.java'),(Join-Path $PSScriptRoot 'tests\FeatureCoreTest.java'),(Join-Path $PSScriptRoot 'tests\FeatureStorageTest.java'))
& (Join-Path $JdkHome 'bin\javac.exe') -encoding UTF-8 -cp $LayoutlibJar -d $TestOutput $sources
if($LASTEXITCODE -ne 0){throw 'Feature test compilation failed'}
foreach($name in @('FeatureCoreTest','FeatureStorageTest')){
    & (Join-Path $JdkHome 'bin\java.exe') -cp "$TestOutput;$LayoutlibJar" "com.snowread.app.$name" $TestOutput
    if($LASTEXITCODE -ne 0){throw "Feature tests failed: $name"}
}
