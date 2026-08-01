@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.9"
set "GRADLE_SHA256=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
if defined GRADLE_USER_HOME (set "CACHE_ROOT=%GRADLE_USER_HOME%\siphon-bootstrap") else (set "CACHE_ROOT=%USERPROFILE%\.gradle\siphon-bootstrap")
set "ZIP=%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_HOME=%CACHE_ROOT%\gradle-%GRADLE_VERSION%"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run
if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
if not exist "%ZIP%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%'"
  if errorlevel 1 exit /b 1
)
for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLower()"') do set "ACTUAL_SHA=%%H"
if /I not "%ACTUAL_SHA%"=="%GRADLE_SHA256%" (
  echo Gradle download checksum mismatch.
  del /q "%ZIP%" 2>nul
  exit /b 1
)
if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP%' -DestinationPath '%CACHE_ROOT%' -Force"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
