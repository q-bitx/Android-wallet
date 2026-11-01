param([string[]]$Tasks = @('testDebugUnitTest','lintDebug','assembleDebug','assembleDebugAndroidTest'))
$ErrorActionPreference = 'Stop'
$taskTools = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../toolchain'))
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = (Get-ChildItem (Join-Path $taskTools 'jdk') -Directory | Select-Object -First 1).FullName
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $taskTools 'android-sdk' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $taskTools 'gradle-cache' }
$taskGradle = Join-Path $taskTools 'gradle-8.7/bin/gradle.bat'
Push-Location $PSScriptRoot
try { & $taskGradle --no-daemon --console=plain @Tasks; if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" } }
finally { Pop-Location }
