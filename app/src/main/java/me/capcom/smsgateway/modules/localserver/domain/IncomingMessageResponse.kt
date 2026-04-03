package me.capcom.smsgateway.modules.localserver.domain

import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import java.util.Date

data class IncomingMessageResponse(
    val id: String,
    val type: IncomingMessageType,
    val sender: String,
    val recipient: String?,
    val simNumber: Int?,
    val subscriptionId: Int?,
    val contentPreview: String,
    val createdAt: Date,
)
