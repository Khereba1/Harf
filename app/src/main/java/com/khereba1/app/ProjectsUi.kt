package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun shareProjectFile(context: Context, uri: Uri) {
    try {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "application/octet-stream"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Throwable) {
        AppLog.log("SHARE", "Sharing project failed", e)
    }
}

/** "My projects": the saved projects with thumbnails, plus import of a .harf file. */
@Composable
fun ProjectsDialog(vm: HarfViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<SavedInfo?>(null) }
    var deleting by remember { mutableStateOf<SavedInfo?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importProject(uri)
    }
    LaunchedEffect(Unit) { vm.refreshSaved() }
    CardDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.my_projects), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            if (vm.saved.isEmpty()) {
                Text(
                    text = stringResource(R.string.projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(vm.saved, key = { it.id }) { info ->
                        ProjectRow(
                            info = info,
                            onOpen = {
                                vm.openProject(info.id)
                                onDismiss()
                            },
                            onRename = { renaming = info },
                            onExport = { vm.exportSaved(info.id, info.name) { uri -> shareProjectFile(context, uri) } },
                            onDelete = { deleting = info },
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.import_project))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
    val r = renaming
    if (r != null) {
        var text by remember(r.id) { mutableStateOf(r.name) }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameSaved(r.id, text)
                        renaming = null
                    },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val d = deleting
    if (d != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_project),
            message = d.name,
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteSaved(d.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun ProjectRow(info: SavedInfo, onOpen: () -> Unit, onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val thumb = remember(info.id, info.modified) {
        try {
            info.thumb?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        } catch (e: Throwable) {
            null
        }
    }
    var menu by remember { mutableStateOf(false) }
    val date = remember(info.modified) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(info.modified))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(bitmap = thumb, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
            } else {
                HIcon(R.drawable.ic_image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(info.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(date, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconAction(R.drawable.ic_more, null, { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename_project)) }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.export_project)) }, onClick = {
                    menu = false
                    onExport()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}
