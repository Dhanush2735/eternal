#!/usr/bin/env bash
# Dependency-free checks; compilation output stays outside the workspace snapshot.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cd "$ROOT"
P=eternal/src/main/resources/data/eternalengine/eternal/hyderabad
python3 - "$P" <<'PY'
import gzip, hashlib, json, pathlib, sys
p=pathlib.Path(sys.argv[1]); m=json.loads((p/'charminar_osm_2026-11-03.metadata.json').read_text()); b=(p/m['file']).read_bytes()
assert hashlib.sha256(b).hexdigest()==m['gzip_sha256']
assert hashlib.sha256(gzip.decompress(b)).hexdigest()==m['raw_sha256']
assert m['attribution']=='© OpenStreetMap contributors'
print('Snapshot raw/gzip hashes and attribution verified')
PY
javac -d "$OUT" eternal/src/main/java/com/eternalengine/hyderabad/{PilotYears,PilotOsmSnapshot,PilotProjectedPreview}.java eternal/tests/{PilotYearsTest,PilotOsmSnapshotTest,PilotProjectedPreviewTest}.java
java -cp "$OUT:eternal/src/main/resources" PilotProjectedPreviewTest
java -cp "$OUT" PilotYearsTest
echo 'Pilot year boundary checks passed'
java -cp "$OUT:eternal/src/main/resources" PilotOsmSnapshotTest "$P/charminar_osm_2026-11-03.xml.gz"
mkdir -p "$OUT/net/buildtheearth/terraminusminus/projection/dymaxion"
cp terra3/src/main/resources/net/buildtheearth/terraminusminus/projection/dymaxion/conformal.gz "$OUT/net/buildtheearth/terraminusminus/projection/dymaxion/"
mapfile -d '' SOURCES < <(find worldfill/probes/bte -name '*.java' -print0)
javac -encoding UTF-8 -cp "$OUT" -d "$OUT" "${SOURCES[@]}" eternal/tests/PilotOsmProjectionTest.java
java -cp "$OUT" PilotOsmProjectionTest "$P/charminar_osm_2026-11-03.xml.gz"
