package com.ty.wifikeyxposed

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.DynamicColors
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class SettingsActivity : ComponentActivity(), XposedServiceHelper.OnServiceListener {
    private var remotePrefs by mutableStateOf<SharedPreferences?>(null)
    private var isServiceBound by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        
        // 使用 XposedServiceHelper 绑定服务
        try {
            XposedServiceHelper.registerListener(this)
        } catch (e: Exception) {
            // 回退逻辑：如果不在 Xposed 环境，尝试使用本地 SP (仅用于预览或测试)
            isServiceBound = true
            remotePrefs = getSharedPreferences("settings", MODE_PRIVATE)
        }

        setContent {
            AppTheme {
                if (isServiceBound && remotePrefs != null) {
                    SettingsScreen(remotePrefs!!) { finish() }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在连接 LSPosed 服务...", fontSize = 14.sp)
                            Text("请确保模块已在 LSPosed 中启用并重启", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        // 获取远程偏好设置，文件名为 "settings"
        remotePrefs = service.getRemotePreferences("settings")
        isServiceBound = true
    }

    override fun onServiceDied(service: XposedService) {
        isServiceBound = false
        remotePrefs = null
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        DynamicColors.isDynamicColorAvailable() -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    // 默认值全部设为 false，确保同步逻辑严谨
    var blockNews by remember { mutableStateOf(prefs.getBoolean("block_news", false)) }
    var unlockVip by remember { mutableStateOf(prefs.getBoolean("unlock_vip", false)) }
    var deepCleanVip by remember { mutableStateOf(prefs.getBoolean("deep_clean_vip", false)) }
    var removeAds by remember { mutableStateOf(prefs.getBoolean("remove_ads", false)) }
    var liteTeenager by remember { mutableStateOf(prefs.getBoolean("lite_teenager", false)) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Wifi万能钥匙增强", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "功能增强 (已连接远程服务)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )

            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingItem(
                        title = "拦截广告推送",
                        subtitle = "极致过滤通知栏新闻内容",
                        icon = Icons.Default.Notifications,
                        checked = blockNews,
                        onCheckedChange = {
                            blockNews = it
                            prefs.edit().putBoolean("block_news", it).apply()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "解锁本地会员",
                        subtitle = "开启极速连接、SVIP 标识等特权",
                        icon = Icons.Default.Star,
                        checked = unlockVip,
                        onCheckedChange = {
                            unlockVip = it
                            prefs.edit().putBoolean("unlock_vip", it).apply()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "极致净化 (隐藏会员入口)",
                        subtitle = "隐藏我的页面所有会员相关的横幅与图标",
                        icon = Icons.Default.CleaningServices,
                        checked = deepCleanVip,
                        onCheckedChange = {
                            deepCleanVip = it
                            prefs.edit().putBoolean("deep_clean_vip", it).apply()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "去除内置广告",
                        subtitle = "拦截开屏、列表及视频广告",
                        icon = Icons.Default.Clear,
                        checked = removeAds,
                        onCheckedChange = {
                            removeAds = it
                            prefs.edit().putBoolean("remove_ads", it).apply()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简版青少年模式",
                        subtitle = "利用内置拦截实现净化，无需密码且无时长限制",
                        icon = Icons.Default.ChildCare,
                        checked = liteTeenager,
                        onCheckedChange = {
                            liteTeenager = it
                            prefs.edit().putBoolean("lite_teenager", it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, fontSize = 13.sp) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
