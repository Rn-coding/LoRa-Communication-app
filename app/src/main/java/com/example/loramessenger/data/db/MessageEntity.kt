package com.example.loramessenger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.loramessenger.domain.ChatMessage
import com.example.loramessenger.domain.DeliveryStatus

@Entity(tableName = "messages")
data class MessageEntity(
  @PrimaryKey val id: Long,
  val text: String,
  val outgoing: Boolean,
  val timestamp: Long,
  val status: String?,
  val sender: String,
) {
  fun toDomain() = ChatMessage(id, text, outgoing, timestamp, status?.let(DeliveryStatus::valueOf), sender)
  companion object {
    fun fromDomain(message: ChatMessage) = MessageEntity(message.id, message.text, message.outgoing, message.timestamp, message.status?.name, message.sender)
  }
}
