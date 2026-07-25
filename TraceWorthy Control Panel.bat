@echo off
REM Double-click to open the TraceWorthy Control Panel.
cd /d "%~dp0"
python traceworthy_launcher.py
if errorlevel 1 pause
