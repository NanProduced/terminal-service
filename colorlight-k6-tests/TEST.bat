@echo off
setlocal enabledelayedexpansion

pushd "%~dp0"

if defined K6_BIN (
    set "K6_COMMAND=%K6_BIN%"
) else (
    set "K6_COMMAND=k6"
)

set "SCRIPT_EXIT=0"
call :__init_console_encoding

call :__prepare_environment
if errorlevel 1 goto :cleanup

:main_menu
cls
call :__print_banner
echo.
call :__print_menu
echo.
set "USER_CHOICE="
set /p "USER_CHOICE=请选择要执行的场景 (1-5 / Q 退出): "
set "USER_CHOICE=!USER_CHOICE: =!"
echo.

if /i "!USER_CHOICE!"=="q" goto :cleanup

if "!USER_CHOICE!"=="1" (
    call :__run_scenario 1 "HTTP状态上报" "tests/01-status-report.js"
    goto :main_menu
)
if "!USER_CHOICE!"=="2" (
    call :__run_scenario 2 "WebSocket V10 压测" "tests/02-websocket-v10.js"
    goto :main_menu
)
if "!USER_CHOICE!"=="3" (
    call :__run_scenario 3 "WebSocket V11 压测" "tests/03-websocket-v11.js"
    goto :main_menu
)
if "!USER_CHOICE!"=="4" (
    call :__run_scenario 4 "混合场景压测" "tests/04-mixed-load.js"
    goto :main_menu
)
if "!USER_CHOICE!"=="5" (
    call :__run_all_scenarios
    goto :main_menu
)

if "!USER_CHOICE!"=="" goto :main_menu

echo [WARN] 无效选项，请输入 1-5 或 Q。
pause
goto :main_menu

:cleanup
call :__restore_console_encoding
popd
exit /b %SCRIPT_EXIT%

:: ==================== 工具函数 ====================

:__print_banner
echo ================================================
echo   Colorlight Terminal K6 - 压测控制台
echo ================================================
echo 配置文件: config/test-params.json
echo 输出目录: results/  logs/  reports/
exit /b 0

:__print_menu
echo 请选择要执行的压测场景:
echo.
echo [1] HTTP状态上报 - 约 28 分钟
echo [2] WebSocket V10 压测 - 约 18 分钟
echo [3] WebSocket V11 压测 - 约 20 分钟
echo [4] 混合场景压测 - 约 20 分钟
echo [5] 压测基准 - 顺序执行以上所有场景，约 120 分钟或更长
echo.
echo [Q] 退出脚本
exit /b 0

:__prepare_environment
echo [INFO] 检查 k6 执行环境...
call "%K6_COMMAND%" version >nul 2>&1
if errorlevel 1 (
    echo [ERR] 未检测到 k6，请先安装并配置 PATH。
    echo        下载地址: https://k6.io/docs/get-started/installation/
    set "SCRIPT_EXIT=1"
    exit /b 1
)
echo [OK] k6 检查通过。
echo.

if not exist results mkdir results >nul 2>&1
if not exist logs mkdir logs >nul 2>&1
if not exist reports mkdir reports >nul 2>&1
exit /b 0

:__run_scenario
set "IDX=%~1"
set "SCENARIO_NAME=%~2"
set "SCRIPT_PATH=%~3"

if not exist "%SCRIPT_PATH%" (
    echo [ERR] 脚本文件不存在: %SCRIPT_PATH%
    echo       请检查文件是否存在或路径是否正确
    pause
    exit /b 1
)

echo ================================================
echo 场景 %IDX%: %SCENARIO_NAME%
echo 脚本: %SCRIPT_PATH%
echo ================================================
echo.

set "TIMESTAMP="
for /f "tokens=* delims=" %%I in ('powershell -NoLogo -NoProfile -Command "(Get-Date).ToString('yyyyMMdd-HHmmss')" 2^>nul') do set "TIMESTAMP=%%I"
if not defined TIMESTAMP (
    for /f "tokens=2-4 delims=/ " %%a in ('date /t') do set "TIMESTAMP=%%c%%a%%b"
    for /f "tokens=1-2 delims=/:" %%a in ('time /t') do set "TIMESTAMP=!TIMESTAMP!%%a%%b"
)

set "SUMMARY_FILE=results\!TIMESTAMP!_scenario%IDX%_summary.json"
set "METRICS_FILE=results\!TIMESTAMP!_scenario%IDX%_metrics.ndjson"

echo [INFO] 启动 k6...
echo.

"%K6_COMMAND%" run --summary-export="!SUMMARY_FILE!" --out="json=!METRICS_FILE!" "%SCRIPT_PATH%"
set "RUN_EXIT=!errorlevel!"

if not "!RUN_EXIT!"=="0" (
    echo.
    echo [ERR] 场景执行失败，退出码: !RUN_EXIT!
    set "SCRIPT_EXIT=!RUN_EXIT!"
) else (
    echo.
    echo [OK] 场景执行完成，结果已写入 results/ 目录。
    set "SCRIPT_EXIT=0"
)

echo.
pause
exit /b !RUN_EXIT!

:__run_all_scenarios
echo ================================================
echo 压测基准：顺序执行所有场景
echo ================================================
set "BASELINE_EXIT=0"

echo.
echo >>> 阶段 1/4: HTTP状态上报
call :__run_scenario 1 "HTTP状态上报" "tests/01-status-report.js"
set "STAGE_EXIT=!errorlevel!"
if not "!STAGE_EXIT!"=="0" (
    set "BASELINE_EXIT=!STAGE_EXIT!"
    goto :baseline_summary
)

echo.
echo 等待 3 分钟让系统恢复...
timeout /t 180 /nobreak

echo.
echo >>> 阶段 2/4: WebSocket V10 压测
call :__run_scenario 2 "WebSocket V10 压测" "tests/02-websocket-v10.js"
set "STAGE_EXIT=!errorlevel!"
if not "!STAGE_EXIT!"=="0" (
    set "BASELINE_EXIT=!STAGE_EXIT!"
    goto :baseline_summary
)

echo.
echo 等待 3 分钟让系统恢复...
timeout /t 180 /nobreak

echo.
echo >>> 阶段 3/4: WebSocket V11 压测
call :__run_scenario 3 "WebSocket V11 压测" "tests/03-websocket-v11.js"
set "STAGE_EXIT=!errorlevel!"
if not "!STAGE_EXIT!"=="0" (
    set "BASELINE_EXIT=!STAGE_EXIT!"
    goto :baseline_summary
)

echo.
echo 等待 3 分钟让系统恢复...
timeout /t 180 /nobreak

echo.
echo >>> 阶段 4/4: 混合场景压测
call :__run_scenario 4 "混合场景压测" "tests/04-mixed-load.js"
set "STAGE_EXIT=!errorlevel!"
if not "!STAGE_EXIT!"=="0" (
    set "BASELINE_EXIT=!STAGE_EXIT!"
    goto :baseline_summary
)

:baseline_summary
if "!BASELINE_EXIT!"=="0" (
    echo.
    echo [OK] 压测基准流程执行完毕，请查看 results/ 获取详细数据。
) else (
    echo.
    echo [ERR] 基准流程执行失败，退出码 !BASELINE_EXIT!
)
pause
exit /b !BASELINE_EXIT!

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
