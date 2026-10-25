#!/usr/bin/env python3
"""离线守住缓存试点的任务与事件合同；执行行为由远程日志和 XML 另验。"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
assembly = (ROOT / 'scripts/verify-kuikly-assembly.sh').read_text()
assert '-p verification-consumer' in assembly
assert '-PpermissionMavenRepo="$PWD/build/maven"' in assembly
assert assembly.count('--no-daemon --max-workers=1') == 2
assert assembly.count('--info --profile --no-daemon --max-workers=1') == 2
assert '/usr/bin/time -lp -o ci-diagnostics/assembly-staging.time.txt' in assembly
assert '/usr/bin/time -lp -o ci-diagnostics/assembly-consumer.time.txt' in assembly
policy = (ROOT / 'scripts/ci-repositories.gradle').read_text()
assert "if (System.getenv('GYC_CI_BUILD_CACHE') == 'true')" in policy
assert 'gradle.startParameter.buildCacheEnabled = true' in policy
assert 'withType(org.gradle.api.tasks.testing.AbstractTestTask).configureEach' in policy
assert 'outputs.upToDateWhen { false }' in policy
assert "outputs.doNotCacheIf('CI must execute behavior tests') { true }" in policy
print('CI cache contract: source-only cache, real test execution policy and existing gates preserved')
