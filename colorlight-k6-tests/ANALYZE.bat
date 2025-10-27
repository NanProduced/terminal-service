@echo off
setlocal enabledelayedexpansion

set "SCRIPT_EXIT=0"
call :__init_console_encoding

cd /d "%~dp0"

echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║      Colorlight Terminal K6 - 性能分析工具 v1.0              ║
echo ║                   分析并生成测试报告                         ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

:: 检查Node.js
node --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: Node.js 未安装
    echo.
    echo 请访问 https://nodejs.org/ 安装 Node.js
    echo.
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)

:: 如果指定了文件参数，直接分析
if not "%1"=="" (
    if not exist "%1" (
        echo ❌ 错误: 文件不存在 "%1"
        pause
        set "SCRIPT_EXIT=1"
        goto :cleanup
    )
    set INPUT_FILE=%1
    goto do_analysis
)

:: 扫描results目录找最新文件
echo 扫描最新测试结果...
echo.

set LATEST_FILE=
for /f "delims=" %%A in ('dir /b /o-d "results\*.json" 2^>nul') do (
    set LATEST_FILE=%%A
    goto found_file
)

:found_file
if not defined LATEST_FILE (
    echo ❌ 未找到测试结果文件
    echo.
    echo 请先运行 TEST.bat 执行性能测试
    echo.
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)

set INPUT_FILE=results\!LATEST_FILE!

echo 找到最新结果: !LATEST_FILE!
echo.

:do_analysis
echo 分析中...
echo.

if exist "tools\analyze-results.js" (
    node tools\analyze-results.js "!INPUT_FILE!"
) else (
    echo [ERR] 找不到分析脚本: tools\analyze-results.js
    set "SCRIPT_EXIT=1"
    pause
    goto :cleanup
)

if errorlevel 1 (
    echo.
    echo ❌ 分析失败!
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)

echo.
pause
set "SCRIPT_EXIT=0"
goto :cleanup

:cleanup
call :__restore_console_encoding
exit /b %SCRIPT_EXIT%

:__init_console_encoding
set "ORIGINAL_CP="
for /f "tokens=2 delims=:." %%I in ('chcp') do set "ORIGINAL_CP=%%I"
set "ORIGINAL_CP=!ORIGINAL_CP: =!"
if not defined ORIGINAL_CP set "ORIGINAL_CP=65001"
if /i not "!ORIGINAL_CP!"=="65001" (
    chcp 65001 >nul
)
exit /b 0

:__restore_console_encoding
if defined ORIGINAL_CP (
    if /i not "!ORIGINAL_CP!"=="65001" (
        chcp !ORIGINAL_CP! >nul
    )
)
exit /b 0
