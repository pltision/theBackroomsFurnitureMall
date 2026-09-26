# gradlew-internal.ps1 —— 在"工程内部"跑 Gradle，不写 ~/.gradle
#
# 为什么需要它：
#   gradlew 默认用 ~/.gradle 当 GRADLE_USER_HOME（wrapper 发行包、依赖缓存、文件锁都在那里）。
#   这会让构建必须访问工作区之外的目录；在受限环境里就得反复申请额外权限。
#   这个脚本把 GRADLE_USER_HOME 指到工程内的 .gradle-home/，于是整个构建都发生在工作区内。
#
# 用法（参数原样传给 Gradle）：
#   pwsh -File .\gradlew-internal.ps1 runData
#   pwsh -File .\gradlew-internal.ps1 build
#   pwsh -File .\gradlew-internal.ps1 :genBlockList:generateDefaultBlockIds
#
# 第一次使用前，需要把已有缓存搬进来（只做一次，约 3.4 GB）：
#   New-Item -ItemType Directory -Force .gradle-home | Out-Null
#   Copy-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches" .gradle-home\
#   Copy-Item -Recurse -Force "$env:USERPROFILE\.gradle\wrapper" .gradle-home\
# 也可以不搬——脚本会自己下载，只是第一次会慢一些。

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$internalHome = Join-Path $projectRoot '.gradle-home'

New-Item -ItemType Directory -Force -Path $internalHome | Out-Null
$env:GRADLE_USER_HOME = $internalHome

# 找 Java 21。优先用常见的 JDK 安装位置；找不到就让 Gradle 自己找 PATH 里的 java。
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $candidates = @(
        (Join-Path $env:USERPROFILE '.jdks\corretto-21.0.5'),
        (Join-Path $env:USERPROFILE '.jdks\corretto-21'),
        'C:\Program Files\Eclipse Adoptium\jdk-21',
        'C:\Program Files\Amazon Corretto\jdk21'
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe'))) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}
if ($env:JAVA_HOME) {
    Write-Host "[internal-gradle] JAVA_HOME     = $env:JAVA_HOME"
} else {
    Write-Host "[internal-gradle] JAVA_HOME 未设置，将使用 PATH 里的 java（需要是 JDK 21）"
}
Write-Host "[internal-gradle] GRADLE_USER_HOME = $env:GRADLE_USER_HOME"

& (Join-Path $projectRoot 'gradlew.bat') @GradleArgs
exit $LASTEXITCODE
