param(
    [string]$Adb = 'D:\SDK\platform-tools\adb.exe',
    [string]$FixtureDirectory = 'WorkLogAITest',
    [string]$ResultPath = 'build\stage9-saf-fixtures\saf-results.csv',
    [int]$FirstFixture = 1,
    [int]$LastFixture = 21,
    [string]$Fixtures = ''
)

$ErrorActionPreference = 'Stop'

function Get-UiDocument {
    & $Adb shell uiautomator dump /sdcard/worklog-stage9-window.xml | Out-Null
    [xml](& $Adb shell cat /sdcard/worklog-stage9-window.xml)
}

function Get-Center {
    param([System.Xml.XmlNode]$Node)

    if (-not $Node) { return $null }
    $matches = [regex]::Match($Node.bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $matches.Success) { return $null }
    $left = [int]$matches.Groups[1].Value
    $top = [int]$matches.Groups[2].Value
    $right = [int]$matches.Groups[3].Value
    $bottom = [int]$matches.Groups[4].Value
    @([int](($left + $right) / 2), [int](($top + $bottom) / 2))
}

function Tap-Node {
    param([System.Xml.XmlNode]$Node)

    $center = Get-Center $Node
    if (-not $center) { throw 'Unable to resolve a UI node location.' }
    & $Adb shell input tap ([int]$center[0]) ([int]$center[1]) | Out-Null
}

function Wait-ForNode {
    param(
        [string]$XPath,
        [int]$TimeoutSeconds = 15
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $document = Get-UiDocument
        $node = $document.SelectSingleNode($XPath)
        if ($node) { return @($document, $node) }
        Start-Sleep -Milliseconds 400
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for UI node: $XPath"
}

function Open-DataManagement {
    $document = Get-UiDocument
    if ($document.SelectSingleNode("//node[@content-desc='从备份恢复']")) { return }

    if ($document.SelectSingleNode("//node[@text='数据管理']")) {
        & $Adb shell input swipe 630 2200 630 650 450 | Out-Null
        Start-Sleep -Milliseconds 500
        $document = Get-UiDocument
        if ($document.SelectSingleNode("//node[@content-desc='从备份恢复']")) { return }
    }

    $settings = $document.SelectSingleNode("//node[@content-desc='设置']")
    if ($settings) {
        Tap-Node $settings
        Start-Sleep -Seconds 1
        $document = Get-UiDocument
    }

    $dataManagement = $document.SelectSingleNode("//node[@content-desc='数据管理']")
    if (-not $dataManagement) {
        & $Adb shell input swipe 630 2200 630 650 450 | Out-Null
        Start-Sleep -Milliseconds 500
        $document = Get-UiDocument
        $dataManagement = $document.SelectSingleNode("//node[@content-desc='数据管理']")
    }
    if (-not $dataManagement) { throw 'The settings data-management action is not visible.' }
    Tap-Node $dataManagement
    Wait-ForNode "//node[@content-desc='从备份恢复']" 10 | Out-Null
}

function Open-RestorePicker {
    Open-DataManagement
    $document = Get-UiDocument
    $restore = $document.SelectSingleNode("//node[@content-desc='从备份恢复']")
    if (-not $restore) {
        & $Adb shell input swipe 630 2200 630 650 450 | Out-Null
        Start-Sleep -Milliseconds 500
        $document = Get-UiDocument
        $restore = $document.SelectSingleNode("//node[@content-desc='从备份恢复']")
    }
    if (-not $restore) { throw 'The data-management restore action is not visible.' }
    Tap-Node $restore

    $picker = (Wait-ForNode "//node[@package='com.android.documentsui']" 15)[0]
    if (-not $picker.SelectSingleNode("//node[@text='$FixtureDirectory']")) {
        & $Adb shell input keyevent 4 | Out-Null
        Start-Sleep -Milliseconds 500
        $picker = Get-UiDocument
    }
    $folder = $picker.SelectSingleNode("//node[@text='$FixtureDirectory']")
    if ($folder) {
        Tap-Node $folder
        Wait-ForNode "//node[@content-desc='搜索']" 10 | Out-Null
    }
}

function Select-Fixture {
    param([string]$FileName)

    Open-RestorePicker
    $document = Get-UiDocument
    $search = $document.SelectSingleNode("//node[@content-desc='搜索']")
    if (-not $search) { throw 'The system document picker search action is unavailable.' }
    Tap-Node $search
    $edit = (Wait-ForNode "//node[@resource-id='com.android.documentsui:id/search_src_text']" 10)[1]
    Tap-Node $edit
    & $Adb shell input text $FileName | Out-Null
    $file =
        (Wait-ForNode "//node[@resource-id='android:id/title' and @text='$FileName']" 20)[1]
    Tap-Node $file
}

function Get-Result {
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        $document = Get-UiDocument
        if ($document.SelectSingleNode("//node[@package='com.worklogai.app']")) {
            $confirm = $document.SelectSingleNode("//node[@text='确认恢复备份？']")
            if ($confirm) {
                $cancel = $document.SelectSingleNode("//node[@text='取消']")
                if ($cancel) { Tap-Node $cancel }
                return 'UNSAFE_ACCEPTED'
            }
            $busy = $document.SelectSingleNode("//node[contains(@text,'正在检查备份')]")
            if (-not $busy) {
                $error = $document.SelectSingleNode("//node[@text='备份文件格式不正确']")
                if ($error) { return 'CONTROLLED_REJECTION' }
            }
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    'TIMEOUT'
}

$fixtureNumbers =
    if ($Fixtures) {
        $Fixtures.Split(',') | ForEach-Object { [int]$_.Trim() }
    } else {
        $FirstFixture..$LastFixture
    }
$files = $fixtureNumbers | ForEach-Object {
    $prefix = '{0:D2}-' -f $_
    Get-ChildItem 'build\stage9-saf-fixtures\damaged' -Filter "$prefix*.zip" | Select-Object -First 1
}

$results = foreach ($file in $files) {
    $started = [DateTime]::UtcNow
    try {
        Select-Fixture $file.Name
        $result = Get-Result
    } catch {
        $result = "HARNESS_ERROR:$($_.Exception.Message):$($_.InvocationInfo.ScriptLineNumber)"
        & $Adb shell am force-stop com.android.documentsui | Out-Null
        & $Adb shell am start -W -n com.worklogai.app/.app.MainActivity | Out-Null
    }
    [pscustomobject]@{
        Fixture = $file.Name
        Result = $result
        ElapsedMilliseconds = [int]([DateTime]::UtcNow - $started).TotalMilliseconds
    }
}

$resultDirectory = Split-Path $ResultPath -Parent
if ($resultDirectory) { New-Item -ItemType Directory -Force $resultDirectory | Out-Null }
$results | Export-Csv $ResultPath -NoTypeInformation -Encoding UTF8
$results | Format-Table -AutoSize

if ($results.Result -contains 'UNSAFE_ACCEPTED' -or $results.Result -contains 'TIMEOUT' -or
    ($results.Result | Where-Object { $_ -like 'HARNESS_ERROR:*' })) {
    exit 1
}
