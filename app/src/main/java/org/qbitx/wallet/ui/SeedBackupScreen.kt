package org.qbitx.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun SeedBackupScreen(seed: String, onConfirmed: () -> Unit) {
    var checking by remember(seed) { mutableStateOf(false) }
    var answers by remember(seed) { mutableStateOf("") }
    val words = seed.split(' ')
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Back up your wallet", style = MaterialTheme.typography.headlineMedium)
            Text("Write all 24 words on paper in order. These words control your QBX. Keep them offline and private. Recovery format: qbx-pqhd-v1; empty BIP39 passphrase.")
            if (!checking) {
                Text(words.mapIndexed { i, w -> "${i+1}. $w" }.joinToString("\n"), fontFamily = FontFamily.Monospace)
                Button(onClick = { checking = true }) { Text("I wrote down all 24 words") }
            } else {
                Text("Enter words 4, 12 and 20, separated by spaces.")
                OutlinedTextField(value = answers, onValueChange = { answers = it }, label = { Text("Confirm backup") })
                Button(onClick = onConfirmed,
                    enabled = answers.trim().split(Regex("\\s+")) == listOf(words[3], words[11], words[19])) {
                    Text("Open wallet")
                }
                TextButton(onClick = { checking = false; answers = "" }) { Text("Show words again") }
            }
        }
    }
}
