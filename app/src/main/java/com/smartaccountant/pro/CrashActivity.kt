package com.smartaccountant.pro

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * شاشة طوارئ تُعرض عند أي انهيار غير متوقع في التطبيق.
 * تعرض تتبع الخطأ (stack trace) حتى يتمكن المستخدم من تصويره وإرساله.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE)
            ?: "لا تتوفر تفاصيل الخطأ"

        val textView = TextView(this).apply {
            text = "عذراً، حدث خطأ غير متوقع في التطبيق.\n\n" + stackTrace
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(24, 24, 24, 24)
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            addView(scrollView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        setContentView(container)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
    }
}
