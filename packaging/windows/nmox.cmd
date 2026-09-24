@echo off
rem nmox - open a folder or files in NMOX Studio from a terminal:
rem
rem     cd myproject
rem     nmox .
rem
rem Installed as {app}\cli\nmox.cmd; the installer's "Add nmox to PATH" task
rem puts that folder on PATH.
rem
rem Paths are made absolute and given their verb: a folder is AIMED (--aim,
rem what File, Open Folder... does, with or without a manifest), a file is
rem OPENED (--open). Options, and the value an option takes, pass through.
rem Bare `nmox` just starts the IDE. NAME:LINE or NAME:LINE:COL naming an
rem existing file opens it at that line, as `code -g` does (-g and --goto
rem are accepted and dropped); the platform's --open takes FILE:LINE and
rem has no column, so a column is dropped. The Unix launchers spell the same rule
rem (packaging/linux/nmox, packaging/macos/build-dmg.sh).
rem
rem A name that is not there is refused HERE, before anything starts:
rem "nmox: <name>: no such file or folder" on stderr and exit code 2 - for a
rem bare name and for the value of an explicit --aim or --open (--aim also
rem refuses a file: it takes a folder). START detaches the launcher, so a
rem refusal the IDE made would reach nobody at this console.
rem
rem START returns at once, so the console is not held until the IDE quits.
rem cmd splits arguments on = too, so an option value holding = (-J-Dx=y)
rem does not survive this shim; run bin\nmoxstudio64.exe for those.
rem
rem Every argument reaches NMOX_ARGS through :append, never through
rem `call set`: CALL expands % a second time and doubles ^, so a file named
rem 100%done or a^b arrived mangled. The value is read into a variable with
rem delayed expansion OFF (so ! stays a character), appended with it ON (so
rem & | < > ^ % in the value are never parsed again), and carried out of the
rem inner SETLOCAL by FOR /F into a context where it is off again.
setlocal DisableDelayedExpansion
set "NMOX_EXE=%~dp0..\bin\nmoxstudio64.exe"
set "NMOX_ARGS="
set "NMOX_VALUE="
:next
if "%~1"=="" goto launch
set "NMOX_A=%~1"
set "NMOX_VERB="
if defined NMOX_VALUE goto value
for %%V in (--userdir --cachedir --jdkhome --open --aim --locale --laf --fontsize --branding --clusters) do if /i "%~1"=="%%V" set "NMOX_VALUE=%%V"
if /i "%~1"=="-g" goto skip
if /i "%~1"=="--goto" goto skip
if "%NMOX_A:~0,1%"=="-" goto keep
if exist "%~1\*" goto folder
if exist "%~1" goto file
call :goto
if errorlevel 1 goto missing
for %%P in ("%NMOX_GF%") do set "NMOX_A=%%~fP:%NMOX_GL%"
set "NMOX_VERB=--open"
goto keep
:value
if /i "%NMOX_VALUE%"=="--aim" if not exist "%~1\*" if exist "%~1" goto notfolder
if /i "%NMOX_VALUE%"=="--aim" if not exist "%~1" goto missing
if /i "%NMOX_VALUE%"=="--open" if not exist "%~1" goto openline
set "NMOX_VALUE="
goto keep
:openline
rem an explicit --open's value stays as typed - the platform resolves it -
rem with a column dropped
call :goto
if errorlevel 1 goto missing
set "NMOX_A=%NMOX_GF%:%NMOX_GL%"
set "NMOX_VALUE="
goto keep
:keep
call :append
:skip
shift
goto next
:folder
rem "\." then the for-variable's full-path form drops a trailing backslash,
rem which would otherwise escape the closing quote on the launcher's command line
for %%D in ("%~f1\.") do set "NMOX_A=%%~fD"
set "NMOX_VERB=--aim"
goto keep
:file
set "NMOX_A=%~f1"
set "NMOX_VERB=--open"
goto keep
:append
setlocal EnableDelayedExpansion
if defined NMOX_VERB (set "NMOX_L=!NMOX_ARGS! !NMOX_VERB! "!NMOX_A!"") else set "NMOX_L=!NMOX_ARGS! "!NMOX_A!""
for /f "delims=" %%L in (""!NMOX_L!"") do endlocal & set "NMOX_ARGS=%%~L"
exit /b 0
:goto
rem NMOX_A as NAME:LINE or NAME:LINE:COL with NAME an existing file: sets
rem NMOX_GF to NAME as typed and NMOX_GL to LINE, errorlevel 0; else 1.
rem Trailing digits are peeled one character at a time (cmd has no
rem suffix operator), at most twice. The two values leave the SETLOCAL
rem through FOR /F split at |, which no Windows file name can hold, the
rem way :append carries its value out.
setlocal EnableDelayedExpansion
set "G_F=!NMOX_A!"
set "G_PASS="
:gotopass
set "G_P="
:gotodigit
if not defined G_F goto gotonone
set "G_C=!G_F:~-1!"
set "G_ISD="
for %%D in (0 1 2 3 4 5 6 7 8 9) do if "!G_C!"=="%%D" set "G_ISD=1"
if not defined G_ISD goto gotocolon
set "G_P=!G_C!!G_P!"
set "G_F=!G_F:~0,-1!"
goto gotodigit
:gotocolon
if not defined G_P goto gotonone
if not "!G_C!"==":" goto gotonone
set "G_F=!G_F:~0,-1!"
set "G_L=!G_P!"
if exist "!G_F!" if not exist "!G_F!\*" goto gotofound
if defined G_PASS goto gotonone
set "G_PASS=1"
goto gotopass
:gotofound
for /f "tokens=1,2 delims=| eol=|" %%A in ("!G_F!|!G_L!") do endlocal & set "NMOX_GF=%%A" & set "NMOX_GL=%%B"
exit /b 0
:gotonone
endlocal
exit /b 1
:missing
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A!: no such file or folder
exit /b 2
:notfolder
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A!: not a folder ^(--aim takes a folder^)
exit /b 2
:launch
if defined NMOX_VALUE (
    >&2 echo(nmox: %NMOX_VALUE% needs a value
    exit /b 2
)
start "" "%NMOX_EXE%" %NMOX_ARGS%
