@echo off
setlocal
cd /d "%~dp0"
echo.
echo === Chaos Vote Mod Builder ===
echo.
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java 21 first.
  echo https://adoptium.net/temurin/releases/?version=21
  pause
  exit /b 1
)
for /f "tokens=3" %%A in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%A
echo Detected Java: %JAVA_VER%
echo.
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed on this PC.
  echo Easiest option: upload this project to GitHub and let the included GitHub Action build the JAR.
  echo.
  echo Alternatively, install Gradle 8.10.2 and run this file again.
  echo https://gradle.org/releases/
  pause
  exit /b 1
)
call gradle build
if errorlevel 1 (
  echo.
  echo BUILD FAILED.
  pause
  exit /b 1
)
echo.
echo BUILD COMPLETE.
echo Your mod JAR is in build\libs\
dir /b build\libs\*.jar
pause
