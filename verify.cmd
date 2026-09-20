@echo off
where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven is required. Install Maven 3.9+ and run this script again.
    exit /b 1
)

call mvn --batch-mode --no-transfer-progress clean test
exit /b %errorlevel%
