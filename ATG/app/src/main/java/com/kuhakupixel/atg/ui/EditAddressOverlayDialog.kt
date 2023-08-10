package com.kuhakupixel.atg.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.kuhakupixel.atg.ui.util.NumberInputField
import com.kuhakupixel.libuberalles.overlay.OverlayContext
import com.kuhakupixel.libuberalles.overlay.service.dialog.OverlayDialog

class EditAddressOverlayDialog(overlayContext: OverlayContext, alpha: Float = 1.0f) :
    OverlayDialog(overlayContext, alpha = alpha) {
    private val valueInput: MutableState<String> = mutableStateOf("")

    @Composable
    override fun DialogBody() {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NumberInputField(
                value = valueInput.value,
                onValueChange = { value ->
                    valueInput.value = value
                },
                label = "Value Input ...",
                placeholder = "value ...",
            )
        }

    }

    fun show(title: String, onConfirm: (input: String) -> Unit) {
        super.show(
            title = title,
            onConfirm = {
                onConfirm(valueInput.value)
            },
            onClose = {}
        )
    }

}
