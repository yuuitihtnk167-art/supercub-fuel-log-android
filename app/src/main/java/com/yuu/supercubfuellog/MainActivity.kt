package com.yuu.supercubfuellog

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.yuu.supercubfuellog.ui.AppRoot
import com.yuu.supercubfuellog.ui.SupercubFuelLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SupercubFuelLogTheme {
                val context = LocalContext.current
                val viewModel: MainViewModel = viewModel()
                val webClientId = remember { resolveWebClientId(context) }
                val isWebClientIdConfigured = remember(webClientId) {
                    webClientId.isNotBlank() && webClientId != WEB_CLIENT_ID_PLACEHOLDER
                }

                val gso = remember {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                }
                val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

                val signInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    viewModel.handleGoogleSignInResult(result.data)
                }

                AppRoot(
                    viewModel = viewModel,
                    onSignInClick = {
                        if (!isWebClientIdConfigured) {
                            viewModel.postMessage(
                                "Googleログイン設定が未完了です。web_client_id を設定するか、Firebase設定更新後の google-services.json を再配置してください。"
                            )
                            return@AppRoot
                        }
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    },
                    onSignOutClick = { viewModel.signOut(googleSignInClient) },
                )
            }
        }
    }

    private companion object {
        const val WEB_CLIENT_ID_PLACEHOLDER = "REPLACE_WITH_WEB_CLIENT_ID"
    }

    private fun resolveWebClientId(context: Context): String {
        val custom = context.getString(R.string.web_client_id).trim()
        if (custom.isNotBlank() && custom != WEB_CLIENT_ID_PLACEHOLDER) {
            return custom
        }

        val generatedId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (generatedId != 0) {
            val generated = context.getString(generatedId).trim()
            if (generated.isNotBlank()) {
                return generated
            }
        }
        return custom
    }
}
