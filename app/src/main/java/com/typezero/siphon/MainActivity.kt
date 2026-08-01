package com.typezero.siphon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.typezero.siphon.ui.SiphonViewModel
import com.typezero.siphon.ui.Tab
import com.typezero.siphon.ui.screens.SiphonRoot
import com.typezero.siphon.ui.theme.SiphonTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SiphonApp).container }
    private val vm: SiphonViewModel by viewModels { SiphonViewModel.Factory(container) }

    private val mediaPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val requestMediaPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            vm.onPermissionResult(mediaPermissions.all { grants[it] == true || hasPermission(it) })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasMediaPermissions()) vm.onPermissionResult(true)
        handleShareIntent(intent)

        setContent {
            SiphonTheme {
                SiphonRoot(
                    vm = vm,
                    onRequestPermission = { requestMediaPermissions.launch(mediaPermissions) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasMediaPermissions(): Boolean = mediaPermissions.all(::hasPermission)

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { shared ->
                vm.setLinkUrl(shared.trim())
                vm.selectTab(Tab.LINK)
            }
        }
    }
}
