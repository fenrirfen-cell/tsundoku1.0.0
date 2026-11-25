package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/**
 * Fenrir: no pop-up windows from sources. CloudStream plugins and Aniyomi extensions are code
 * loaded from outside the app; when that code tries to put its own window (a dialog, a pop-up)
 * on screen, the window is dropped and a short notice shows instead. Windows from the app itself,
 * Android and the web viewer are untouched.
 */
object SourcePopupGuard {
    private val blocked: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    private val knownApp = HashMap<String, Boolean>()
    private var lastNotice = 0L

    private val trusted = listOf(
        "android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "dalvik.", "libcore.",
        "com.android.", "org.chromium.", "com.google.", "sun.", "jdk.", "org.jetbrains.",
    )

    fun wrap(context: Context, windowManager: Any?): Any? {
        val wm = windowManager as? WindowManager ?: return windowManager
        if (Proxy.isProxyClass(wm.javaClass)) return wm
        val appLoader = context.applicationContext.classLoader
        return Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            val view = args?.firstOrNull() as? View
            when (method.name) {
                "addView" -> if (view != null && fromSource(appLoader)) {
                    blocked.add(view)
                    notice(context)
                    return@newProxyInstance null
                }
                "removeView", "removeViewImmediate", "updateViewLayout" ->
                    if (view != null && view in blocked) {
                        if (method.name != "updateViewLayout") blocked.remove(view)
                        return@newProxyInstance null
                    }
            }
            try {
                method.invoke(wm, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    /** Is any code on the way here from outside the app (a plugin or extension)? */
    private fun fromSource(appLoader: ClassLoader): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            val name = frame.className
            if (trusted.any { name.startsWith(it) }) return@any false
            // the guard's own stand-in window manager (a generated proxy class) isn't a source
            if (name.startsWith("\$Proxy") || name.contains(".\$Proxy") || name.startsWith("com.sun.proxy") ||
                name.startsWith("jdk.proxy") || name.contains("SourcePopupGuard")
            ) {
                return@any false
            }
            val inApp = synchronized(knownApp) {
                knownApp.getOrPut(name) { runCatching { Class.forName(name, false, appLoader) }.isSuccess }
            }
            !inApp
        }

    private fun notice(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastNotice < 4000) return
        lastNotice = now
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(context.applicationContext, "Blocked a pop-up from a source", Toast.LENGTH_SHORT).show() }
        }
    }
}
