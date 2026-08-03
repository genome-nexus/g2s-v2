# Deploying G2S

Everything runs in Docker — the databases (MySQL, MongoDB) and the three
Java services alike. No native JDK/Maven install, no manual keystore
generation, no BLAST sidecar shims. Works the same way on Linux or Windows.

## Prerequisites

- Docker + Docker Compose (Docker Desktop on Windows)

## Steps

Run everything below from the `g2s/` folder (wherever you clone the repo).

### 1. Get the code

```bash
git clone https://github.com/genome-nexus/g2s-v2.git g2s   # or git pull if already cloned
cd g2s
```

### 2. Build and start everything

```bash
docker compose build
docker compose up -d
```

This brings up, in order:

- `mysql` — runs `yichuan0712/g2s-pdb2026-db`, a `mariadb:10.0` image with the
  `pdb_2026` dump baked in as a `/docker-entrypoint-initdb.d/` script, so the
  first start imports it automatically (takes a few minutes). The other
  services wait on its healthcheck before starting, so you don't need to
  watch the logs by hand.
- `mongo`
- `blast-index-init` — a one-shot container that extracts the pre-built BLAST
  index (`yichuan0712/g2s-blast-index`) into `./workdir/`. Runs once and
  exits; safe to re-run (`docker compose up -d` again), it no-ops if the
  index is already there.
- `pdb-alignment-api` (:8081), `pdb` (:8082), `pdb-alignment-web` (:5443,
  HTTPS with a self-signed cert baked in at build time) — built from the same
  image (`docker/Dockerfile.app`), which also installs the exact pinned
  BLAST+ 2.16.0 `blastp` binary the "search by protein sequence" feature
  needs.

(`mysql-old` is a legacy archive DB that nothing in the app connects to — not
started by default. `docker compose --profile legacy up -d` if you need it.)

Verify the MySQL import finished:
```bash
docker exec pdb-mariadb mysql -u cbio -pcbio pdb_2026 -e "SELECT COUNT(*) FROM pdb_seq_alignment;"
```
Expect a large row count (millions).

### 3. Quick check

```
http://<host>:8081/swagger-ui.html
http://<host>:8082/swagger-ui.html
https://<host>:5443/   (accept the self-signed certificate)
```

## Rebuilding after a code change

```bash
docker compose build pdb-alignment-api pdb pdb-alignment-web
docker compose up -d
```

## Stopping

```bash
docker compose down
```
Data lives in host-mounted folders (`./mysql_data`, `./mongo_data`,
`./workdir`), not Docker volumes, so `down` leaves it intact — delete those
folders by hand if you want a clean slate.
