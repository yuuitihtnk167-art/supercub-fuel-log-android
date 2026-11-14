package com.yuu.supercubfuellog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuu.supercubfuellog.R

private val CubGreen = Color(0xFF244F43)
private val WarmCream = Color(0xFFFFF7E9)

/** Stateless so the welcome design can be previewed without Firebase or Google sign-in. */
@Composable
internal fun LoginWelcome(
    approvalMessage: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    loginButton: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(WarmCream, Color(0xFFF2EBDD)))
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SUPER CUB FUEL LOG",
                color = CubGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            if (approvalMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF4DFD0),
                    contentColor = Color(0xFF7A3326),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        approvalMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Image(
                painter = painterResource(R.drawable.login_cub_ride),
                contentDescription = "紅葉の道をスーパーカブで走るライダー",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "今日もカブと、\nひと走り。",
                color = CubGreen,
                fontSize = 29.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "給油の記録を、次のツーリングへ。",
                color = Color(0xFF626B5E),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            loginButton()
            Spacer(Modifier.height(16.dp))
            Text(
                "同じGoogleアカウントで、\n記録を引き継げます。",
                color = Color(0xFF626B5E),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun WelcomeLoginButton(
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CubGreen,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFB8C7B9),
            disabledContentColor = Color(0xFF314C40)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            if (inProgress) "ログイン中…" else "Googleでログイン",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 720, showBackground = true)
@Preview(name = "Small phone", widthDp = 320, heightDp = 480, showBackground = true)
@Composable
private fun LoginWelcomePreview() {
    MaterialTheme {
        LoginWelcome {
            WelcomeLoginButton(enabled = true, inProgress = false, onClick = {})
        }
    }
}

@Preview(name = "Approval notice", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun LoginApprovalNoticePreview() {
    MaterialTheme {
        LoginWelcome(approvalMessage = "利用には管理者の承認が必要です") {
            WelcomeLoginButton(enabled = true, inProgress = false, onClick = {})
        }
    }
}

@Preview(name = "Small phone scrolled", widthDp = 320, heightDp = 480, fontScale = 1.3f, showBackground = true)
@Composable
private fun LoginSmallScrolledPreview() {
    MaterialTheme {
        LoginWelcome(scrollState = rememberScrollState(initial = Int.MAX_VALUE)) {
            WelcomeLoginButton(enabled = true, inProgress = false, onClick = {})
        }
    }
}
