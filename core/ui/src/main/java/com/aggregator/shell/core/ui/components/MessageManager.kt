package com.aggregator.shell.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aggregator.shell.core.common.AppException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple toast/message system for the app.
 */
class MessageManager {
    private val _messageFlow = MutableStateFlow<MessageState?>(null)
    val messageState: StateFlow<MessageState?> = _messageFlow.asStateFlow()

    fun showError(message: String) {
        _messageFlow.value = MessageState.Error(message)
    }

    fun showSuccess(message: String) {
        _messageFlow.value = MessageState.Success(message)
    }

    fun showInfo(message: String) {
        _messageFlow.value = MessageState.Info(message)
    }

    fun dismiss() {
        _messageFlow.value = null
    }
}

data class MessageState(
    val type: MessageType,
    val message: String
) {
    enum class MessageType {
        ERROR, SUCCESS, INFO
    }

    companion object {
        fun Error(msg: String) = MessageState(MessageType.ERROR, msg)
        fun Success(msg: String) = MessageState(MessageType.SUCCESS, msg)
        fun Info(msg: String) = MessageState(MessageType.INFO, msg)
    }
}

@Composable
fun MessageDialog(state: MessageState?) {
    if (state == null) return

    val icon = when (state.type) {
        MessageState.MessageType.ERROR -> Icons.Default.Error
        else -> null
    }

    val title = when (state.type) {
        MessageState.MessageType.ERROR -> "错误"
        MessageState.MessageType.SUCCESS -> "成功"
        MessageState.MessageType.INFO -> "提示"
    }

    val buttonText = when (state.type) {
        MessageState.MessageType.ERROR -> "确定"
        else -> "确定"
    }

    Dialog(onDismissRequest = {}) {
        androidx.compose.material3.AlertDialog(
            icon = icon?.let { { Icon(it, contentDescription = null) } },
            title = { Text(title) },
            text = { Text(state.message) },
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {}) {
                    Text(buttonText)
                }
            }
        )
    }
}

@Composable
fun rememberMessageManager(): MessageManager {
    return remember { MessageManager() }
}
