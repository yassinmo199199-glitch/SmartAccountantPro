package com.smartaccountant.pro.core.config

object AppConfig {
    const val APP_VERSION_NAME = "2.1.0"
    const val ENABLE_CLOUD_SYNC = true
    const val SYNC_INTERVAL_MINUTES = 15L

    const val USE_CLOUDFLARE_R2 = false
    const val CLOUDFLARE_R2_UPLOAD_ENDPOINT = "https://your-r2-worker.your-domain.workers.dev/upload"
    const val CLOUDFLARE_R2_PUBLIC_BASE_URL = "https://cdn.your-domain.com"
}
