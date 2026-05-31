@echo off
REM Windows launcher — double-click in Explorer, or run from cmd/PowerShell.
cd /d "%~dp0"
where py >nul 2>nul && (py setup_wizard.py & goto :eof)
python setup_wizard.py
