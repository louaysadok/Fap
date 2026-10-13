package com.fap.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
public fun LockScreen(pin: String, onUnlock: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            "Enter PIN",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        OutlinedTextField(
            value = entered,
            onValueChange = {
                entered = it.filter { ch -> ch.isDigit() }.take(6)
                error = false
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            isError = error,
            modifier = Modifier.fillMaxWidth()
        )
        if (error) {
            Text(
                "Wrong PIN",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Button(
            onClick = {
                if (entered == pin) onUnlock() else error = true
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Unlock")
        }
    }
}

@Composable
public fun LegalDialog(onAccept: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text("Before you start") },
        text = {
            Text(
                "This app only reads pages that are publicly available, and it is rate limited so it " +
                    "does not hammer the site. It does not bypass logins, paywalls or age gates.\n\n" +
                    "Most media on these sites belongs to the creator. Download it for your own " +
                    "personal use only - do not redistribute, re-upload or sell it, and delete anything " +
                    "you do not have the right to keep.\n\n" +
                    "You are responsible for complying with the site's terms and with the law where " +
                    "you live."
            )
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("I understand") }
        }
    )
}
