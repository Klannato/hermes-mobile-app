package dev.nous.hermesmobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Foreground service that holds the phone awake while a task is running.
 *
 * While the agent is mid-task the phone is kept from sleeping: a partial
 * wake-lock keeps the CPU running and the foreground notification stops the
 * system from killing the process. Both are released the moment the task ends,
 * so the phone only stays awake for as long as there is work to finish.
 */
class HermesService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var startedAtMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val releaseSafety = Runnable {
        if (KeepAlivePolicy.shouldRelease(startedAtMs, System.currentTimeMillis())) stopKeepAwake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopKeepAwake()
            return START_NOT_STICKY
        }
        startKeepAwake(intent?.getStringExtra(EXTRA_LABEL) ?: "Hermes is working\u2026")
        return START_STICKY
    }

    private fun startKeepAwake(label: String) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes-mobile:task").apply { acquire() }
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Running tasks", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hermes")
            .setContentText(label)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
        // Safety net: never hold the phone awake indefinitely. If the client's
        // "task done" signal is lost (e.g. the tunnel drops), release after a cap.
        startedAtMs = System.currentTimeMillis()
        handler.removeCallbacks(releaseSafety)
        handler.postDelayed(releaseSafety, KeepAlivePolicy.MAX_HOLD_MS)
    }

    private fun stopKeepAwake() {
        handler.removeCallbacks(releaseSafety)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "hermes_task"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "dev.nous.hermesmobile.STOP_TASK"
        private const val EXTRA_LABEL = "label"

        fun startTask(context: Context, label: String) {
            context.startForegroundService(
                Intent(context, HermesService::class.java).putExtra(EXTRA_LABEL, label)
            )
        }

        fun stopTask(context: Context) {
            context.startService(
                Intent(context, HermesService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
