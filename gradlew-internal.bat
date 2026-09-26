@echo off
rem gradlew-internal.bat —— 在"工程内部"跑 Gradle，不写 ~/.gradle
rem
rem 见 gradlew-internal.ps1 里的详细说明。用法：
rem   gradlew-internal.bat runData
rem   gradlew-internal.bat build
rem   gradlew-internal.bat :genBlockList:generateDefaultBlockIds

setlocal
set "PROJECT_ROOT=%~dp0"
set "GRADLE_USER_HOME=%PROJECT_ROOT%.gradle-home"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdks\corretto-21.0.5\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\corretto-21.0.5"
)
if defined JAVA_HOME echo [internal-gradle] JAVA_HOME=%JAVA_HOME%
echo [internal-gradle] GRADLE_USER_HOME=%GRADLE_USER_HOME%

call "%PROJECT_ROOT%gradlew.bat" %*
exit /b %ERRORLEVEL%
