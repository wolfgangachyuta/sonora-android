package com.sonora.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Separate launcher entry for Athenaeum; both experiences share the same installed package. */
class AthenaeumLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("open_athenaeum", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
