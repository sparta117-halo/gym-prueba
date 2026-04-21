@echo off
setlocal

set SCRIPT_DIR=%~dp0
set WRAPPER_PROPS=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties
set MAVEN_VERSION=3.9.9
set DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Java\jdk-21"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if exist "%WRAPPER_PROPS%" (
  for /f "tokens=1,* delims==" %%A in (%WRAPPER_PROPS%) do (
    if /I "%%A"=="distributionUrl" set DIST_URL=%%B
  )
)

set MAVEN_DIR=%SCRIPT_DIR%.mvn\apache-maven-%MAVEN_VERSION%
set MAVEN_CMD=%MAVEN_DIR%\bin\mvn.cmd

if not exist "%MAVEN_CMD%" (
  echo Downloading Maven %MAVEN_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$distUrl='%DIST_URL%';" ^
    "$zipPath=Join-Path '%SCRIPT_DIR%.mvn' 'apache-maven.zip';" ^
    "$extractPath=Join-Path '%SCRIPT_DIR%.mvn' 'apache-maven-extract';" ^
    "New-Item -ItemType Directory -Force -Path (Join-Path '%SCRIPT_DIR%' '.mvn') | Out-Null;" ^
    "if (Test-Path $extractPath) { Remove-Item -Recurse -Force $extractPath; }" ^
    "Invoke-WebRequest -Uri $distUrl -OutFile $zipPath;" ^
    "Expand-Archive -LiteralPath $zipPath -DestinationPath $extractPath -Force;" ^
    "$folder=Get-ChildItem -Path $extractPath -Directory | Select-Object -First 1;" ^
    "if (-not $folder) { throw 'No Maven distribution extracted.' }" ^
    "if (Test-Path '%MAVEN_DIR%') { Remove-Item -Recurse -Force '%MAVEN_DIR%'; }" ^
    "Move-Item -Path $folder.FullName -Destination '%MAVEN_DIR%';" ^
    "Remove-Item -Force $zipPath;" ^
    "Remove-Item -Recurse -Force $extractPath;"
  if errorlevel 1 exit /b %errorlevel%
)

call "%MAVEN_CMD%" %*
endlocal