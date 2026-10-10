#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
verify_receipt() {
python3 - "$@" <<'RECEIPT'
import json
from pathlib import Path
import sys
import tempfile
import uuid

PASS = 'PASS: permission receiver repeated invalidate, queued call, factory race, Context callback and SDK dealloc'

def verify(path, run_id, launch_status):
    if launch_status != 0:
        raise ValueError('ModuleCheck launch failed')
    uuid.UUID(run_id)
    receipt = json.loads(path.read_text())
    if receipt.get('run_id') != run_id or receipt.get('result') != PASS:
        raise ValueError('ModuleCheck completion receipt does not match this run')
    return receipt

if sys.argv[1:] == ['--test']:
    with tempfile.TemporaryDirectory(prefix='permission-receipt-') as folder:
        path = Path(folder) / 'receipt.json'
        run_id = str(uuid.uuid4())
        for name, contents, status in [('missing', None, 0),
                                      ('old run', {'run_id': str(uuid.uuid4()), 'result': PASS}, 0),
                                      ('wrong PASS', {'run_id': run_id, 'result': PASS + ' wrong'}, 0),
                                      ('missing PASS', {'run_id': run_id}, 0),
                                      ('failed launch', {'run_id': run_id, 'result': PASS}, 7)]:
            if contents is not None:
                path.write_text(json.dumps(contents))
            try:
                verify(path, run_id, status)
            except (OSError, ValueError):
                print('Receipt rejection checked: ' + name)
            else:
                raise AssertionError('Receipt should reject ' + name)
        path.write_text(json.dumps({'run_id': run_id, 'result': PASS}))
        assert verify(path, run_id, 0)['run_id'] == run_id
        print('Receipt fresh-run acceptance checked')
else:
    path, run_id, status, saved = sys.argv[1:]
    receipt = verify(Path(path), run_id, int(status))
    Path(saved).write_text(json.dumps(receipt) + '\n')
    print('Fresh ModuleCheck receipt: run=' + run_id + ' ' + receipt['result'])
RECEIPT
}
if [[ "${1:-}" == --test-receipt ]]; then
    verify_receipt --test
    exit 0
fi
products=${1:?Pass the real Simulator Pod build Products directory}
test -f "$products/GycPermissionKuikly.framework/GycPermissionKuikly"
test -f "$products/OpenKuiklyIOSRender.framework/OpenKuiklyIOSRender"
output="$PWD/build/kuikly-module-check"
app="$output/ModuleCheck.app"
mkdir -p "$app/Frameworks"
cp -R "$products/GycPermissionKuikly.framework" "$products/OpenKuiklyIOSRender.framework" "$app/Frameworks/"
xcrun swiftc -parse-as-library -target "$(uname -m)-apple-ios15.0-simulator"   -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" -F "$products"   -framework GycPermissionKuikly -framework OpenKuiklyIOSRender   -Xlinker -rpath -Xlinker @executable_path/Frameworks   iosApp/KuiklyTests/ModuleLifecycleCheck.swift -o "$app/ModuleCheck"
cat > "$app/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleIdentifier</key><string>io.github.gycrosskit.permission.module-check</string>
<key>CFBundleExecutable</key><string>ModuleCheck</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleName</key><string>ModuleCheck</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>1.0</string>
<key>LSRequiresIPhoneOS</key><true/>
</dict></plist>
PLIST
codesign --force --sign - "$app/Frameworks/GycPermissionKuikly.framework" "$app/Frameworks/OpenKuiklyIOSRender.framework" "$app" >/dev/null
simulator=${KUIKLY_MODULE_SIMULATOR:-$(xcrun simctl list devices available -j | python3 -c 'import json,sys; devices=[d for rows in json.load(sys.stdin)["devices"].values() for d in rows if d.get("isAvailable") and d["name"].startswith("iPhone")]; print(next((d for d in devices if d["state"]=="Booted"), devices[0])["udid"])')}
xcrun simctl bootstatus "$simulator" -b
xcrun simctl install "$simulator" "$app"
touch "$output/launch-start"
run_id=$(uuidgen)
mkdir -p ci-diagnostics
launch_status=0
SIMCTL_CHILD_PERMISSION_CHECK_RUN_ID="$run_id" xcrun simctl launch --console --terminate-running-process "$simulator" io.github.gycrosskit.permission.module-check 2>&1 | tee "$output/result.log" || launch_status=$?
cp "$output/result.log" "ci-diagnostics/permission-module-$run_id.log"
echo "ModuleCheck launch pipeline exit: $launch_status"
receipt_status=1
if [[ "$launch_status" -eq 0 ]]; then
    if container=$(xcrun simctl get_app_container "$simulator" io.github.gycrosskit.permission.module-check data); then
        if verify_receipt "$container/Documents/ModuleCheck-$run_id.json" "$run_id" "$launch_status" "ci-diagnostics/permission-module-$run_id.json"; then
            receipt_status=0
        fi
    fi
fi
if [[ "$receipt_status" -ne 0 ]]; then
    echo "ModuleCheck failed or its fresh completion receipt is missing/mismatched (run=$run_id)" >&2
    xcrun simctl spawn "$simulator" log show --last 2m --style compact --predicate 'process == "ModuleCheck"' 2>&1 | tail -n 120 >&2 || true
    mkdir -p ci-diagnostics
    # 仅保存本次 app 新生成的报告，最多 3 份，每份 256 KiB；不改变失败结果。
    python3 - "$HOME/Library/Logs/DiagnosticReports" "$output/launch-start" <<'CRASH_REPORTS' || true
from pathlib import Path
import sys
folder, stamp = map(Path, sys.argv[1:])
reports = [p for p in folder.glob('ModuleCheck-*') if p.is_file() and p.stat().st_mtime_ns >= stamp.stat().st_mtime_ns]
for report in sorted(reports, key=lambda p: p.stat().st_mtime_ns, reverse=True)[:3]:
    with report.open('rb') as source:
        Path('ci-diagnostics', report.name + '.txt').write_bytes(source.read(262144))
CRASH_REPORTS
    exit 1
fi
