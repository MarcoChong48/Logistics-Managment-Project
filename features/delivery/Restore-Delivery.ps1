param(
  [Parameter(Mandatory=$true)][string]$BackupFile,
  [string]$TargetFolder = 'restore-check'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$deliveryRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$deliveryRestore = [System.IO.Path]::GetFullPath((Join-Path $deliveryRoot $TargetFolder))
if (-not $deliveryRestore.StartsWith($deliveryRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw 'The restore test folder must stay inside this Delivery project.'
}
if (Test-Path -LiteralPath $deliveryRestore) { throw 'The restore test folder already exists. Choose a new folder; existing data is never replaced.' }
if (-not (Test-Path -LiteralPath $BackupFile)) { throw 'The backup file was not found.' }

# Restore into a new folder. Do not overwrite the working database.
Expand-Archive -LiteralPath $BackupFile -DestinationPath $deliveryRestore
Write-Host "Restored into: $deliveryRestore"
Write-Host 'Compare the order, custody, stock and private proof records before using this copy.'
