#!/usr/bin/env python3
"""离线守住缓存试点的任务与事件合同；执行行为由远程日志和 XML 另验。"""
from pathlib import Path
import contextlib
import io
import os
import re
import tempfile
import textwrap
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
workflow = (ROOT / '.github/workflows/regression.yml').read_text()
jobs = dict(re.findall(r'^  ([\w-]+):\n(.*?)(?=^  [\w-]+:\n|\Z)',
                       workflow.split('\njobs:\n', 1)[1], re.M | re.S))
assert set(jobs) == {'changes', 'contracts', 'android', 'native', 'verify-public',
                     'release-android', 'release-native'}
for name in ('android', 'native'):
    job = jobs[name]
    assert "GYC_CI_BUILD_CACHE: 'true'" in job
    assert 'gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a' in job
    assert "cache-read-only: ${{ github.event_name == 'pull_request' }}" in job
    if name == 'native':
        cache_step = re.search(r'uses: gradle/actions/setup-gradle@[^\n]+\n        if: ([^\n]+)', job)
        assert cache_step and cache_step.group(1) == "needs.changes.outputs.source == 'true'"
    assert 'cache: gradle' not in job, 'Do not restore Gradle User Home twice'
assert 'GRADLE_BUILD_ACTION_SKIP_RESTORE' not in workflow
assert 'gradle-home-cache-excludes:' not in jobs['native']
native = jobs['native']
restore = native.split('      - name: Restore commonized platform libraries\n', 1)[1].split('      - ', 1)[0]
save = native.split('      - name: Save commonized platform libraries\n', 1)[1].split('      - ', 1)[0]
check = native.split('      - name: Check commonized cache completeness\n', 1)[1].split('      - ', 1)[0]
assert native.index('Restore complete Kotlin Native toolchains') < native.index('Restore commonized platform libraries') < native.index('native_target=')
assert "if: needs.changes.outputs.source == 'true'" in restore
assert 'permission-commonized-v1-${{ steps.native-key.outputs.key }}-' in restore
for path in ('gradle/native-toolchain.properties', 'gradle/libs.versions.toml', 'gradle.properties', 'settings.gradle.kts', '**/build.gradle.kts'):
    assert path in restore
for step in (restore, save):
    assert '~/.konan/kotlin-native-prebuilt-*/klib/commonized' in step
    for suffix in ('lock', 'part', 'tmp'):
        assert f'!~/.konan/**/*.{suffix}' in step
    assert '.success' not in step, 'Success markers must remain in the archive'
for step in (check, save):
    assert "if: success() &&" in step and "github.event_name != 'pull_request'" in step
    assert "steps.commonized-cache.outputs.cache-hit != 'true'" in step
assert "steps.commonized-ready.outputs.ready == 'true'" in save
assert 'key: ${{ steps.commonized-cache.outputs.cache-primary-key }}' in save

# 实际执行 workflow 的检查代码，证明缺失/部分/其他版本的 marker 都不会保存。
code = textwrap.dedent(check.split("python3 - <<'PY'\n", 1)[1].rsplit('\n          PY', 1)[0])
with tempfile.TemporaryDirectory() as temporary:
    home = Path(temporary)
    output = home / 'output'
    config = home / 'gradle/native-toolchain.properties'
    config.parent.mkdir()
    config.write_text('kotlin=2.2.21-1.0.0\n')
    previous = Path.cwd()
    try:
        os.chdir(home)
        for arch, bundle in (('arm64', 'aarch64'), ('x86_64', 'x86_64')):
            root = home / '.konan' / f'kotlin-native-prebuilt-macos-{bundle}-2.2.21-1.0.0/klib/commonized/2.2.21-1.0.0'
            targets = ['(ios_arm64, ios_simulator_arm64, ios_x64)', '(ios_arm64, ios_simulator_arm64, ios_x64, ohos_arm64)']
            for count in (0, 1, 2):
                if count:
                    marker = root / targets[count - 1] / '.success'
                    marker.parent.mkdir(parents=True)
                    marker.touch()
                output.write_text('')
                with (patch.object(Path, 'home', return_value=home),
                      patch('platform.machine', return_value=arch),
                      patch.dict(os.environ, GITHUB_OUTPUT=str(output)),
                      contextlib.redirect_stdout(io.StringIO())):
                    exec(compile(code, '<workflow commonized check>', 'exec'), {})
                assert output.read_text() == f'ready={str(count == 2).lower()}\n'
        config.write_text('kotlin=2.2.22-1.0.0\n')
        output.write_text('')
        with (patch.object(Path, 'home', return_value=home),
              patch('platform.machine', return_value='arm64'),
              patch.dict(os.environ, GITHUB_OUTPUT=str(output)),
              contextlib.redirect_stdout(io.StringIO())):
            exec(compile(code, '<workflow commonized check>', 'exec'), {})
        assert output.read_text() == 'ready=false\n'
    finally:
        os.chdir(previous)
for path in ('caches/build-cache-1', 'caches/modules-2', 'wrapper/dists'):
    assert path not in jobs['native'], 'Keep compiler outputs, dependencies and Wrapper caches'
for name in ('release-android', 'release-native'):
    assert 'GYC_CI_BUILD_CACHE' not in jobs[name]
    assert 'cache: gradle' in jobs[name] and '--refresh-dependencies' in jobs[name]
assert ':permission-core:testDebugUnitTest' in jobs['android']
assert '-p verification-kuikly test --info --no-daemon --max-workers=1' in jobs['android']
for module in ('permission-core', 'permission-kuikly'):
    for target in ('IosArm64', '${native_target}', 'OhosArm64'):
        assert f':{module}:compileKotlin{target}' in jobs['native']
for script in ('ci-build-kuikly-render.sh', 'verify-kuikly-module.sh', 'verify-kuikly-assembly.sh'):
    assert script in jobs['native']
assembly = (ROOT / 'scripts/verify-kuikly-assembly.sh').read_text()
assert '-p verification-consumer' in assembly
assert '-PpermissionMavenRepo="$PWD/build/maven"' in assembly
assert assembly.count('--no-daemon --max-workers=1') == 2
assert assembly.count('--info --profile --no-daemon --max-workers=1') == 2
assert '/usr/bin/time -lp -o ci-diagnostics/assembly-staging.time.txt' in assembly
assert '/usr/bin/time -lp -o ci-diagnostics/assembly-consumer.time.txt' in assembly
for path in ('build/reports/profile/**', 'verification-consumer/build/reports/profile/**'):
    assert path in native
policy = (ROOT / 'scripts/ci-repositories.gradle').read_text()
assert "if (System.getenv('GYC_CI_BUILD_CACHE') == 'true')" in policy
assert 'gradle.startParameter.buildCacheEnabled = true' in policy
assert 'withType(org.gradle.api.tasks.testing.AbstractTestTask).configureEach' in policy
assert 'outputs.upToDateWhen { false }' in policy
assert "outputs.doNotCacheIf('CI must execute behavior tests') { true }" in policy
print('CI cache contract: source-only cache, real test execution policy and existing gates preserved')
