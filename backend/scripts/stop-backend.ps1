$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Split-Path -Parent $scriptRoot
$runtimeDir = Join-Path $backendRoot '.runtime'
$stateFile = Join-Path $runtimeDir 'backend-processes.json'
$backendPorts = @(8080, 8081, 8082, 8083, 8084, 8085)

function Stop-ProcessTree([int]$TargetPid) {
  $existing = Get-Process -Id $TargetPid -ErrorAction SilentlyContinue
  if ($null -eq $existing) {
    return
  }

  taskkill /PID $TargetPid /T /F | Out-Null
}

$candidatePids = New-Object System.Collections.Generic.List[int]

if (Test-Path -LiteralPath $stateFile) {
  $entries = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
  foreach ($entry in @($entries)) {
    $process = Get-Process -Id ([int]$entry.pid) -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.ProcessName -in @('powershell', 'cmd', 'java')) {
      $candidatePids.Add([int]$entry.pid)
    }
  }
}

foreach ($port in $backendPorts) {
  $connections = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
  foreach ($connection in @($connections)) {
    $candidatePids.Add([int]$connection.OwningProcess)
  }
}

$candidatePids |
  Sort-Object -Unique |
  Where-Object { $_ -gt 4 -and $_ -ne $PID } |
  ForEach-Object {
    Stop-ProcessTree -TargetPid $_
  }

if (Test-Path -LiteralPath $stateFile) {
  Remove-Item -LiteralPath $stateFile -Force
}

Write-Host 'Backend detenido.'