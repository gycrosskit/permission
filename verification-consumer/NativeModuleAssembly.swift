import PermissionConsumer
import GycPermissionKuikly

// 编译生成的真实 consumer framework API；宿主只装配方法引用，不实现 transport。
public func configurePermissionModule(platform: IosPermissionPlatform) {
    GycPermissionModule.configure {
        let handler = IosPermissionModuleHandler(platform: platform)
        return (call: handler.call, dispose: handler.dispose)
    }
}
