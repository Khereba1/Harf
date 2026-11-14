package com.khereba1.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var showProjects by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.startPhoto(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1410), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background),
                ),
            )
            .padding(28.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF8A5B), Color(0xFFC8442B)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ح",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Harf",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !vm.busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                HIcon(R.drawable.ic_image, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.pick_photo), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { showBlank = true },
                enabled = !vm.busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                HIcon(R.drawable.ic_canvas, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.blank_canvas))
            }
            OutlinedButton(
                onClick = { showProjects = true },
                enabled = !vm.busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                HIcon(R.drawable.ic_folder, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.my_projects))
            }
            if (vm.busy) CircularProgressIndicator()
        }
        IconAction(
            res = R.drawable.ic_settings,
            desc = stringResource(R.string.settings),
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.BottomCenter),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showProjects) {
        ProjectsDialog(vm = vm, onDismiss = { showProjects = false })
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
