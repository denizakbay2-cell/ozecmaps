#!/bin/sh
# Starts Payara Micro with settings from the environment, so the same image runs locally and in the cloud.
set -e

# A URL may percent-encode reserved characters in the user and password (a "/" in the password becomes "%2F"); turn
# them back into the characters themselves, or the database rejects the credentials.
urldecode() {
    printf '%b' "$(printf '%s' "$1" | sed 's/%/\\x/g')"
}

# Render, Railway, Heroku and Neon hand out DATABASE_URL=postgresql://user:password@host[:port]/database[?params].
# DataSourceConfig needs a JDBC URL and separate credentials, so translate it unless DB_URL is already set. The
# user and password are URL-decoded; the "@" and ":" that separate them are never encoded, so the splits are safe on
# the still-encoded string. Neon adds channel_binding=require, which the PostgreSQL JDBC driver rejects; keep only
# sslmode, which it understands and which Neon needs.
if [ -z "$DB_URL" ] && [ -n "$DATABASE_URL" ]; then
    address="${DATABASE_URL#*://}"
    credentials="${address%%@*}"
    hostpath="${address#*@}"
    base="${hostpath%%\?*}"
    case "$hostpath" in
        *sslmode=require*) DB_URL="jdbc:postgresql://${base}?sslmode=require" ;;
        *) DB_URL="jdbc:postgresql://${base}" ;;
    esac
    DB_USER="${DB_USER:-$(urldecode "${credentials%%:*}")}"
    DB_PASSWORD="${DB_PASSWORD:-$(urldecode "${credentials#*:}")}"
    export DB_URL DB_USER DB_PASSWORD
fi

# Platforms such as Render choose the port through $PORT. post-boot.asadmin switches on gzip for JSON and text.
exec /opt/payara/entrypoint.sh --deploymentDir /opt/payara/deployments --port "${PORT:-8080}" \
    --postbootcommandfile /opt/payara/post-boot.asadmin "$@"
