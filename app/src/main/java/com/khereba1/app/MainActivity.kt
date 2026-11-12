package com.khereba1.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {

    private val vm: HarfViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            HarfTheme {
                AppRoot(vm = vm, onLanguage = { lang ->
                    vm.saveNow()
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", lang).apply()
                    recreate()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }

    /** Photos shared / opened from other apps (Gallery "Edit with", Share sheet). */
    private fun handleIncoming(i: Intent?) {
        if (i == null) return
        val action = i.action ?: return
        var uri: Uri? = null
        if (action == Intent.ACTION_SEND) {
            val type = i.type
            if (type != null && type.startsWith("image/")) {
                uri = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
            }
        } else if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            uri = i.data
        }
        if (uri != null) vm.startPhoto(uri)
    }
}

@Composable
fun AppRoot(vm: HarfViewModel, onLanguage: (String) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val message: String? = vm.toastRes?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearToast()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!vm.ready) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (vm.project == null) {
                StartScreen(vm = vm, onLanguage = onLanguage)
            } else {
                EditorScreen(vm = vm)
            }
        }
    }
}
