package me.capcom.smsgateway.ui.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.ItemCloudWebhookBinding
import me.capcom.smsgateway.modules.gateway.GatewayApi
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent

class CloudWebhookAdapter(
    private val onDelete: (GatewayApi.WebHook) -> Unit,
) : ListAdapter<GatewayApi.WebHook, CloudWebhookAdapter.ViewHolder>(WebhookDiffCallback()) {

    class ViewHolder(private val binding: ItemCloudWebhookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(webhook: GatewayApi.WebHook, onDelete: (GatewayApi.WebHook) -> Unit) {
            val context = binding.root.context
            binding.urlText.text = webhook.url
            binding.idText.text = context.getString(R.string.webhook_id_format, webhook.id)
            binding.eventChip.text = webhook.event.value

            val style = styleForEvent(webhook.event)
            binding.eventChip.setBackgroundResource(style.chipBackgroundRes)
            binding.accentBar.setBackgroundColor(style.accentColor)

            binding.deleteButton.setOnClickListener { onDelete(webhook) }

            binding.root.setOnLongClickListener {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Webhook ID", webhook.id))
                Toast.makeText(context, R.string.id_copied, Toast.LENGTH_SHORT).show()
                true
            }
        }

        private fun styleForEvent(event: WebHookEvent): EventStyle = when (event) {
            WebHookEvent.SmsFailed -> EventStyle(
                R.drawable.bg_event_chip_failed,
                Color.parseColor("#F44336"),
            )
            WebHookEvent.MmsReceived,
            WebHookEvent.MmsDownloaded -> EventStyle(
                R.drawable.bg_event_chip_mms,
                Color.parseColor("#9C27B0"),
            )
            WebHookEvent.SystemPing -> EventStyle(
                R.drawable.bg_event_chip_system,
                Color.parseColor("#FF9800"),
            )
            else -> EventStyle(
                R.drawable.bg_event_chip_sms,
                Color.parseColor("#2196F3"),
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCloudWebhookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
    }

    private data class EventStyle(val chipBackgroundRes: Int, val accentColor: Int)

    class WebhookDiffCallback : DiffUtil.ItemCallback<GatewayApi.WebHook>() {
        override fun areItemsTheSame(
            oldItem: GatewayApi.WebHook,
            newItem: GatewayApi.WebHook,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: GatewayApi.WebHook,
            newItem: GatewayApi.WebHook,
        ): Boolean = oldItem == newItem
    }
}
