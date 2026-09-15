@echo off
setlocal
echo Stopping FitMind frontend and backend...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$temp=[IO.Path]::GetTempPath(); foreach($name in @('fitmind-frontend.pid','fitmind-backend.pid')){ $file=Join-Path $temp $name; if(Test-Path -LiteralPath $file){ $processId=[int](Get-Content -LiteralPath $file); taskkill.exe /PID $processId /T /F | Out-Null; Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue } }"
echo FitMind processes were requested to stop.
ping 127.0.0.1 -n 3 >nul
exit /b 0
