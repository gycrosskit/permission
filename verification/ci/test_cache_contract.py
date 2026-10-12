#!/usr/bin/env python3
"""离线守住缓存试点的任务与事件合同；执行行为由远程日志和 XML 另验。"""
from pathlib import Path
import re

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
    assert 'cache: gradle' not in job, 'Do not restore Gradle User Home twice'
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
policy = (ROOT / 'scripts/ci-repositories.gradle').read_text()
assert "if (System.getenv('GYC_CI_BUILD_CACHE') == 'true')" in policy
assert 'gradle.startParameter.buildCacheEnabled = true' in policy
assert 'withType(org.gradle.api.tasks.testing.AbstractTestTask).configureEach' in policy
assert 'outputs.upToDateWhen { false }' in policy
assert "outputs.doNotCacheIf('CI must execute behavior tests') { true }" in policy
print('CI cache contract: source-only cache, real test execution policy and existing gates preserved')
