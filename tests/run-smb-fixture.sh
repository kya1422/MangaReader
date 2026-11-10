#!/usr/bin/env bash
# Requires the disposable localhost fixture described in tests/smb/README.md, and locally supplied SMBJ jars.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${SMBJ_JARS:?Set SMBJ_JARS to the SMBJ jar and runtime jars as a Java classpath}"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -cp "$SMBJ_JARS" -d "$tmp" $(find tests/zip/src -name '*.java' ! -name TestMain.java) app/src/main/java/jp/reader/manga/{SmbBackend,SmbPathMap,NestedBook,FirstBook,RemoteZip,JpegHeader,PngHeader,ZipNames}.java tests/smb/src/jp/reader/manga/TestSmbBackend.java
java -Dfile.encoding=UTF-8 -cp "$tmp:$SMBJ_JARS" jp.reader.manga.TestSmbBackend
