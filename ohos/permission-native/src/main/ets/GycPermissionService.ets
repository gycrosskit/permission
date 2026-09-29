import { abilityAccessCtrl, common, Permissions } from '@kit.AbilityKit';

export type GycPermission = 'CAMERA' | 'MICROPHONE' | 'LOCATION_WHEN_IN_USE';
export type GycPermissionStatus = 'NOT_DETERMINED' | 'GRANTED' | 'LIMITED' | 'DENIED' | 'RESTRICTED';

/** Native permission state remains the source of truth after returning from app settings. */
export class GycPermissionService {
  private readonly access = abilityAccessCtrl.createAtManager();

  getStatus(permission: GycPermission): GycPermissionStatus {
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

  async request(context: common.UIAbilityContext, permission: GycPermission): Promise<GycPermissionStatus> {
    const current = this.getStatus(permission);
    if (current !== 'NOT_DETERMINED') return current;
    await this.access.requestPermissionsFromUser(context, this.names(permission));
    return this.getStatus(permission);
  }

  resumeAfterSettings(permission: GycPermission): GycPermissionStatus {
    return this.getStatus(permission);
  }

  private names(permission: GycPermission): Permissions[] {
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
