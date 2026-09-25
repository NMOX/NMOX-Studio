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
rem -w and -d (3.2.0) make nmox.cmd git's editor and difftool, as the Unix
rem launchers do: every file is also written as an item into a private
rem request folder under %TEMP% (nmox-request.*), and when -w or -d was given
rem the IDE is handed that folder with --nmox-request and answers there -
rem accepted (its process id), refused (why) or done (every tab it opened was
rem closed); :await waits for the answer and removes the folder. A file named
rem before -w also rides --open, so it opens twice and is waited on once. The
rem items are written with the console switched to UTF-8 (chcp 65001, put
rem back after each item): echo writes in the console's code page and the IDE
rem reads UTF-8, so a name with an accent would otherwise not be found.
rem
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
set "NMOX_WAIT="
set "NMOX_DIFF="
set "NMOX_DIFFED="
set "NMOX_REQ="
set "NMOX_FOLDER="
:next
if "%~1"=="" goto launch
set "NMOX_A=%~1"
set "NMOX_VERB="
if defined NMOX_VALUE goto value
if defined NMOX_DIFF goto diffarg
for %%V in (--userdir --cachedir --jdkhome --open --aim --locale --laf --fontsize --branding --clusters) do if /i "%~1"=="%%V" set "NMOX_VALUE=%%V"
if /i "%~1"=="-h" goto usage
if /i "%~1"=="--help" goto usage
if "%~1"=="/?" goto usage
if /i "%~1"=="-r" goto skip
if /i "%~1"=="--reuse-window" goto skip
if /i "%~1"=="-n" goto newwindow
if /i "%~1"=="--new-window" goto newwindow
if /i "%~1"=="-w" goto waitflag
if /i "%~1"=="--wait" goto waitflag
if /i "%~1"=="-d" goto diffflag
if /i "%~1"=="--diff" goto diffflag
for %%V in (-a --add -v --version) do if /i "%~1"=="%%V" goto vscodeonly
if /i "%~1"=="-g" goto skip
if /i "%~1"=="--goto" goto skip
if "%NMOX_A:~0,1%"=="-" goto keep
if exist "%~1\*" goto folder
if exist "%~1" goto file
call :goto
if errorlevel 1 goto missing
for %%P in ("%NMOX_GF%") do set "NMOX_OPEN=%%~fP"
set "NMOX_LINE=%NMOX_GL%"
call :recordopen
if errorlevel 1 exit /b 1
if defined NMOX_WAIT goto skip
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
set "NMOX_FOLDER=1"
goto keep
:file
set "NMOX_OPEN=%~f1"
set "NMOX_LINE="
call :recordopen
if errorlevel 1 exit /b 1
if defined NMOX_WAIT goto skip
set "NMOX_A=%~f1"
set "NMOX_VERB=--open"
goto keep
:waitflag
set "NMOX_WAIT=1"
goto skip
:diffflag
set "NMOX_DIFF=2"
goto skip
:diffarg
rem git names the missing side of an added or a deleted file /dev/null,
rem which Git for Windows hands a .cmd as nul: that side is nothing
set "NMOX_SIDE="
if /i "%~1"=="nul" goto diffside
if "%~1"=="/dev/null" goto diffside
if not exist "%~1" goto notafile
if exist "%~1\*" goto notafile
set "NMOX_SIDE=%~f1"
:diffside
if not "%NMOX_DIFF%"=="2" goto diffright
set "NMOX_DL=%NMOX_SIDE%"
set "NMOX_DIFF=1"
goto skip
:diffright
if not defined NMOX_DL if not defined NMOX_SIDE goto diffnone
set "NMOX_DR=%NMOX_SIDE%"
set "NMOX_DIFF="
set "NMOX_DIFFED=1"
call :recorddiff
if errorlevel 1 exit /b 1
goto skip
:recordopen
set "NMOX_KIND=open"
goto record
:recorddiff
set "NMOX_KIND=diff"
:record
rem appends one item to the request folder's items file, making the folder
rem first; the names are read with delayed expansion ON, so nothing in a name
rem is parsed again, and written with the console in UTF-8. Two entry
rem labels rather than an argument: CALL parses its arguments a second time
if defined NMOX_REQ goto recordwrite
set /a NMOX_TRY=0
:mkreq
set /a NMOX_TRY+=1
set "NMOX_REQ=%TEMP%\nmox-request.%RANDOM%%RANDOM%"
md "%NMOX_REQ%" 2>nul
if not errorlevel 1 goto recordwrite
if %NMOX_TRY% lss 5 goto mkreq
>&2 echo(nmox: could not make a request folder in %%TEMP%%
exit /b 1
:recordwrite
set "NMOX_CP="
for /f "tokens=2 delims=:" %%C in ('chcp') do set "NMOX_CP=%%C"
if defined NMOX_CP set "NMOX_CP=%NMOX_CP: =%"
if defined NMOX_CP set "NMOX_CP=%NMOX_CP:.=%"
chcp 65001 >nul
:recordput
setlocal EnableDelayedExpansion
if "!NMOX_KIND!"=="open" goto recordfile
>>"!NMOX_REQ!\items" echo(diff
>>"!NMOX_REQ!\items" echo(!NMOX_DL!
>>"!NMOX_REQ!\items" echo(!NMOX_DR!
goto recorded
:recordfile
>>"!NMOX_REQ!\items" echo(open
>>"!NMOX_REQ!\items" echo(!NMOX_OPEN!
>>"!NMOX_REQ!\items" echo(!NMOX_LINE!
:recorded
endlocal
if defined NMOX_CP chcp %NMOX_CP% >nul
exit /b 0
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
rem past nine digits the platform's int parse fails, in silence
if not "!G_P:~9,1!"=="" goto gotonone
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
:usage
echo(Usage: nmox [options] [folder ^| file[:line[:column]]]...
echo(
echo(  nmox .              aim NMOX Studio at this folder, as File ^> Open Folder... does
echo(  nmox src/app.js     open a file
echo(  nmox src/app.js:42  open it at line 42 (-g and --goto are accepted)
echo(  nmox -w file        open it and wait until its tab is closed
echo(  nmox -d left right  compare two files side by side
echo(  nmox                start NMOX Studio
echo(
echo(It returns at once unless -w asks it to wait; a second nmox hands its folder
echo(or files to the IDE already running. A name that is not there is refused
echo(here, before anything starts. VS Code's -r is accepted and -n opens in the
echo(one window; -a and -v have no counterpart and are refused. Any other
echo(option goes to the IDE unchanged.
echo(
echo(NMOX Studio as git's editor and difftool:
echo(  git config --global core.editor "nmox -w"
echo(  git config --global diff.tool nmox
echo(  git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
exit /b 0
:newwindow
>&2 echo(nmox: NMOX Studio has one window; opening there
goto skip
:vscodeonly
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A! is VS Code's and has no counterpart here
exit /b 2
:missing
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A!: no such file or folder
exit /b 2
:notfolder
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A!: not a folder ^(--aim takes a folder^)
exit /b 2
:notafile
setlocal EnableDelayedExpansion
>&2 echo(nmox: !NMOX_A!: not a file ^(-d compares two files^)
exit /b 2
:needtwo
>&2 echo(nmox: -d needs two files
exit /b 2
:diffnone
>&2 echo(nmox: -d needs at least one file
exit /b 2
:waitfolder
>&2 echo(nmox: -w waits for a file to be closed, not a folder
exit /b 2
:waitnofile
>&2 echo(nmox: -w needs a file to wait for
exit /b 2
:launch
if defined NMOX_VALUE (
    >&2 echo(nmox: %NMOX_VALUE% needs a value
    exit /b 2
)
if defined NMOX_DIFF goto needtwo
if defined NMOX_WAIT if defined NMOX_FOLDER goto waitfolder
if defined NMOX_WAIT if not defined NMOX_REQ goto waitnofile
if not defined NMOX_REQ goto launchplain
if defined NMOX_WAIT goto launchrequest
if defined NMOX_DIFFED goto launchrequest
rd /s /q "%NMOX_REQ%" 2>nul
:launchplain
start "" "%NMOX_EXE%" %NMOX_ARGS%
exit /b 0
:launchrequest
>"%NMOX_REQ%\request" echo(nmox-request 1
if defined NMOX_WAIT >>"%NMOX_REQ%\request" echo(wait
type "%NMOX_REQ%\items" >>"%NMOX_REQ%\request"
rem the folder rides the start line itself: NMOX_ARGS has one way in (:append)
start "" "%NMOX_EXE%" %NMOX_ARGS% --nmox-request "%NMOX_REQ%"
set "NMOX_N=0"
set "NMOX_PID="
:await
if exist "%NMOX_REQ%\refused" goto awaitrefused
if exist "%NMOX_REQ%\done" goto awaitend
if not defined NMOX_PID if exist "%NMOX_REQ%\accepted" set /p NMOX_PID=<"%NMOX_REQ%\accepted"
if defined NMOX_PID if not defined NMOX_WAIT goto awaitend
if defined NMOX_PID goto awaitalive
set /a NMOX_N+=1
if %NMOX_N% geq 120 goto awaitsilent
goto awaitsleep
:awaitalive
tasklist /FI "PID eq %NMOX_PID%" /NH 2>nul | find " %NMOX_PID% " >nul
if errorlevel 1 goto awaitgone
:awaitsleep
ping -n 2 127.0.0.1 >nul
goto await
:awaitrefused
setlocal EnableDelayedExpansion
set /p NMOX_WHY=<"!NMOX_REQ!\refused"
>&2 echo(nmox: !NMOX_WHY!
endlocal
rd /s /q "%NMOX_REQ%" 2>nul
exit /b 2
:awaitgone
if exist "%NMOX_REQ%\done" goto awaitend
rd /s /q "%NMOX_REQ%" 2>nul
>&2 echo(nmox: NMOX Studio quit before the file was closed
exit /b 1
:awaitsilent
rd /s /q "%NMOX_REQ%" 2>nul
>&2 echo(nmox: NMOX Studio did not answer
exit /b 1
:awaitend
rd /s /q "%NMOX_REQ%" 2>nul
exit /b 0
