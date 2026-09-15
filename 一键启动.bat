@echo off
setlocal
set "FITMIND_ROOT=%~sdp0"
set "FITMIND_FRONTEND=%FITMIND_ROOT%frontend"
set "FITMIND_BACKEND=%FITMIND_ROOT%backend"

if not exist "%FITMIND_FRONTEND%\package.json" (
  echo [ERROR] Frontend package.json not found.
  pause
  exit /b 1
)

if not exist "%FITMIND_BACKEND%\pom.xml" (
  echo [ERROR] Backend pom.xml not found.
  pause
  exit /b 1
)

del /q "%TEMP%\fitmind-backend.pid" "%TEMP%\fitmind-frontend.pid" >nul 2>&1
echo Starting FitMind backend...
start "FitMind Backend" /D "%FITMIND_BACKEND%" powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$p=Start-Process -FilePath 'mvn.cmd' -ArgumentList 'spring-boot:run' -WorkingDirectory '%FITMIND_BACKEND%' -PassThru; [IO.File]::WriteAllText((Join-Path ([IO.Path]::GetTempPath()) 'fitmind-backend.pid'),$p.Id.ToString())"
echo Starting FitMind frontend...
start "FitMind Frontend" /D "%FITMIND_FRONTEND%" powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$p=Start-Process -FilePath 'pnpm.cmd' -ArgumentList 'run','dev' -WorkingDirectory '%FITMIND_FRONTEND%' -PassThru; [IO.File]::WriteAllText((Join-Path ([IO.Path]::GetTempPath()) 'fitmind-frontend.pid'),$p.Id.ToString())"

echo Frontend: http://127.0.0.1:5299/
echo Backend:  http://127.0.0.1:8080/
ping 127.0.0.1 -n 6 >nul
start "" "http://127.0.0.1:5299/"
exit /b 0
