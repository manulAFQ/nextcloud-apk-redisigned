package ru.manul.nextcloudgallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.manul.nextcloudgallery.ui.theme.CloudBlue

enum class AppTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("Дом", Icons.Outlined.Home, Icons.Rounded.Home),
    ALBUMS("Альбомы", Icons.Outlined.Collections, Icons.Rounded.Collections),
    MARKS("Отметки", Icons.Outlined.StarOutline, Icons.Rounded.Star),
    SETTINGS("Настройки", Icons.Outlined.Settings, Icons.Rounded.Settings)
}

@Composable
internal fun FloatingBottomBar(
    selected: AppTab,
    onSelected: (AppTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    BottomTabItem(
                        tab = tab,
                        selected = selected == tab,
                        onClick = { onSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val gearRotation = remember { Animatable(0f) }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.82f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.46f, stiffness = 550f),
        label = "navIconScale"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -2.5f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "navIconLift"
    )
    LaunchedEffect(selected, tab) {
        if (selected && tab == AppTab.SETTINGS) {
            gearRotation.snapTo(0f)
            gearRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
    }

    Surface(
        modifier = modifier
            .height(56.dp)
            .scale(if (pressed) 0.97f else 1f),
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) CloudBlue.copy(alpha = 0.11f) else Color.Transparent
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Spacer(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CloudBlue.copy(alpha = 0.08f))
                    )
                }
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) CloudBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationY = lift
                            rotationZ = if (tab == AppTab.SETTINGS) gearRotation.value else 0f
                    }
                )
            }
        }
    }
}
