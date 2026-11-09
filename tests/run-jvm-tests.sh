#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/zip" "$tmp/cache"
javac -encoding UTF-8 -d "$tmp/zip" $(find tests/zip/src -name '*.java') app/src/main/java/jp/reader/manga/RemoteZip.java app/src/main/java/jp/reader/manga/JpegHeader.java app/src/main/java/jp/reader/manga/ZipNames.java app/src/main/java/jp/reader/manga/NextBook.java
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "$tmp/zip" jp.reader.manga.TestMain
javac -encoding UTF-8 -d "$tmp/cache" $(find tests/cache/src -name '*.java') app/src/main/java/jp/reader/manga/DownloadCache.java app/src/main/java/jp/reader/manga/CachePolicy.java
java -Dfile.encoding=UTF-8 -cp "$tmp/cache" jp.reader.manga.Test
mkdir -p "$tmp/spread"
javac -encoding UTF-8 -d "$tmp/spread" tests/spread/src/jp/reader/manga/TestSpread.java app/src/main/java/jp/reader/manga/SpreadPlanner.java
java -Dfile.encoding=UTF-8 -cp "$tmp/spread" jp.reader.manga.TestSpread
mkdir -p "$tmp/ocr"
javac -encoding UTF-8 -d "$tmp/ocr" tests/ocr/src/jp/reader/manga/TestOcrInference.java app/src/main/java/jp/reader/manga/OcrSpreadInference.java
java -Dfile.encoding=UTF-8 -cp "$tmp/ocr" jp.reader.manga.TestOcrInference
mkdir -p "$tmp/geometry"
javac -encoding UTF-8 -d "$tmp/geometry" tests/geometry/src/jp/reader/manga/TestGeometry.java app/src/main/java/jp/reader/manga/SpreadGeometry.java
java -Dfile.encoding=UTF-8 -cp "$tmp/geometry" jp.reader.manga.TestGeometry
mkdir -p "$tmp/policy"
javac -encoding UTF-8 -d "$tmp/policy" tests/policy/src/jp/reader/manga/TestPolicy.java app/src/main/java/jp/reader/manga/CachePolicy.java
java -Dfile.encoding=UTF-8 -cp "$tmp/policy" jp.reader.manga.TestPolicy
mkdir -p "$tmp/http"
javac -encoding UTF-8 -d "$tmp/http" tests/http/src/jp/reader/manga/TestHttp.java app/src/main/java/jp/reader/manga/HttpErrors.java
java -Dfile.encoding=UTF-8 -cp "$tmp/http" jp.reader.manga.TestHttp
