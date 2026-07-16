#!/usr/bin/env bash
#
# Install Docker Engine + Compose on Rocky Linux, and prepare the host.
# Run ON THE VM (192.168.1.203):  bash install-docker.sh
#
set -euo pipefail

echo ">> Installing Docker CE on Rocky Linux ..."
sudo dnf -y install dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo systemctl enable --now docker

echo ">> Configuring container log rotation ..."
sudo mkdir -p /etc/docker
echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"5"}}' | sudo tee /etc/docker/daemon.json >/dev/null
sudo systemctl restart docker

echo ">> Creating host log directories under /opt/log (with the container users' ownership) ..."
sudo mkdir -p /opt/log/postgres /opt/log/tadbir-budget-backend /opt/log/tadbir-budget-frontend
sudo chown -R 999:999   /opt/log/postgres                # postgres image uid
sudo chown -R 1001:1001 /opt/log/tadbir-budget-backend   # backend non-root uid (Dockerfile)
sudo chmod -R 777       /opt/log/tadbir-budget-frontend   # image uids vary

echo ">> Opening firewall: 80 (frontend). Backend stays localhost-only ..."
if systemctl is-active --quiet firewalld; then
  sudo firewall-cmd --permanent --add-port=80/tcp     # frontend
  sudo firewall-cmd --reload
fi

echo ">> Allowing current user to run docker without sudo ..."
sudo usermod -aG docker "$USER"

docker --version
docker compose version || true
echo ">> Done. Log out and back in (or run: newgrp docker) so the docker group takes effect."
