param(
  [int]$BackendStartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $rootDir 'stop-all.ps1')
Start-Sleep -Seconds 2
& (Join-Path $rootDir 'start-all.ps1') -BackendStartupTimeoutSeconds $BackendStartupTimeoutSeconds