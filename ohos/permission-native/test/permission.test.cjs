const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const devEco = process.env.DEVECO_STUDIO_HOME || '/Applications/DevEco-Studio.app/Contents';
const ts = require(path.join(devEco, 'tools/hvigor/hvigor/node_modules/typescript'));
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
  assert.throws(() => service.getStatus('UNKNOWN'));
});
