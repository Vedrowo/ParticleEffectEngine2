@echo off
setlocal

set MPJ_HOME=%~dp0mpj-v0_44
set PATH=%MPJ_HOME%\bin;%PATH%

echo Checking MPJ daemon...
call mpjdaemon.bat -boot 2>nul

call mpjrun.bat -np 4 -dev niodev -cp target\classes MainDistributed

endlocal