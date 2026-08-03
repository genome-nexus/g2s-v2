# Builds all 3 G2S Java services (pdb-alignment-api, pdb, pdb-alignment-web)
# into one image. Which service actually runs is picked by the `command:` of
# each docker-compose service that references this image - see
# docker-compose.yml.
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /build

COPY pom.xml .
COPY pdb pdb
COPY pdb-alignment-pipeline pdb-alignment-pipeline
COPY pdb-alignment-api pdb-alignment-api
COPY pdb-alignment-web pdb-alignment-web

# Same self-signed cert the README used to have you generate by hand; baked
# into the jar at build time since pdb-alignment-web loads it from the
# classpath (server.ssl.key-store: classpath:keystore.p12).
RUN keytool -genkeypair -alias tomcat -storetype PKCS12 -keyalg RSA -keysize 2048 \
    -keystore pdb-alignment-web/src/main/resources/keystore.p12 \
    -storepass 123456 -keypass 123456 -validity 3650 \
    -dname "CN=localhost, OU=Dev, O=G2S, L=NA, ST=NA, C=US"

RUN mvn -B clean package -DskipTests -pl pdb,pdb-alignment-api,pdb-alignment-web -am

# Rename to fixed names so the runtime stage doesn't hardcode each module's
# <version>, which would otherwise silently need updating here on every bump.
RUN mkdir -p /out \
    && cp pdb-alignment-api/target/pdb-alignment-api-*.jar /out/pdb-alignment-api.jar \
    && cp pdb/target/pdb-*.war /out/pdb.war \
    && cp pdb-alignment-web/target/pdb-alignment-web-*.jar /out/pdb-alignment-web.jar

FROM eclipse-temurin:8-jre-jammy AS runtime
WORKDIR /app

# Pin the exact BLAST+ version the old Docker-sidecar shim used
# (ncbi/blast:2.16.0) instead of `apt install ncbi-blast+`, which on
# Debian/Ubuntu resolves to 2.12.0 - four releases behind, and this feeds a
# science-facing feature where version drift can change results.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 ca-certificates curl \
    && curl -fsSL https://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/2.16.0/ncbi-blast-2.16.0+-x64-linux.tar.gz \
       -o /tmp/blast.tar.gz \
    && tar -xzf /tmp/blast.tar.gz -C /tmp \
    && cp /tmp/ncbi-blast-2.16.0+/bin/blastp /usr/local/bin/blastp \
    && rm -rf /tmp/blast.tar.gz /tmp/ncbi-blast-2.16.0+ \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/pdb-alignment-api.jar /app/pdb-alignment-api.jar
COPY --from=build /out/pdb.war /app/pdb.war
COPY --from=build /out/pdb-alignment-web.jar /app/pdb-alignment-web.jar

COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8081 8082 5443
ENTRYPOINT ["/entrypoint.sh"]
