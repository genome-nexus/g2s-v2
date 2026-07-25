# Deploying G2S

Docker runs the databases (MySQL, MongoDB). The three Java services run as
native processes on the host, not in Docker. `blastp`/`makeblastdb` run via a
Docker sidecar container (`ncbi/blast` image) instead of a native BLAST+
install. Works the same way on Linux or Windows — commands below are given
for both where they differ.

## Prerequisites

- Docker + Docker Compose (Docker Desktop on Windows)
- JDK 8 and Maven (to build the jars/war)

## Steps

Run everything below from the `g2s/` folder (adjust `DEPLOY_ROOT` /
`C:\...\g2s` to wherever you clone the repo).

### 1. Get the code

```bash
git clone https://github.com/genome-nexus/g2s-v2.git g2s   # or git pull if already cloned
cd g2s
```

### 2. Start the databases

```bash
docker compose up -d mysql mongo
```

`mysql` now runs `yichuan0712/g2s-pdb2026-db` — a `mariadb:10.0` image with
the `pdb_2026` dump baked in as a `/docker-entrypoint-initdb.d/` script, so
the first start imports it automatically (takes a few minutes; watch with
`docker logs -f pdb-mariadb`). Nothing to import by hand.

(`mysql-old` in `docker-compose.yml` is a legacy archive DB that nothing in
the app connects to — no need to start it.)

Verify the import finished:
```bash
docker exec pdb-mariadb mysql -u cbio -pcbio pdb_2026 -e "SELECT COUNT(*) FROM pdb_seq_alignment;"
```
Expect a large row count (millions).

### 3. Get the BLAST index

```bash
docker pull yichuan0712/g2s-blast-index:latest
docker create --name g2s-blast-index-tmp yichuan0712/g2s-blast-index:latest
docker cp g2s-blast-index-tmp:/blast-index.tar.gz .
docker rm g2s-blast-index-tmp
mkdir -p workdir
tar -xzf blast-index.tar.gz -C workdir
rm blast-index.tar.gz
```
(Same commands on Linux and Windows — `tar` ships with Windows 10/11.)

This creates `g2s/workdir/` with the BLAST index files in it
(`pdb_seqres.db.*` + `pdb_seqres.fasta`) — that's the index the "search by
protein sequence" feature runs `blastp` against.

### 4. Pull the BLAST image

```bash
docker pull ncbi/blast:2.16.0
```

### 5. Configure `application-local.properties`

This file is gitignored, so it won't exist after `git clone` — create it yourself:

`pdb-alignment-web/src/main/resources/application-local.properties`

Linux:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026?useSSL=false

workspace=/opt/g2s/workdir/
uploaddir=/opt/g2s/tmp/upload

blastp=/opt/g2s/yichuan_scripts/blastp-docker.sh
```

Windows:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026?useSSL=false

workspace=C:/path/to/g2s/workdir/
uploaddir=C:/path/to/g2s/tmp/upload

blastp=C:/path/to/g2s/yichuan_scripts/blastp-docker.cmd
```

Adjust the paths to match where you cloned the repo, and create the
`tmp/upload` folder if it doesn't exist.

> `pdb` and `pdb-alignment-api` have the same kind of file too, but you don't
> need to touch them for a standard setup like this. Only edit them if you
> later change the DB host, port, name, or credentials.

### 6. HTTPS keystore (for port 5443)

Not in git — generate a self-signed cert.

Linux:
```bash
mkdir -p tmp
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
"$JAVA_HOME/bin/keytool" -genkeypair -alias tomcat -storetype PKCS12 -keyalg RSA -keysize 2048 \
    -keystore pdb-alignment-web/src/main/resources/keystore.p12 -storepass 123456 -keypass 123456 -validity 3650 \
    -dname "CN=localhost, OU=Dev, O=G2S, L=NA, ST=NA, C=US"
```

Windows (PowerShell):
```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -alias tomcat -storetype PKCS12 -keyalg RSA -keysize 2048 `
    -keystore pdb-alignment-web\src\main\resources\keystore.p12 -storepass 123456 -keypass 123456 -validity 3650 `
    -dname "CN=localhost, OU=Dev, O=G2S, L=NA, ST=NA, C=US"
```

Consider a real cert + reverse proxy instead of self-signed for anything
reachable outside the machine.

### 7. Build

```bash
mvn clean package -DskipTests
```
(On Windows, run `. .\yichuan_scripts\env.ps1` first to put JDK 8 and Maven on `PATH`.)

### 8. Start the services

Linux:
```bash
./yichuan_scripts/start-services.sh
```
Logs land in `g2s/logs/*.log`. Stop with `./yichuan_scripts/stop-services.sh`.

Windows:
```powershell
.\yichuan_scripts\start-services.ps1
```
Opens three PowerShell windows, one per service. Stop with `.\yichuan_scripts\stop-services.ps1`.

Quick check:
```
http://<host>:8081/swagger-ui.html
http://<host>:8082/swagger-ui.html
https://<host>:5443/
```
