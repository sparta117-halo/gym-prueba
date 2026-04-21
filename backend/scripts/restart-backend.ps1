param(
	[int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $scriptRoot 'stop-backend.ps1')
Start-Sleep -Seconds 2
& (Join-Path $scriptRoot 'start-backend.ps1') -StartupTimeoutSeconds $StartupTimeoutSeconds