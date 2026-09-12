#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="/opt/homebrew/opt/postgresql@17/bin:$JAVA_HOME/bin:$PATH"
mkdir -p .local
if [ ! -f .local/postgres/PG_VERSION ]; then
    initdb -D .local/postgres -U cabinet --auth=trust >/dev/null
fi
if ! pg_ctl -D .local/postgres status >/dev/null 2>&1; then
    pg_ctl -D .local/postgres -l .local/postgres.log -o '-h 127.0.0.1 -p 55432 -k /tmp' start
fi
if ! psql -h 127.0.0.1 -p 55432 -U cabinet -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='cabinet'" | grep -q 1; then
    createdb -h 127.0.0.1 -p 55432 -U cabinet cabinet
fi
export DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/cabinet'
export DATABASE_USERNAME='cabinet'
export DATABASE_PASSWORD=''
# The migration history assumes a pre-existing schema. Local empty databases
# use entity mappings until the backend has a complete baseline migration.
export FLYWAY_ENABLED=false
export SPRING_JPA_HIBERNATE_DDL_AUTO=update
exec sh mvnw spring-boot:run
