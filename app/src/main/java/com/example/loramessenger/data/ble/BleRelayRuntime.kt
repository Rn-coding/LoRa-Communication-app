package com.example.loramessenger.data.ble

import android.content.Context
import com.example.loramessenger.data.ble.BleGattManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object BleRelayRuntime {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  @Volatile private var manager: BleGattManager? = null

  fun manager(context: Context): BleGattManager = manager ?: synchronized(this) {
    manager ?: BleGattManager(context.applicationContext, scope).also { manager = it }
  }
}
