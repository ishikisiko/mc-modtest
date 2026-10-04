@echo off
rem Stops DevHost (the java/javaw process running DevHost.java). The game keeps running.
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' OR Name='java.exe'\" | Where-Object { $_.CommandLine -like '*DevHost.java*' } | ForEach-Object { Stop-Process -Id $_.ProcessId }"
