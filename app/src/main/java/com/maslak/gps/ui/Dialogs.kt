package com.maslak.gps.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maslak.gps.data.Schedule
import kotlin.math.roundToInt

private val dialogColor = Color(0xFF232428)

@Composable
fun SavedDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        title = { Text("المحفوظات") },
        text = {
            if (vm.saved.isEmpty()) {
                Text("لا توجد مواقع محفوظة بعد. حدد موقعاً واضغط \"حفظ\".", color = Palette.TextDim)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(vm.saved) { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.goToPlace(place); onDismiss() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Place, null, tint = Palette.Orange)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(place.point.format(), color = Palette.TextDim, fontSize = 12.sp)
                            }
                            IconButton(onClick = { vm.deletePlace(place) }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Palette.Red)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
fun SaveDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(vm.pinLabel ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        title = { Text("حفظ الموقع") },
        text = {
            Column {
                Text(vm.pin.format(), color = Palette.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.savePlace(name); onDismiss() }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Text(display, color = Palette.Green, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(vm.settings) }
    var altText by remember { mutableStateOf(s.altitude.roundToInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        title = { Text("تخصيص") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabeledSlider("الدقة (Accuracy)", s.accuracy, 1f..30f, "${s.accuracy.roundToInt()} م") {
                    s = s.copy(accuracy = it.roundToInt().toFloat())
                }
                LabeledSlider("سرعة التحديث", s.intervalMs.toFloat(), 100f..2000f, "${s.intervalMs} ms") {
                    s = s.copy(intervalMs = ((it / 100).roundToInt() * 100L).coerceAtLeast(100L))
                }
                LabeledSlider("سرعة الحركة", s.speedKmh, 1f..120f, "${s.speedKmh.roundToInt()} كم/س") {
                    s = s.copy(speedKmh = it.roundToInt().toFloat())
                }
                LabeledSlider("نطاق العشوائي", s.randomRadius, 10f..1000f, "${s.randomRadius.roundToInt()} م") {
                    s = s.copy(randomRadius = ((it / 10).roundToInt() * 10f))
                }
                OutlinedTextField(
                    altText,
                    { v -> altText = v.filter { it.isDigit() || it == '-' }.take(6) },
                    label = { Text("الارتفاع (م)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اهتزاز طبيعي", color = Color.White)
                        Text("يحاكي تذبذب GPS الحقيقي (أوقفه لأعلى دقة)", color = Palette.TextDim, fontSize = 11.sp)
                    }
                    Switch(s.naturalDrift, { s = s.copy(naturalDrift = it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings(s.copy(altitude = altText.toDoubleOrNull() ?: s.altitude))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = { s = com.maslak.gps.data.MockSettings(); altText = s.altitude.roundToInt().toString() }) {
                Text("افتراضي")
            }
        },
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
fun ScheduleDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var delay by remember { mutableStateOf(vm.schedule.startDelayMin.takeIf { it > 0 }?.toString() ?: "") }
    var duration by remember { mutableStateOf(vm.schedule.durationMin.takeIf { it > 0 }?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        title = { Text("الجدولة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تُطبّق عند تشغيل \"تفعيل تغيير الموقع\" في المرة القادمة.", color = Palette.TextDim, fontSize = 13.sp)
                OutlinedTextField(
                    delay, { v -> delay = v.filter(Char::isDigit).take(4) },
                    label = { Text("ابدأ بعد (دقائق)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    duration, { v -> duration = v.filter(Char::isDigit).take(4) },
                    label = { Text("أوقف تلقائياً بعد (دقائق)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateSchedule(Schedule(delay.toIntOrNull() ?: 0, duration.toIntOrNull() ?: 0))
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = { vm.updateSchedule(Schedule()); onDismiss() }) { Text("إلغاء الجدولة") }
        },
    )
}

@Composable
fun HelpDialog(vm: MainViewModel, onOpenDev: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        title = { Text("دليل التفعيل") },
        text = {
            Column(
                Modifier.verticalScrollCompat(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val steps = listOf(
                    "افتح الإعدادات ← حول الهاتف ← اضغط على \"رقم الإصدار\" 7 مرات لتفعيل خيارات المطور.",
                    "ارجع للإعدادات ← النظام ← خيارات المطور.",
                    "اضغط \"اختيار تطبيق الموقع الوهمي\" (Select mock location app) واختر ${Palette.NAME}.",
                    "ارجع للتطبيق، حدد الموقع على الخريطة أو ابحث عنه، ثم فعّل \"تفعيل تغيير الموقع\".",
                    "لأعلى دقة: اجعل وضع الموقع في الهاتف على \"دقة عالية\"، وأوقف \"الاهتزاز الطبيعي\" من التخصيص، واجعل الدقة 1–3 م.",
                    "إذا لم يتغير الموقع في تطبيق ما: أغلقه بالكامل وافتحه بعد تشغيل الموقع الوهمي.",
                )
                steps.forEachIndexed { i, t ->
                    Row {
                        Text("${i + 1}.", color = Palette.Green, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(t, color = Color.White, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "الحالة: " + if (vm.mockAllowed) "✓ التطبيق مختار كتطبيق موقع وهمي" else "✗ لم يتم اختيار التطبيق بعد",
                    color = if (vm.mockAllowed) Palette.Green else Palette.Red, fontSize = 13.sp,
                )
                Text(
                    "استخدم التطبيق بمسؤولية: بعض التطبيقات تمنع المواقع الوهمية في شروط استخدامها.",
                    color = Palette.TextDim, fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenDev) { Text("فتح خيارات المطور") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
