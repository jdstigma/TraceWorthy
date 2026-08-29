@echo off
REM Build TraceWorthy-iPhone.exe from iphone_gui.py using PyInstaller.
REM Double-click this file. First build downloads tools and can take a few minutes.
cd /d "%~dp0"

echo Installing build tools + dependencies...
python -m pip install --upgrade pyinstaller -r requirements.txt || goto :err

echo.
echo Building TraceWorthy-iPhone.exe ...
pyinstaller --onefile --windowed --name TraceWorthy-iPhone ^
  --paths . --paths ..\analysis ^
  --hidden-import analyze_calls --hidden-import stats --hidden-import packet ^
  --hidden-import backup_locator --hidden-import callhistory_parser --hidden-import profile ^
  --collect-all pandas --collect-all matplotlib --collect-all fpdf ^
  --collect-submodules iphone_backup_decrypt --collect-all Crypto ^
  iphone_gui.py || goto :err

echo.
echo ============================================================
echo  Done. Your app is:  dist\TraceWorthy-iPhone.exe
echo  Keep it next to the iphone\ and analysis\ folders if you
echo  run from source; the .exe itself is self-contained.
echo ============================================================
pause
exit /b 0

:err
echo.
echo Build failed. Make sure Python is installed and on PATH.
pause
exit /b 1
