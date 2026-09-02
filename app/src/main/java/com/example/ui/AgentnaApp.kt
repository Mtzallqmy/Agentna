package com.mtzallqmy.agentna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.ChatItem
import com.mtzallqmy.agentna.runtime.ProviderCatalog
import com.mtzallqmy.agentna.ui.theme.ElectricCyan
import com.mtzallqmy.agentna.ui.theme.NeonIndigo
import com.mtzallqmy.agentna.ui.theme.StatusError
import com.mtzallqmy.agentna.ui.theme.StatusSuccess
import com.mtzallqmy.agentna.ui.theme.StatusWarning

enum class AppTab(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHAT(Icons.Default.Forum),
    AGENTS(Icons.Default.SmartToy),
    WORKSPACE(Icons.Default.FolderOpen),
    ACTIVITY(Icons.Default.Security),
    SETTINGS(Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentnaApp(viewModel: AgentViewModel) {
    val language by viewModel.currentLanguage.collectAsState()
    val rtl = language == AppLanguage.ARABIC
    var tab by remember { mutableStateOf(AppTab.CHAT) }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Scaffold(
            topBar = { BrandBar(rtl, viewModel::toggleLanguage) },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, null) },
                            label = { Text(tabLabel(item, rtl)) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    AppTab.CHAT -> ChatPane(viewModel, rtl)
                    AppTab.AGENTS -> AgentsPane(viewModel, rtl)
                    AppTab.WORKSPACE -> WorkspacePane(viewModel, rtl)
                    AppTab.ACTIVITY -> ActivityPane(viewModel, rtl)
                    AppTab.SETTINGS -> SettingsPane(viewModel, rtl)
                }
            }
        }
    }
}

@Composable
private fun BrandBar(rtl: Boolean, toggleLanguage: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(ElectricCyan, NeonIndigo))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Hub, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Agentna", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(StatusSuccess.copy(alpha = .14f))
                            .border(1.dp, StatusSuccess.copy(alpha = .35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text(if (rtl) "محلي" else "ON-DEVICE", color = StatusSuccess, style = MaterialTheme.typography.labelSmall) }
                }
                Text(
                    if (rtl) "الوكيل يعمل على هاتفك • الاستدلال عبر مزودك مباشرة" else "Agent runtime on your phone • inference goes directly to your provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = toggleLanguage) { Text(if (rtl) "EN" else "عربي") }
        }
    }
}

@Composable
private fun ChatPane(viewModel: AgentViewModel, rtl: Boolean) {
    val chat by viewModel.chatItems.collectAsState()
    val working by viewModel.isAgentWorking.collectAsState()
    val status by viewModel.currentWorkStatus.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val selectedId by viewModel.selectedAgentId.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (agents.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(12.dp).fillMaxWidth()) {
                agents.take(3).forEachIndexed { index, agent ->
                    SegmentedButton(
                        selected = agent.id == selectedId,
                        onClick = { viewModel.selectAgent(agent.id) },
                        shape = SegmentedButtonDefaults.itemShape(index, minOf(agents.size, 3))
                    ) { Text(agent.name, maxLines = 1) }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chat, key = { it.id }) { item -> ChatBubble(item, rtl, viewModel) }
            if (working) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(status ?: if (rtl) "يعمل محلياً…" else "Running locally…")
                }
            }
        }
        Surface(tonalElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    placeholder = { Text(if (rtl) "اطلب مهمة…" else "Ask Agentna to do something…") },
                    enabled = !working
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { val v = input.trim(); if (v.isNotEmpty()) { input = ""; viewModel.sendMessage(v) } },
                    enabled = input.isNotBlank() && !working
                ) { Icon(Icons.Default.ArrowUpward, null) }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem, rtl: Boolean, viewModel: AgentViewModel) {
    when (item.role) {
        "tool" -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BuildCircle, null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(item.toolName.orEmpty(), fontWeight = FontWeight.SemiBold)
                }
                if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        "approval" -> item.approval?.let { ApprovalCard(it, rtl, viewModel) }
        else -> {
            val user = item.role == "user"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                Surface(
                    color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(if (user) .86f else .94f)
                ) { Text(item.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: ApprovalModel, rtl: Boolean, viewModel: AgentViewModel) {
    Card(border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GppMaybe, null, tint = StatusWarning)
                Spacer(Modifier.width(8.dp)); Text(if (rtl) "موافقة مطلوبة" else "Approval required", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp)); Text(approval.toolName, color = ElectricCyan); Text(approval.reason, style = MaterialTheme.typography.bodySmall)
            if (approval.status == "pending") {
                Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.rejectAction(approval) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "رفض" else "Reject") }
                    Button(onClick = { viewModel.approveAction(approval) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "موافقة" else "Approve") }
                }
            }
        }
    }
}

@Composable
private fun AgentsPane(viewModel: AgentViewModel, rtl: Boolean) {
    val agents by viewModel.agents.collectAsState()
    val selected by viewModel.selectedAgentId.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (rtl) "الوكلاء المحليون" else "Local agents", if (rtl) "كل وكيل يلتزم بصلاحيات Android المحددة له." else "Each agent is constrained by its Android permissions.") }
        items(agents, key = { it.id }) { agent ->
            Card(onClick = { viewModel.selectAgent(agent.id) }, border = if (agent.id == selected) CardDefaults.outlinedCardBorder(true) else null) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (agent.icon == "search") Icons.Default.TravelExplore else Icons.Default.SmartToy, null, tint = ElectricCyan)
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(agent.name, fontWeight = FontWeight.Bold); Text(agent.description, style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(10.dp)); Text("${agent.primaryProvider} • ${viewModel.providerModel(agent.primaryProvider)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(if (agent.networkPermission) "HTTPS ✓   Workspace ${if (agent.filesystemPermission) "✓" else "—"}" else "Network —", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WorkspacePane(viewModel: AgentViewModel, rtl: Boolean) {
    val files by viewModel.files.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshFiles() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (rtl) "مساحة العمل" else "Workspace", if (rtl) "ملفات خاصة داخل مساحة التطبيق." else "Private files in the app sandbox.", Modifier.weight(1f))
            IconButton(onClick = viewModel::refreshFiles) { Icon(Icons.Default.Refresh, null) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (files.isEmpty()) item { EmptyState(Icons.Default.FolderOpen, if (rtl) "لا توجد ملفات بعد" else "No workspace files yet") }
            items(files, key = { it.id }) { file ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = ElectricCyan); Spacer(Modifier.width(8.dp)); Text(file.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${file.size} B", style = MaterialTheme.typography.labelSmall) }
                        Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (file.contentPreview.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(file.contentPreview.take(800), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 10) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPane(viewModel: AgentViewModel, rtl: Boolean) {
    val approvals by viewModel.approvals.collectAsState()
    val logs by viewModel.executionLogs.collectAsState()
    val runtime by viewModel.runtimeStatus.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle(if (rtl) "الأمان والنشاط" else "Safety & activity", if (rtl) "لا توجد حاوية أو تحكم خفي بالجهاز." else "No hidden container or background device control.")
            Card { Column(Modifier.padding(16.dp)) { Text("Runtime: $runtime", fontWeight = FontWeight.Bold, color = StatusSuccess); Text(if (rtl) "Room محلي • Keystore • HTTPS مباشر • لا يوجد Gateway" else "Local Room • Keystore • direct HTTPS • no gateway", style = MaterialTheme.typography.bodySmall) } }
        }
        item { Text(if (rtl) "الموافقات" else "Approvals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(approvals.take(8), key = { it.id }) { ApprovalCard(it, rtl, viewModel) }
        item { Text(if (rtl) "سجل التنفيذ" else "Execution log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(logs.take(30), key = { it.id }) { log ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) { Text(log.event, fontWeight = FontWeight.SemiBold); Text(log.message, style = MaterialTheme.typography.bodySmall); if (log.details.isNotBlank()) Text(log.details.take(500), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun SettingsPane(viewModel: AgentViewModel, rtl: Boolean) {
    val revision by viewModel.providerRevision.collectAsState()
    val tests by viewModel.providerTestStatus.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle(if (rtl) "مزودات الذكاء الاصطناعي" else "AI providers", if (rtl) "المفاتيح مشفرة بـ Android Keystore ولا تُرسل إلى Agentna." else "Keys are encrypted by Android Keystore and never sent to an Agentna server.") }
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
    val revision by viewModel.providerRevision.collectAsState()
    var key by remember(id, revision) { mutableStateOf(viewModel.providerMaskedKey(id)) }
    var model by remember(id, revision) { mutableStateOf(viewModel.providerModel(id)) }
    Card(border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (viewModel.providerHasKey(id)) StatusSuccess else StatusWarning))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(model, { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (rtl) "النموذج" else "Model") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveProvider(id, key, model) }, modifier = Modifier.weight(1f)) { Text(if (rtl) "حفظ" else "Save") }
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
    Column(modifier) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(10.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun tabLabel(tab: AppTab, rtl: Boolean) = if (rtl) when (tab) {
    AppTab.CHAT -> "محادثة"; AppTab.AGENTS -> "الوكلاء"; AppTab.WORKSPACE -> "الملفات"; AppTab.ACTIVITY -> "الأمان"; AppTab.SETTINGS -> "الإعدادات"
} else when (tab) {
    AppTab.CHAT -> "Chat"; AppTab.AGENTS -> "Agents"; AppTab.WORKSPACE -> "Files"; AppTab.ACTIVITY -> "Safety"; AppTab.SETTINGS -> "Settings"
}
