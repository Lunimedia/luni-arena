param([string]$SdkPath, [switch]$CleanNative)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $ProjectRoot

# An installed Android Studio is not necessarily the Android SDK. Prefer the
# project's setting, then environment variables and the normal Windows paths.
$LocalProperties = Join-Path $ProjectRoot 'local.properties'
$SdkCandidates = @()
if ($SdkPath) { $SdkCandidates += $SdkPath }
if (Test-Path $LocalProperties) {
  $SdkLine = Get-Content $LocalProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
  if ($SdkLine) { $SdkCandidates += ($SdkLine -replace '^\s*sdk\.dir\s*=\s*', '') }
}
if ($env:ANDROID_HOME) { $SdkCandidates += $env:ANDROID_HOME }
if ($env:ANDROID_SDK_ROOT) { $SdkCandidates += $env:ANDROID_SDK_ROOT }
if ($env:LOCALAPPDATA) { $SdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
$SdkCandidates += @('D:\Android\Sdk', 'D:\AndroidSDK', 'D:\Sdk')

$ResolvedSdk = $null
$IncompleteSdk = $null
foreach ($Candidate in $SdkCandidates) {
  if (-not $Candidate -or -not (Test-Path -LiteralPath $Candidate -PathType Container)) { continue }
  $Candidate = (Resolve-Path -LiteralPath $Candidate).Path
  if (Test-Path (Join-Path $Candidate 'platforms\android-36\android.jar')) {
    $ResolvedSdk = $Candidate
    break
  }
  if (-not $IncompleteSdk -and (Test-Path (Join-Path $Candidate 'platforms'))) { $IncompleteSdk = $Candidate }
}
if (-not $ResolvedSdk) {
  if ($IncompleteSdk) {
    throw "SDK encontrado em '$IncompleteSdk', mas falta Android SDK Platform 36. Instale em Android Studio > Settings > Languages & Frameworks > Android SDK > SDK Platforms."
  }
  throw 'SDK Android nao localizado. No Android Studio, copie Android SDK Location em Settings > Languages & Frameworks > Android SDK e execute: powershell -ExecutionPolicy Bypass -File .\tools\build-windows.ps1 -SdkPath "CAMINHO_DO_SDK"'
}

$env:ANDROID_HOME = $ResolvedSdk
$env:ANDROID_SDK_ROOT = $ResolvedSdk
$SdkForProperties = $ResolvedSdk -replace '\\', '/'
$PreviousLines = @()
if (Test-Path $LocalProperties) { $PreviousLines = @(Get-Content $LocalProperties | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' }) }
$PreviousLines += "sdk.dir=$SdkForProperties"
Set-Content -Path $LocalProperties -Value $PreviousLines -Encoding Ascii
Write-Host "Android SDK: $ResolvedSdk"

# ZIP timestamps do not carry a time zone. When UTC timestamps are extracted
# as Windows local time, CMake inputs can land in the future and keep Ninja's
# build.ninja manifest dirty forever.
$Clock = Get-Date
$NativeSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'app\src\main\cpp') -File -Recurse)
$FutureSources = @($NativeSources | Where-Object { $_.LastWriteTime -gt $Clock.AddMinutes(1) })
if ($FutureSources.Count -gt 0) {
  $SafeTime = $Clock.AddMinutes(-2)
  foreach ($Source in $FutureSources) { $Source.LastWriteTime = $SafeTime }
  Write-Host "Corrigidas datas futuras em $($FutureSources.Count) arquivo(s) nativos."
}
$NativeCache = Join-Path $ProjectRoot 'app\.cxx'
if ($CleanNative -or $FutureSources.Count -gt 0) {
  if (Test-Path -LiteralPath $NativeCache) {
    Remove-Item -LiteralPath $NativeCache -Recurse -Force
    Write-Host 'Cache CMake/Ninja recriado.'
  }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instale JDK 17 e configure JAVA_HOME.' }
if (Get-Command py -ErrorAction SilentlyContinue) { py -3 tools/fetch_core.py }
elseif (Get-Command python -ErrorAction SilentlyContinue) { python tools/fetch_core.py }
else { throw 'Instale Python 3.' }

$GradleVersion = '8.11.1'
$Gradle = Join-Path $PSScriptRoot "gradle-$GradleVersion\bin\gradle.bat"
if (-not (Test-Path $Gradle)) {
  $Zip = Join-Path $PSScriptRoot "gradle-$GradleVersion-bin.zip"
  Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $Zip
  Expand-Archive $Zip -DestinationPath $PSScriptRoot
  Remove-Item $Zip
}
& $Gradle --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar. Veja o primeiro erro acima.' }
Write-Host "APK: $PWD\app\build\outputs\apk\debug\app-debug.apk"
