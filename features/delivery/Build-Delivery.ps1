$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$deliveryJavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Java\jdk-21' }
$deliveryRelease = Join-Path $deliveryJavaHome 'release'
if (-not (Test-Path -LiteralPath $deliveryRelease) -or -not (Select-String -LiteralPath $deliveryRelease -Pattern '^JAVA_VERSION="21[."]' -Quiet)) {
  $deliveryJavaHome = 'C:\Program Files\Java\jdk-21'
}
if (-not (Test-Path -LiteralPath (Join-Path $deliveryJavaHome 'bin\javac.exe'))) {
  throw 'A Java 21 JDK is needed to build. Set JAVA_HOME to its folder.'
}
$env:JAVA_HOME = $deliveryJavaHome
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js 24 to build the React interface.' }

$deliveryTools = Join-Path $PSScriptRoot '.tools'
$deliveryMaven = Join-Path $deliveryTools 'apache-maven-3.9.11\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $deliveryMaven)) {
  New-Item -ItemType Directory -Path $deliveryTools -Force | Out-Null
  $deliveryArchive = Join-Path $deliveryTools 'apache-maven-3.9.11-bin.zip'
  $deliveryDownload = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
  Invoke-WebRequest -Uri $deliveryDownload -OutFile $deliveryArchive -UseBasicParsing
  $deliveryChecksum = (Invoke-WebRequest -Uri ($deliveryDownload + '.sha512') -UseBasicParsing).Content.Trim().Split()[0]
  if ((Get-FileHash -LiteralPath $deliveryArchive -Algorithm SHA512).Hash -ine $deliveryChecksum) {
    throw 'The Maven download did not pass its checksum check.'
  }
  Expand-Archive -LiteralPath $deliveryArchive -DestinationPath $deliveryTools
}

Push-Location (Join-Path $PSScriptRoot 'frontend')
try {
  & npm.cmd ci --cache ..\.cache\npm --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'Frontend package download failed.' }
  & npm.cmd run build
  if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

Push-Location (Join-Path $PSScriptRoot 'backend')
try {
  # Run backend checks before creating the shared runnable file.
  & $deliveryMaven '-Dmaven.repo.local=../.cache/m2' clean package
  if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
} finally { Pop-Location }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'backend\target\delivery-app.jar') -Destination (Join-Path $PSScriptRoot 'delivery-app.jar') -Force
Write-Host 'Build passed. Double-click Start Delivery App.cmd to run.' -ForegroundColor Green

