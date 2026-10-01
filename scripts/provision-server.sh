#!/usr/bin/env bash
# Prepare a fresh Ubuntu 24.04 host to run VisionOne. Run it once, as root, on the new server:
#
#   ssh root@<ip> 'bash -s' < scripts/provision-server.sh
#
# Idempotent: running it twice changes nothing the second time. It does NOT deploy the app - that is
# `make deploy`, after DNS resolves. This only makes the box ready and closed.

set -euo pipefail

say() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

[ "$(id -u)" -eq 0 ] || { echo "run as root"; exit 1; }

say "System packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get upgrade -y -qq
apt-get install -y -qq ca-certificates curl gnupg git make ufw fail2ban unattended-upgrades

say "Timezone: UTC"
# The server stays UTC on purpose. Each practice's timezone lives in the organization row
# (THRIVE is America/New_York), so the app converts per tenant and the host never has an opinion.
timedatectl set-timezone UTC

say "Swap"
# 4 GB of RAM running a JVM, Keycloak and Postgres has no headroom for a spike. Swap is not a
# substitute for memory, it is what stops the kernel killing Postgres to save the JVM.
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  # Prefer reclaiming cache over swapping a live heap out from under the JVM.
  sysctl -q -w vm.swappiness=10
  grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf
else
  echo "already present"
fi

say "Docker Engine + Compose plugin"
if ! command -v docker >/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
else
  echo "already installed"
fi

say "Log rotation for containers"
# Without this a chatty container fills the disk and takes Postgres down with it.
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl restart docker

say "Firewall"
# Only SSH and the two ports Caddy needs. Postgres, Keycloak and the API publish nothing, so they
# are reachable on the internal Docker network and nowhere else.
#
# Worth knowing: Docker writes its own iptables rules and a published port bypasses UFW. That is
# not a hole here precisely BECAUSE only 80 and 443 are published - but it does mean adding
# "ports:" to a service in the compose file opens it to the internet regardless of what ufw says.
ufw --force reset >/dev/null
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp  comment 'SSH' >/dev/null
ufw allow 80/tcp  comment 'HTTP - Lets Encrypt validation and the redirect to HTTPS' >/dev/null
ufw allow 443/tcp comment 'HTTPS' >/dev/null
ufw --force enable >/dev/null
ufw status verbose | sed 's/^/    /'

say "SSH brute-force protection"
systemctl enable --now fail2ban
cat > /etc/fail2ban/jail.d/sshd.local <<'JAIL'
[sshd]
enabled = true
maxretry = 5
bantime = 1h
JAIL
systemctl restart fail2ban

say "Key-only SSH"
if [ -s /root/.ssh/authorized_keys ]; then
  sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
  sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
  # Cloud images often re-enable passwords from a drop-in that wins over sshd_config.
  mkdir -p /etc/ssh/sshd_config.d
  printf 'PasswordAuthentication no\n' > /etc/ssh/sshd_config.d/99-no-passwords.conf
  sshd -t && systemctl reload ssh
  echo "passwords disabled - your key is the only way in, do not lose it"
else
  echo "SKIPPED: no key in /root/.ssh/authorized_keys, so passwords are being left enabled"
  echo "         rather than locking you out. Add your key, then re-run this script."
fi

say "Unattended security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'CONF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
CONF

say "Ready"
printf '  docker : %s\n' "$(docker --version)"
printf '  compose: %s\n' "$(docker compose version --short)"
printf '  memory : %s\n' "$(free -h | awk '/^Mem:/{print $2" total, "$7" available"}')"
printf '  swap   : %s\n' "$(free -h | awk '/^Swap:/{print $2}')"
printf '  disk   : %s\n' "$(df -h / | awk 'NR==2{print $4" free of "$2}')"
printf '  ip     : %s\n' "$(curl -s --max-time 5 https://api.ipify.org || echo unknown)"
cat <<'NEXT'

Next, in order:
  1. Point app.visiondigitallab.com at the IP above with an A record. Wait for it to resolve.
     Check from anywhere:  dig +short app.visiondigitallab.com
  2. git clone the repo onto this box, cd into it.
  3. cp infra/.env.prod.example infra/.env.prod  and fill in every blank with NEW secrets:
       openssl rand -base64 24
  4. make deploy

Do not skip the wait in step 1. Caddy asks Let's Encrypt for the certificate the first time it
starts, and that request fails if the name does not yet resolve to this machine.
NEXT
