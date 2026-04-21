param(
  [int]$BackendStartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendScript = Join-Path $rootDir 'backend\scripts\start-backend.ps1'
$frontendRoot = Join-Path $rootDir 'frontend'
$runtimeDir = Join-Path $rootDir '.runtime'
$stateFile = Join-Path $runtimeDir 'web-stack-processes.json'
$logDir = Join-Path $runtimeDir 'logs'
$stdoutLog = Join-Path $logDir 'frontend.out.log'
$stderrLog = Join-Path $logDir 'frontend.err.log'
$proxyStdoutLog = Join-Path $logDir 'proxy-https.out.log'
$proxyStderrLog = Join-Path $logDir 'proxy-https.err.log'
$lanAccessFile = Join-Path $frontendRoot 'public\lan-access.json'

function Ensure-Directory([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

function Test-PortListening([int]$Port) {
  return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Get-LanIpAddress() {
  $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
      $_.IPAddress -notlike '127.*' -and
      $_.IPAddress -notlike '169.254.*' -and
      $_.PrefixOrigin -ne 'WellKnown'
    } |
    Sort-Object InterfaceMetric, SkipAsSource

  return ($addresses | Select-Object -First 1 -ExpandProperty IPAddress)
}

Ensure-Directory $runtimeDir
Ensure-Directory $logDir

$lanIp = Get-LanIpAddress
$lanData = [pscustomobject]@{
  hostname = $env:COMPUTERNAME
  lanIp = $lanIp
  httpUrl = if ($lanIp) { "http://$($lanIp):3000" } else { $null }
  httpsUrl = if ($lanIp) { "https://$($lanIp):3443" } else { $null }
}
$lanData | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $lanAccessFile

& $backendScript -StartupTimeoutSeconds $BackendStartupTimeoutSeconds

if (Test-PortListening -Port 3000) {
  throw 'El puerto 3000 ya esta ocupado. Ejecuta stop-all.ps1 o restart-all.ps1 antes de iniciar de nuevo.'
}

if (Test-PortListening -Port 3443) {
  throw 'El puerto 3443 ya esta ocupado. Ejecuta stop-all.ps1 o restart-all.ps1 antes de iniciar de nuevo.'
}

Set-Location $frontendRoot
& npm run build

if ($LASTEXITCODE -ne 0) {
  throw 'Fallo el build del frontend.'
}

if (Test-Path -LiteralPath $stdoutLog) {
  Clear-Content -LiteralPath $stdoutLog
}

if (Test-Path -LiteralPath $stderrLog) {
  Clear-Content -LiteralPath $stderrLog
}

if (Test-Path -LiteralPath $proxyStdoutLog) {
  Clear-Content -LiteralPath $proxyStdoutLog
}

if (Test-Path -LiteralPath $proxyStderrLog) {
  Clear-Content -LiteralPath $proxyStderrLog
}

$command = "Set-Location '$frontendRoot'; & npm run serve:prod"
$process = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru

Start-Sleep -Seconds 3

$proxyCommand = "Set-Location '$frontendRoot'; & npm run proxy:https"
$proxyProcess = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $proxyCommand -WorkingDirectory $frontendRoot -WindowStyle Hidden -RedirectStandardOutput $proxyStdoutLog -RedirectStandardError $proxyStderrLog -PassThru

[pscustomobject]@{
  frontendPid = $process.Id
  frontendStdoutLog = $stdoutLog
  frontendStderrLog = $stderrLog
  proxyPid = $proxyProcess.Id
  proxyStdoutLog = $proxyStdoutLog
  proxyStderrLog = $proxyStderrLog
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile

Write-Host 'Stack web listo.'
Write-Host 'Frontend: http://localhost:3000'
Write-Host 'Frontend HTTPS: https://localhost:3443'
Write-Host 'Backend: http://localhost:8080'
if ($lanIp) {
  Write-Host "LAN HTTP: http://$($lanIp):3000"
  Write-Host "LAN HTTPS: https://$($lanIp):3443"
}
Write-Host "Logs frontend: $logDir"