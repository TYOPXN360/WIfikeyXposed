package com.ty.wifikeyxposed;

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 模块 Application — 按 libxposed 官方 example 模式实现:
 * 在此 (而非 Activity) 全局唯一一次注册 XposedServiceHelper 监听。
 *
 * LSPosed 通过 XposedProvider (ContentProvider, 先于 Application.onCreate) 推送 binder,
 * 因此 registerListener 时缓存里通常已有 service, 会立即回调 onServiceBind。
 * 若框架繁忙未推送, Activity 侧有超时降级兜底, 不会卡在"正在连接"。
 */
class ModuleApp : Application(), XposedServiceHelper.OnServiceListener {

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        @Volatile
        var service: XposedService? = null
            private set

        private val stateListeners = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            stateListeners.add(listener)
            if (notifyImmediately) listener.onServiceStateChanged(service)
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            stateListeners.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            // 文档要求: registerListener 只能调用一次 — 放在 Application 保证全局仅注册一次
            XposedServiceHelper.registerListener(this)
        } catch (_: Exception) {
            // 非 LSPosed 环境 (service binder 缺失), 静默降级到本地偏好
        }
    }

    override fun onServiceBind(service: XposedService) {
        ModuleApp.service = service
        stateListeners.forEach { it.onServiceStateChanged(service) }
    }

    override fun onServiceDied(service: XposedService) {
        ModuleApp.service = null
        stateListeners.forEach { it.onServiceStateChanged(null) }
    }
}
