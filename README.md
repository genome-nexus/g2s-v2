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
  first start imports it automatically (takes a while - see the note below).
  The other services wait on its healthcheck before starting, so you don't
  need to watch the logs by hand.
- `mongo`
- `blast-index-init` — a one-shot container that extracts the pre-built BLAST
  index (`yichuan0712/g2s-blast-index`) into `./workdir/`. Runs once and
  exits; safe to re-run (`docker compose up -d` again), it no-ops if the
  index is already there.
- `pdb-alignment-web` (:5443, HTTPS with a self-signed cert baked in at build
  time) — built from `docker/Dockerfile.app`, which also installs the exact
  pinned BLAST+ 2.16.0 `blastp` binary the "search by protein sequence"
  feature needs. This is the only one of the three Java services actually
  used by cbioportal-frontend/Genome Nexus today.

Not started by default:
- `mysql-old` — legacy archive DB, nothing in the app connects to it.
- `pdb-alignment-api` (:8081) and `pdb` (:8082) — not currently used by
  anything; kept running-capable rather than deleted in case something still
  needs their raw APIs directly.

Start either with `docker compose --profile legacy up -d` (`mysql-old`) or
`docker compose --profile unused-apis up -d` (`pdb-alignment-api` + `pdb`).

First start only - MySQL is importing ~29M rows and can take **well over the
"a few minutes" you'd guess** (30+ minutes is normal on Docker
Desktop/WSL2). The healthcheck has a generous grace period for this, so the
Java services will just wait rather than crash-loop; watch progress with
`docker logs -f pdb-mariadb`. Every start after that is fast (data already
on disk).

Verify the MySQL import finished:
```bash
docker exec pdb-mariadb mysql -u cbio -pcbio pdb_2026 -e "SELECT COUNT(*) FROM pdb_seq_alignment;"
```
Expect a large row count (millions).

### 3. Quick check

```
https://<host>:5443/   (accept the self-signed certificate)
```
Only started `--profile unused-apis`:
```
http://<host>:8081/swagger-ui.html
http://<host>:8082/swagger-ui.html
```

## Starting it again later

Once you've done the steps above once, you don't repeat them - from the same
`g2s/` folder:

```bash
docker compose up -d
```
That's it. The image is already built and the databases already have their
data on disk (`./mysql_data`, `./mongo_data`, `./workdir` persist across
`docker compose down`/restarts), so this comes back up in seconds, not
minutes. You only need `docker compose build` again after pulling a code
change - see below.

## Rebuilding after a code change

```bash
docker compose build pdb-alignment-web pdb-alignment-api pdb
docker compose up -d
```

## Stopping

```bash
docker compose down
```
Data lives in host-mounted folders (`./mysql_data`, `./mongo_data`,
`./workdir`), not Docker volumes, so `down` leaves it intact — delete those
folders by hand if you want a clean slate.
