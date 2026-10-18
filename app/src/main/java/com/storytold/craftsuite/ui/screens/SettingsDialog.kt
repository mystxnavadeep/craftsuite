package com.storytold.craftsuite.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storytold.craftsuite.core.ai.AiConfig

@Composable
fun SettingsDialog(
    currentConfig: AiConfig,
    onSave: (AiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var endpointUrl by remember { mutableStateOf(currentConfig.endpointUrl) }
    var modelName by remember { mutableStateOf(currentConfig.modelName) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud AI & Privacy Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "BYOK Privacy: API keys and document contents are never transmitted to third parties except the configured model endpoint.",
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Cloud AI API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("Model Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    currentConfig.copy(
                        apiKey = apiKey.trim(),
                        endpointUrl = endpointUrl.trim(),
                        modelName = modelName.trim()
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
