@echo off
REM Build TraceWorthy.exe from traceworthy_launcher.py using PyInstaller.
REM Double-click this file. First build downloads tools and can take a few minutes.
cd /d "%~dp0"

echo Installing build tools (pyinstaller, pandas, matplotlib, fpdf2)...
python -m pip install --upgrade pyinstaller pandas matplotlib fpdf2 || goto :err

echo.
echo Building TraceWorthy.exe ...
pyinstaller --onefile --windowed --name TraceWorthy ^
  --paths analysis --paths google_voice ^
  --hidden-import analyze_calls --hidden-import gvoice_to_csv ^
  --hidden-import packet --hidden-import stats ^
  --collect-all pandas --collect-all matplotlib --collect-all fpdf ^
  traceworthy_launcher.py || goto :err

echo.
echo ============================================================
echo  Done. Your app is:  dist\TraceWorthy.exe
echo  Keep TraceWorthy.exe inside this TraceWorthy folder so it can
echo  find the analysis, google_voice and twilio subfolders.
echo ============================================================
pause
exit /b 0

:err
echo.
echo Build failed. Make sure Python is installed and on PATH.
pause
exit /b 1
