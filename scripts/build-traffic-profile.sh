#!/bin/sh
# Rebuilds src/main/resources/traffic/istanbul-congestion.csv.gz, the usual traffic of Istanbul by place, day type and
# hour (ADR-0009), from the hourly traffic density data of Istanbul Metropolitan Municipality:
# https://data.ibb.gov.tr/dataset/3ee6d744-5da2-40c8-9cd6-0e3e41f1928f (one CSV of about 140 MB per month).
#
#   scripts/build-traffic-profile.sh                 # the two months the bundled profile was made from
#   scripts/build-traffic-profile.sh URL [URL ...]   # other months: the CSV links on the data set page
#
# The data is published under the IBB Open Data License; the profile keeps the attribution in its header.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
data="$root/data"
agent="city-navigator/0.1 (Jakarta EE course project)"
base="https://data.ibb.gov.tr/dataset/3ee6d744-5da2-40c8-9cd6-0e3e41f1928f/resource"
if [ $# -eq 0 ]; then
    set -- "$base/d291989c-429d-4e61-9c70-1f76294b96b8/download/traffic_density_202410.csv" \
           "$base/bedd5ab2-9a00-4966-9921-9672d4478a51/download/traffic_density_202411.csv"
fi
mkdir -p "$data"

files=""
for url in "$@"; do
    name=$(basename "$url")
    if [ ! -s "$data/$name" ]; then
        # the server cannot resume a broken transfer, so a failed one starts again
        for attempt in 1 2 3 4 5 6; do
            rm -f "$data/$name.part"
            if curl -sSL --fail -A "$agent" -o "$data/$name.part" "$url"; then
                mv "$data/$name.part" "$data/$name"
                break
            fi
            echo "$name: attempt $attempt failed, trying again"
            sleep 10
        done
        [ -s "$data/$name" ] || { echo "$name could not be downloaded" >&2; exit 1; }
    fi
    echo "$name: $(wc -c < "$data/$name") bytes"
    files="$files data/$name"
done

docker run --rm -v "$root":/workspace -v m2-cache:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-21 sh -c "
    set -e
    mvn -B -q -DskipTests compile
    java -cp target/classes com.citynavigator.tools.BuildTrafficProfile \
        src/main/resources/traffic/istanbul-congestion.csv.gz $files"
