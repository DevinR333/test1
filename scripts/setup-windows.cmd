@echo off
rem Windows launcher.
rem
rem The setup is a shell script, and oracles-disasm needs a Unix-like
rem environment to build at all, so this finds MSYS2 and runs it there.
rem Double-click this file, or run it from CMD.

setlocal enabledelayedexpansion

echo.
echo   Oracle of Seasons port - Windows setup
echo   ======================================
echo.

set "MSYS2_ROOT="
for %%D in ("C:\msys64" "C:\msys2" "D:\msys64" "%LOCALAPPDATA%\msys64" "%USERPROFILE%\msys64") do (
    if exist "%%~D\usr\bin\bash.exe" (
        set "MSYS2_ROOT=%%~D"
        goto :found
    )
)

echo   MSYS2 is not installed, and this build needs it.
echo.
echo   MSYS2 gives Windows the Unix tools the disassembly's build
echo   requires. It installs like any normal program.
echo.
echo     1. Download the installer:  https://www.msys2.org
echo     2. Run it and accept the defaults.
echo     3. Run this file again.
echo.
echo   If you installed MSYS2 somewhere other than C:\msys64, open the
echo   "MSYS2 MINGW64" shell from your Start menu and run this instead:
echo.
echo       ./scripts/setup-disasm.sh --hack-base
echo.
pause
exit /b 1

:found
echo   Found MSYS2 at !MSYS2_ROOT!
echo   Starting setup. This takes a while - it compiles the assembler.
echo.

set "MSYS2_PATH_TYPE=inherit"
set "CHERE_INVOKING=1"

rem MSYSTEM=MINGW64 picks the toolchain the disassembly expects.
set "MSYSTEM=MINGW64"
"!MSYS2_ROOT!\usr\bin\bash.exe" -lc "cd \"$(cygpath -u '%CD%')\" && ./scripts/setup-disasm.sh --hack-base"

set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (
    echo   Setup finished.
) else (
    echo   Setup stopped with error code %RC%.
    echo   Copy the messages above and send them over - that is enough to fix it.
)
echo.
pause
exit /b %RC%
