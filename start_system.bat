@echo off
if not defined HC_PLATFORM_DIR set "HC_PLATFORM_DIR=%~dp0..\VideoFlow_AI"
if not exist "%HC_PLATFORM_DIR%\pom.xml" if exist "%~dp0HC_Drown_Plantform\pom.xml" set "HC_PLATFORM_DIR=%~dp0HC_Drown_Plantform"
if not exist "%HC_PLATFORM_DIR%\pom.xml" (
    echo HC platform not found. Clone VideoFlow_AI beside this repository or set HC_PLATFORM_DIR.
    pause
    exit /b 1
)
title 黄村乡 AI 防溺水智能管理系统 - 快捷启动中枢
cls

:MENU
cls
echo ======================================================================
echo           黄村乡 AI 防溺水智能管理系统 - 一键启动控制台
echo ======================================================================
echo.
echo  请选择启动模式：
echo.
echo  [1] 本地访问模式 (仅本机访问 localhost / 127.0.0.1)
echo  [2] 局域网共享模式 (支持同局域网其他电脑/手机输入本机 IP 访问)
echo  [3] 退出脚本
echo.
echo ======================================================================
set /p mode="请输入选项数字 (1 或 2): "

if "%mode%"=="1" goto LOCAL_MODE
if "%mode%"=="2" goto LAN_MODE
if "%mode%"=="3" exit
goto INVALID

:LOCAL_MODE
cls
echo ======================================================================
echo           正在以 [本地访问模式] 启动系统...
echo ======================================================================
set TARGET_IP=127.0.0.1
goto START_SERVICES

:LAN_MODE
cls
echo ======================================================================
echo           正在以 [局域网共享模式] 启动系统...
echo ======================================================================
echo.
echo 正在获取本机的局域网 IP 地址...
set LOCAL_IP=127.0.0.1
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
    set LOCAL_IP=%%a
)
:: 截取去除空格的IP
for /f "tokens=*" %%i in ("%LOCAL_IP%") do set LOCAL_IP=%%i
set TARGET_IP=%LOCAL_IP%

echo.
echo ----------------------------------------------------------------------
echo [局域网访问地址]（请在同局域网其他电脑/手机浏览器打开）：
echo  1. 数据大屏前端: http://%LOCAL_IP%:5173
echo  2. 算法管理平台: http://%LOCAL_IP%:8000/platform/
echo ----------------------------------------------------------------------
echo.
goto START_SERVICES

:START_SERVICES
echo ----------------------------------------------------------------------
echo [0/4] 自动清理残留占用端口 (8080, 8000, 5173)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8080\>" 2^>nul') do (
    taskkill /f /pid %%a 2>nul
)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8000\>" 2^>nul') do (
    taskkill /f /pid %%a 2>nul
)
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":5173\>" 2^>nul') do (
    taskkill /f /pid %%a 2>nul
)
echo   [OK] 端口清理完成，防止端口冲突。

echo.
echo ----------------------------------------------------------------------
echo [1/4] 检查并启动 ZLMediaKit 流媒体服务器...
tasklist /fi "imagename eq MediaServer.exe" 2>NUL | find /i "MediaServer.exe" >NUL
if "%ERRORLEVEL%"=="0" (
    echo   [OK] ZLMediaKit MediaServer 已经在后台运行中。
) else (
    if exist "%~dp0MediaServer.exe" (
        echo   [RUN] 正在启动本目录下的 MediaServer.exe...
        start "ZLMediaKit-MediaServer" cmd /k "cd /d "%~dp0" && MediaServer.exe"
    ) else if exist "%~dp0ZLMediaKit/MediaServer.exe" (
        echo   [RUN] MediaServer.exe...
        start "ZLMediaKit-MediaServer" cmd /k "cd /d "%~dp0ZLMediaKit" && MediaServer.exe"
    ) else if exist "F:\ZLMediaKit-Server\ZLMediaKit-Server\zlmediakit\MediaServer.exe" (
        echo   [RUN] MediaServer.exe...
        start "ZLMediaKit-MediaServer" cmd /k "cd /d "F:\ZLMediaKit-Server\ZLMediaKit-Server\zlmediakit" && MediaServer.exe"
        echo   [RUN] 正在启动 ZLMediaKit/MediaServer.exe...
        start "ZLMediaKit-MediaServer" cmd /k "cd /d "%~dp0ZLMediaKit" && MediaServer.exe"
    ) else (
        echo   [INFO] 未在当前目录下扫描到 MediaServer.exe，内置流分析处理可正常使用。
    )
)

echo.
echo [2/4] 正在启动 Python AI 算法推理后端 (端口 8000)...
start "Python-AI-Engine-8000" cmd /k "cd /d "%~dp0backend" && "%~dp0venv/Scripts/python.exe" run.py"

timeout /t 3 /nobreak >nul

echo.
echo [3/4] 正在启动 Java 核心业务后台服务 (端口 8080)...
start "Java-Spring-Boot-8080" cmd /k "cd /d "%HC_PLATFORM_DIR%" && mvn spring-boot:run"

timeout /t 5 /nobreak >nul

echo.
echo [4/4] 正在启动 Vue3 三维防溺水数据大屏 (端口 5173)...
start "Vue-BigScreen-Frontend-5173" cmd /k "cd /d "%HC_PLATFORM_DIR%/frontend" && npm run dev"

timeout /t 6 /nobreak >nul

echo.
echo 正在为您自动在浏览器中打开系统界面...
start http://%TARGET_IP%:5173/
start http://%TARGET_IP%:8000/platform/

echo.
echo ======================================================================
echo [OK] 所有服务一键启动指令已成功下发！
echo.
echo [浏览器访问入口]：
echo  1. 三维防溺水数据大屏: http://%TARGET_IP%:5173/
echo  2. 视频算法管理平台: http://%TARGET_IP%:8000/platform/
echo ======================================================================
echo.
pause
exit

:INVALID
echo 错误：输入选项无效，请按任意键重新选择...
pause >nul
goto MENU
