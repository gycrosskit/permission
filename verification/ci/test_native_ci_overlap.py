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
MOCK = r'''import json, os, sys, uuid
from pathlib import Path
args = sys.argv[1:]
with open(os.environ['MOCK_CALLS'], 'a') as f:
    f.write(json.dumps([Path(sys.argv[0]).name, *args]) + '\n')
case = os.environ['MOCK_CASE']
if Path(sys.argv[0]).name == 'uuidgen':
    assert not args
    print(str(uuid.uuid4()).upper())
    sys.exit(0)
if Path(sys.argv[0]).name == 'codesign':
    sys.exit(17 if case == 'sign_failure' else 0)
if args == ['--sdk', 'iphonesimulator', '--show-sdk-path']:
    print('/mock/sdk')
elif args[0] == 'swiftc':
    if case == 'compile_failure': sys.exit(18)
    Path(args[args.index('-o') + 1]).write_text('mock app')
elif args[:3] == ['simctl', 'list', 'devices']:
    devices = [] if case == 'no_runtime' else [{'isAvailable': True, 'name': 'iPhone',
        'state': 'Booted' if case in ['already_booted', 'output_failure_booted'] else 'Shutdown',
        'udid': os.environ['MOCK_DEVICE']}]
    if case in ['already_booted', 'output_failure_booted']:
        devices.insert(0, {'isAvailable': True, 'name': 'iPhone', 'state': 'Shutdown',
                          'udid': '12345678-1234-1234-1234-123456789DEF'})
    print(json.dumps({'devices': {'mock-runtime': devices}}))
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
elif args[0] == 'simctl' and args[1] in ['boot', 'bootstatus', 'install', 'shutdown', 'uninstall']:
    assert args[2] == os.environ['MOCK_DEVICE']
    if {'boot_failure': 'boot', 'ready_failure': 'bootstatus', 'install_failure': 'install'}.get(case) == args[1]:
        sys.exit(20)
elif args[:2] == ['simctl', 'spawn']:
    pass
else:
    raise AssertionError(args)
'''

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
    for name in ['xcrun', 'codesign', 'uuidgen']:
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
        if case == 'normal' and args == (str(products),):
            assert commands.count(['uuidgen']) == 1
        return result, [c[2] for c in commands if c[:2] == ['xcrun', 'simctl']]

    result, sim = run('normal', '--prepare-simulator')
    assert result.returncode == 0 and sim == ['list', 'boot']
    assert output.read_text() == f'device={DEVICE}\nbooted_by_ci=true\n'
    result, sim = run('already_booted', '--prepare-simulator')
    assert result.returncode == 0 and sim == ['list']
    assert output.read_text() == f'device={DEVICE}\nbooted_by_ci=false\n'
    for case in ['boot_failure', 'no_runtime', 'output_failure']:
        result, sim = run(case, '--prepare-simulator', output_path=temp if case == 'output_failure' else output)
        assert result.returncode != 0 and output.read_text() == '', case
        assert sim == ['list'] if case == 'no_runtime' else sim[-1:] == ['shutdown']
    result, sim = run('output_failure_booted', '--prepare-simulator', output_path=temp)
    assert result.returncode != 0 and sim == ['list']
    result, sim = run('normal', '--cleanup-simulator', DEVICE, 'true')
    assert result.returncode == 0 and sim == ['uninstall', 'shutdown'], (result.returncode, sim, result.stderr)
    for args in [(DEVICE,), (DEVICE, 'false')]:
        result, sim = run('normal', '--cleanup-simulator', *args)
        assert result.returncode == 0 and sim == ['uninstall']
    result, sim = run('normal', '--cleanup-simulator', DEVICE, 'invalid')
    assert result.returncode != 0 and not sim
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

print('Native module contract: reused boot, ownership-safe cleanup and fresh receipt')
