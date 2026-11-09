param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Stop the app first so the database and proof files use one checkpoint.
$deliveryRunning = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($deliveryRunning) { throw 'Stop the Delivery app before backing up its database and proof files.' }
$deliveryData = Join-Path $PSScriptRoot 'data'
if (-not (Test-Path -LiteralPath $deliveryData)) { throw 'No local data folder was found.' }
$deliveryBackups = Join-Path $PSScriptRoot 'backups'
New-Item -ItemType Directory -Path $deliveryBackups -Force | Out-Null
$deliveryBackup = Join-Path $deliveryBackups ('delivery-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
Compress-Archive -LiteralPath $deliveryData -DestinationPath $deliveryBackup
Write-Host "Backup saved: $deliveryBackup"
Write-Host 'Place holder: the hosting owner must provide scheduled protected backups and retention.'

