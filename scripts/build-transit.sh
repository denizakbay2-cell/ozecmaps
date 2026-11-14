#!/bin/sh
# Builds src/main/resources/transit/istanbul-transit.bin.gz, the public transport timetables of Istanbul the
# application ships, from the open GTFS data of İBB (İBB Açık Veri Lisansı):
#   1. downloads the two data sets into data/gtfs: İETT's buses and Metrobüs, and the ferries, metro, tram, Marmaray,
#      minibuses and dolmuşes of the other operators,
#   2. turns them into the compact timetable with the BuildTransitNetwork tool.
# Needs Docker and curl. The result is about 1.5 MB.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
gtfs="$root/data/gtfs"
mkdir -p "$gtfs/ibb" "$gtfs/iett"

fetch() {
    curl -fsS --retry 3 --max-time 900 -o "$1" "$2"
}

echo "1/2 downloading the timetables from data.ibb.gov.tr ..."
others=https://data.ibb.gov.tr/dataset/121a9892-7945-419a-9b89-49f6083926df/resource
for file in agency:42ae499d-ae9c-4906-ac5c-96e0c155e00b calendar:c84ca913-29ac-4f15-87cd-076aef3dccd6 \
        frequencies:a4c86ce6-64da-41e2-9584-5d83b5fb895c routes:36b554c7-cae0-4b7e-978f-fc6a43664e88 \
        stops:d1f7c258-bbc1-406f-9ab2-7a7c1797c673 trips:dcee1700-e59f-4a5f-8009-f602045a4507 \
        stop_times:ac646b83-3b6f-4ca2-afb4-9071ab44d9af shapes:83317085-aa56-41b0-9447-ea579567f2cb; do
    fetch "$gtfs/ibb/${file%%:*}.txt" "$others/${file#*:}/download/${file%%:*}.csv"
done
iett=https://data.ibb.gov.tr/dataset/8540e256-6df5-4719-85bc-e64e91508ede/resource
for file in agency:df13606d-194b-4587-b868-39ecdc5f8769 calendar:6c9623b1-3858-4b37-b936-8ffa78de2a69 \
        routes:46dbe388-c8c2-45c4-ac72-c06953de56a2 stops:2299bc82-983b-4bdf-8520-5cef8c555e29 \
        trips:7ff49bdd-b0d2-4a6e-9392-b598f77f5070; do
    fetch "$gtfs/iett/${file%%:*}.txt" "$iett/${file#*:}/download/${file%%:*}.csv"
done
fetch "$gtfs/iett/stop_times.zip" "$iett/80401c1c-c240-4a32-8f40-ef697100a681/download/stop_times.zip"

echo "2/2 building the timetable ..."
mkdir -p "$root/src/main/resources/transit"
source="İBB Açık Veri Portalı: Toplu Ulaşım GTFS Verisi ve İETT GTFS Verisi, $(date +%Y-%m-%d) tarihinde alındı (İBB Açık Veri Lisansı)"
docker run --rm -v "$root":/workspace -v m2-cache:/root/.m2 -w /workspace -e SOURCE="$source" \
    maven:3.9-eclipse-temurin-21 sh -c '
    set -e
    mvn -B -q -DskipTests compile
    java -Xmx3g -cp target/classes com.citynavigator.tools.BuildTransitNetwork \
        src/main/resources/transit/istanbul-transit.bin.gz "$SOURCE" data/gtfs/ibb data/gtfs/iett'
ls -la "$root/src/main/resources/transit/istanbul-transit.bin.gz"
