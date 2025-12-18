@echo off
cd /d %~dp0
echo ================================
echo     CHAT CLIENT
echo ================================
echo.

echo 1. Checking dependencies...
if not exist lib (
    echo Downloading dependencies...
    call mvn dependency:copy-dependencies -DoutputDirectory=lib
)

echo.
echo 2. Compiling project...
call mvn clean compile
if errorlevel 1 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)

echo.
echo 3. Starting Chat Client...
echo    Make sure server is running on localhost:8080
echo.

java -Dfile.encoding=UTF-8 -cp "target/classes;lib/*" chat.client.ChatClient

pause