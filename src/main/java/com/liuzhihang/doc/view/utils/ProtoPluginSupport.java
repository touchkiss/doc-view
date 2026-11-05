package com.liuzhihang.doc.view.utils;

/**
 * Protocol Buffers 插件 (idea.plugin.protoeditor) 可用性判断。
 * <p>
 * proto message 的解析依赖 com.intellij.protobuf.lang.psi.*，该 API 只在安装了
 * Protocol Buffers 插件的 IDE 中存在。所有触达 protobuf PSI 的代码都必须先经过
 * {@link #isAvailable()}，否则在缺少该插件的 IDE 上会抛出 NoClassDefFoundError。
 *
 * @author liuzhihang
 */
public final class ProtoPluginSupport {

    /**
     * 插件是否可用, 一个 IDE 进程内不会变化, 缓存结果
     */
    private static volatile Boolean available;

    private ProtoPluginSupport() {
    }

    /**
     * Protocol Buffers 插件是否已安装并启用
     *
     * @return true 表示可以安全访问 com.intellij.protobuf.* API
     */
    public static boolean isAvailable() {
        Boolean result = available;
        if (result == null) {
            synchronized (ProtoPluginSupport.class) {
                result = available;
                if (result == null) {
                    // The optional dependency is attached to this plugin's classloader only
                    // when Protocol Buffers is enabled. Loading the actual PSI API therefore
                    // checks usability without relying on internal plugin-manager APIs.
                    result = isClassLoadable();
                    available = result;
                }
            }
        }
        return result;
    }

    /**
     * 兜底判断: protobuf PSI 类是否真的能加载。
     * 通过插件自身类加载器验证可选依赖；未启用或 PSI API 缺失时返回 false。
     */
    private static boolean isClassLoadable() {
        try {
            Class.forName("com.intellij.protobuf.lang.psi.PbFile", false,
                    ProtoPluginSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
