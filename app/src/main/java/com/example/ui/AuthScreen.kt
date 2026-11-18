package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AuthManager
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonEmber
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.ObsidianPine
import com.example.ui.theme.OnAmberInk
import com.example.ui.theme.SlateMist
import com.example.ui.theme.SunlitAmber

@Composable
fun AuthScreen(
    textureProgress: Float,
    texturesReady: Boolean,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            scope = scope,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* stay on interactive sign-in card */ }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ObsidianPine.copy(alpha = 0.55f),
                        Color.Transparent,
                        ObsidianPine.copy(alpha = 0.85f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            color = GlassSurfaceDark,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wildwood_emblem_1795040057599),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WILDWOOD: REALITY",
                    style = MaterialTheme.typography.displaySmall,
                    color = BoneWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(id = R.string.subtitle_alpine_hunt),
                    style = MaterialTheme.typography.labelSmall,
                    color = SunlitAmber,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(id = R.string.sign_in_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateMist,
                    textAlign = TextAlign.Center
                )

                if (!texturesReady) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${stringResource(id = R.string.synthesizing_textures)} ${(textureProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMist
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { textureProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SunlitAmber,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = CrimsonContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CrimsonEmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = BoneWhite,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        AuthManager.signInWithGoogle(
                            context = context,
                            scope = scope,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunlitAmber,
                        contentColor = OnAmberInk
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = OnAmberInk,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(id = R.string.sign_in_google),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
