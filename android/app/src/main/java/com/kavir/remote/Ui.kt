@file:OptIn(ExperimentalMaterial3Api::class)

package com.kavir.remote

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
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

// ------------------------------------------------------------------ connect
@Composable
fun ConnectScreen(vm: MainVM, modifier: Modifier = Modifier) {
    var host by remember { mutableStateOf(vm.store.host) }
    var code by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it.first) } }
    Scaffold(modifier = modifier, containerColor = KBg, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎮 Kavir Remote", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "سرور را روی کامپیوتر اجرا کنید، سپس آدرس و کد ۶ رقمی نمایش‌داده‌شده را وارد کنید.",
                color = KMute
            )
            Field(host, { host = it }, "آدرس کامپیوتر (مثلاً 192.168.1.5:8765)", isLtr = true, keyboard = KeyboardType.Uri)
            Field(code, { code = it.filter(Char::isDigit).take(6) }, "کد ۶ رقمی", isLtr = true, keyboard = KeyboardType.Number)
            Button(
                onClick = { vm.pair(host, code) },
                enabled = !vm.busy && host.isNotBlank() && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KRed)
            ) { Text(if (vm.busy) "در حال اتصال…" else "اتصال", fontWeight = FontWeight.Bold) }
        }
    }
}

// ------------------------------------------------------------------ main
@Composable
fun MainScreen(vm: MainVM, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it.first) } }
    Scaffold(
        modifier = modifier, containerColor = KBg, snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = KPanel) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("خانه") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.PlayArrow, null) },
                    label = { Text("در حال اجرا (${vm.running.size})") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("تنظیمات") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) { 0 -> HomeScreen(vm); 1 -> RunningScreen(vm); else -> SettingsScreen(vm) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = KOrange)
        }
    }
}

@Composable
private fun StatusCard(vm: MainVM, hero: AppItem?) {
    Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(KPurple, Color(0xFF1A0B2B))))) {
        if (hero != null) AsyncImage(vm.api.imageUrl(hero.background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
        Box(Modifier.fillMaxSize().background(shade))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (vm.online) KOk else KRed))
                Spacer(Modifier.size(8.dp))
                Text(vm.pcName.ifBlank { "کامپیوتر" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(
                (if (vm.online) "متصل" else "قطع — در حال تلاش برای اتصال مجدد…") + " · ${vm.apps.size} برنامه · ${vm.running.size} در حال اجرا",
                color = KMute, fontSize = 13.sp
            )
        }
        IconButton({ vm.refresh() }, Modifier.align(Alignment.TopStart)) { Icon(Icons.Filled.Refresh, "Refresh") }
    }
}

@Composable
private fun GameCard(vm: MainVM, a: AppItem, live: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(20.dp)).background(KPanel)
            .clickable { vm.launch(a) }
    ) {
        if (a.cover.isNotEmpty()) AsyncImage(vm.api.imageUrl(a.cover), a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(KPurple, Color(0xFF1A0B2B)))), contentAlignment = Alignment.Center) {
            Text("🎮", fontSize = 48.sp)
        }
        Box(Modifier.fillMaxSize().background(shade))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(a.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.description.isNotBlank()) Text(a.description, color = KMute, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (live) Text(
            "● اجرا", color = Color(0xFF021A0E), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(99.dp)).background(KOk).padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Row(Modifier.align(Alignment.TopStart)) {
            IconButton(onEdit, Modifier.size(34.dp)) { Icon(Icons.Filled.Edit, "ویرایش", Modifier.size(18.dp)) }
            IconButton(onDelete, Modifier.size(34.dp)) { Icon(Icons.Filled.Delete, "حذف", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainVM) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppItem?>(null) }
    var deleting by remember { mutableStateOf<AppItem?>(null) }
    val liveIds = vm.running.map { it.appId }.toSet()
    val list = vm.apps.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val hero = vm.apps.firstOrNull { it.id in liveIds && it.background.isNotEmpty() } ?: vm.apps.firstOrNull { it.background.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StatusCard(vm, hero) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("جستجوی بازی یا برنامه…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, null) } },
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (query.isBlank()) "هنوز چیزی اضافه نشده. با دکمه ＋ بازی یا برنامه اضافه کنید." else "نتیجه‌ای پیدا نشد.", color = KMute)
            }
            gridItems(list, key = { it.id }) { a ->
                GameCard(vm, a, a.id in liveIds, { editing = a }, { deleting = a })
            }
        }
        FloatingActionButton({ adding = true }, Modifier.align(Alignment.BottomStart).padding(20.dp), containerColor = KOrange, contentColor = Color.Black) {
            Icon(Icons.Filled.Add, "افزودن")
        }
    }

    if (adding) EditDialog(vm, null) { adding = false }
    editing?.let { EditDialog(vm, it) { editing = null } }
    deleting?.let { Confirm("حذف از لیست", "«${it.name}» از لیست حذف شود؟ فایل اصلی روی کامپیوتر پاک نمی‌شود.", "حذف", { vm.delete(it) }) { deleting = null } }
}

@Composable
private fun EditDialog(vm: MainVM, item: AppItem?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var exe by remember { mutableStateOf(item?.exe ?: "") }
    var desc by remember { mutableStateOf(item?.description ?: "") }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var bg by remember { mutableStateOf<Uri?>(null) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pickCover.launch("image/*") }, Modifier.weight(1f)) { Text(if (cover != null) "کاور ✓" else "تصویر کاور", fontSize = 12.sp) }
                    OutlinedButton({ pickBg.launch("image/*") }, Modifier.weight(1f)) { Text(if (bg != null) "بکگراند ✓" else "تصویر بکگراند", fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.save(item?.id, name, exe, desc, cover, bg, onDismiss) },
                enabled = !vm.busy && name.isNotBlank() && exe.isNotBlank()
            ) { Text(if (vm.busy) "…" else "ذخیره", color = KOrange, fontWeight = FontWeight.Bold) }
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
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = KOrange)
        content()
    }
}

@Composable
private fun SettingsScreen(vm: MainVM) {
    var repo by remember { mutableStateOf(vm.store.ghRepo) }
    var token by remember { mutableStateOf(vm.store.ghToken) }
    var disconnect by remember { mutableStateOf(false) }
    val rel = vm.release
    val newer = rel != null && Updater.isNewer(rel.tag, BuildConfig.VERSION_NAME)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Section("اتصال به کامپیوتر") {
            Text("${vm.pcName}  ·  ${vm.store.host}", style = LocalTextStyle.current.merge(ltr))
            Text(if (vm.online) "وضعیت: متصل" else "وضعیت: قطع (تلاش مجدد خودکار)", color = if (vm.online) KOk else KRed)
            OutlinedButton({ disconnect = true }, Modifier.fillMaxWidth()) { Text("قطع ارتباط و جفت‌سازی مجدد") }
        }
        Section("GitHub Update") {
            Field(repo, { repo = it }, "GitHub Repository URL", isLtr = true, keyboard = KeyboardType.Uri)
            Field(token, { token = it }, "GitHub Personal Access Token (برای ریپوی عمومی لازم نیست)", isLtr = true, secret = true)
            OutlinedButton({ vm.saveGithub(repo, token) }, Modifier.fillMaxWidth()) { Text("ذخیره") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.saveGithub(repo, token); vm.checkUpdate() }, Modifier.weight(1f), enabled = !vm.busy && repo.isNotBlank()) { Text("Check for Updates") }
                Button({ vm.update() }, Modifier.weight(1f), enabled = !vm.busy && newer, colors = ButtonDefaults.buttonColors(containerColor = KRed)) { Text("Update") }
            }
            if (vm.updateMsg.isNotBlank()) Text(vm.updateMsg, color = if (newer) KOrange else KMute)
            if (newer && rel != null && rel.notes.isNotBlank()) Text(rel.notes.take(500), color = KMute, fontSize = 12.sp)
            if (vm.updateProgress in 0..99) LinearProgressIndicator(progress = { vm.updateProgress / 100f }, Modifier.fillMaxWidth(), color = KOrange)
            Text("نسخه نصب‌شده: ${BuildConfig.VERSION_NAME}", color = KMute, fontSize = 12.sp)
        }
    }
    if (disconnect) Confirm("قطع ارتباط", "ارتباط با این کامپیوتر قطع شود؟ برای اتصال دوباره کد جدید لازم است.", "قطع", { vm.unpair() }) { disconnect = false }
}
