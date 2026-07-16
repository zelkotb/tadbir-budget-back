#!/usr/bin/env bash
#
# Deploy the backend on the VM (Rocky Linux). Run it from inside the version folder that
# build-and-ship.ps1 created (e.g. ~/tadbir-1.0.0), which holds docker-compose.yml, .env and
# the image tarball.
#
#   bash deploy.sh                       # auto-detects the tadbir-backend-*.tar in this folder
#   bash deploy.sh tadbir-backend-1.0.0.tar
#
# The image version is derived from the tarball name, so you never set it by hand on the VM.
set -euo pipefail
cd "$(dirname "$0")"

IMAGE_TAR="${1:-$(ls -t tadbir-backend-*.tar* 2>/dev/null | head -n1 || true)}"

[ -f docker-compose.yml ] || { echo "ERROR: docker-compose.yml not found in $(pwd)"; exit 1; }
[ -f .env ]               || { echo "ERROR: .env not found in $(pwd)"; exit 1; }
[ -n "$IMAGE_TAR" ] && [ -f "$IMAGE_TAR" ] || { echo "ERROR: no image tarball (tadbir-backend-*.tar) found in $(pwd)"; exit 1; }

# tadbir-backend-1.0.0.tar / .tar.gz  ->  1.0.0
VERSION="$(basename "$IMAGE_TAR" | sed -E 's/^tadbir-backend-(.+)\.tar(\.gz)?$/\1/')"

echo ">> Loading backend image from $IMAGE_TAR (version $VERSION) ..."
docker load -i "$IMAGE_TAR"   # auto-detects plain .tar or gzip .tar.gz

echo ">> Starting Postgres + backend ..."
# BACKEND_VERSION (from the tarball) overrides any value in .env, so the right image tag is used.
BACKEND_VERSION="$VERSION" docker compose up -d

echo ">> Status:"
BACKEND_VERSION="$VERSION" docker compose ps
echo ">> Tail logs with:  docker compose logs -f backend"
echo ">> Backend + Postgres are up. Health: curl -s http://127.0.0.1:8080/actuator/health"
