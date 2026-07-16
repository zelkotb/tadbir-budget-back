# Build the jar + Docker image on Windows, then copy a per-version release folder to the VM.
#
# The version is the Maven project version in the root pom.xml (<project><version>) - the single
# place you set it manually. Run from the repo root (D:\projects\tadbir-budget-back) in PowerShell:
#
#   ./deploy/build-and-ship.ps1
#
# It ships everything to  <VmTarget>:<RemoteBase>/tadbir-<version>/  (created, and overwritten
# if it already exists). You are prompted for the VM (root) SSH password during the copy.
# Then, on the VM:  cd tadbir-<version> && bash deploy.sh
param(
    [string]$VmTarget   = "root@192.168.1.203",
    [string]$RemoteBase = "/opt"
)
$ErrorActionPreference = "Stop"

# Repo root = parent of this script's folder
Set-Location (Split-Path $PSScriptRoot -Parent)

# --- version comes from pom.xml <project><version> (single source of truth) ---
if (-not (Test-Path "pom.xml")) { throw "pom.xml not found at repo root" }
[string]$Version = ([xml](Get-Content -Raw "pom.xml")).project.version
$Version = $Version.Trim()
if ([string]::IsNullOrWhiteSpace($Version)) { throw "Could not read <version> from pom.xml" }
Write-Host ">> Version (from pom.xml): $Version" -ForegroundColor Cyan

# Make sure a .env exists to ship (create from the example if missing)
if (-not (Test-Path ".env")) {
    Write-Host "No .env found - creating one from .env.example. Review it before real use." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

# Fresh staging folder named after the release
$release = "tadbir-$Version"
$stage   = Join-Path "dist" $release
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

Write-Host ">> [1/4] Building jar (mvn -DskipTests clean package) ..." -ForegroundColor Cyan
mvn -B -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

Write-Host ">> [2/4] Building image tadbir-backend:$Version ..." -ForegroundColor Cyan
docker build -t "tadbir-backend:$Version" .
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host ">> [3/4] Saving image and staging files into $stage ..." -ForegroundColor Cyan
docker save -o (Join-Path $stage "tadbir-backend-$Version.tar") "tadbir-backend:$Version"
if ($LASTEXITCODE -ne 0) { throw "docker save failed" }
Copy-Item "docker-compose.yml", ".env", "deploy/deploy.sh", "deploy/install-docker.sh" -Destination $stage

Write-Host ">> [4/4] Copying $release to ${VmTarget}:${RemoteBase}/ (enter the VM password when prompted) ..." -ForegroundColor Cyan
scp -r $stage "${VmTarget}:${RemoteBase}/"
if ($LASTEXITCODE -ne 0) { throw "scp failed" }

Write-Host ""
Write-Host ">> Shipped to ${VmTarget}:${RemoteBase}/$release" -ForegroundColor Green
Write-Host "   On the VM:"
Write-Host "     cd ${RemoteBase}/$release"
Write-Host "     sed -i 's/\r//' install-docker.sh deploy.sh   # first time / after each copy"
Write-Host "     bash install-docker.sh   # first time only, then: newgrp docker"
Write-Host "     chmod 600 .env"
Write-Host "     bash deploy.sh"
