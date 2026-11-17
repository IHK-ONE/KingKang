# Builds the current version and, when explicitly asked, pushes the tag and publishes the
# GitHub release. Without -Push/-Publish it only builds and prints the exact commands.
param(
    [switch]$Push,
    [switch]$Publish
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$gradle = Get-Content app/build.gradle.kts -Raw
$version = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$code = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $version -or -not $code) { throw "无法从 app/build.gradle.kts 读取版本号" }

$dirty = git status --porcelain
if ($dirty) { throw "工作区不干净，请先提交或还原改动：`n$dirty" }

Write-Host "版本 $version (versionCode $code)"
& .\gradlew.bat testDebugUnitTest assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "构建或测试失败" }

$apk = "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path $apk)) { throw "找不到 $apk" }

$changelog = Get-Content CHANGELOG.md -Raw
$pattern = "(?s)##\s+$([regex]::Escape($version)).*?(?=\r?\n##\s|\z)"
$section = [regex]::Match($changelog, $pattern).Value.Trim()
if (-not $section) { $section = "## KingKang $version" }
$notes = Join-Path $root "build/release-notes-$version.md"
New-Item -ItemType Directory -Force (Split-Path $notes) | Out-Null
$section | Set-Content -Path $notes -Encoding utf8

$tag = "v$version"
Write-Host "APK：$apk"
Write-Host "说明：$notes"

if ($Push) {
    git tag -f $tag
    git push origin main
    git push origin $tag
    Write-Host "已推送 $tag"
} else {
    Write-Host "未推送。确认后运行：git tag $tag; git push origin main; git push origin $tag"
}

if ($Publish) {
    gh release create $tag $apk --title "KingKang $version" --notes-file $notes --target main
    Write-Host "已创建 GitHub Release $tag"
} else {
    Write-Host "未发布。确认后运行：gh release create $tag $apk --title `"KingKang $version`" --notes-file `"$notes`" --target main"
}
