param(
  [int]$StartupTimeoutSeconds = 180,
  [switch]$ForceRestart
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Split-Path -Parent $scriptRoot
$runtimeDir = Join-Path $backendRoot '.runtime'
$logDir = Join-Path $runtimeDir 'logs'
$stateFile = Join-Path $runtimeDir 'backend-processes.json'
$mavenWrapper = Join-Path $backendRoot 'mvnw.cmd'
$configRepoUri = [System.Uri]::new((Join-Path $backendRoot 'config-repo')).AbsoluteUri

$services = @(
  @{ Name = 'service-config'; Port = 8081; HealthUrl = 'http://127.0.0.1:8081/actuator/health' },
  @{ Name = 'service-membresia'; Port = 8082; HealthUrl = 'http://127.0.0.1:8082/actuator/health' },
  @{ Name = 'service-rutinas'; Port = 8083; HealthUrl = 'http://127.0.0.1:8083/actuator/health' },
  @{ Name = 'service-media'; Port = 8084; HealthUrl = 'http://127.0.0.1:8084/actuator/health' },
  @{ Name = 'service-scheluder'; Port = 8085; HealthUrl = 'http://127.0.0.1:8085/actuator/health' },
  @{ Name = 'service-gateway'; Port = 8080; HealthUrl = 'http://127.0.0.1:8080/actuator/health' }
)

function Ensure-Directory([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

function Test-PortListening([int]$Port) {
  $connection = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
  return $null -ne $connection
}

function Wait-ForHealth([string]$ServiceName, [string]$HealthUrl, [int]$TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 5
      if ($null -ne $response) {
        return
      }
    } catch {
    }

    Start-Sleep -Seconds 2
  }

  throw "El servicio $ServiceName no respondio sano en $HealthUrl dentro de $TimeoutSeconds segundos."
}

function Get-EnvironmentAssignments($service) {
  $assignments = @(
    "`$env:SERVER_PORT='$($service.Port)'"
  )

  switch ($service.Name) {
    'service-config' {
      $assignments += "`$env:SPRING_PROFILES_ACTIVE='native'"
      $assignments += "`$env:CONFIG_REPO_LOCATION='$configRepoUri'"
    }
    'service-membresia' {
      $assignments += "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/forcegym_next'"
      $assignments += "`$env:SPRING_DATASOURCE_USERNAME='postgres'"
      $assignments += "`$env:SPRING_DATASOURCE_PASSWORD='tilin'"
    }
    'service-rutinas' {
      $assignments += "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/forcegym_next'"
      $assignments += "`$env:SPRING_DATASOURCE_USERNAME='postgres'"
      $assignments += "`$env:SPRING_DATASOURCE_PASSWORD='tilin'"
    }
    'service-gateway' {
      $assignments += "`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/forcegym_next'"
      $assignments += "`$env:SPRING_DATASOURCE_USERNAME='postgres'"
      $assignments += "`$env:SPRING_DATASOURCE_PASSWORD='tilin'"
      $assignments += "`$env:SERVICE_CONFIG_URL='http://localhost:8081'"
      $assignments += "`$env:SERVICE_MEMBRESIA_URL='http://localhost:8082'"
      $assignments += "`$env:SERVICE_RUTINAS_URL='http://localhost:8083'"
      $assignments += "`$env:SERVICE_MEDIA_URL='http://localhost:8084'"
      $assignments += "`$env:SERVICE_SCHELUDER_URL='http://localhost:8085'"
      $assignments += "`$env:FORCE_GYM_JWT_SECRET='0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF'"
    }
  }

  return ($assignments -join '; ')
}

if ($ForceRestart) {
  & (Join-Path $scriptRoot 'stop-backend.ps1')
}

Ensure-Directory $runtimeDir
Ensure-Directory $logDir

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
  throw "No se encontro mvnw.cmd en $backendRoot"
}

if (-not (Test-PortListening -Port 5432)) {
  throw 'PostgreSQL no esta escuchando en localhost:5432. Arranca la base antes del backend.'
}

$runningPorts = @()
foreach ($service in $services) {
  if (Test-PortListening -Port $service.Port) {
    $runningPorts += $service.Port
  }
}

if ($runningPorts.Count -gt 0) {
  throw "Ya hay puertos backend ocupados: $($runningPorts -join ', '). Ejecuta stop-backend.ps1 o restart-backend.ps1 antes de iniciar de nuevo."
}

$processState = @()

foreach ($service in $services) {
  $stdoutLog = Join-Path $logDir "$($service.Name).out.log"
  $stderrLog = Join-Path $logDir "$($service.Name).err.log"
  $moduleDir = Join-Path $backendRoot $service.Name
  $modulePom = Join-Path (Join-Path $backendRoot $service.Name) 'pom.xml'

  if (Test-Path -LiteralPath $stdoutLog) {
    Clear-Content -LiteralPath $stdoutLog
  }

  if (Test-Path -LiteralPath $stderrLog) {
    Clear-Content -LiteralPath $stderrLog
  }

  if (-not (Test-Path -LiteralPath $modulePom)) {
    throw "No se encontro el pom del modulo $($service.Name) en $modulePom"
  }

  $environmentSetup = Get-EnvironmentAssignments -service $service
  $command = "$environmentSetup; Set-Location '$moduleDir'; & '$mavenWrapper' -f '$modulePom' spring-boot:run"
  $process = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru

  $processState += [pscustomobject]@{
    name = $service.Name
    port = $service.Port
    pid = $process.Id
    healthUrl = $service.HealthUrl
    stdoutLog = $stdoutLog
    stderrLog = $stderrLog
  }

  Start-Sleep -Seconds 2
}

$processState | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile

foreach ($service in $services) {
  Wait-ForHealth -ServiceName $service.Name -HealthUrl $service.HealthUrl -TimeoutSeconds $StartupTimeoutSeconds
}

Write-Host 'Backend listo.'
Write-Host 'Servicios activos:'
$processState | Format-Table name, port, pid -AutoSize
Write-Host "Logs: $logDir"