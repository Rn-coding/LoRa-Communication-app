package com.example.loramessenger.data.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Serializes every asynchronous GATT transaction; callbacks complete only the active command. */
class GattCommandQueue(scope: CoroutineScope) {
  private sealed interface Command {
    val completed: CompletableDeferred<Unit>
    data class Await(val name: String, val start: () -> Boolean, override val completed: CompletableDeferred<Unit>) : Command
    data class Immediate(val action: () -> Unit, override val completed: CompletableDeferred<Unit>) : Command
  }
  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val operationMutex = Mutex()
  @Volatile private var callbackResult: CompletableDeferred<Boolean>? = null

  init {
    scope.launch {
      for (command in commands) operationMutex.withLock {
        try {
          when (command) {
            is Command.Immediate -> command.action()
            is Command.Await -> {
              val awaiting = CompletableDeferred<Boolean>()
              callbackResult = awaiting
              try {
                check(command.start()) { "Could not start GATT operation: ${command.name}" }
                check(withTimeout(WATCHDOG_MS) { awaiting.await() }) { "GATT operation failed: ${command.name}" }
              } finally { callbackResult = null }
            }
          }
          command.completed.complete(Unit)
        } catch (error: Throwable) {
          command.completed.completeExceptionally(error)
        }
      }
    }
  }

  suspend fun enqueue(name: String, start: () -> Boolean) {
    val completed = CompletableDeferred<Unit>()
    commands.send(Command.Await(name, start, completed))
    completed.await()
  }
  suspend fun enqueueImmediate(action: () -> Unit) {
    val completed = CompletableDeferred<Unit>()
    commands.send(Command.Immediate(action, completed))
    completed.await()
  }
  fun complete(success: Boolean) { callbackResult?.complete(success) }
  private companion object { const val WATCHDOG_MS = 5_000L }
}
