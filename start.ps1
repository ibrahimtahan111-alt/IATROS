# Starts IATROS locally: backend (dev profile, http://localhost:8080) and frontend (http://localhost:5173).
# Each server runs in its own window; close a window to stop that server.

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path $envFile)) {
    Write-Host "Missing $envFile. Copy .env.example to .env and fill in IATROS_DB_PASSWORD." -ForegroundColor Red
    exit 1
}

# Child windows inherit these variables; values never appear on a command line.
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { return }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim().Trim('"').Trim("'")
    if ($value -ne '') { Set-Item -Path "Env:$name" -Value $value }
}

function Test-PortListening([int] $port) {
    $null -ne (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

function Start-ServerWindow([string] $title, [string] $directory, [string] $command) {
    $script = "`$Host.UI.RawUI.WindowTitle = '$title'; Set-Location '$directory'; $command"
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $script
}

if (Test-PortListening 8080) {
    Write-Host 'Backend already running on port 8080.' -ForegroundColor Yellow
} else {
    Write-Host 'Starting backend...'
    Start-ServerWindow 'IATROS backend' (Join-Path $root 'backend') ".\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'"
}

if (Test-PortListening 5173) {
    Write-Host 'Frontend already running on port 5173.' -ForegroundColor Yellow
} else {
    $frontend = Join-Path $root 'frontend'
    $install = if (Test-Path (Join-Path $frontend 'node_modules')) { '' } else { 'npm install; ' }
    Write-Host 'Starting frontend...'
    Start-ServerWindow 'IATROS frontend' $frontend "${install}npm run dev"
}

Write-Host 'Waiting for the backend to become healthy (up to 3 minutes)...'
$deadline = (Get-Date).AddMinutes(3)
$healthy = $false
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}

if ($healthy) {
    Write-Host 'IATROS is running at http://localhost:5173' -ForegroundColor Green
    Start-Process 'http://localhost:5173'
} else {
    Write-Host 'Backend did not become healthy in time. Check the "IATROS backend" window for errors.' -ForegroundColor Red
    exit 1
}
