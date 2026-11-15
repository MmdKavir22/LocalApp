@file:OptIn(ExperimentalMaterial3Api::class)

package com.kavir.remote

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage

private val ltr = TextStyle(textDirection = TextDirection.Ltr)
private val shade = Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2070410)))

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                  isLtr: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false,
                  singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        textStyle = if (isLtr) LocalTextStyle.current.merge(ltr) else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun Confirm(title: String, text: String, ok: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text(ok, color = KRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        containerColor = KPanel
    )
}

// ------------------------------------------------------------------ background
@Composable
private fun AppBackground(vm: MainVM, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(KBg)) {
        when (vm.bgStyle) {
            0 -> {
                val t = rememberInfiniteTransition(label = "bg")
                val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "p")
                Box(Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.radialGradient(listOf(KPurple.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * (0.2f + 0.6f * p), size.height * 0.15f), radius = size.maxDimension * 0.7f))
                    drawRect(Brush.radialGradient(listOf(KRed.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * (0.9f - 0.6f * p), size.height * 0.8f), radius = size.maxDimension * 0.6f))
                    drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * (0.4f + 0.3f * p)), radius = size.maxDimension * 0.45f))
                })
            }
            1 -> {
                val liveIds = vm.running.map { it.appId }.toSet()
                val pick = vm.apps.firstOrNull { it.id in liveIds && (it.background.isNotEmpty() || it.cover.isNotEmpty()) }
                    ?: vm.apps.firstOrNull { it.background.isNotEmpty() } ?: vm.apps.firstOrNull { it.cover.isNotEmpty() }
                val file = pick?.background?.ifEmpty { pick.cover }.orEmpty()
                if (file.isNotEmpty()) {
                    AsyncImage(vm.api.imageUrl(file), null, Modifier.fillMaxSize().blur(22.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(KBg.copy(alpha = vm.dim)))
                }
            }
        }
        content()
    }
}

// ------------------------------------------------------------------ connect
@Composable
fun ConnectScreen(vm: MainVM, modifier: Modifier = Modifier) {
    var manual by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(vm.store.host) }
    var code by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it.first) } }
    LaunchedEffect(Unit) { vm.discover() }
    Scaffold(modifier = modifier, containerColor = KBg, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎮 ${vm.appName}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            when {
                vm.waitMsg.isNotEmpty() -> {
                    CircularProgressIndicator(color = LocalAccent.current)
                    Text(vm.waitMsg, color = KMute)
                }
                vm.searching || vm.busy -> {
                    CircularProgressIndicator(color = LocalAccent.current)
                    Text(if (vm.busy) "در حال اتصال…" else "در حال جستجوی کامپیوتر روی Wi-Fi…", color = KMute)
                }
                vm.found.isEmpty() -> {
                    Text("کامپیوتری پیدا نشد. سرور را روی کامپیوتر اجرا کنید و مطمئن شوید گوشی به همان Wi-Fi وصل است.", color = KMute)
                    Button({ vm.discover() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("جستجوی دوباره") }
                }
                else -> {
                    Text("کامپیوترهای پیدا‌شده:", color = KMute)
                    vm.found.forEach { f ->
                        Button(
                            { vm.connect(f) }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KRed)
                        ) { Text("🖥  ${f.name}", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            TextButton({ manual = !manual }) { Text(if (manual) "بستن اتصال دستی" else "اتصال دستی (IP و کد)", color = KMute) }
            if (manual) {
                Field(host, { host = it }, "آدرس کامپیوتر (مثلاً 192.168.1.5:8765)", isLtr = true, keyboard = KeyboardType.Uri)
                Field(code, { code = it.filter(Char::isDigit).take(6) }, "کد ۶ رقمی", isLtr = true, keyboard = KeyboardType.Number)
                Button(
                    onClick = { vm.pair(host, code) },
                    enabled = !vm.busy && host.isNotBlank() && code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
                ) { Text("اتصال", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ------------------------------------------------------------------ main
@Composable
fun MainScreen(vm: MainVM, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it.first) } }
    AppBackground(vm) {
    Scaffold(
        modifier = modifier, containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = KPanel.copy(alpha = 0.92f)) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("خانه") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.PlayArrow, null) },
                    label = { Text("در حال اجرا (${vm.running.size})") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("تنظیمات") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> HomeScreen(vm); 1 -> RunningScreen(vm); else -> SettingsScreen(vm) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = LocalAccent.current)
        }
    }
    }
}

@Composable
private fun AppIcon(vm: MainVM, a: AppItem, size: Dp) {
    val file = a.icon.ifEmpty { a.cover }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
        if (file.isNotEmpty()) AsyncImage(vm.api.imageUrl(file), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(a.name.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
    }
}

private val barPalette = listOf(
    Color(0xFF5B2A86) to Color(0xFFE0243A), Color(0xFF3B1A6E) to Color(0xFFFF8A1F),
    Color(0xFF1B2A6B) to Color(0xFF8E2DE2), Color(0xFF6A1B4D) to Color(0xFFFF5F6D),
    Color(0xFF14213D) to Color(0xFF5B2A86), Color(0xFF7A1F2B) to Color(0xFFFF8A1F)
)
private fun barColors(name: String) = barPalette[(name.hashCode() and 0x7fffffff) % barPalette.size]

/** Compact horizontal bar: laid out left-to-right (icon, name), gradient running right-to-left, soft press animation. */
@Composable
private fun AppBar(vm: MainVM, a: AppItem, live: Boolean, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val (c1, c2) = barColors(a.name)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.fillMaxWidth().height(60.dp).scale(scale).clip(RoundedCornerShape(16.dp))
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(c1, c2), startX = size.width, endX = 0f)) }
                .clickable(interactionSource = src, indication = null) { vm.launch(a) }
        ) {
            if (a.background.isNotEmpty())
                AsyncImage(vm.api.imageUrl(a.background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.22f)
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(vm, a, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(a.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (live) Text(
                    "● اجرا", color = Color(0xFF021A0E), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(KOk).padding(horizontal = 8.dp, vertical = 2.dp)
                ) else Icon(Icons.Filled.PlayArrow, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

/** Home = only the list of apps. Tap an app to launch it on the PC. Everything else lives in Settings. */
@Composable
private fun HomeScreen(vm: MainVM) {
    var query by remember { mutableStateOf("") }
    val liveIds = vm.running.map { it.appId }.toSet()
    val list = vm.apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (vm.online) KOk else KRed))
            Spacer(Modifier.size(8.dp))
            Text(vm.appName, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (vm.online) vm.pcName else "قطع — تلاش مجدد…", color = KMute, fontSize = 12.sp)
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("جستجو…") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, null) } },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) Text(if (vm.apps.isEmpty()) "هنوز برنامه‌ای اضافه نشده. از «تنظیمات» اضافه کنید." else "نتیجه‌ای پیدا نشد.", color = KMute)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.id }) { a -> AppBar(vm, a, a.id in liveIds, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun EditDialog(vm: MainVM, item: AppItem?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var exe by remember { mutableStateOf(item?.exe ?: "") }
    var desc by remember { mutableStateOf(item?.description ?: "") }
    var icon by remember { mutableStateOf<Uri?>(null) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var bg by remember { mutableStateOf<Uri?>(null) }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) icon = it }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) cover = it }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) bg = it }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() }, containerColor = KPanel,
        title = { Text(if (item == null) "افزودن بازی / برنامه" else "ویرایش") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "نام")
                Field(exe, { exe = it }, "مسیر فایل اجرایی در ویندوز (EXE Path)", isLtr = true)
                Field(desc, { desc = it }, "توضیحات", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ pickIcon.launch("image/*") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(if (icon != null) "آیکون ✓" else "آیکون", fontSize = 12.sp) }
                    OutlinedButton({ pickCover.launch("image/*") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(if (cover != null) "کاور ✓" else "کاور", fontSize = 12.sp) }
                    OutlinedButton({ pickBg.launch("image/*") }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(if (bg != null) "بکگراند ✓" else "بکگراند", fontSize = 12.sp) }
                }
                Text("اگر آیکون انتخاب نکنید، آیکون خود برنامه از ویندوز گرفته می‌شود.", color = KMute, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.save(item?.id, name, exe, desc, icon, cover, bg, onDismiss) },
                enabled = !vm.busy && name.isNotBlank() && exe.isNotBlank()
            ) { Text(if (vm.busy) "…" else "ذخیره", color = LocalAccent.current, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("انصراف") } }
    )
}

// ------------------------------------------------------------------ running
@Composable
private fun RunningScreen(vm: MainVM) {
    var closing by remember { mutableStateOf<RunItem?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("برنامه‌های در حال اجرا", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton({ vm.refresh() }) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Refresh") }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.running.isEmpty()) Text("برنامه‌ای در حال اجرا نیست.", color = KMute)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.running, key = { it.pid }) { r ->
                val bgFile = vm.apps.firstOrNull { it.id == r.appId }?.background.orEmpty()
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(KPanel)) {
                    if (bgFile.isNotEmpty()) AsyncImage(vm.api.imageUrl(bgFile), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(KOk))
                        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.Bold)
                            Text("PID ${r.pid} · ${r.status}", color = KMute, fontSize = 12.sp)
                        }
                        Button({ closing = r }, colors = ButtonDefaults.buttonColors(containerColor = KRed)) { Text("بستن") }
                    }
                }
            }
        }
    }
    closing?.let { Confirm("بستن برنامه", "«${it.name}» (PID ${it.pid}) روی کامپیوتر بسته شود؟ تغییرات ذخیره‌نشده ممکن است از بین برود.", "بستن", { vm.close(it) }) { closing = null } }
}

// ------------------------------------------------------------------ settings + updater
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KPanel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = LocalAccent.current)
        content()
    }
}

@Composable
private fun SettingsScreen(vm: MainVM) {
    var disconnect by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppItem?>(null) }
    var deleting by remember { mutableStateOf<AppItem?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section("برنامه‌ها") {
            Button({ adding = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("افزودن برنامه")
            }
            if (vm.apps.isEmpty()) Text("هنوز برنامه‌ای اضافه نشده.", color = KMute, fontSize = 13.sp)
            vm.apps.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KPanel2).padding(start = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(vm, a, 36.dp)
                    Text(a.name, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ vm.move(i, -1) }, Modifier.size(36.dp), enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "بالا") }
                    IconButton({ vm.move(i, 1) }, Modifier.size(36.dp), enabled = i < vm.apps.size - 1) { Icon(Icons.Filled.KeyboardArrowDown, "پایین") }
                    IconButton({ editing = a }, Modifier.size(36.dp)) { Icon(Icons.Filled.Edit, "ویرایش", Modifier.size(20.dp)) }
                    IconButton({ deleting = a }, Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, "حذف", Modifier.size(20.dp)) }
                }
            }
        }
        Section("شخصی‌سازی") {
            var nm by remember { mutableStateOf(vm.appName) }
            Field(nm, { nm = it }, "نام برنامه")
            OutlinedButton({ vm.saveName(nm) }, Modifier.fillMaxWidth()) { Text("ذخیره نام") }
            Text("رنگ اصلی", color = KMute, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c)
                            .border(if (vm.accentIdx == i) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { vm.setAccent(i) }
                    )
                }
            }
            Text("پس‌زمینه", color = KMute, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("متحرک", "تصویر بازی", "ساده").forEachIndexed { i, t ->
                    if (vm.bgStyle == i) Button({ vm.setBg(i) }, Modifier.weight(1f)) { Text(t, fontSize = 12.sp) }
                    else OutlinedButton({ vm.setBg(i) }, Modifier.weight(1f)) { Text(t, fontSize = 12.sp) }
                }
            }
            if (vm.bgStyle == 1) {
                Text("میزان تیرگی تصویر", color = KMute, fontSize = 13.sp)
                Slider(vm.dim, { vm.dim = it }, valueRange = 0.2f..0.9f, onValueChangeFinished = { vm.persistDim() })
            }
        }
        Section("اتصال به کامپیوتر") {
            Text("${vm.pcName}  ·  ${vm.store.host}", style = LocalTextStyle.current.merge(ltr))
            Text(if (vm.online) "وضعیت: متصل" else "وضعیت: قطع (تلاش مجدد خودکار)", color = if (vm.online) KOk else KRed)
            OutlinedButton({ disconnect = true }, Modifier.fillMaxWidth()) { Text("قطع ارتباط و جفت‌سازی مجدد") }
        }
        Text("Kavir Remote ${BuildConfig.VERSION_NAME} · به‌روزرسانی از طریق Kavir Store", color = KMute, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (adding) EditDialog(vm, null) { adding = false }
    editing?.let { EditDialog(vm, it) { editing = null } }
    deleting?.let { Confirm("حذف از لیست", "«${it.name}» از لیست حذف شود؟ فایل اصلی روی کامپیوتر پاک نمی‌شود.", "حذف", { vm.delete(it) }) { deleting = null } }
    if (disconnect) Confirm("قطع ارتباط", "ارتباط با این کامپیوتر قطع شود؟ برای اتصال دوباره کد جدید لازم است.", "قطع", { vm.unpair() }) { disconnect = false }
}
