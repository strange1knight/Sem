@echo off
cd /d %~dp0
echo ================================
echo     CHAT SERVER
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
echo 3. Starting Chat Server...
echo    Port: 8080
echo    Press Ctrl+C to stop
echo.

java -Dfile.encoding=UTF-8 -cp "target/classes;lib/*" chat.server.ChatServer

pause