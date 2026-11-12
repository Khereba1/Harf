package com.khereba1.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StartScreen(vm: HarfViewModel, onLanguage: (String) -> Unit) {
    val context = LocalContext.current
    var showBlank by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.startPhoto(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "حرف",
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Harf",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.pick_photo)) }
            OutlinedButton(
                onClick = { showBlank = true },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.blank_canvas)) }
            if (vm.busy) CircularProgressIndicator()
        }
        TextButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Text(stringResource(R.string.settings)) }
    }

    if (showBlank) {
        BlankCanvasDialog(
            onDismiss = { showBlank = false },
            onCreate = { w, h, color ->
                showBlank = false
                vm.startBlank(w, h, color)
            },
        )
    }
    if (showSettings) {
        SettingsDialog(
            context = context,
            onDismiss = { showSettings = false },
            onLanguage = { lang ->
                showSettings = false
                onLanguage(lang)
            },
        )
    }
}
