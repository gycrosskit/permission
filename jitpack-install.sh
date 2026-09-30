#!/usr/bin/env bash
set -euo pipefail

archive=permission-maven.tar.gz
checksum="$(awk -v version="$VERSION" '$1 == version {print $2}' release-checksums.txt)"
[[ "$checksum" =~ ^[a-f0-9]{64}$ ]] || { echo "No verified archive checksum for $VERSION" >&2; exit 1; }
curl -fL --retry 3 -o "$archive" "https://github.com/gycrosskit/permission/releases/download/${VERSION}/${archive}"
echo "$checksum  $archive" | sha256sum -c -
mkdir -p "$HOME/.m2/repository" build/release-maven
tar -xzf "$archive" -C "$HOME/.m2/repository"
tar -xzf "$archive" -C build/release-maven
python3 - <<'PY'
import json
from pathlib import Path

# JitPack rewrites classified source/metadata JAR URLs to missing plain JARs.
changed = 0
for root in (Path.home() / '.m2/repository/com/github/gycrosskit/permission', Path('build/release-maven')):
    for file in root.rglob('*.module'):
        data = json.loads(file.read_text())
        variants = [v for v in data['variants'] if not v['name'].endswith(('SourcesElements-published', 'MetadataElements-published'))]
        if len(variants) != len(data['variants']):
            data['variants'] = variants
            file.write_text(json.dumps(data, indent=2))
            changed += 1
if not changed:
    raise SystemExit('No JitPack KMP metadata variants were fixed')
PY
