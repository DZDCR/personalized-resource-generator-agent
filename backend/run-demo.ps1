param()

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
Set-Location $root

Write-Host "Starting demo profile (H2, http://localhost:8080)..."
& (Join-Path $root 'run.ps1') -Profile demo
exit $LASTEXITCODE
