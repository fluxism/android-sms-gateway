package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.DialogAddCloudWebhookBinding
import me.capcom.smsgateway.databinding.FragmentCloudWebhooksBinding
import me.capcom.smsgateway.modules.gateway.GatewayApi
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.webhooks.domain.WebHookEvent
import me.capcom.smsgateway.ui.adapters.CloudWebhookAdapter
import org.koin.android.ext.android.inject
import java.net.URL

class CloudWebhooksFragment : Fragment() {

    private var _binding: FragmentCloudWebhooksBinding? = null
    private val binding get() = _binding!!

    private val service: GatewayService by inject()
    private val settings: GatewaySettings by inject()

    private val adapter = CloudWebhookAdapter(::confirmDelete)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCloudWebhooksBinding.inflate(inflater, container, false)
        applyBackground(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webhookList.layoutManager = LinearLayoutManager(requireContext())
        binding.webhookList.adapter = adapter

        binding.refreshButton.setOnClickListener { loadWebhooks() }
        binding.errorRetryButton.setOnClickListener { loadWebhooks() }
        binding.addFab.setOnClickListener { showAddDialog() }

        loadWebhooks()
    }

    private fun applyBackground(view: View) {
        val backgroundValue = TypedValue()
        requireContext().theme.resolveAttribute(
            android.R.attr.colorBackground,
            backgroundValue,
            true,
        )
        view.setBackgroundColor(backgroundValue.data)
    }

    private fun showLoading(visible: Boolean) {
        binding.loadingOverlay.isVisible = visible
    }

    private fun showEmpty() {
        binding.emptyState.isVisible = true
        binding.errorState.isVisible = false
        binding.webhookList.isVisible = false
    }

    private fun showError(message: String) {
        binding.errorState.isVisible = true
        binding.errorMessage.text = message
        binding.emptyState.isVisible = false
        binding.webhookList.isVisible = false
    }

    private fun showList(items: List<GatewayApi.WebHook>) {
        binding.webhookList.isVisible = true
        binding.emptyState.isVisible = false
        binding.errorState.isVisible = false
        adapter.submitList(items)
        binding.headerSubtitle.text = resources.getQuantityString(
            R.plurals.cloud_webhooks_count,
            items.size,
            items.size,
        )
    }

    private fun loadWebhooks() {
        if (!hasCredentials()) {
            showError(getString(R.string.cloud_webhooks_not_registered))
            binding.headerSubtitle.text = getString(R.string.cloud_webhooks_not_registered)
            return
        }

        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { service.listCloudWebhooks() }
                .onSuccess { items ->
                    if (_binding == null) return@onSuccess
                    if (items.isEmpty()) {
                        adapter.submitList(emptyList())
                        binding.headerSubtitle.text = resources.getQuantityString(
                            R.plurals.cloud_webhooks_count, 0, 0,
                        )
                        showEmpty()
                    } else {
                        showList(items)
                    }
                }
                .onFailure { e ->
                    if (_binding == null) return@onFailure
                    showError(
                        getString(
                            R.string.cloud_webhooks_load_failed,
                            e.localizedMessage ?: e.message ?: e.toString(),
                        )
                    )
                }
            if (_binding != null) showLoading(false)
        }
    }

    private fun hasCredentials(): Boolean {
        val info = settings.registrationInfo ?: return false
        return info.password != null
    }

    private fun showAddDialog() {
        if (!hasCredentials()) {
            Toast.makeText(
                requireContext(),
                R.string.cloud_webhooks_not_registered,
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        val dialogBinding = DialogAddCloudWebhookBinding.inflate(layoutInflater)
        val events = WebHookEvent.values().toList()
        val eventAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            events.map { it.value },
        )
        dialogBinding.eventInput.setAdapter(eventAdapter)
        dialogBinding.eventInput.setText(events.first().value, false)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_webhook)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val urlInput = dialogBinding.urlInput
                val urlLayout = dialogBinding.urlInputLayout
                val eventText = dialogBinding.eventInput.text?.toString().orEmpty()

                val urlText = urlInput.text?.toString()?.trim().orEmpty()
                if (!validateUrl(urlText, urlLayout, urlInput)) return@setOnClickListener

                val event = events.firstOrNull { it.value == eventText }
                if (event == null) {
                    dialogBinding.eventInputLayout.error =
                        getString(R.string.webhook_event_required)
                    return@setOnClickListener
                }
                dialogBinding.eventInputLayout.error = null

                dialog.dismiss()
                createWebhook(urlText, event)
            }
        }
        dialog.show()
    }

    private fun validateUrl(
        url: String,
        layout: TextInputLayout,
        input: TextInputEditText,
    ): Boolean {
        if (url.isEmpty()) {
            layout.error = getString(R.string.webhook_url_required)
            input.requestFocus()
            return false
        }
        val parsed = runCatching { URL(url) }.getOrNull()
        if (parsed == null || (parsed.protocol != "http" && parsed.protocol != "https")) {
            layout.error = getString(R.string.invalid_url)
            input.requestFocus()
            return false
        }
        layout.error = null
        return true
    }

    private fun createWebhook(url: String, event: WebHookEvent) {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { service.createCloudWebhook(url, event) }
                .onSuccess {
                    if (_binding == null) return@onSuccess
                    Toast.makeText(
                        requireContext(),
                        R.string.webhook_created,
                        Toast.LENGTH_SHORT,
                    ).show()
                    loadWebhooks()
                }
                .onFailure { e ->
                    if (_binding == null) return@onFailure
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.webhook_create_failed,
                            e.localizedMessage ?: e.message ?: e.toString(),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private fun confirmDelete(webhook: GatewayApi.WebHook) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_webhook)
            .setMessage(getString(R.string.delete_webhook_confirm, webhook.url))
            .setPositiveButton(R.string.delete) { _, _ -> deleteWebhook(webhook) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteWebhook(webhook: GatewayApi.WebHook) {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { service.deleteCloudWebhook(webhook.id) }
                .onSuccess {
                    if (_binding == null) return@onSuccess
                    Toast.makeText(
                        requireContext(),
                        R.string.webhook_deleted,
                        Toast.LENGTH_SHORT,
                    ).show()
                    loadWebhooks()
                }
                .onFailure { e ->
                    if (_binding == null) return@onFailure
                    showLoading(false)
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.webhook_delete_failed,
                            e.localizedMessage ?: e.message ?: e.toString(),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
