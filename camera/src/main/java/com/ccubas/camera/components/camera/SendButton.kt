package com.ccubas.camera.components.camera

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Composable
fun SendButton(
    modifier: Modifier = Modifier,
    selectedUris: List<Uri>,
    maxSelection: Int = Int.MAX_VALUE,
    onSend: (List<Uri>) -> Unit,
    buttonText: String = "Enviar"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            onClick = { onSend(selectedUris) },
            enabled = selectedUris.isNotEmpty()
        ) {
            val maxText = if (maxSelection == Int.MAX_VALUE) "" else "/$maxSelection"
            Text("$buttonText (${selectedUris.size}$maxText)")
        }
    }
}

@SuppressLint("UseKtx")
@Preview(showBackground = true)
@Composable
fun SendButtonPreview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Disabled state (no selection)
        SendButton(
            selectedUris = emptyList(),
            maxSelection = 5,
            onSend = {}
        )
        
        // Enabled state with selection
        SendButton(
            selectedUris = listOf(
                "content://media/1".toUri(),
                "content://media/2".toUri(),
                Uri.parse("content://media/3")
            ),
            maxSelection = 5,
            onSend = {}
        )
        
        // Unlimited selection
        SendButton(
            selectedUris = listOf(
                Uri.parse("content://media/1"),
                Uri.parse("content://media/2")
            ),
            maxSelection = Int.MAX_VALUE,
            onSend = {}
        )
        
        // Custom button text
        SendButton(
            selectedUris = listOf(
                Uri.parse("content://media/1")
            ),
            maxSelection = 10,
            onSend = {},
            buttonText = "Añadir"
        )
    }
}