@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Run-Delivery.ps1"
if errorlevel 1 pause

