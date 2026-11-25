package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    // Fenrir: windows a source's own code tries to open are blocked (see SourcePopupGuard)
    private var fenrirGuardedWindowManager: Any? = null

    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        if (name != Context.WINDOW_SERVICE || service == null) return service
        return fenrirGuardedWindowManager
            ?: eu.kanade.tachiyomi.util.system.SourcePopupGuard.wrap(this, service).also { fenrirGuardedWindowManager = it }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
