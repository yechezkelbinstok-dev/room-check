package com.roomcheck.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.Updater
import java.io.File

/**
 * Starts the update on offer. The first time, Android has to be told Room Check may install apps
 * at all - that is its own settings page, so the person is taken there, and the update carries on
 * by itself when they come back (see MainActivity). Every time after, it just goes.
 */
fun startUpdate(context: Context, vm: AppViewModel) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        vm.askedToInstall = true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        )
        return
    }
    vm.askedToInstall = false
    val app = context.applicationContext
    vm.runUpdate(File(app.cacheDir, "update")) { apk -> Updater.install(app, apk) }
}

/**
 * Where Android says how an update install went. Success needs nothing here: the app is closed and
 * replaced, and opens as the new version next time. When Android wants the person to confirm - an
 * older phone, or a time it will not install unattended - its confirm screen is brought up. Any
 * other outcome is a failure, passed on to be shown.
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.failures.value = "Update cancelled"
            else -> Updater.failures.value =
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android would not install it"
        }
    }
}
