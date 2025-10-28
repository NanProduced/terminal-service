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

setlocal enabledelayedexpansion

:: 第1步：创建临时文件列表
dir /b /o-d "results\*_metrics.ndjson" > "%temp%\k6_ndjson.tmp" 2>nul
dir /b /o-d "results\*_summary.json" > "%temp%\k6_json.tmp" 2>nul

:: 第2步：计算文件数量
set "NDJSON_COUNT=0"
for /f "tokens=*" %%A in (%temp%\k6_ndjson.tmp) do set /a NDJSON_COUNT+=1

set "JSON_COUNT=0"
for /f "tokens=*" %%A in (%temp%\k6_json.tmp) do set /a JSON_COUNT+=1

set /a TOTAL_COUNT=!NDJSON_COUNT! + !JSON_COUNT!

:: 第3步：选择要分析的文件
if !TOTAL_COUNT! EQU 0 (
    echo ❌ 未找到测试结果文件
    echo.
    echo 请先运行 TEST.bat 执行性能测试
    echo.
    pause
    set "SCRIPT_EXIT=1"
    del "%temp%\k6_ndjson.tmp" "%temp%\k6_json.tmp" 2>nul
    endlocal
    goto :cleanup
)

:: 如果只有一个文件，直接使用
if !TOTAL_COUNT! EQU 1 (
    if !NDJSON_COUNT! EQU 1 (
        for /f "tokens=*" %%A in (%temp%\k6_ndjson.tmp) do set "INPUT_FILE=results\%%A"
        echo ✅ 找到指标文件: !INPUT_FILE!
    ) else (
        for /f "tokens=*" %%A in (%temp%\k6_json.tmp) do set "INPUT_FILE=results\%%A"
        echo ℹ️  找到摘要文件: !INPUT_FILE!
    )
    del "%temp%\k6_ndjson.tmp" "%temp%\k6_json.tmp" 2>nul
    endlocal & set "INPUT_FILE=%INPUT_FILE%"
    goto do_analysis
)

:: 如果有多个文件，显示菜单让用户选择
echo.
call :show_file_selection_menu_v2
set "MENU_RESULT=%ERRORLEVEL%"

del "%temp%\k6_ndjson.tmp" "%temp%\k6_json.tmp" 2>nul
endlocal

if not defined INPUT_FILE (
    echo.
    echo ❌ 未选择有效的文件
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)

:do_analysis
setlocal enabledelayedexpansion
echo 分析中...
echo.

if exist "tools\analyze-results.js" (
    node tools\analyze-results.js "%INPUT_FILE%"
) else (
    echo [ERR] 找不到分析脚本: tools\analyze-results.js
    set "SCRIPT_EXIT=1"
    pause
    endlocal
    goto :cleanup
)

if errorlevel 1 (
    echo.
    echo ❌ 分析失败!
    pause
    set "SCRIPT_EXIT=1"
    endlocal
    goto :cleanup
)

echo.
pause
set "SCRIPT_EXIT=0"
endlocal
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

:show_file_selection_menu_v2
setlocal enabledelayedexpansion

echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║               选择要分析的测试结果文件                         ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

set "CHOICE_IDX=0"

if !NDJSON_COUNT! GTR 0 (
    echo [指标文件 - 推荐 ⭐] （包含完整的性能指标数据）
    for /f "tokens=*" %%A in (%temp%\k6_ndjson.tmp) do (
        set /a CHOICE_IDX+=1
        echo  !CHOICE_IDX!^) %%A
        set "CHOICE_!CHOICE_IDX!=results\%%A"
    )
    echo.
)

if !JSON_COUNT! GTR 0 (
    echo [摘要文件] （仅包含测试概览统计）
    for /f "tokens=*" %%A in (%temp%\k6_json.tmp) do (
        set /a CHOICE_IDX+=1
        echo  !CHOICE_IDX!^) %%A
        set "CHOICE_!CHOICE_IDX!=results\%%A"
    )
    echo.
)

set /a MANUAL_IDX=!CHOICE_IDX! + 1
echo [其他]
echo  !MANUAL_IDX!^) 手动输入文件路径
echo  Q^) 退出
echo.

set "USER_CHOICE="
set /p "USER_CHOICE=请选择 (1-!CHOICE_IDX!/!MANUAL_IDX!/Q): "
set "USER_CHOICE=!USER_CHOICE: =!"

if /i "!USER_CHOICE!"=="q" (
    endlocal
    exit /b 1
)

REM 检查选择的有效性
if "!USER_CHOICE!"=="" (
    echo ❌ 请输入有效的选择
    endlocal
    exit /b 1
)

REM 手动输入路径
if "!USER_CHOICE!"=="!MANUAL_IDX!" (
    echo.
    set "MANUAL_FILE="
    set /p "MANUAL_FILE=请输入文件路径 (例如 results\20251027-143000_scenario1_metrics.ndjson): "

    if not exist "!MANUAL_FILE!" (
        echo ❌ 文件不存在: !MANUAL_FILE!
        endlocal
        exit /b 1
    )

    endlocal & set "INPUT_FILE=%MANUAL_FILE%"
    exit /b 0
)

REM 检查数字范围
for /l %%i in (1, 1, !CHOICE_IDX!) do (
    if "!USER_CHOICE!"=="%%i" (
        if defined CHOICE_%%i (
            endlocal & set "INPUT_FILE=!CHOICE_%%i!"
            echo.
            echo ✅ 已选择文件: !CHOICE_%%i!
            exit /b 0
        )
    )
)

echo ❌ 无效的选择: !USER_CHOICE!
endlocal
exit /b 1
