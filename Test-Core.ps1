param([string]$JdkHome = $env:JAVA_HOME, [string]$TestOutput)
$ErrorActionPreference = 'Stop'
if (!$JdkHome -or !(Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) { throw 'Set JAVA_HOME or pass -JdkHome with a full JDK 17+ installation.' }
$projectRoot = $PSScriptRoot
if (!$TestOutput) { $TestOutput = Join-Path $projectRoot '.test-build' }
New-Item -ItemType Directory -Force -Path $TestOutput | Out-Null
$sourceRoot = Join-Path $projectRoot 'app\src\main\java\com\snowread\app'
$testSources = @(
    (Join-Path $sourceRoot 'BookNote.java'),
    (Join-Path $sourceRoot 'ReaderStatusText.java'),
    (Join-Path $projectRoot 'tests\ReaderStatusTextTest.java'),
    (Join-Path $sourceRoot 'PrivacyPolicy.java'),
    (Join-Path $projectRoot 'tests\PrivacyPolicyTest.java'),
    (Join-Path $sourceRoot 'NoteExport.java'),
    (Join-Path $sourceRoot 'LanTransferServer.java'),
    (Join-Path $projectRoot 'tests\BookNoteTest.java'),
    (Join-Path $projectRoot 'tests\LanTransferServerTest.java'),
    (Join-Path $sourceRoot 'ReadingTimeCounter.java'),
    (Join-Path $projectRoot 'tests\ReadingTimeCounterTest.java'),
    (Join-Path $sourceRoot 'CommonHan.java'),
    (Join-Path $sourceRoot 'EncodingDetector.java'),
    (Join-Path $sourceRoot 'ReadingAnchor.java'),
    (Join-Path $sourceRoot 'PositionMigration.java'),
    (Join-Path $sourceRoot 'Typeset.java'),
    (Join-Path $sourceRoot 'TitleCleaner.java'),
    (Join-Path $sourceRoot 'SearchGroups.java'),
    (Join-Path $sourceRoot 'TextDocument.java'),
    (Join-Path $sourceRoot 'PageIndex.java'),
    (Join-Path $sourceRoot 'ReaderChrome.java'),
    (Join-Path $sourceRoot 'CoverGeometry.java'),
    (Join-Path $sourceRoot 'ShelfOrganization.java'),
    (Join-Path $sourceRoot 'SearchResults.java'),
    (Join-Path $sourceRoot 'ChapterOrder.java'),
    (Join-Path $sourceRoot 'ChapterRule.java'),
    (Join-Path $sourceRoot 'TextChunker.java'),
    (Join-Path $sourceRoot 'SpeechQueue.java'),
    (Join-Path $sourceRoot 'SleepTimer.java'),
    (Join-Path $sourceRoot 'ReaderPolicy.java'),
    (Join-Path $sourceRoot 'DirectoryRows.java'),
    (Join-Path $projectRoot 'tests\DirectoryRowsTest.java'),
    (Join-Path $projectRoot 'tests\ReaderPolicyTest.java'),
    (Join-Path $projectRoot 'tests\TextDocumentTest.java'),
    (Join-Path $projectRoot 'tests\ReaderBehaviorTest.java'),
    (Join-Path $projectRoot 'tests\ShelfOrganizationTest.java'),
    (Join-Path $projectRoot 'tests\SearchResultsTest.java'),
    (Join-Path $projectRoot 'tests\ChapterOrderTest.java'),
    (Join-Path $projectRoot 'tests\ChapterRuleTest.java'),
    (Join-Path $projectRoot 'tests\ListenCoreTest.java')
)
& (Join-Path $JdkHome 'bin\javac.exe') -encoding UTF-8 -d $TestOutput $testSources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
foreach ($testClass in @('com.snowread.app.ReaderStatusTextTest','com.snowread.app.PrivacyPolicyTest','com.snowread.app.BookNoteTest','com.snowread.app.LanTransferServerTest','com.snowread.app.ChapterRuleTest','com.snowread.app.TextDocumentTest','com.snowread.app.ReaderBehaviorTest','com.snowread.app.ShelfOrganizationTest','com.snowread.app.SearchResultsTest','com.snowread.app.ChapterOrderTest','com.snowread.app.ListenCoreTest','com.snowread.app.ReaderPolicyTest','com.snowread.app.DirectoryRowsTest','com.snowread.app.ReadingTimeCounterTest')) {
    & (Join-Path $JdkHome 'bin\java.exe') --class-path $TestOutput $testClass
    if ($LASTEXITCODE -ne 0) { throw "Tests failed: $testClass" }
}
