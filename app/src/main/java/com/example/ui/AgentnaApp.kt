package com.mtzallqmy.agentna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtzallqmy.agentna.automation.AutomationScheduler
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.AutomationModel
import com.mtzallqmy.agentna.data.model.ChatItem
import com.mtzallqmy.agentna.runtime.ProviderCatalog
import com.mtzallqmy.agentna.ui.theme.ElectricCyan
import com.mtzallqmy.agentna.ui.theme.GlowCardGradient
import com.mtzallqmy.agentna.ui.theme.NeonIndigo
import com.mtzallqmy.agentna.ui.theme.StatusError
import com.mtzallqmy.agentna.ui.theme.StatusSuccess
import com.mtzallqmy.agentna.ui.theme.StatusWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AppTab(val icon: ImageVector) {
    CHAT(Icons.Default.Forum),
    AGENTS(Icons.Default.SmartToy),
    WORKSPACE(Icons.Default.FolderOpen),
    AUTOMATIONS(Icons.Default.Schedule),
    ACTIVITY(Icons.Default.Security),
    SETTINGS(Icons.Default.Tune)
}

@Composable
fun AgentnaApp(viewModel: AgentViewModel) {
    val language by viewModel.currentLanguage.collectAsState()
    val rtl = language == AppLanguage.ARABIC
    var tab by remember { mutableStateOf(AppTab.CHAT) }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Scaffold(
            topBar = { BrandBar(rtl, viewModel.runtimeStatus.collectAsState().value, viewModel::toggleLanguage) },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(tabLabel(item, rtl), maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    AppTab.CHAT -> ChatPane(viewModel, rtl)
                    AppTab.AGENTS -> AgentsPane(viewModel, rtl)
                    AppTab.WORKSPACE -> WorkspacePane(viewModel, rtl)
                    AppTab.AUTOMATIONS -> AutomationsPane(viewModel, rtl)
                    AppTab.ACTIVITY -> ActivityPane(viewModel, rtl)
                    AppTab.SETTINGS -> SettingsPane(viewModel, rtl)
                }
            }
        }
    }
}

@Composable
private fun BrandBar(rtl: Boolean, runtime: String, toggleLanguage: () -> Unit) {
    Surface(tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Hub, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Agentna", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(if (rtl) "محلي" else "ON-DEVICE", runtime != "ERROR")
                }
                Text(
                    if (rtl) "المحرك والأدوات والذاكرة على هاتفك • الاستدلال عبر مزودك مباشرة"
                    else "Runtime, tools and memory on your phone • direct provider inference",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = toggleLanguage) { Text(if (rtl) "EN" else "عربي") }
        }
    }
}

@Composable
private fun StatusPill(text: String, healthy: Boolean) {
    val color = if (healthy) StatusSuccess else StatusError
    Box(
        Modifier.clip(RoundedCornerShape(30.dp)).background(color.copy(alpha = .12f))
            .border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ChatPane(viewModel: AgentViewModel, rtl: Boolean) {
    val chat by viewModel.chatItems.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val selected by viewModel.selectedAgentId.collectAsState()
    val working by viewModel.isAgentWorking.collectAsState()
    val status by viewModel.currentWorkStatus.collectAsState()
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(agents, key = { it.id }) { agent ->
                FilterChip(
                    selected = agent.id == selected,
                    onClick = { viewModel.selectAgent(agent.id) },
                    label = { Text(agent.name, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) }
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chat.isEmpty()) item {
                EmptyState(Icons.Default.Forum, if (rtl) "اطلب مهمة؛ لن يدّعي Agentna تنفيذ شيء لم ينفذه فعلاً." else "Ask for a task. Agentna never fabricates tool execution.")
            }
            items(chat, key = { it.id }) { ChatBubble(it, rtl, viewModel) }
            if (working) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(status ?: if (rtl) "يعمل محلياً…" else "Running locally…")
                }
            }
        }
        Surface(tonalElevation = 5.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    placeholder = { Text(if (rtl) "اكتب المهمة…" else "Describe the task…") },
                    enabled = !working
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { input.trim().takeIf { it.isNotEmpty() }?.let { value -> input = ""; viewModel.sendMessage(value) } },
                    enabled = input.isNotBlank() && !working
                ) { Icon(Icons.Default.ArrowUpward, null) }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem, rtl: Boolean, viewModel: AgentViewModel) {
    when (item.role) {
        "approval" -> item.approval?.let { ApprovalCard(it, rtl, viewModel) }
        "tool" -> OutlinedCard {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BuildCircle, null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp)); Text(item.toolName.orEmpty(), fontWeight = FontWeight.Bold)
                }
                if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 10)
            }
        }
        else -> {
            val user = item.role == "user"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                Surface(
                    color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(if (user) .86f else .95f)
                ) { Text(item.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: ApprovalModel, rtl: Boolean, viewModel: AgentViewModel) {
    OutlinedCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GppMaybe, null, tint = StatusWarning)
                Spacer(Modifier.width(8.dp)); Text(if (rtl) "موافقة بشرية مطلوبة" else "Human approval required", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp)); Text(approval.toolName, color = ElectricCyan, fontFamily = FontFamily.Monospace)
            Text(approval.reason, style = MaterialTheme.typography.bodySmall)
            if (approval.status == "pending") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.rejectAction(approval) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "رفض" else "Reject") }
                    Button(onClick = { viewModel.approveAction(approval) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "موافقة" else "Approve") }
                }
            } else Text(approval.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentsPane(viewModel: AgentViewModel, rtl: Boolean) {
    val agents by viewModel.agents.collectAsState()
    val selected by viewModel.selectedAgentId.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (rtl) "الوكلاء" else "Agents", if (rtl) "كل وكيل مقيد بصلاحياته المحلية." else "Every agent is constrained by local permissions.") }
        items(agents, key = { it.id }) { agent ->
            Card(onClick = { viewModel.selectAgent(agent.id) }, colors = CardDefaults.cardColors(containerColor = if (agent.id == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, null, tint = ElectricCyan)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(agent.name, fontWeight = FontWeight.Bold); Text(agent.description, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("${agent.primaryProvider} • ${viewModel.providerModel(agent.primaryProvider)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Workspace ${if (agent.filesystemPermission) "✓" else "—"}   HTTPS ${if (agent.networkPermission) "✓" else "—"}   Auto ${if (agent.automationPermission) "✓" else "—"}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WorkspacePane(viewModel: AgentViewModel, rtl: Boolean) {
    val files by viewModel.files.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (rtl) "مساحة العمل" else "Workspace", if (rtl) "ملفات داخل sandbox الخاص بالتطبيق فقط." else "Files confined to the app sandbox.", Modifier.weight(1f))
            IconButton(onClick = viewModel::refreshFiles) { Icon(Icons.Default.Refresh, null) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (files.isEmpty()) item { EmptyState(Icons.Default.FolderOpen, if (rtl) "لا توجد ملفات بعد" else "No workspace files yet") }
            items(files, key = { it.id }) { file ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = ElectricCyan); Spacer(Modifier.width(8.dp))
                            Text(file.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${file.size} B", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (file.contentPreview.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(file.contentPreview.take(900), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 12) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationsPane(viewModel: AgentViewModel, rtl: Boolean) {
    val automations by viewModel.automations.collectAsState()
    val agents by viewModel.agents.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (rtl) "الأتمتة المحلية" else "Local automations", if (rtl) "WorkManager يشغلها محلياً وقد يؤخرها Android حسب البطارية والقيود." else "WorkManager runs them locally; Android may defer execution for system constraints.", Modifier.weight(1f))
            FilledIconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, null) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (automations.isEmpty()) item { EmptyState(Icons.Default.Schedule, if (rtl) "لا توجد أتمتة" else "No automations") }
            items(automations, key = { it.id }) { AutomationCard(it, viewModel, rtl) }
        }
    }
    if (showCreate) CreateAutomationDialog(
        rtl = rtl,
        agents = agents.filter { it.automationPermission },
        onDismiss = { showCreate = false },
        onSave = { name, prompt, agentId, hour, minute ->
            viewModel.saveDailyAutomation(name, prompt, agentId, hour, minute); showCreate = false
        }
    )
}

@Composable
private fun AutomationCard(automation: AutomationModel, viewModel: AgentViewModel, rtl: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(GlowCardGradient), contentAlignment = Alignment.Center) { Icon(Icons.Default.Schedule, null, tint = ElectricCyan) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(automation.name, fontWeight = FontWeight.Bold)
                    Text(AutomationScheduler.describe(automation.cronExpression), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = automation.enabled, onCheckedChange = { viewModel.toggleAutomation(automation.id) })
            }
            if (automation.description.isNotBlank()) Text(automation.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp)); Text(automation.prompt, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp)); HorizontalDivider(); Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(automation.lastStatus ?: if (automation.enabled) "scheduled" else "disabled", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = statusColor(automation.lastStatus))
                OutlinedButton(onClick = { viewModel.runAutomationNow(automation.id) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(if (rtl) "تشغيل" else "Run") }
                IconButton(onClick = { viewModel.deleteAutomation(automation.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = StatusError) }
            }
            automation.nextRunAt?.let { Text((if (rtl) "التشغيل التالي: " else "Next: ") + formatEpoch(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CreateAutomationDialog(rtl: Boolean, agents: List<com.mtzallqmy.agentna.data.model.AgentModel>, onDismiss: () -> Unit, onSave: (String, String, String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("8") }
    var minute by remember { mutableStateOf("0") }
    var agentId by remember(agents) { mutableStateOf(agents.firstOrNull()?.id.orEmpty()) }
    val valid = name.isNotBlank() && prompt.isNotBlank() && agentId.isNotBlank() && hour.toIntOrNull()?.let { it in 0..23 } == true && minute.toIntOrNull()?.let { it in 0..59 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, null) },
        title = { Text(if (rtl) "أتمتة يومية" else "Daily automation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(if (rtl) "الاسم" else "Name") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, label = { Text(if (rtl) "المهمة" else "Task") }, minLines = 3, maxLines = 6)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(agents, key = { it.id }) { agent -> FilterChip(selected = agentId == agent.id, onClick = { agentId = agent.id }, label = { Text(agent.name) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text(if (rtl) "الساعة" else "Hour") }, singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text(if (rtl) "الدقيقة" else "Minute") }, singleLine = true)
                }
                Text(if (rtl) "الموعد تقريبي: قد يؤخر Android العمل للحفاظ على البطارية." else "Timing is best-effort: Android may defer background work to preserve battery.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, prompt, agentId, hour.toInt(), minute.toInt()) }, enabled = valid) { Text(if (rtl) "إنشاء" else "Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (rtl) "إلغاء" else "Cancel") } }
    )
}

@Composable
private fun ActivityPane(viewModel: AgentViewModel, rtl: Boolean) {
    val approvals by viewModel.approvals.collectAsState()
    val logs by viewModel.executionLogs.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle(if (rtl) "الأمان والنشاط" else "Safety & activity", if (rtl) "سجل محلي لكل الأدوات والموافقات." else "Local audit trail for tools and approvals.")
            Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WorkspacePremium, null, tint = StatusSuccess); Spacer(Modifier.width(9.dp)); Column { Text(if (rtl) "Room + Keystore + HTTPS مباشر" else "Room + Keystore + direct HTTPS", fontWeight = FontWeight.Bold); Text(if (rtl) "لا Gateway ولا تحكم خفي بالجهاز" else "No gateway and no hidden device control", style = MaterialTheme.typography.bodySmall) } } }
        }
        item { Text(if (rtl) "الموافقات" else "Approvals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(approvals.take(8), key = { it.id }) { ApprovalCard(it, rtl, viewModel) }
        item { Text(if (rtl) "سجل التنفيذ" else "Execution log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(logs.take(40), key = { it.id }) { log ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) { Text(log.event, fontWeight = FontWeight.SemiBold); Text(log.message, style = MaterialTheme.typography.bodySmall); if (log.details.isNotBlank()) Text(log.details.take(600), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun SettingsPane(viewModel: AgentViewModel, rtl: Boolean) {
    val revision by viewModel.providerRevision.collectAsState()
    val tests by viewModel.providerTestStatus.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(if (rtl) "مزودات الذكاء الاصطناعي" else "AI providers", if (rtl) "المفاتيح مشفرة بـ Android Keystore ولا تمر عبر خادم Agentna." else "Keys are encrypted by Android Keystore and never pass through an Agentna server.") }
        items(ProviderCatalog.providers, key = { it.id }) { provider ->
            key(provider.id, revision) { ProviderCard(viewModel, provider.id, provider.displayName, rtl, tests[provider.id]) }
        }
        item {
            OutlinedButton(onClick = viewModel::resetDatabase, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(if (rtl) "إعادة ضبط البيانات المحلية" else "Reset local data")
            }
        }
    }
}

@Composable
private fun ProviderCard(viewModel: AgentViewModel, id: String, name: String, rtl: Boolean, testStatus: String?) {
    var apiKey by remember(id) { mutableStateOf(viewModel.providerMaskedKey(id)) }
    var model by remember(id) { mutableStateOf(viewModel.providerModel(id)) }
    OutlinedCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (viewModel.providerHasKey(id)) StatusSuccess else StatusWarning))
            }
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(apiKey, { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(model, { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (rtl) "النموذج" else "Model") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveProvider(id, apiKey, model) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "حفظ" else "Save") }
                OutlinedButton(onClick = { viewModel.testProvider(id) }, enabled = viewModel.providerHasKey(id), modifier = Modifier.weight(1f)) { Text(if (rtl) "اختبار" else "Test") }
            }
            testStatus?.let { status ->
                Spacer(Modifier.height(6.dp)); Text(
                    when { status == "testing" -> if (rtl) "جارٍ الاختبار…" else "Testing…"; status == "ok" -> "✓ OK"; else -> status.removePrefix("error:") },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == "ok") StatusSuccess else if (status == "testing") ElectricCyan else StatusError
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun statusColor(status: String?) = when (status) {
    "success", "scheduled" -> StatusSuccess
    "failed", "agent_missing", "permission_denied" -> StatusError
    "waiting_approval" -> StatusWarning
    else -> ElectricCyan
}

private fun formatEpoch(value: String): String = runCatching {
    val instant = Instant.ofEpochMilli(value.toLong())
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault(value)

private fun tabLabel(tab: AppTab, rtl: Boolean) = if (rtl) when (tab) {
    AppTab.CHAT -> "محادثة"; AppTab.AGENTS -> "الوكلاء"; AppTab.WORKSPACE -> "الملفات"; AppTab.AUTOMATIONS -> "أتمتة"; AppTab.ACTIVITY -> "الأمان"; AppTab.SETTINGS -> "الإعدادات"
} else when (tab) {
    AppTab.CHAT -> "Chat"; AppTab.AGENTS -> "Agents"; AppTab.WORKSPACE -> "Files"; AppTab.AUTOMATIONS -> "Auto"; AppTab.ACTIVITY -> "Safety"; AppTab.SETTINGS -> "Settings"
}
