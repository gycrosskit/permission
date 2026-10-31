import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';

/** 宿主须在 module.json5 声明的相机、录音或前台定位能力。 */
export type GycPermission = 'CAMERA' | 'MICROPHONE' | 'LOCATION_WHEN_IN_USE';
/** 系统授权状态；LIMITED 表示仅有粗略定位，仍可按受限能力使用。 */
export type GycPermissionStatus = 'NOT_DETERMINED' | 'GRANTED' | 'LIMITED' | 'DENIED' | 'RESTRICTED' | 'UNSUPPORTED';

/** 宿主自有能力；系统声明、用途说明和平台状态映射由宿主实现。 */
export interface GycPermissionHandler {
  getStatus(): GycPermissionStatus;
  request(context: common.UIAbilityContext): Promise<GycPermissionStatus>;
  resumeAfterSettings(): GycPermissionStatus;
}

const BUILTIN_PERMISSIONS: string[] = ['CAMERA', 'MICROPHONE', 'LOCATION_WHEN_IN_USE'];

/** UIAbility 主线程权限入口；每次读取系统事实，不缓存返回设置页之前的状态。 */
export class GycPermissionService {
  private readonly access = abilityAccessCtrl.createAtManager();
  private readonly handlers: Map<string, GycPermissionHandler> = new Map();

  constructor(handlers: Map<string, GycPermissionHandler> = new Map()) {
    handlers.forEach((handler: GycPermissionHandler, key: string) => {
      if (!key.trim() || BUILTIN_PERMISSIONS.indexOf(key) >= 0) {
        throw new Error('Permission keys must be non-blank and must not replace built-in permissions');
      }
      this.handlers.set(key, handler);
    });
  }
  // 取消页面等待无法关闭系统授权框；不同 Page 也须等真实回执后再发起下一次申请。
  private static pending: Promise<void> = Promise.resolve();

  /** 读取当前系统状态，不弹窗；未注册能力返回 UNSUPPORTED，未知 SDK 状态抛出错误。@param permission 宿主声明的能力。 */
  getStatus(permission: string): GycPermissionStatus {
    if (BUILTIN_PERMISSIONS.indexOf(permission) < 0) {
      return this.handlers.get(permission)?.getStatus() ?? 'UNSUPPORTED';
    }
    const names = this.names(permission);
    const status = this.access.getSelfPermissionStatus(names[0]);
    switch (status) {
      case abilityAccessCtrl.PermissionStatus.GRANTED:
        return names.length === 2 &&
          this.access.getSelfPermissionStatus(names[1]) !== abilityAccessCtrl.PermissionStatus.GRANTED
          ? 'LIMITED' : 'GRANTED';
      case abilityAccessCtrl.PermissionStatus.NOT_DETERMINED: return 'NOT_DETERMINED';
      case abilityAccessCtrl.PermissionStatus.DENIED: return 'DENIED';
      case abilityAccessCtrl.PermissionStatus.RESTRICTED: return 'RESTRICTED';
      default: throw new Error('Unsupported permission status');
    }
  }

  /**
   * 内置仅 NOT_DETERMINED 时申请并重读；扩展由 handler 决定，均等真实回执后释放队列。
   * @param context 当前 UIAbility，用于系统权限框。
   * @param permission 宿主声明的能力，定位同时申请粗略与精确权限。
   * @param isActive 排队期间宿主已销毁时跳过申请；直接原生调用默认有效。
   */
  async request(context: common.UIAbilityContext, permission: string,
    isActive: () => boolean = () => true): Promise<GycPermissionStatus> {
    const operation = GycPermissionService.pending.then(async () => {
      const handler = this.handlers.get(permission);
      if (handler) return isActive() ? handler.request(context) : handler.getStatus();
      const current = this.getStatus(permission);
      if (current !== 'NOT_DETERMINED' || !isActive()) return current;
      await this.access.requestPermissionsFromUser(context, this.names(permission));
      return this.getStatus(permission);
    });
    GycPermissionService.pending = operation.then(() => {}, () => {});
    return operation;
  }

  /** 设置页返回后重新读取系统状态，不发起新的申请。@param permission 待确认能力。 */
  resumeAfterSettings(permission: string): GycPermissionStatus {
    return this.handlers.get(permission)?.resumeAfterSettings() ?? this.getStatus(permission);
  }

  private names(permission: string): Permissions[] {
    switch (permission) {
      case 'CAMERA': return ['ohos.permission.CAMERA'];
      case 'MICROPHONE': return ['ohos.permission.MICROPHONE'];
      case 'LOCATION_WHEN_IN_USE':
        // Precise location must be requested together with approximate location.
        return ['ohos.permission.APPROXIMATELY_LOCATION', 'ohos.permission.LOCATION'];
      default: throw new Error('Unsupported permission');
    }
  }
}
