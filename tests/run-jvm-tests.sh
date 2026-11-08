#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/zip" "$tmp/cache"
javac -encoding UTF-8 -d "$tmp/zip" $(find tests/zip/src -name '*.java') app/src/main/java/jp/reader/manga/RemoteZip.java app/src/main/java/jp/reader/manga/NextBook.java
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "$tmp/zip" jp.reader.manga.TestMain
javac -encoding UTF-8 -d "$tmp/cache" $(find tests/cache/src -name '*.java') app/src/main/java/jp/reader/manga/DownloadCache.java
java -Dfile.encoding=UTF-8 -cp "$tmp/cache" jp.reader.manga.Test
mkdir -p "$tmp/spread"
javac -encoding UTF-8 -d "$tmp/spread" tests/spread/src/jp/reader/manga/TestSpread.java app/src/main/java/jp/reader/manga/SpreadPlanner.java
java -Dfile.encoding=UTF-8 -cp "$tmp/spread" jp.reader.manga.TestSpread
