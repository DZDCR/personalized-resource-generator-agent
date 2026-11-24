param(
    [string]$Profile = ""
)

$ErrorActionPreference = 'Stop'
$appDir = (Get-Location).Path
Set-Location $appDir

Write-Host "Compiling backend and resolving runtime classpath..."
& mvn -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt" "-Dmdep.includeScope=runtime"
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

$cpFile = Join-Path $appDir 'target\cp.txt'
$cpText = [System.IO.File]::ReadAllText($cpFile, [System.Text.Encoding]::UTF8).Trim()
$classpath = (Join-Path $appDir 'target\classes') + ';' + $cpText

if ($Profile) {
    $env:SPRING_PROFILES_ACTIVE = $Profile
    Write-Host "Starting learning-agent-system with profile '$Profile' on http://localhost:8080 ..."
} else {
    Write-Host "Starting learning-agent-system on http://localhost:8080 ..."
}

& java -cp $classpath com.example.learning.LearningApplication
exit $LASTEXITCODE
