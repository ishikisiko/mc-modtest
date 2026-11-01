@echo off
rem Starts DevHost without a console window. To start it at logon, put a shortcut to this
rem file in shell:startup. DevHost logs to devhost.log next to this file.
rem JAVA must be a JDK (source-file mode needs the compiler): set it below if javaw is not on PATH.
set "JAVA=javaw"
cd /d "%~dp0"
set "DOWNLOAD_PROXIES=127.0.0.1:7897,127.0.0.1:7890"
start "DevHost" "%JAVA%" "-Ddevhost.downloadProxies=%DOWNLOAD_PROXIES%" DevHost.java devhost.properties
