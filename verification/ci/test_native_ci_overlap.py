#!/usr/bin/env python3
"""准备设备、fresh receipt 和并行失败传播；所有平台命令均由替身执行。"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
DEVICE = '12345678-1234-1234-1234-123456789ABC'
PASS = 'PASS: permission receiver repeated invalidate, queued call, factory race, Context callback and SDK dealloc'
MOCK = r'''import json, os, sys
from pathlib import Path
args = sys.argv[1:]
with open(os.environ['MOCK_CALLS'], 'a') as f:
    f.write(json.dumps([Path(sys.argv[0]).name, *args]) + '\n')
case = os.environ['MOCK_CASE']
if Path(sys.argv[0]).name == 'codesign':
    sys.exit(17 if case == 'sign_failure' else 0)
if args == ['--sdk', 'iphonesimulator', '--show-sdk-path']:
    print('/mock/sdk')
elif args[0] == 'swiftc':
    if case == 'compile_failure': sys.exit(18)
    Path(args[args.index('-o') + 1]).write_text('mock app')
elif args[:3] == ['simctl', 'list', 'runtimes']:
    print(json.dumps({'runtimes': [{'isAvailable': case != 'no_runtime',
        'identifier': 'com.apple.CoreSimulator.SimRuntime.iOS-18-5',
        'supportedDeviceTypes': [{'name': 'iPhone', 'identifier': 'mock-iPhone'}]}]}))
elif args[:3] == ['simctl', 'list', 'devices']:
    print(json.dumps({'devices': {'mock-runtime': [{'isAvailable': True, 'name': 'iPhone',
        'state': 'Booted', 'udid': os.environ['MOCK_DEVICE']}]}}))
elif args[:2] == ['simctl', 'create']:
    print(os.environ['MOCK_DEVICE'])
elif args[:2] == ['simctl', 'launch']:
    assert args[args.index('--terminate-running-process') + 1] == os.environ['MOCK_DEVICE']
    run_id = os.environ['SIMCTL_CHILD_PERMISSION_CHECK_RUN_ID']
    if case != 'missing_receipt':
        receipt = {'run_id': 'old-run' if case == 'stale_receipt' else run_id,
                   'result': 'wrong PASS' if case == 'wrong_pass' else os.environ['MOCK_PASS']}
        (Path(os.environ['MOCK_CONTAINER']) / 'Documents' / ('ModuleCheck-' + run_id + '.json')).write_text(json.dumps(receipt))
    print(os.environ['MOCK_PASS'])
    sys.exit(19 if case == 'launch_failure' else 0)
elif args[:2] == ['simctl', 'get_app_container']:
    print(os.environ['MOCK_CONTAINER'])
elif args[0] == 'simctl' and args[1] in ['boot', 'bootstatus', 'install', 'shutdown', 'delete']:
    assert args[2] == os.environ['MOCK_DEVICE']
    if {'boot_failure': 'boot', 'ready_failure': 'bootstatus', 'install_failure': 'install'}.get(case) == args[1]:
        sys.exit(20)
elif args[:2] == ['simctl', 'spawn']:
    pass
else:
    raise AssertionError(args)
'''

workflow = (ROOT / '.github/workflows/regression.yml').read_text()
native = workflow.split('\n  native:\n', 1)[1].split('\n  release-android:\n', 1)[0]
assert native.index('--prepare-simulator') < native.index('Restore complete Kotlin Native toolchains')
assert native.index('--prepare-simulator') < native.index('compileKotlinIosArm64')
assert 'if: always() && steps.module-simulator.outputs.device !=' in native
assert native.index('--cleanup-simulator') < native.index('Save public CI diagnostics')
assert 'KUIKLY_MODULE_SIMULATOR: ${{ steps.module-simulator.outputs.device }}' in native
assert 'python3 verification/ci/test_native_ci_overlap.py' in workflow
block = re.search(r'      - name: Compile native Kuikly receiver with pinned SDK\n.*?        run: \|\n(.*?)(?=      - name:)', native, re.S).group(1)
block = '\n'.join(line[10:] for line in block.splitlines())

with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    repo = temp / 'repo'
    (repo / 'scripts').mkdir(parents=True)
    script = repo / 'scripts/verify-kuikly-module.sh'
    shutil.copyfile(ROOT / 'scripts/verify-kuikly-module.sh', script)
    products = temp / 'Products'
    for name in ['GycPermissionKuikly', 'OpenKuiklyIOSRender']:
        folder = products / (name + '.framework')
        folder.mkdir(parents=True)
        (folder / name).write_text('mock framework')
    container = temp / 'container'
    (container / 'Documents').mkdir(parents=True)
    bin_dir = temp / 'bin'
    bin_dir.mkdir()
    for name in ['xcrun', 'codesign']:
        tool = bin_dir / name
        tool.write_text('#!' + sys.executable + '\n' + MOCK)
        tool.chmod(0o755)
    calls, output = temp / 'calls.jsonl', temp / 'github-output'
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
               MOCK_CALLS=str(calls), MOCK_DEVICE=DEVICE, MOCK_CONTAINER=str(container),
               MOCK_PASS=PASS, GITHUB_OUTPUT=str(output), KUIKLY_MODULE_SIMULATOR=DEVICE)

    def run(case, *args, output_path=output):
        calls.write_text('')
        output.write_text('')
        env.update(MOCK_CASE=case, GITHUB_OUTPUT=str(output_path))
        result = subprocess.run(['bash', str(script), *args], env=env,
                                capture_output=True, text=True, timeout=15)
        commands = [json.loads(line) for line in calls.read_text().splitlines()]
        return result, [c[2] for c in commands if c[:2] == ['xcrun', 'simctl']]

    result, sim = run('normal', '--prepare-simulator')
    assert result.returncode == 0 and sim == ['list', 'create', 'boot']
    assert output.read_text() == f'device={DEVICE}\n'
    for case in ['boot_failure', 'no_runtime', 'output_failure']:
        result, sim = run(case, '--prepare-simulator', output_path=temp if case == 'output_failure' else output)
        assert result.returncode != 0 and output.read_text() == '', case
        assert sim == ['list'] if case == 'no_runtime' else sim[-2:] == ['shutdown', 'delete']
    result, sim = run('normal', '--cleanup-simulator', DEVICE)
    assert result.returncode == 0 and sim == ['shutdown', 'delete'], (result.returncode, sim, result.stderr)
    result, sim = run('normal', '--cleanup-simulator', 'booted')
    assert result.returncode != 0 and not sim
    for case in ['normal', 'compile_failure', 'sign_failure', 'ready_failure', 'install_failure',
                 'launch_failure', 'missing_receipt', 'stale_receipt', 'wrong_pass']:
        result, sim = run(case, str(products))
        assert (result.returncode == 0) == (case == 'normal'), (case, result.stderr)
        assert ('MODULE_PHASE receipt.pass' in result.stdout) == (case == 'normal')
        assert not any(command in sim for command in ['create', 'boot', 'shutdown', 'delete'])
    del env['KUIKLY_MODULE_SIMULATOR']
    result, sim = run('normal', str(products))
    assert result.returncode == 0 and sim[0] == 'list'

    # 执行 workflow 原 shell 块：双方必须在另一方完成前启动，任一失败均使门禁失败。
    parallel_bin = temp / 'parallel-bin'
    parallel_bin.mkdir()
    bash = parallel_bin / 'bash'
    bash.write_text('#!' + sys.executable + '\nimport os, sys\n'
                    'assert sys.argv[1] == "scripts/ci-build-kuikly-render.sh"\n'
                    'if os.environ["MOCK_CASE"] == "pod_failure": sys.exit(21)\n'
                    'print(os.environ["MOCK_PRODUCTS"])\n')
    bash.chmod(0o755)
    python = parallel_bin / 'python3'
    python.write_text('#!' + sys.executable + '\n' + r'''import os, sys, time
from pathlib import Path
args = sys.argv[1:]
assert args[:3] == ['scripts/ci-run.py', '--no-progress-seconds', '300']
name = 'module' if 'scripts/verify-kuikly-module.sh' in args else 'assembly'
assert args[-1] == os.environ['MOCK_PRODUCTS']
assert os.environ['KUIKLY_MODULE_SIMULATOR'] == os.environ['MOCK_DEVICE']
folder = Path(os.environ['MOCK_PARALLEL'])
(folder / (name + '.start')).touch()
other = 'assembly' if name == 'module' else 'module'
deadline = time.monotonic() + 4
while not (folder / (other + '.start')).exists():
    if time.monotonic() > deadline: sys.exit(22)
    time.sleep(0.01)
time.sleep(0.05)
(folder / (name + '.done')).touch()
sys.exit(23 if os.environ['MOCK_CASE'] in [name + '_failure', 'both_failure'] else 0)
''')
    python.chmod(0o755)
    for case in ['normal', 'module_failure', 'assembly_failure', 'both_failure', 'pod_failure', 'missing_device']:
        folder = temp / case
        folder.mkdir()
        parallel_env = dict(env, PATH=str(parallel_bin) + os.pathsep + os.environ['PATH'],
                            KUIKLY_MODULE_SIMULATOR='' if case == 'missing_device' else DEVICE,
                            MOCK_CASE=case, MOCK_PRODUCTS=str(products), MOCK_PARALLEL=str(folder), RUNNER_TEMP=str(temp))
        result = subprocess.run(['/bin/bash', '-e', '-o', 'pipefail', '-c', block], env=parallel_env,
                                capture_output=True, text=True, timeout=8)
        assert (result.returncode == 0) == (case == 'normal'), (case, result.stderr)
        assert len(list(folder.iterdir())) == (0 if case in ['pod_failure', 'missing_device'] else 4)
        if case not in ['pod_failure', 'missing_device']:
            assert 'Native checks: module=' in result.stdout
print('Native CI contract: early owned boot, fresh receipt, real parallel start, both results required')
