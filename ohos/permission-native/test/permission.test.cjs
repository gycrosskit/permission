const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const devEco = process.env.DEVECO_STUDIO_HOME || '/Applications/DevEco-Studio.app/Contents';
const ts = require(process.env.TYPESCRIPT_PATH || path.join(devEco, 'tools/hvigor/hvigor/node_modules/typescript'));
const source = fs.readFileSync(path.join(__dirname, '../src/main/ets/GycPermissionService.ets'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText;

test('权限状态、粗略定位和系统申请', async () => {
  const status = { GRANTED: 0, NOT_DETERMINED: 1, DENIED: -1, RESTRICTED: 3 };
  const values = new Map();
  const requests = [];
  const access = {
    getSelfPermissionStatus: name => values.get(name) ?? status.NOT_DETERMINED,
    requestPermissionsFromUser: async (_, names) => {
      requests.push(names);
      names.forEach(name => values.set(name, status.GRANTED));
    }
  };
  const exports = {};
  vm.runInNewContext(compiled, {
    exports,
    require: name => {
      assert.equal(name, '@kit.AbilityKit');
      return { abilityAccessCtrl: { createAtManager: () => access, PermissionStatus: status } };
    }
  });
  const service = new exports.GycPermissionService();
  assert.equal(service.getStatus('CAMERA'), 'NOT_DETERMINED');
  assert.equal(await service.request({}, 'CAMERA'), 'GRANTED');
  assert.equal(requests.length, 1);
  values.set('ohos.permission.CAMERA', status.DENIED);
  assert.equal(await service.request({}, 'CAMERA'), 'DENIED');
  assert.equal(requests.length, 1);
  values.set('ohos.permission.APPROXIMATELY_LOCATION', status.GRANTED);
  values.set('ohos.permission.LOCATION', status.DENIED);
  assert.equal(service.resumeAfterSettings('LOCATION_WHEN_IN_USE'), 'LIMITED');
  assert.equal(await service.request({}, 'MICROPHONE'), 'GRANTED');
  assert.deepEqual(Array.from(requests[1]), ['ohos.permission.MICROPHONE']);
  values.set('ohos.permission.CAMERA', status.GRANTED);
  assert.equal(service.resumeAfterSettings('CAMERA'), 'GRANTED', 'settings state must be re-read');
  values.set('ohos.permission.CAMERA', status.RESTRICTED);
  assert.equal(await service.request({}, 'CAMERA'), 'RESTRICTED');
  assert.equal(requests.length, 2, 'restricted state cannot reopen permission dialog');
  values.set('ohos.permission.APPROXIMATELY_LOCATION', status.NOT_DETERMINED);
  values.set('ohos.permission.LOCATION', status.NOT_DETERMINED);
  assert.equal(await service.request({}, 'LOCATION_WHEN_IN_USE'), 'GRANTED');
  assert.deepEqual(Array.from(requests[2]), ['ohos.permission.APPROXIMATELY_LOCATION', 'ohos.permission.LOCATION']);
  values.set('ohos.permission.CAMERA', 999);
  assert.throws(() => service.getStatus('CAMERA'), /Unsupported permission status/);
  assert.throws(() => service.getStatus('UNKNOWN'));
});


test('权限桥校验输入并在销毁后抑制迟到授权回执', async () => {
  let complete, requests = 0, destroyed = 0;
  const serviceExports = {};
  vm.runInNewContext(compiled, {
    exports: serviceExports,
    require: () => ({ abilityAccessCtrl: {
      PermissionStatus: { GRANTED: 0, NOT_DETERMINED: 1 },
      createAtManager: () => ({
        getSelfPermissionStatus: () => 1,
        requestPermissionsFromUser: () => { requests++; return new Promise(resolve => { complete = resolve; }); }
      })
    } })
  });
  const bridgeSource = fs.readFileSync(path.join(__dirname, '../src/main/ets/GycPermissionModule.ets'), 'utf8');
  const bridgeExports = {};
  vm.runInNewContext(ts.transpileModule(bridgeSource, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText, {
    exports: bridgeExports,
    require: name => name === './GycPermissionService' ? serviceExports : {
      KuiklyRenderBaseModule: class {
        controller = { getUIAbilityContext: () => ({}) };
        onDestroy() { destroyed++; }
      }
    }
  });
  const bridge = new bridgeExports.GycPermissionModule(), values = [];
  const invoke = (method, params) => bridge.call(method, params, value => values.push(value.status));
  const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
  for (const params of ['{', 'null', '{}', '{"permission":"UNKNOWN"}']) invoke('request', params);
  invoke('unknown', '{"permission":"CAMERA"}');
  await flush();
  assert.deepEqual(values, ['error', 'error', 'error', 'error', 'error']);
  assert.equal(requests, 0, 'invalid bridge input cannot reach system permission dialog');
  bridge.controller = null;
  invoke('getStatus', '{"permission":"CAMERA"}');
  invoke('request', '{"permission":"CAMERA"}');
  await flush();
  assert.deepEqual(values.slice(-2), ['NOT_DETERMINED', 'error'], 'reading status does not require a UI host');
  bridge.controller = { getUIAbilityContext: () => ({}) };
  invoke('request', '{"permission":"CAMERA"}');
  await flush();
  assert.equal(requests, 1);
  const beforeDestroy = values.length;
  bridge.onDestroy();
  complete(); await flush();
  invoke('request', '{"permission":"CAMERA"}'); await flush();
  assert.equal(values.length, beforeDestroy);
  assert.equal(requests, 1, 'destroyed bridge cannot start another system request');
  assert.equal(destroyed, 1);
});

test('不同页面申请等真实系统回执；销毁队列项不弹框，失败不阻塞后继', async () => {
  const calls = [];
  const exports = {};
  vm.runInNewContext(compiled, { exports, require: () => ({ abilityAccessCtrl: {
    PermissionStatus: { GRANTED: 0, NOT_DETERMINED: 1 },
    createAtManager: () => ({
      getSelfPermissionStatus: () => 1,
      requestPermissionsFromUser: (_, names) => new Promise((resolve, reject) => {
        calls.push({ names: Array.from(names), resolve, reject });
      })
    })
  } }) });
  const first = new exports.GycPermissionService();
  const next = new exports.GycPermissionService();
  const camera = first.request({}, 'CAMERA');
  let active = true;
  const destroyed = next.request({}, 'MICROPHONE', () => active);
  const successor = next.request({}, 'LOCATION_WHEN_IN_USE');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 1);
  active = false;
  calls[0].resolve();
  await camera; await destroyed;
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 2);
  assert.deepEqual(calls[1].names, ['ohos.permission.APPROXIMATELY_LOCATION', 'ohos.permission.LOCATION']);
  const failed = assert.rejects(successor);
  calls[1].reject(new Error('SDK unavailable'));
  await failed;
  const retry = first.request({}, 'CAMERA');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 3);
  calls[2].resolve(); await retry;
});

test('本地取消只撤销排队申请，不释放已显示系统弹窗的屏障', async () => {
  const calls = [], serviceExports = {}, bridgeExports = {};
  vm.runInNewContext(compiled, { exports: serviceExports, require: () => ({ abilityAccessCtrl: {
    PermissionStatus: { GRANTED: 0, NOT_DETERMINED: 1 },
    createAtManager: () => ({ getSelfPermissionStatus: () => 1,
      requestPermissionsFromUser: (_, names) => new Promise(resolve => calls.push({ names: Array.from(names), resolve })) })
  } }) });
  const bridgeSource = fs.readFileSync(path.join(__dirname, '../src/main/ets/GycPermissionModule.ets'), 'utf8');
  vm.runInNewContext(ts.transpileModule(bridgeSource, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText, { exports: bridgeExports, require: name => name === './GycPermissionService' ? serviceExports : {
    KuiklyRenderBaseModule: class { controller = { getUIAbilityContext: () => ({}) }; onDestroy() {} }
  } });
  const first = new bridgeExports.GycPermissionModule(), page = new bridgeExports.GycPermissionModule();
  const destroyed = new bridgeExports.GycPermissionModule();
  const invoke = (module, method, id, permission = 'CAMERA') => module.call(method,
    JSON.stringify({ requestId: id, permission }), null);
  const flush = () => new Promise(resolve => setImmediate(resolve));
  invoke(first, 'request', 'visible'); await flush();
  invoke(page, 'request', 'old', 'MICROPHONE');
  invoke(page, 'cancelQueued', 'old');
  invoke(page, 'request', 'latest', 'LOCATION_WHEN_IN_USE');
  invoke(destroyed, 'request', 'destroyed'); destroyed.onDestroy();
  invoke(first, 'cancelQueued', 'visible'); await flush();
  assert.equal(calls.length, 1, 'cancel cannot close or release the real system dialog');
  calls[0].resolve(); await flush();
  assert.equal(calls.length, 2, 'cancelled queued microphone did not show a dialog');
  assert.deepEqual(calls[1].names, ['ohos.permission.APPROXIMATELY_LOCATION', 'ohos.permission.LOCATION']);
  calls[1].resolve(); await flush();
  assert.equal(calls.length, 2, 'destroyed queued page never starts a dialog');
});
