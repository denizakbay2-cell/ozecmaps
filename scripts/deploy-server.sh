#!/bin/sh
# Publishes ozecmaps on an Ubuntu server reachable over SSH. Written for an Oracle Cloud Always Free ARM machine, works on
# any Ubuntu 22.04 or 24.04 server with the same processor type as this computer (the image is built here):
#   1. installs Docker on the server the first time and opens HTTP and HTTPS in its firewall,
#   2. builds the application image here and sends it over,
#   3. copies the production stack (deploy/), the national graph (data/turkey-car.graph) and creates the secrets,
#   4. starts PostgreSQL, the application and Caddy, which fetches the HTTPS certificate.
#
#   scripts/deploy-server.sh ubuntu@203.0.113.10                         # https://ozecmaps.203-0-113-10.sslip.io
#   scripts/deploy-server.sh ubuntu@203.0.113.10 ozecmaps.example.com    # a domain whose DNS points at the server
#
# Run it again after a change: the graph is sent only when it changed, data and secrets stay. SSH_KEY picks the key
# (default ~/.ssh/ozecmaps_oracle). The server's cloud firewall must allow TCP 80 and 443 (see README).
set -eu

if [ $# -lt 1 ]; then
    echo "usage: $0 user@host [domain]" >&2
    exit 1
fi
target=$1
host=${target#*@}
site=${2:-ozecmaps.$(printf '%s' "$host" | tr . -).sslip.io}
root=$(cd "$(dirname "$0")/.." && pwd)
key=${SSH_KEY:-$HOME/.ssh/ozecmaps_oracle}
remote=/opt/ozecmaps
ssh_opts="-i $key -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=30"

on_server() {
    # shellcheck disable=SC2086,SC2029
    ssh $ssh_opts "$target" "$@"
}

copy() {
    # shellcheck disable=SC2086,SC2029
    scp -q $ssh_opts "$1" "$target:$2"
}

case "$(on_server uname -m)" in
    aarch64 | arm64) server_arch=arm64 ;;
    x86_64 | amd64) server_arch=amd64 ;;
    *) echo "unknown processor type on the server" >&2; exit 1 ;;
esac
[ -s "$root/data/turkey-car.graph" ] || echo "note: data/turkey-car.graph is missing; car routes outside Kadıköy will use OSRM"

echo "1/5 preparing the server (Docker, firewall) ..."
on_server "sudo sh -s" <<'EOF'
set -e
if ! command -v docker >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    # a fresh machine often runs its first automatic updates; wait for them instead of failing
    apt-get -o DPkg::Lock::Timeout=600 update -qq
    apt-get -o DPkg::Lock::Timeout=600 install -y -qq docker.io docker-compose-v2 >/dev/null
    systemctl enable --now docker >/dev/null
fi
# Oracle's Ubuntu images reject everything but SSH; open HTTP and HTTPS now and after reboots
for rule in "-p tcp --dport 80" "-p tcp --dport 443" "-p udp --dport 443"; do
    # shellcheck disable=SC2086,SC2029
    iptables -C INPUT $rule -j ACCEPT 2>/dev/null || iptables -I INPUT $rule -j ACCEPT
done
if [ -f /etc/iptables/rules.v4 ] && ! grep -q -- "--dport 443" /etc/iptables/rules.v4; then
    sed -i '/-A INPUT -j REJECT/i -A INPUT -p tcp -m state --state NEW -m tcp --dport 80 -j ACCEPT\n-A INPUT -p tcp -m state --state NEW -m tcp --dport 443 -j ACCEPT\n-A INPUT -p udp -m udp --dport 443 -j ACCEPT' /etc/iptables/rules.v4
fi
mkdir -p /opt/ozecmaps/data
chown -R "${SUDO_USER:-root}" /opt/ozecmaps
EOF

echo "2/5 building the application image ($server_arch) ..."
docker build -q -t city-navigator-app "$root" >/dev/null
image_arch=$(docker image inspect city-navigator-app --format '{{.Architecture}}')
if [ "$image_arch" != "$server_arch" ]; then
    echo "the image was built for $image_arch but the server is $server_arch; deploy from a $server_arch machine" >&2
    exit 1
fi

echo "3/5 copying the stack, the national graph and the secrets ..."
copy "$root/deploy/compose.yml" "$remote/compose.yml"
copy "$root/deploy/Caddyfile" "$remote/Caddyfile"
if [ -s "$root/data/turkey-car.graph" ]; then
    here_sum=$(shasum -a 256 "$root/data/turkey-car.graph" | cut -d ' ' -f 1)
    there_sum=$(on_server "sha256sum $remote/data/turkey-car.graph 2>/dev/null | cut -d ' ' -f 1" || true)
    if [ "$here_sum" != "$there_sum" ]; then
        echo "    sending the national graph (about 290 MB) ..."
        copy "$root/data/turkey-car.graph" "$remote/data/turkey-car.graph"
    fi
fi
# secrets are made once on the server and never leave it; the site address follows the arguments
on_server "sh -s" <<EOF
set -e
cd $remote
touch .env && chmod 600 .env
grep -q '^DB_PASSWORD=' .env || echo "DB_PASSWORD=\$(openssl rand -hex 24)" >> .env
grep -q '^ADMIN_TOKEN=' .env || echo "ADMIN_TOKEN=\$(openssl rand -hex 16)" >> .env
sed -i '/^SITE_ADDRESS=/d' .env && echo "SITE_ADDRESS=$site" >> .env
EOF

echo "4/5 sending the application image ..."
docker save city-navigator-app | gzip -1 | on_server "gunzip | sudo docker load -q"

echo "5/5 starting ..."
on_server "cd $remote && sudo docker compose up -d --remove-orphans && sudo docker image prune -f >/dev/null"
printf "waiting for https://%s " "$site"
for _ in $(seq 1 60); do
    if curl -fsS -o /dev/null --max-time 5 "https://$site/api/ping" 2>/dev/null; then
        echo
        echo "ozecmaps is live: https://$site"
        echo "admin token for reporting incidents: ssh into the server and run: grep ADMIN_TOKEN $remote/.env"
        exit 0
    fi
    printf "."
    sleep 5
done
echo
echo "the site did not answer within 5 minutes; logs: ssh $target 'cd $remote && sudo docker compose logs --tail 50'" >&2
exit 1
