param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Use Java 21 so all group members run the same Java version.
$deliveryJavaCandidates = @()
if ($env:JAVA_HOME) { $deliveryJavaCandidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$deliveryJavaCandidates += 'C:\Program Files\Java\jdk-21\bin\java.exe'
$deliveryJavaCandidates += 'C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe'
$deliveryJava = $deliveryJavaCandidates | Where-Object {
  $deliveryRelease = Join-Path (Split-Path (Split-Path $_ -Parent) -Parent) 'release'
  (Test-Path -LiteralPath $_) -and (Test-Path -LiteralPath $deliveryRelease) -and
    [bool](Select-String -LiteralPath $deliveryRelease -Pattern '^JAVA_VERSION="21[."]' -Quiet)
} | Select-Object -First 1
if (-not $deliveryJava) {
  throw 'Java 21 was not found. Install Java 21 and set JAVA_HOME to its folder.'
}
$deliveryJar = Join-Path $PSScriptRoot 'delivery-app.jar'
if (-not (Test-Path -LiteralPath $deliveryJar)) {
  throw 'delivery-app.jar was not found. Run Build-Delivery.ps1 first.'
}

# The demo binds to this computer. Cloud hosting is a Place holder.
Write-Host ''
Write-Host "Delivery app: http://localhost:$Port" -ForegroundColor Cyan
Write-Host 'Demo account: ken / Delivery123!'
Write-Host 'Second worker: mei / Delivery123!'
Write-Host 'Place holder: demo users and shared team data must be replaced before integration.'
Write-Host 'Keep this window open. Press Ctrl+C to stop the app.'
Write-Host ''
& $deliveryJava '-jar' $deliveryJar "--server.port=$Port" '--server.address=127.0.0.1' '--spring.profiles.active=demo'
if ($LASTEXITCODE -ne 0) { throw 'The Delivery app stopped with an error. Check the message above.' }

