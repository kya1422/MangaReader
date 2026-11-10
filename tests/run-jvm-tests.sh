#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/zip" "$tmp/cache"
javac -encoding UTF-8 -d "$tmp/zip" $(find tests/zip/src -name '*.java') app/src/main/java/jp/reader/manga/RemoteZip.java app/src/main/java/jp/reader/manga/JpegHeader.java app/src/main/java/jp/reader/manga/PngHeader.java app/src/main/java/jp/reader/manga/ZipNames.java app/src/main/java/jp/reader/manga/NextBook.java app/src/main/java/jp/reader/manga/FirstBook.java app/src/main/java/jp/reader/manga/NestedBook.java
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "$tmp/zip" jp.reader.manga.TestMain
javac -encoding UTF-8 -d "$tmp/cache" $(find tests/cache/src -name '*.java') app/src/main/java/jp/reader/manga/DownloadCache.java app/src/main/java/jp/reader/manga/CachePolicy.java
java -Dfile.encoding=UTF-8 -cp "$tmp/cache" jp.reader.manga.Test
mkdir -p "$tmp/spread"
javac -encoding UTF-8 -d "$tmp/spread" tests/spread/src/jp/reader/manga/TestSpread.java app/src/main/java/jp/reader/manga/SpreadPlanner.java
java -Dfile.encoding=UTF-8 -cp "$tmp/spread" jp.reader.manga.TestSpread
mkdir -p "$tmp/geometry"
javac -encoding UTF-8 -d "$tmp/geometry" tests/geometry/src/jp/reader/manga/TestGeometry.java app/src/main/java/jp/reader/manga/SpreadGeometry.java
java -Dfile.encoding=UTF-8 -cp "$tmp/geometry" jp.reader.manga.TestGeometry
mkdir -p "$tmp/policy"
javac -encoding UTF-8 -d "$tmp/policy" tests/policy/src/jp/reader/manga/TestPolicy.java app/src/main/java/jp/reader/manga/CachePolicy.java
java -Dfile.encoding=UTF-8 -cp "$tmp/policy" jp.reader.manga.TestPolicy
mkdir -p "$tmp/http"
javac -encoding UTF-8 -d "$tmp/http" tests/http/src/jp/reader/manga/TestHttp.java app/src/main/java/jp/reader/manga/HttpErrors.java
java -Dfile.encoding=UTF-8 -cp "$tmp/http" jp.reader.manga.TestHttp
mkdir -p "$tmp/browse"
javac -encoding UTF-8 -d "$tmp/browse" tests/browse/src/jp/reader/manga/TestBrowse.java app/src/main/java/jp/reader/manga/BrowseSearch.java
java -Dfile.encoding=UTF-8 -cp "$tmp/browse" jp.reader.manga.TestBrowse
mkdir -p "$tmp/covercrop"
javac -encoding UTF-8 -d "$tmp/covercrop" tests/covercrop/src/jp/reader/manga/TestCoverCrop.java app/src/main/java/jp/reader/manga/CoverCrop.java
java -Dfile.encoding=UTF-8 -cp "$tmp/covercrop" jp.reader.manga.TestCoverCrop
mkdir -p "$tmp/titlesort"
javac -encoding UTF-8 -d "$tmp/titlesort" tests/titlesort/src/jp/reader/manga/TestTitleSort.java app/src/main/java/jp/reader/manga/TitleSort.java
java -Dfile.encoding=UTF-8 -cp "$tmp/titlesort" jp.reader.manga.TestTitleSort
mkdir -p "$tmp/orientation"
javac -encoding UTF-8 -d "$tmp/orientation" tests/orientation/src/jp/reader/manga/TestOrientation.java app/src/main/java/jp/reader/manga/OrientationCodec.java
java -Dfile.encoding=UTF-8 -cp "$tmp/orientation" jp.reader.manga.TestOrientation
mkdir -p "$tmp/manualcover"
javac -encoding UTF-8 -d "$tmp/manualcover" $(find tests/manualcover/src -name '*.java') app/src/main/java/jp/reader/manga/FolderCovers.java app/src/main/java/jp/reader/manga/CoverCrop.java
java -Dfile.encoding=UTF-8 -cp "$tmp/manualcover" jp.reader.manga.TestManualCover
mkdir -p "$tmp/coverwindow"
javac -encoding UTF-8 -d "$tmp/coverwindow" tests/coverwindow/src/jp/reader/manga/TestCoverWindow.java app/src/main/java/jp/reader/manga/CoverWindow.java
java -Dfile.encoding=UTF-8 -cp "$tmp/coverwindow" jp.reader.manga.TestCoverWindow
mkdir -p "$tmp/coverdispatch"
javac -encoding UTF-8 -d "$tmp/coverdispatch" tests/coverdispatch/src/jp/reader/manga/TestCoverDispatcher.java app/src/main/java/jp/reader/manga/CoverDispatcher.java
java -Dfile.encoding=UTF-8 -cp "$tmp/coverdispatch" jp.reader.manga.TestCoverDispatcher
mkdir -p "$tmp/hidden"
javac -encoding UTF-8 -d "$tmp/hidden" $(find tests/zip/src -name '*.java' ! -name TestMain.java) app/src/main/java/jp/reader/manga/{HiddenFolders,NestedBook,FirstBook,RemoteZip,JpegHeader,PngHeader,ZipNames}.java tests/hidden/src/jp/reader/manga/TestHidden.java
java -Dfile.encoding=UTF-8 -cp "$tmp/hidden" jp.reader.manga.TestHidden
mkdir -p "$tmp/davxml"
javac -encoding UTF-8 -d "$tmp/davxml" $(find tests/davxml/src -name '*.java') app/src/main/java/jp/reader/manga/DavXml.java
java -Dfile.encoding=UTF-8 -cp "$tmp/davxml" jp.reader.manga.TestDavXml
mkdir -p "$tmp/arrivals"
javac -encoding UTF-8 -d "$tmp/arrivals" $(find tests/zip/src -name '*.java' ! -name TestMain.java) app/src/main/java/jp/reader/manga/{NewArrivals,HiddenFolders,NestedBook,FirstBook,RemoteZip,JpegHeader,PngHeader,ZipNames}.java tests/arrivals/src/jp/reader/manga/TestArrivals.java
java -Dfile.encoding=UTF-8 -cp "$tmp/arrivals" jp.reader.manga.TestArrivals
mkdir -p "$tmp/smbpath"
javac -encoding UTF-8 -d "$tmp/smbpath" $(find tests/zip/src -name '*.java' ! -name TestMain.java) app/src/main/java/jp/reader/manga/{SmbPathMap,SmbAliases,NestedBook,FirstBook,RemoteZip,JpegHeader,PngHeader,ZipNames}.java tests/smbpath/src/jp/reader/manga/TestSmbPathMap.java
java -Dfile.encoding=UTF-8 -cp "$tmp/smbpath" jp.reader.manga.TestSmbPathMap
mkdir -p "$tmp/smbaddress"
javac -encoding UTF-8 -d "$tmp/smbaddress" tests/smbaddress/src/jp/reader/manga/TestSmbAddress.java app/src/main/java/jp/reader/manga/SmbAddress.java
java -Dfile.encoding=UTF-8 -cp "$tmp/smbaddress" jp.reader.manga.TestSmbAddress
