package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.core.configureAppCheck
import com.example.notifications.ReminderBroadcastReceiver
import com.example.ui.navigation.VieSimpleRootApp

class MainActivity : FragmentActivity() {
    private var notificationTargetRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        notificationTargetRoute = intent?.getStringExtra(ReminderBroadcastReceiver.EXTRA_TARGET_ROUTE)
        enableEdgeToEdge()
        setContent {
            VieSimpleRootApp(
                notificationTargetRoute = notificationTargetRoute,
                onNotificationRouteConsumed = { notificationTargetRoute = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent.getStringExtra(ReminderBroadcastReceiver.EXTRA_TARGET_ROUTE)
        if (!route.isNullOrBlank()) {
            notificationTargetRoute = route
        }
    }
}
