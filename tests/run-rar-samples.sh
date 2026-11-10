#!/usr/bin/env bash
# Runs against caller-supplied, non-committed RAR4/RAR5 fixtures and locally supplied jars.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JUNRAR_JAR:?Set JUNRAR_JAR to junrar 8.1.2 jar}"
: "${SLF4J_JAR:?Set SLF4J_JAR to slf4j-api 2.0.17 jar}"
if [ "$#" -lt 1 ]; then echo 'Usage: run-rar-samples.sh rar4.rar rar5.rar' >&2; exit 2; fi
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -cp "$JUNRAR_JAR:$SLF4J_JAR" -d "$tmp" $(find tests/zip/src -name '*.java' ! -name TestMain.java) app/src/main/java/jp/reader/manga/{RemoteRar,RemoteZip,RemoteBooks,JpegHeader,PngHeader,ZipNames}.java tests/rar/src/jp/reader/manga/TestRar.java
java -Djava.awt.headless=true -cp "$tmp:$JUNRAR_JAR:$SLF4J_JAR" jp.reader.manga.TestRar "$@"
