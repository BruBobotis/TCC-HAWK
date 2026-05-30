package com.example.tcc_hawk.ui.screens.caregivers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID

private data class Caregiver(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relation: String,
    val phone: String,
    val priority: Int,      // 1 = principal, 2..n = ordem
    val active: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiversScreen() {
    var caregivers by remember {
        mutableStateOf(
            listOf(
                Caregiver(name = "Maria Silva", relation = "Filha", phone = "(11) 9xxxx-xxxx", priority = 1),
                Caregiver(name = "João Santos", relation = "Neto", phone = "(11) 9xxxx-xxxx", priority = 2)
            )
        )
    }

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Caregiver?>(null) }

    fun normalize(list: List<Caregiver>): List<Caregiver> {
        // garante prioridades 1..n
        val sorted = list.sortedBy { it.priority }
        return sorted.mapIndexed { idx, c -> c.copy(priority = idx + 1) }
    }

    fun setPrincipal(id: String) {
        caregivers = normalize(
            caregivers.map { c ->
                if (c.id == id) c.copy(priority = 1) else c.copy(priority = c.priority + 1)
            }
        )
    }

    fun addCaregiver(name: String, relation: String, phone: String, makePrincipal: Boolean) {
        val base = normalize(caregivers)
        val newPriority = if (makePrincipal) 1 else (base.size + 1)
        val new = Caregiver(name = name, relation = relation, phone = phone, priority = newPriority)

        caregivers = if (makePrincipal) {
            // empurra os outros
            normalize(listOf(new) + base.map { it.copy(priority = it.priority + 1) })
        } else {
            normalize(base + new)
        }
    }

    fun removeCaregiver(id: String) {
        caregivers = normalize(caregivers.filterNot { it.id == id })
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Cuidadores") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, null)
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gerenciamento de responsáveis", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Defina quem recebe alertas primeiro. Em eventos críticos, o sistema notifica o Principal e, se necessário, escala para os próximos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(caregivers.sortedBy { it.priority }, key = { it.id }) { c ->
                    CaregiverCard(
                        caregiver = c,
                        onMakePrincipal = { setPrincipal(c.id) },
                        onDelete = { deleteTarget = c },
                        onCall = { /* TODO: integrar Intent de chamada */ }
                    )
                }

                item {
                    Spacer(Modifier.height(90.dp)) // espaço pro FAB
                }
            }
        }

        if (showAdd) {
            AddCaregiverDialog(
                onDismiss = { showAdd = false },
                onSave = { name, relation, phone, principal ->
                    addCaregiver(name, relation, phone, principal)
                    showAdd = false
                }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Remover cuidador?") },
                text = { Text("Tem certeza que deseja remover ${target.name}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            removeCaregiver(target.id)
                            deleteTarget = null
                        }
                    ) { Text("Remover") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun CaregiverCard(
    caregiver: Caregiver,
    onMakePrincipal: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit
) {
    val isPrincipal = caregiver.priority == 1
    val badgeText = if (isPrincipal) "Principal" else "${caregiver.priority}º"

    Card(shape = MaterialTheme.shapes.extraLarge) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.padding(10.dp)) {
                    Icon(
                        if (isPrincipal) Icons.Filled.Star else Icons.Filled.Person,
                        contentDescription = null
                    )
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(caregiver.name, fontWeight = FontWeight.SemiBold)
                    AssistChip(
                        onClick = { },
                        label = { Text(badgeText) },
                        leadingIcon = {
                            Icon(
                                if (isPrincipal) Icons.Filled.Star else Icons.Filled.SwapVert,
                                contentDescription = null
                            )
                        }
                    )
                }
                Text(
                    "${caregiver.relation} • ${caregiver.phone}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isPrincipal) {
                    TextButton(onClick = onMakePrincipal, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Definir como principal")
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onCall) { Icon(Icons.Filled.Call, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCaregiverDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, relation: String, phone: String, principal: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Familiar") }
    var makePrincipal by remember { mutableStateOf(true) }

    var expanded by remember { mutableStateOf(false) }
    val relations = listOf("Filho(a)", "Neto(a)", "Cônjuge", "Cuidador", "Familiar", "Outro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo cuidador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    leadingIcon = { Icon(Icons.Filled.Person, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone") },
                    leadingIcon = { Icon(Icons.Filled.Phone, null) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("(11) 9xxxx-xxxx") }
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = relation,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Relação") },
                        leadingIcon = { Icon(Icons.Filled.Badge, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        relations.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    relation = item
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Star, null)
                        Text("Definir como principal")
                    }
                    Switch(checked = makePrincipal, onCheckedChange = { makePrincipal = it })
                }

                Text(
                    "Dica: o Principal é notificado primeiro. Se não houver resposta, o sistema escala para os próximos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim(), relation, phone.trim(), makePrincipal)
                },
                enabled = name.trim().isNotEmpty() && phone.trim().isNotEmpty()
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}