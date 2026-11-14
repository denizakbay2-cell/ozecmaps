#!/bin/sh
# Starts Payara Micro with settings from the environment, so the same image runs locally and in the cloud.
set -e

# Render, Railway and Heroku hand out DATABASE_URL=postgresql://user:password@host[:port]/database.
# DataSourceConfig needs a JDBC URL and separate credentials, so translate it unless DB_URL is already set.
if [ -z "$DB_URL" ] && [ -n "$DATABASE_URL" ]; then
    address="${DATABASE_URL#*://}"
    credentials="${address%%@*}"
    DB_URL="jdbc:postgresql://${address#*@}"
    DB_USER="${DB_USER:-${credentials%%:*}}"
    DB_PASSWORD="${DB_PASSWORD:-${credentials#*:}}"
    export DB_URL DB_USER DB_PASSWORD
fi

# Platforms such as Render choose the port through $PORT. post-boot.asadmin switches on gzip for JSON and text.
exec /opt/payara/entrypoint.sh --deploymentDir /opt/payara/deployments --port "${PORT:-8080}" \
    --postbootcommandfile /opt/payara/post-boot.asadmin "$@"
