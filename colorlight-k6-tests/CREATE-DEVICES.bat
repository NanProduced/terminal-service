@echo off
setlocal enabledelayedexpansion

pushd "%~dp0"

set "SCRIPT_EXIT=0"
set "NODE_CMD="

call :__init_console_encoding

:: ==========================================
:: Colorlight Terminal K6 - 设备批量创建工具
:: ==========================================

cls
echo.
echo ╔══════════════════════════════════════════════════════════════════════════════╗
echo ║    Colorlight Terminal K6 - 设备批量创建向导 v1.0                             ║
echo ║                                                                              ║
echo ╚══════════════════════════════════════════════════════════════════════════════╝
echo.

echo [1/5] 检查环境...
call :__resolve_node_path
if errorlevel 1 (
    echo.
    echo [ERR] 未检测到 Node.js v14+，请先安装或将 node.exe 加入 PATH
    echo       下载地址: https://nodejs.org/
    echo.
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)
"%NODE_CMD%" --version >nul 2>&1
if not errorlevel 1 (
    for /f "tokens=* delims=" %%i in ('"%NODE_CMD%" --version 2^>nul') do set "NODE_VERSION=%%i"
)
if not defined NODE_VERSION set "NODE_VERSION=版本未知"
echo [OK] Node.js 已就绪 (!NODE_VERSION!)
echo       路径: !NODE_CMD!
echo.

echo [2/5] 检查配置文件...
if not exist "config\device-config.json" (
    echo.
    echo [ERR] 缺少 config\device-config.json
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)
if not exist "config\server-config.json" (
    echo.
    echo [ERR] 缺少 config\server-config.json
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)
echo [OK] 配置文件已就绪
echo.

echo [3/5] 检查工具脚本...
if not exist "tools\create-devices.js" (
    echo.
    echo [ERR] 缺少 tools\create-devices.js
    pause
    set "SCRIPT_EXIT=1"
    goto :cleanup
)
echo [OK] 设备创建脚本已找到
echo.

echo [4/5] 读取创建参数...
echo.
if not exist "logs" mkdir logs
set "LOG_FILE=logs\device-creation.log"
echo [!date! !time!] 开始执行设备创建 > "!LOG_FILE!"

call "!NODE_CMD!" tools\create-devices.js
set "ERROR_CODE=!errorlevel!"

echo.
if "!ERROR_CODE!"=="0" (
    echo ╔══════════════════════════════════════════════════════════════════════════╗
    echo ║ √ 设备创建完成                                                          ║
    echo ╚══════════════════════════════════════════════════════════════════════════╝
    echo.
    echo [!date! !time!] 结果: 成功 >> "!LOG_FILE!"
    echo.
    echo 后续步骤:
    echo   1. 返回主目录运行 TEST.bat
    echo   2. 选择需要的性能压测场景
    echo.
    set "SCRIPT_EXIT=0"
) else (
    echo ╔══════════════════════════════════════════════════════════════════════════╗
    echo ║ × 设备创建失败                                                          ║
    echo ╚══════════════════════════════════════════════════════════════════════════╝
    echo.
    echo [!date! !time!] 结果: 失败 - 错误码 !ERROR_CODE! >> "!LOG_FILE!"
    echo.
    echo 可能原因:
    echo   1. API Token 已失效
    echo   2. 无法连接目标服务
    echo   3. 服务器地址填写错误
    echo.
    echo 建议排查:
    echo   1. 检查 config\device-config.json 中的 token
    echo   2. 检查 config\server-config.json 中的服务器地址
    echo   3. 确认网络连通性
    echo   4. 查看日志: !LOG_FILE!
    echo.
    set "SCRIPT_EXIT=!ERROR_CODE!"
)

echo.
pause
goto :cleanup

:cleanup
call :__restore_console_encoding
popd
exit /b %SCRIPT_EXIT%

:__resolve_node_path
set "NODE_CMD="
for %%C in (node.exe node) do (
    for /f "delims=" %%P in ('where %%C 2^>nul') do if not defined NODE_CMD set "NODE_CMD=%%~fP"
    if defined NODE_CMD goto :resolve_done
)
if not defined NODE_CMD if defined NODE_HOME (
    if exist "%NODE_HOME%\node.exe" set "NODE_CMD=%NODE_HOME%\node.exe"
)
if not defined NODE_CMD if exist "%~dp0tools\node\node.exe" set "NODE_CMD=%~dp0tools\node\node.exe"
if not defined NODE_CMD if defined ProgramFiles (
    if exist "%ProgramFiles%\nodejs\node.exe" set "NODE_CMD=%ProgramFiles%\nodejs\node.exe"
)
if not defined NODE_CMD if defined ProgramFiles(x86) (
    if exist "%ProgramFiles(x86)%\nodejs\node.exe" set "NODE_CMD=%ProgramFiles(x86)%\nodejs\node.exe"
)
:resolve_done
if not defined NODE_CMD exit /b 1
exit /b 0

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
