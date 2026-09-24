@echo off
rem nmox - open a folder or files in NMOX Studio from a terminal:
rem
rem     cd myproject
rem     nmox .
rem
rem Installed as {app}\cli\nmox.cmd; the installer's "Add nmox to PATH" task puts
rem that folder on PATH. START returns at once, so the console is not held
rem until the IDE quits, and it keeps this console's working directory, which
rem the launcher hands to a running instance along with the arguments - so
rem "." is the folder you typed it in.
start "" "%~dp0..\bin\nmoxstudio64.exe" %*
