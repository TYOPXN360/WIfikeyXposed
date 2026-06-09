package com.ty.wifikeyxposed

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
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

    companion object {
        @Volatile
        private var cachedService: XposedService? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        
        try {
            XposedServiceHelper.registerListener(this)
            // 如果服务已经绑定过（从后台恢复），立即回调避免卡死
            cachedService?.let { onServiceBind(it) }
        } catch (e: Exception) {
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
                        }
                    }
                }
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        cachedService = service
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
    val context = LocalContext.current
    
    // 监听后台偏好设置变化
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            tick++ // 触发重组
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 使用 key 来确保重组时重新读取真实值
    var blockNews by remember(tick) { mutableStateOf(prefs.getBoolean("block_news", false)) }
    var unlockVip by remember(tick) { mutableStateOf(prefs.getBoolean("unlock_vip", false)) }
    var deepCleanVip by remember(tick) { mutableStateOf(prefs.getBoolean("deep_clean_vip", false)) }
    var removeAds by remember(tick) { mutableStateOf(prefs.getBoolean("remove_ads", false)) }
    var blockCoinTaskBall by remember(tick) { mutableStateOf(prefs.getBoolean("block_coin_task_ball", false)) }
    var liteTeenager by remember(tick) { mutableStateOf(prefs.getBoolean("lite_teenager", false)) }
    var removeCloudControl by remember(tick) { mutableStateOf(prefs.getBoolean("remove_cloud_control", false)) }

    // 首页小组件精简状态
    var hideToolClean by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_clean", false)) }
    var hideToolSpeedup by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_speedup", false)) }
    var hideToolCooling by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_cooling", false)) }
    var hideToolSpeedtest by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_speedtest", false)) }
    var hideToolNetwork by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_network", false)) }
    var hideToolSecurity by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_security", false)) }
    var hideToolKuaikan by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_kuaikan", false)) }
    var hideToolNovel by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_novel", false)) }
    var hideToolGame by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_game", false)) }
    var hideToolMore by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_more", false)) }
    var hideToolVip by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_vip", false)) }
    var hideToolUser by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_user", false)) }
    var hideToolIm by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_im", false)) }
    var hideToolEmpower by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_empower", false)) }
    var hideToolDynamicCard by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_dynamic_card", false)) }
    var hideToolTarget30 by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_target30", false)) }
    var hideToolArea by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tool_area", false)) }

    // WiFi 防护状态
    var blockWifiClearConfig by remember(tick) { mutableStateOf(prefs.getBoolean("block_wifi_clear_config", false)) }
    var blockWifiDeleteModel by remember(tick) { mutableStateOf(prefs.getBoolean("block_wifi_delete_model", false)) }
    var blockWifiPostClean by remember(tick) { mutableStateOf(prefs.getBoolean("block_wifi_post_clean", false)) }
    var bypassQsGuide by remember(tick) { mutableStateOf(prefs.getBoolean("bypass_qs_guide", false)) }
    var bypassOverlayGuide by remember(tick) { mutableStateOf(prefs.getBoolean("bypass_overlay_guide", false)) }

    // 底栏精简状态
    var hideHome by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_home", false)) }
    var hideNearby by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_nearby", false)) }
    var hideVideo by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_video", false)) }
    var hideWelfare by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_welfare", false)) }
    var hideIm by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_im", false)) }
    var hideWeb by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_web", false)) }
    var hideGuard by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_guard", false)) }
    var hideMe by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_me", false)) }
    // v5.2.18 新增底栏 Tab
    var hideDeepseek by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_deepseek", false)) }
    var hideShopmall by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_shopmall", false)) }
    var hideBus by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_bus", false)) }
    var hideFilm by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_film", false)) }
    var hideAi by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_ai", false)) }
    var hideKouxin by remember(tick) { mutableStateOf(prefs.getBoolean("hide_tab_kouxin", false)) }

    // 对话框状态
    var showHomeWarning by remember { mutableStateOf(false) }
    var showMeWarning by remember { mutableStateOf(false) }

    val allSelected = blockNews && unlockVip && deepCleanVip && removeAds && blockCoinTaskBall && liteTeenager && removeCloudControl &&
            hideNearby && hideVideo && hideWelfare && hideIm && hideWeb && hideGuard &&
            hideDeepseek && hideShopmall && hideBus && hideFilm && hideAi && hideKouxin &&
            hideToolClean && hideToolSpeedup && hideToolCooling && hideToolSpeedtest &&
            hideToolNetwork && hideToolSecurity && hideToolKuaikan && hideToolNovel && hideToolGame && hideToolMore &&
            hideToolVip && hideToolUser && hideToolIm && hideToolEmpower && hideToolDynamicCard && hideToolTarget30 && hideToolArea
            // 注意：根据要求，全选默认不包含 hideHome 和 hideMe

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Wifi万能钥匙增强", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val target = !allSelected
                        blockNews = target
                        unlockVip = target
                        deepCleanVip = target
                        removeAds = target
                        blockCoinTaskBall = target
                        liteTeenager = target
                        removeCloudControl = target
                        
                        hideNearby = target
                        hideVideo = target
                        hideWelfare = target
                        hideIm = target
                        hideWeb = target
                        hideGuard = target
                        hideDeepseek = target
                        hideShopmall = target
                        hideBus = target
                        hideFilm = target
                        hideAi = target
                        hideKouxin = target

                        hideToolClean = target
                        hideToolSpeedup = target
                        hideToolCooling = target
                        hideToolSpeedtest = target
                        hideToolNetwork = target
                        hideToolSecurity = target
                        hideToolKuaikan = target
                        hideToolNovel = target
                        hideToolGame = target
                        hideToolMore = target
                        hideToolVip = target
                        hideToolUser = target
                        hideToolIm = target
                        hideToolEmpower = target
                        hideToolDynamicCard = target
                        hideToolTarget30 = target
                        hideToolArea = target

                        prefs.edit().run {
                            putBoolean("block_news", target)
                            putBoolean("unlock_vip", target)
                            putBoolean("deep_clean_vip", target)
                            putBoolean("remove_ads", target)
                            putBoolean("block_coin_task_ball", target)
                            putBoolean("lite_teenager", target)
                            putBoolean("remove_cloud_control", target)
                            
                            putBoolean("hide_tab_nearby", target)
                            putBoolean("hide_tab_video", target)
                            putBoolean("hide_tab_welfare", target)
                            putBoolean("hide_tab_im", target)
                            putBoolean("hide_tab_web", target)
                            putBoolean("hide_tab_guard", target)
                            putBoolean("hide_tab_deepseek", target)
                            putBoolean("hide_tab_shopmall", target)
                            putBoolean("hide_tab_bus", target)
                            putBoolean("hide_tab_film", target)
                            putBoolean("hide_tab_ai", target)
                            putBoolean("hide_tab_kouxin", target)

                            putBoolean("hide_tool_clean", target)
                            putBoolean("hide_tool_speedup", target)
                            putBoolean("hide_tool_cooling", target)
                            putBoolean("hide_tool_speedtest", target)
                            putBoolean("hide_tool_network", target)
                            putBoolean("hide_tool_security", target)
                            putBoolean("hide_tool_kuaikan", target)
                            putBoolean("hide_tool_novel", target)
                            putBoolean("hide_tool_game", target)
                            putBoolean("hide_tool_more", target)
                            putBoolean("hide_tool_vip", target)
                            putBoolean("hide_tool_user", target)
                            putBoolean("hide_tool_im", target)
                            putBoolean("hide_tool_empower", target)
                            putBoolean("hide_tool_dynamic_card", target)
                            putBoolean("hide_tool_target30", target)
                            putBoolean("hide_tool_area", target)
                            apply()
                        }
                    }) {
                        Text(if (allSelected) "取消全选" else "全选")
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
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "去除云控系统",
                        subtitle = "屏蔽远程策略下发，防止本地功能被服务器覆盖",
                        icon = Icons.Default.CloudOff,
                        checked = removeCloudControl,
                        onCheckedChange = {
                            removeCloudControl = it
                            prefs.edit().putBoolean("remove_cloud_control", it).apply()
                        }
                    )
                    
                    if (removeCloudControl) {
                        Box(modifier = Modifier.padding(start = 56.dp, bottom = 12.dp, end = 16.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val packageName = "com.snda.wifilocating"
                                    val clearAction = "com.ty.wifikeyxposed.ACTION_CLEAR_CLOUD"
                                    val intent = Intent(clearAction).apply { setPackage(packageName) }
                                    context.sendBroadcast(intent)
                                    Toast.makeText(context, "云控配置已清除", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("清除云控配置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "精简底栏 (极致净化)",
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
                        title = "精简首页 / 连接",
                        subtitle = "核心功能项，谨慎开启",
                        icon = Icons.Default.Home,
                        checked = hideHome,
                        onCheckedChange = { if (it) showHomeWarning = true else { hideHome = false; prefs.edit().putBoolean("hide_tab_home", false).apply() } }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简发现 / 附近",
                        subtitle = "隐藏社交与周边信息入口",
                        icon = Icons.Default.Explore,
                        checked = hideNearby,
                        onCheckedChange = { hideNearby = it; prefs.edit().putBoolean("hide_tab_nearby", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简视频",
                        subtitle = "隐藏短视频流入口",
                        icon = Icons.Default.VideoLibrary,
                        checked = hideVideo,
                        onCheckedChange = { hideVideo = it; prefs.edit().putBoolean("hide_tab_video", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简福利 / 赚钱",
                        subtitle = "隐藏任务中心与广告任务入口",
                        icon = Icons.Default.MonetizationOn,
                        checked = hideWelfare,
                        onCheckedChange = { hideWelfare = it; prefs.edit().putBoolean("hide_tab_welfare", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简消息 / 聊天",
                        subtitle = "隐藏即时通讯与通知消息入口",
                        icon = Icons.Default.Message,
                        checked = hideIm,
                        onCheckedChange = { hideIm = it; prefs.edit().putBoolean("hide_tab_im", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简资讯 / 网页",
                        subtitle = "隐藏内置新闻浏览器入口",
                        icon = Icons.Default.Public,
                        checked = hideWeb,
                        onCheckedChange = { hideWeb = it; prefs.edit().putBoolean("hide_tab_web", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简安全守护",
                        subtitle = "隐藏检测与安全增强入口",
                        icon = Icons.Default.Shield,
                        checked = hideGuard,
                        onCheckedChange = { hideGuard = it; prefs.edit().putBoolean("hide_tab_guard", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简我的",
                        subtitle = "重要：开启后将失去模块设置入口",
                        icon = Icons.Default.Person,
                        checked = hideMe,
                        onCheckedChange = { if (it) showMeWarning = true else { hideMe = false; prefs.edit().putBoolean("hide_tab_me", false).apply() } }
                    )
                    // v5.2.18 新增底栏 Tab
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简 DeepSeek AI",
                        subtitle = "隐藏 AI 对话助手入口",
                        icon = Icons.Default.Psychology,
                        checked = hideDeepseek,
                        onCheckedChange = { hideDeepseek = it; prefs.edit().putBoolean("hide_tab_deepseek", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简商城",
                        subtitle = "隐藏电商购物入口",
                        icon = Icons.Default.ShoppingCart,
                        checked = hideShopmall,
                        onCheckedChange = { hideShopmall = it; prefs.edit().putBoolean("hide_tab_shopmall", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简公交出行",
                        subtitle = "隐藏公交出行服务入口",
                        icon = Icons.Default.DirectionsBus,
                        checked = hideBus,
                        onCheckedChange = { hideBus = it; prefs.edit().putBoolean("hide_tab_bus", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简电影",
                        subtitle = "隐藏电影购票入口",
                        icon = Icons.Default.Movie,
                        checked = hideFilm,
                        onCheckedChange = { hideFilm = it; prefs.edit().putBoolean("hide_tab_film", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简 AI 连接",
                        subtitle = "隐藏 AI 智能连接入口",
                        icon = Icons.Default.AutoAwesome,
                        checked = hideAi,
                        onCheckedChange = { hideAi = it; prefs.edit().putBoolean("hide_tab_ai", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简逛逛",
                        subtitle = "隐藏逛逛社交入口",
                        icon = Icons.Default.Chat,
                        checked = hideKouxin,
                        onCheckedChange = { hideKouxin = it; prefs.edit().putBoolean("hide_tab_kouxin", it).apply() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "精简首页组件 (极致净化)",
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
                        title = "精简工具栏总开关",
                        subtitle = "隐藏首页整个工具栏区域",
                        icon = Icons.Default.Apps,
                        checked = hideToolArea,
                        onCheckedChange = { hideToolArea = it; prefs.edit().putBoolean("hide_tool_area", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简垃圾清理",
                        subtitle = "隐藏首页工具栏中的清理入口",
                        icon = Icons.Default.Delete,
                        checked = hideToolClean,
                        onCheckedChange = { hideToolClean = it; prefs.edit().putBoolean("hide_tool_clean", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简手机加速",
                        subtitle = "隐藏首页工具栏中的加速入口",
                        icon = Icons.Default.Speed,
                        checked = hideToolSpeedup,
                        onCheckedChange = { hideToolSpeedup = it; prefs.edit().putBoolean("hide_tool_speedup", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简手机降温",
                        subtitle = "隐藏首页工具栏中的降温入口",
                        icon = Icons.Default.Thermostat,
                        checked = hideToolCooling,
                        onCheckedChange = { hideToolCooling = it; prefs.edit().putBoolean("hide_tool_cooling", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简网络测速",
                        subtitle = "隐藏首页工具栏中的测速入口",
                        icon = Icons.Default.WifiTethering,
                        checked = hideToolSpeedtest,
                        onCheckedChange = { hideToolSpeedtest = it; prefs.edit().putBoolean("hide_tool_speedtest", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简网络加速",
                        subtitle = "隐藏首页工具栏中的网络加速入口",
                        icon = Icons.Default.NetworkCheck,
                        checked = hideToolNetwork,
                        onCheckedChange = { hideToolNetwork = it; prefs.edit().putBoolean("hide_tool_network", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简安全检测",
                        subtitle = "隐藏首页工具栏中的安全检测入口",
                        icon = Icons.Default.Security,
                        checked = hideToolSecurity,
                        onCheckedChange = { hideToolSecurity = it; prefs.edit().putBoolean("hide_tool_security", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简快看",
                        subtitle = "隐藏首页工具栏中的快看入口",
                        icon = Icons.Default.PlayCircle,
                        checked = hideToolKuaikan,
                        onCheckedChange = { hideToolKuaikan = it; prefs.edit().putBoolean("hide_tool_kuaikan", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简免费小说",
                        subtitle = "隐藏首页工具栏中的免费小说入口",
                        icon = Icons.Default.Book,
                        checked = hideToolNovel,
                        onCheckedChange = { hideToolNovel = it; prefs.edit().putBoolean("hide_tool_novel", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简游戏中心",
                        subtitle = "隐藏首页工具栏中的游戏中心入口",
                        icon = Icons.Default.Gamepad,
                        checked = hideToolGame,
                        onCheckedChange = { hideToolGame = it; prefs.edit().putBoolean("hide_tool_game", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简更多",
                        subtitle = "隐藏首页工具栏中的更多入口",
                        icon = Icons.Default.MoreVert,
                        checked = hideToolMore,
                        onCheckedChange = { hideToolMore = it; prefs.edit().putBoolean("hide_tool_more", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简顶部 VIP",
                        subtitle = "隐藏右上角 VIP 会员入口",
                        icon = Icons.Default.CardMembership,
                        checked = hideToolVip,
                        onCheckedChange = { hideToolVip = it; prefs.edit().putBoolean("hide_tool_vip", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简用户信息",
                        subtitle = "隐藏首页顶部的用户头像与昵称",
                        icon = Icons.Default.AccountCircle,
                        checked = hideToolUser,
                        onCheckedChange = { hideToolUser = it; prefs.edit().putBoolean("hide_tool_user", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简 IM 提醒",
                        subtitle = "隐藏首页的消息/通知弹窗提醒",
                        icon = Icons.Default.Sms,
                        checked = hideToolIm,
                        onCheckedChange = { hideToolIm = it; prefs.edit().putBoolean("hide_tool_im", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简赋能面板",
                        subtitle = "隐藏连接成功后的功能赋能面板",
                        icon = Icons.Default.Extension,
                        checked = hideToolEmpower,
                        onCheckedChange = { hideToolEmpower = it; prefs.edit().putBoolean("hide_tool_empower", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简动态卡片",
                        subtitle = "隐藏首页底部的各类动态广告/内容卡片",
                        icon = Icons.Default.FeaturedPlayList,
                        checked = hideToolDynamicCard,
                        onCheckedChange = { hideToolDynamicCard = it; prefs.edit().putBoolean("hide_tool_dynamic_card", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "屏蔽签到浮球",
                        subtitle = "隐藏主页右下角的「签到领现金」浮动广告球",
                        icon = Icons.Default.Ballot,
                        checked = blockCoinTaskBall,
                        onCheckedChange = { blockCoinTaskBall = it; prefs.edit().putBoolean("block_coin_task_ball", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "精简 Target 30",
                        subtitle = "隐藏 Target 30 相关的升级或指窗提示",
                        icon = Icons.Default.Warning,
                        checked = hideToolTarget30,
                        onCheckedChange = { hideToolTarget30 = it; prefs.edit().putBoolean("hide_tool_target30", it).apply() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "WiFi 防护 (阻止静默操作)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )
            Text(
                "⚠️ 以下前三项为实验性功能，可能不生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
            )

            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingItem(
                        title = "阻止连接前清除网络",
                        subtitle = "[实验性] 拦截连接 WiFi 前静默删除 App 自建网络配置的行为",
                        icon = Icons.Default.Wifi,
                        checked = blockWifiClearConfig,
                        onCheckedChange = { blockWifiClearConfig = it; prefs.edit().putBoolean("block_wifi_clear_config", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "阻止先删后加模式",
                        subtitle = "[实验性] 拦截连接时先删除旧配置再添加新配置的 useDeleteModel 行为",
                        icon = Icons.Default.Block,
                        checked = blockWifiDeleteModel,
                        onCheckedChange = { blockWifiDeleteModel = it; prefs.edit().putBoolean("block_wifi_delete_model", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "阻止失败后清理配置",
                        subtitle = "[实验性] 拦截连接失败后静默清理 WiFi 网络配置的行为",
                        icon = Icons.Default.CleaningServices,
                        checked = blockWifiPostClean,
                        onCheckedChange = { blockWifiPostClean = it; prefs.edit().putBoolean("block_wifi_post_clean", it).apply() }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        title = "跳过 QS 磁贴引导",
                        subtitle = "连接 WiFi 时跳过「添加快速设置磁贴」引导弹窗，直接连接",
                        icon = Icons.Default.Speed,
                        checked = bypassQsGuide,
                        onCheckedChange = { bypassQsGuide = it; prefs.edit().putBoolean("bypass_qs_guide", it).apply() }
                    )
                    SettingItem(
                        title = "跳过悬浮窗权限引导",
                        subtitle = "连接 WiFi 时跳过「授予悬浮窗权限」引导弹窗，直接连接",
                        icon = Icons.Default.Layers,
                        checked = bypassOverlayGuide,
                        onCheckedChange = { bypassOverlayGuide = it; prefs.edit().putBoolean("bypass_overlay_guide", it).apply() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val packageName = "com.snda.wifilocating"
                    val mainActivity = "com.wifitutu.ui.launcher.LauncherActivity"
                    val restartAction = "com.ty.wifikeyxposed.ACTION_RESTART"
                    
                    // 1. 发送免 Root 自杀广播
                    val intent = Intent(restartAction).apply {
                        setPackage(packageName)
                    }
                    context.sendBroadcast(intent)
                    
                    Toast.makeText(context, "重启中...", Toast.LENGTH_SHORT).show()
                    
                    // 2. 延迟 1.5 秒后，通过显式类名直接拉起
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val launchIntent = Intent().apply {
                                component = ComponentName(packageName, mainActivity)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            }
                            context.startActivity(launchIntent)
                        } catch (e: Exception) {
                            // 回退到常规启动
                            val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                            fallback?.let { context.startActivity(it) }
                        }
                    }, 1500)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重启应用", fontWeight = FontWeight.Bold)
            }
            
            Text(
                "提示：利用显式 Intent 直连主入口，实现最高拉起成功率",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp)
            )
        }
    }

    // 警告对话框：精简首页
    if (showHomeWarning) {
        var countdown by remember { mutableIntStateOf(3) }
        LaunchedEffect(Unit) {
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
        }
        AlertDialog(
            onDismissRequest = { showHomeWarning = false },
            title = { Text("风险提示") },
            text = { Text("开启后将可能失去软件主功能，你确定要开启吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        hideHome = true
                        prefs.edit().putBoolean("hide_tab_home", true).apply()
                        showHomeWarning = false
                    },
                    enabled = countdown == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (countdown == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (countdown > 0) "确定 (${countdown}s)" else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHomeWarning = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 警告对话框：精简我的
    if (showMeWarning) {
        var countdown by remember { mutableIntStateOf(3) }
        LaunchedEffect(Unit) {
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
        }
        AlertDialog(
            onDismissRequest = { showMeWarning = false },
            title = { Text("风险提示") },
            text = { Text("开启后将失去应用内进入模块的入口，确定要开启吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        hideMe = true
                        prefs.edit().putBoolean("hide_tab_me", true).apply()
                        showMeWarning = false
                    },
                    enabled = countdown == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (countdown == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (countdown > 0) "确定 (${countdown}s)" else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMeWarning = false }) {
                    Text("取消")
                }
            }
        )
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
