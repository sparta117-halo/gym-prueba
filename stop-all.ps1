$ErrorActionPreference = 'Stop'

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendScript = Join-Path $rootDir 'backend\scripts\stop-backend.ps1'
$runtimeDir = Join-Path $rootDir '.runtime'
$stateFile = Join-Path $runtimeDir 'web-stack-processes.json'

function Stop-ProcessTree([int]$TargetPid) {
  $existing = Get-Process -Id $TargetPid -ErrorAction SilentlyContinue
  if ($null -eq $existing) {
    return
  }

  taskkill /PID $TargetPid /T /F | Out-Null
}

$frontendPids = New-Object System.Collections.Generic.List[int]

if (Test-Path -LiteralPath $stateFile) {
  $entry = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
  if ($null -ne $entry) {
    if ($entry.frontendPid) {
      $frontendPids.Add([int]$entry.frontendPid)
    }

    if ($entry.proxyPid) {
      $frontendPids.Add([int]$entry.proxyPid)
    }
  }
}

$connections = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in @(3000, 3443) }
foreach ($connection in @($connections)) {
  $frontendPids.Add([int]$connection.OwningProcess)
}

$frontendPids |
  Sort-Object -Unique |
  Where-Object { $_ -gt 4 -and $_ -ne $PID } |
  ForEach-Object {
    Stop-ProcessTree -TargetPid $_
  }

if (Test-Path -LiteralPath $stateFile) {
  Remove-Item -LiteralPath $stateFile -Force
}

& $backendScript

Write-Host 'Stack detenido.'