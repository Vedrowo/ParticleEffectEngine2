@echo off
setlocal

set MPJ_HOME=%~dp0mpj-v0_44
set PATH=%MPJ_HOME%\bin;%PATH%

call mpjrun.bat -np 4 -cp target\classes HelloMPI

endlocal