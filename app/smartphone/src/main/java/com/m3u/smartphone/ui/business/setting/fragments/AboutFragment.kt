package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.smartphone.R
import com.m3u.smartphone.ui.material.components.Preference
import com.m3u.smartphone.ui.material.components.TrailingIconPreference
import com.m3u.smartphone.ui.material.model.LocalSpacing

private const val GITHUB_URL  = "https://github.com/jorgedihe/iptv"
private const val PRIVACY_URL = "https://jorgedihe.net/iptv-jdh/privacy.html"
private const val UPSTREAM_URL = "https://github.com/oxyroid/M3UAndroid"

@Composable
internal fun AboutFragment(
    versionName: String,
    versionCode: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = spacing.medium, vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero: logo + name + version ─────────────────────────────────
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = "IPTV JDH",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "Versión $versionName",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = "build $versionCode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(spacing.medium))

        // ── Author credit ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Hecho con",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "por jorgedihe",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(spacing.small))

        // ── External links ─────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            TrailingIconPreference(
                title = "Ver código en GitHub",
                content = "github.com/jorgedihe/iptv",
                icon = Icons.Rounded.Code,
                trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = { uriHandler.openUri(GITHUB_URL) }
            )
            TrailingIconPreference(
                title = "Política de privacidad",
                content = "jorgedihe.net/iptv-jdh/privacy",
                icon = Icons.Rounded.PrivacyTip,
                trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = { uriHandler.openUri(PRIVACY_URL) }
            )
        }

        Spacer(Modifier.height(spacing.medium))

        // ── Upstream attribution (GPL-3.0 requirement) ─────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ATRIBUCIONES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Esta aplicación está basada en el proyecto open-source M3UAndroid de oxyroid, distribuido bajo licencia GPL-3.0. " +
                            "El código fuente de IPTV JDH se mantiene público en GitHub bajo la misma licencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(2.dp))
                Preference(
                    title = "Proyecto original (M3UAndroid)",
                    content = "github.com/oxyroid/M3UAndroid",
                    onClick = { uriHandler.openUri(UPSTREAM_URL) }
                )
            }
        }

        Spacer(Modifier.height(spacing.large))

        Text(
            text = "© 2026 jorgedihe",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
