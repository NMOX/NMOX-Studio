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
rem OPENED (--open). Options, and the value an option takes, pass through;
rem a name that is not there passes through as typed, so the IDE's own
rem refusal names it. Bare `nmox` just starts the IDE. The Unix launchers
rem spell the same rule (packaging/linux/nmox, packaging/macos/build-dmg.sh).
rem
rem START returns at once, so the console is not held until the IDE quits.
rem cmd splits arguments on = too, so an option value holding = (-J-Dx=y)
rem does not survive this shim; run bin\nmoxstudio64.exe for those.
setlocal DisableDelayedExpansion
set "NMOX_EXE=%~dp0..\bin\nmoxstudio64.exe"
set "NMOX_ARGS="
set "NMOX_VALUE="
:next
if "%~1"=="" goto launch
set "NMOX_A=%~1"
if defined NMOX_VALUE goto value
for %%V in (--userdir --cachedir --jdkhome --open --aim --locale --laf --fontsize --branding --clusters) do if /i "%~1"=="%%V" set "NMOX_VALUE=1"
if "%NMOX_A:~0,1%"=="-" goto keep
if exist "%~1\*" goto folder
if exist "%~1" goto file
:keep
call set NMOX_ARGS=%%NMOX_ARGS%% "%~1"
shift
goto next
:value
set "NMOX_VALUE="
goto keep
:folder
rem "\." then the for-variable's full-path form drops a trailing backslash,
rem which would otherwise escape the closing quote on the launcher's command line
for %%D in ("%~f1\.") do set "NMOX_DIR=%%~fD"
call set NMOX_ARGS=%%NMOX_ARGS%% --aim "%%NMOX_DIR%%"
shift
goto next
:file
call set NMOX_ARGS=%%NMOX_ARGS%% --open "%~f1"
shift
goto next
:launch
start "" "%NMOX_EXE%" %NMOX_ARGS%
