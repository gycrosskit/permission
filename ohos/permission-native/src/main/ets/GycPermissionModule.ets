import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { GycPermission, GycPermissionService, GycPermissionStatus } from './GycPermissionService';

interface PermissionParams { permission?: string; }
interface PermissionResult { status: string; }

/** 每页权限桥；宿主声明权限并控制申请时机，销毁后抑制迟到回执。 */
export class GycPermissionModule extends KuiklyRenderBaseModule {
  /** 原生注册名，与 Kotlin PermissionModule.NAME 一致。 */
  static readonly MODULE_NAME: string = 'GycPermissionModule';
  private readonly service = new GycPermissionService();
  private destroyed: boolean = false;

  syncMode(): boolean { return false; }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    this.perform(method, params).then((result: PermissionResult) => {
      if (!this.destroyed) callback?.(result);
    }).catch(() => {
      if (!this.destroyed) callback?.({ status: 'error' });
    });
    return null;
  }

  private async perform(method: string, params: KRAny): Promise<PermissionResult> {
    if (this.destroyed || (method !== 'getStatus' && method !== 'request')) return { status: 'error' };
    const args = JSON.parse(typeof params === 'string' ? params : '{}') as PermissionParams;
    if (!args || (args.permission !== 'CAMERA' && args.permission !== 'MICROPHONE' &&
      args.permission !== 'LOCATION_WHEN_IN_USE')) return { status: 'error' };
    const permission: GycPermission = args.permission;
    let status: GycPermissionStatus;
    if (method === 'request') {
      const context = this.controller?.getUIAbilityContext();
      if (!context) return { status: 'error' };
      status = await this.service.request(context, permission);
    } else {
      status = this.service.getStatus(permission);
    }
    return { status };
  }

  onDestroy(): void {
    this.destroyed = true;
    super.onDestroy();
  }
}
