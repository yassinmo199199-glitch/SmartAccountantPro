package com.smartaccountant.pro

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.smartaccountant.pro.data.remote.OtaCheckWorker

class SmartAccountantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        container = AppContainer(this)
        OtaCheckWorker.schedulePeriodicCheck(this)
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                }
                startActivity(intent)
                // إتاحة وقت لعرض شاشة الخطأ قبل إنهاء العملية
                Handler(Looper.getMainLooper()).postDelayed({
                    defaultHandler?.uncaughtException(thread, throwable)
                }, 3000)
            } catch (_: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
