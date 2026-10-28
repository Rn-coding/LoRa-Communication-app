package com.example.loramessenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.loramessenger.MainActivity
import com.example.loramessenger.R
import com.example.loramessenger.data.ble.BleRelayRuntime
import com.example.loramessenger.domain.RelayNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BleRelayForegroundService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val manager by lazy { BleRelayRuntime.manager(applicationContext) }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    serviceScope.launch { manager.incoming.collect { message -> showIncoming(message) } }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, statusNotification(intent?.getStringExtra(EXTRA_NODE_NAME)))
    when (intent?.action) {
      ACTION_CONNECT -> {
        val node = RelayNode(
          id = intent.getStringExtra(EXTRA_NODE_ID).orEmpty(),
          name = intent.getStringExtra(EXTRA_NODE_NAME) ?: "LoRa Node",
          rssi = intent.getIntExtra(EXTRA_RSSI, -60),
          isSimulated = intent.getBooleanExtra(EXTRA_SIMULATED, false),
        )
        serviceScope.launch { manager.connect(node) }
      }
      ACTION_DISCONNECT -> serviceScope.launch { manager.disconnect(); stopSelf() }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun statusNotification(nodeName: String?): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle("LoRa transceiver active")
    .setContentText("${nodeName ?: "Maintaining relay link"} • lock-screen reception enabled")
    .setOngoing(true)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setContentIntent(openAppIntent())
    .build()

  private fun showIncoming(message: String) {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Incoming LoRa message")
      .setContentText(message)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVibrate(longArrayOf(0, 180, 100, 180))
      .setContentIntent(openAppIntent())
      .build()
    getSystemService(NotificationManager::class.java).notify(INCOMING_NOTIFICATION_ID, notification)
  }

  private fun openAppIntent() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "LoRa relay", NotificationManager.IMPORTANCE_HIGH).apply { description = "BLE relay status and incoming disaster messages"; enableVibration(true) })
    }
  }

  companion object {
    private const val CHANNEL_ID = "lora_relay"
    private const val NOTIFICATION_ID = 1001
    private const val INCOMING_NOTIFICATION_ID = 1002
    private const val ACTION_CONNECT = "com.example.loramessenger.CONNECT"
    private const val ACTION_DISCONNECT = "com.example.loramessenger.DISCONNECT"
    private const val EXTRA_NODE_ID = "node_id"
    private const val EXTRA_NODE_NAME = "node_name"
    private const val EXTRA_RSSI = "rssi"
    private const val EXTRA_SIMULATED = "simulated"

    fun start(context: Context, node: RelayNode) {
      val intent = Intent(context, BleRelayForegroundService::class.java).apply {
        action = ACTION_CONNECT
        putExtra(EXTRA_NODE_ID, node.id); putExtra(EXTRA_NODE_NAME, node.name); putExtra(EXTRA_RSSI, node.rssi); putExtra(EXTRA_SIMULATED, node.isSimulated)
      }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) { context.startService(Intent(context, BleRelayForegroundService::class.java).setAction(ACTION_DISCONNECT)) }
  }
}
