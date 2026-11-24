package com.maslak.gps.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maslak.gps.BuildConfig
import com.maslak.gps.data.MockMode
import com.maslak.gps.map.OsmMap
import com.maslak.gps.mock.RunPhase

@Composable
fun MainScreen(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val phase by vm.phase.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var showSaved by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var fullMap by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) }
    }

    val running = phase != RunPhase.IDLE
    fun openDevSettings() {
        val i = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
        }
    }

    Scaffold(
        containerColor = Palette.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Header(
                phase = phase,
                coords = (current ?: vm.pin).format(),
                onInfo = { vm.showHelp = true },
                onClose = { (context as? Activity)?.finish() },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── البحث ──
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().border(1.dp, Palette.CardBorder, RoundedCornerShape(18.dp)),
                    placeholder = { Text("مكان، إحداثيات، أو رابط Google Maps", color = Palette.TextDim) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Palette.TextDim) },
                    trailingIcon = {
                        when {
                            vm.searching -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            query.isNotEmpty() -> IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, null, tint = Palette.TextDim)
                            }
                            else -> IconButton(onClick = {
                                clipboard.getText()?.text?.let { query = it; vm.search(it) }
                            }) { Icon(Icons.Filled.ContentPaste, "لصق", tint = Palette.TextDim) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.Card,
                        unfocusedContainerColor = Palette.Card,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Palette.Green,
                    ),
                )

                // ── استعادة / حفظ / المحفوظات ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("استعادة", Icons.Filled.Restore, Palette.Red, Modifier.weight(1f), textColor = Color.White) { vm.restore() }
                    ActionButton("حفظ", Icons.Filled.AddCircle, Palette.Green, Modifier.weight(1f), textColor = Color.White) { showSave = true }
                    ActionButton("المحفوظات", Icons.Filled.Bookmark, Palette.Orange, Modifier.weight(1f), textColor = Color.White) { showSaved = true }
                }

                // ── الخريطة ──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Palette.CardBorder, RoundedCornerShape(24.dp))
                ) {
                    OsmMap(
                        modifier = Modifier.fillMaxSize(),
                        pin = vm.pin,
                        satellite = vm.satellite,
                        camera = vm.camera,
                        route = vm.routePoints.toList(),
                        current = current,
                        onTap = vm::onMapTap,
                    )
                    MapOverlays(vm, running, onExpand = { fullMap = true })
                }

                // ── موقعي / نوع الخريطة ──
                Row(Modifier.height(54.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "موقعي", Icons.Filled.NearMe, Palette.Blue, Modifier.weight(1f),
                        textColor = Color.White,
                    ) { vm.goToMyLocation() }
                    Segmented(
                        listOf("قمر صناعي", "عادي"),
                        selected = if (vm.satellite) 0 else 1,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { vm.satellite = it == 0 }
                }

                // ── تفعيل تغيير الموقع ──
                CardBox(Modifier.fillMaxWidth().height(70.dp)) {
                    Icon(Icons.Filled.Navigation, null, tint = Palette.Green, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("تفعيل تغيير الموقع", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (phase) {
                                RunPhase.IDLE -> modeLabel(vm.mode)
                                RunPhase.WAITING -> "بانتظار موعد البدء…"
                                RunPhase.RUNNING -> "يعمل الآن • ${modeLabel(vm.mode)}"
                            },
                            color = if (running) Palette.Green else Palette.TextDim, fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = running,
                        onCheckedChange = { on ->
                            if (on) {
                                if (!vm.hasLocationPermission) onRequestPermissions() else vm.startMock()
                            } else vm.stopMock()
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Palette.Green,
                            checkedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF3A3A3F),
                            uncheckedThumbColor = Color(0xFFBDBDC2),
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }

                // ── الجدولة / عشوائي / مسار ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        if (vm.schedule.isActive) "الجدولة •" else "الجدولة", Icons.Filled.Schedule, Palette.Blue,
                        Modifier.weight(1f), height = 64.dp, selected = vm.schedule.isActive,
                    ) { showSchedule = true }
                    ActionButton(
                        "عشوائي", Icons.Filled.Shuffle, Palette.Purple, Modifier.weight(1f), height = 64.dp,
                        selected = vm.mode == MockMode.RANDOM,
                    ) { vm.toggleRandomMode() }
                    ActionButton(
                        "مسار", Icons.Filled.Timeline, Palette.Teal, Modifier.weight(1f), height = 64.dp,
                        selected = vm.mode == MockMode.ROUTE,
                    ) { vm.toggleRouteMode() }
                }

                // ── حالة الجهاز ──
                CardBox(Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = Palette.Orange, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حالة الجهاز", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(
                            if (vm.hasLocationPermission) "الإذن ✓" else "الإذن ✗",
                            if (vm.hasLocationPermission) Palette.Green else Palette.Red,
                        ) { onRequestPermissions() }
                        Chip(
                            if (vm.mockAllowed) "وهمي ✓" else "وهمي ✗",
                            if (vm.mockAllowed) Palette.Green else Palette.Red,
                        ) { if (!vm.mockAllowed) vm.showHelp = true }
                        Chip("المطور", Palette.Purple) { openDevSettings() }
                        Chip("نسخ", Palette.Orange) {
                            clipboard.setText(AnnotatedString((current ?: vm.pin).format()))
                            vm.messages.tryEmit("تم نسخ الإحداثيات")
                        }
                    }
                }

                // ── الدليل / مشاركة ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        "دليل التفعيل", Icons.Filled.SupportAgent, Palette.Blue, Modifier.weight(1f),
                        height = 64.dp, textColor = Color.White,
                    ) { vm.showHelp = true }
                    ActionButton(
                        "مشاركة الموقع", Icons.Filled.Share, Palette.Orange, Modifier.weight(1f), height = 64.dp,
                    ) {
                        val p = current ?: vm.pin
                        val text = "https://maps.google.com/?q=${p.lat},${p.lng}"
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                "مشاركة الموقع",
                            )
                        )
                    }
                }

                // ── تخصيص / إخفاء / إيقاف ──
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("تخصيص", Icons.Filled.Tune, Palette.Blue, Modifier.weight(1f), height = 62.dp) { showSettings = true }
                    ActionButton(
                        "إخفاء الأداة", Icons.Filled.VisibilityOff, Palette.Gray, Modifier.weight(1f),
                        height = 62.dp, textColor = Color.White,
                    ) { (context as? Activity)?.moveTaskToBack(true) }
                    ActionButton("إيقاف الكل", Icons.Filled.StopCircle, Palette.Red, Modifier.weight(1f), height = 62.dp) { vm.stopAll() }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSaved) SavedDialog(vm) { showSaved = false }
    if (showSave) SaveDialog(vm) { showSave = false }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
    if (showSchedule) ScheduleDialog(vm) { showSchedule = false }
    if (vm.showHelp) HelpDialog(vm, onOpenDev = { openDevSettings() }) { vm.showHelp = false }

    if (fullMap) {
        Dialog(
            onDismissRequest = { fullMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Palette.Bg)) {
                OsmMap(
                    modifier = Modifier.fillMaxSize(),
                    pin = vm.pin,
                    satellite = vm.satellite,
                    camera = vm.camera,
                    route = vm.routePoints.toList(),
                    current = current,
                    onTap = vm::onMapTap,
                )
                MapOverlays(vm, running, expanded = true, onExpand = { fullMap = false })
            }
        }
    }
}

private fun modeLabel(m: MockMode) = when (m) {
    MockMode.FIXED -> "وضع: موقع ثابت"
    MockMode.ROUTE -> "وضع: مسار"
    MockMode.RANDOM -> "وضع: حركة عشوائية"
}

@Composable
private fun Header(phase: RunPhase, coords: String, onInfo: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Header)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Palette.Green),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Navigation, null, tint = Color(0xFF0B1F10), modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("MaslakGPS", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            val (label, color) = when (phase) {
                RunPhase.IDLE -> "متوقف • $coords" to Palette.TextDim
                RunPhase.WAITING -> "بانتظار الجدولة…" to Palette.Orange
                RunPhase.RUNNING -> "نشط • $coords" to Palette.Green
            }
            Text(label, color = color, fontSize = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.Green.copy(alpha = 0.18f)).clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Info, "معلومات", tint = Palette.Green, modifier = Modifier.size(30.dp)) }
            Text("v${BuildConfig.VERSION_NAME}", color = Palette.TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF3A3A3F)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "إغلاق", tint = Color(0xFFBDBDC2)) }
    }
}

@Composable
private fun BoxScope.MapOverlays(vm: MainViewModel, running: Boolean, expanded: Boolean = false, onExpand: () -> Unit) {
    // زر التكبير
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(if (expanded) 24.dp else 12.dp)
            .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC111318))
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (expanded) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull,
            "تكبير", tint = Color.White,
        )
    }

    // إحداثيات الدبوس
    Text(
        vm.pin.format(),
        color = Color.White,
        fontSize = 11.sp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(if (expanded) 24.dp else 12.dp)
            .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xAA111318))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )

    // شريط تحرير المسار
    if (vm.mode == MockMode.ROUTE && !running) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (expanded) 32.dp else 26.dp)
                .then(if (expanded) Modifier.navigationBarsPadding() else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6111318))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("نقاط: ${vm.routePoints.size}", color = Palette.Teal, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Chip("تراجع", Palette.Orange) { vm.undoRoutePoint() }
            Chip("مسح", Palette.Red) { vm.clearRoute() }
            Chip(if (vm.routeLoop) "تكرار ✓" else "تكرار ✗", Palette.Teal) { vm.routeLoop = !vm.routeLoop }
        }
    }

    Text(
        if (vm.satellite) "© Esri" else "© OpenStreetMap © CARTO",
        color = Color(0xB3FFFFFF), fontSize = 9.sp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            .then(if (expanded) Modifier.navigationBarsPadding() else Modifier),
    )
}
