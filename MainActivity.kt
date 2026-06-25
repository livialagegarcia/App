package com.livia.organizadorarquivos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OrganizerApp() }
    }
}

data class FileItem(
    val name: String,
    val uri: Uri,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
    val hash: String
)

data class DuplicateGroup(
    val hash: String,
    val files: List<FileItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerApp() {
    MaterialTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var selectedRoot by remember { mutableStateOf<Uri?>(null) }
        var scanning by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Selecione uma pasta para iniciar a análise.") }
        var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
        var selectedForDeletion by remember { mutableStateOf<Set<Uri>>(emptySet()) }
        var confirmDelete by remember { mutableStateOf(false) }
        val duplicates = remember(files) { duplicateGroups(files) }

        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
                selectedRoot = uri
                files = emptyList()
                selectedForDeletion = emptySet()
                status = "Pasta selecionada. Toque em Analisar arquivos."
            }
        }

        val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                scope.launch {
                    val ok = exportCsv(context.contentResolver, uri, files)
                    status = if (ok) "Sumário exportado em CSV." else "Não foi possível exportar o CSV."
                }
            }
        }

        Scaffold(topBar = { TopAppBar(title = { Text("Organizador de Arquivos") }) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                Text(status)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { folderPicker.launch(null) }) { Text("Escolher pasta") }
                    Button(enabled = selectedRoot != null && !scanning, onClick = {
                        scope.launch {
                            scanning = true
                            status = "Analisando arquivos..."
                            val root = DocumentFile.fromTreeUri(context, selectedRoot!!)
                            val scanned = if (root != null) scanTree(context.contentResolver, root) else emptyList()
                            val foundDuplicates = duplicateGroups(scanned)
                            files = scanned
                            selectedForDeletion = suggestDuplicates(foundDuplicates)
                            scanning = false
                            status = "Análise concluída: ${scanned.size} arquivo(s), ${foundDuplicates.sumOf { it.files.size }} duplicata(s) em ${foundDuplicates.size} grupo(s)."
                        }
                    }) { Text("Analisar") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = files.isNotEmpty(), onClick = { csvLauncher.launch("sumario_arquivos.csv") }) { Text("Exportar CSV") }
                    Button(enabled = selectedForDeletion.isNotEmpty(), onClick = { confirmDelete = true }) { Text("Excluir selecionados") }
                }
                Spacer(Modifier.height(12.dp))
                Summary(files, duplicates, selectedForDeletion)
                Spacer(Modifier.height(12.dp))
                DuplicateList(
                    groups = duplicates,
                    selected = selectedForDeletion,
                    onToggle = { uri ->
                        selectedForDeletion = if (selectedForDeletion.contains(uri)) selectedForDeletion - uri else selectedForDeletion + uri
                    },
                    onSuggest = { selectedForDeletion = suggestDuplicates(duplicates) },
                    onClear = { selectedForDeletion = emptySet() }
                )
            }
        }

        if (scanning) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Analisando") }, text = {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(16.dp)); Text("Calculando hashes dos arquivos.") }
            })
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Confirmar exclusão") },
                text = { Text("Serão excluídos ${selectedForDeletion.size} arquivo(s). Revise a seleção antes de confirmar.") },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            val deleted = deleteSelected(context, files, selectedForDeletion)
                            files = files.filterNot { selectedForDeletion.contains(it.uri) && deleted.contains(it.uri) }
                            selectedForDeletion = emptySet()
                            confirmDelete = false
                            status = "Excluídos ${deleted.size} arquivo(s)."
                        }
                    }) { Text("Excluir") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
fun Summary(files: List<FileItem>, duplicates: List<DuplicateGroup>, selected: Set<Uri>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Resumo", fontWeight = FontWeight.Bold)
            Text("Arquivos analisados: ${files.size}")
            Text("Espaço analisado: ${formatBytes(files.sumOf { it.size })}")
            Text("Grupos duplicados: ${duplicates.size}")
            Text("Selecionados para exclusão: ${selected.size}")
            Text("Espaço selecionado: ${formatBytes(files.filter { selected.contains(it.uri) }.sumOf { it.size })}")
        }
    }
}

@Composable
fun DuplicateList(groups: List<DuplicateGroup>, selected: Set<Uri>, onToggle: (Uri) -> Unit, onSuggest: () -> Unit, onClear: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSuggest, enabled = groups.isNotEmpty()) { Text("Sugerir seleção") }
        OutlinedButton(onClick = onClear) { Text("Limpar seleção") }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (groups.isEmpty()) item { Text("Nenhuma duplicata exata encontrada.") }
        items(groups) { group ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Grupo com ${group.files.size} arquivos iguais", fontWeight = FontWeight.Bold)
                    Text("Hash: ${group.hash.take(16)}...")
                    group.files.forEachIndexed { index, file ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected.contains(file.uri), enabled = index != 0, onCheckedChange = { onToggle(file.uri) })
                            Column {
                                Text(if (index == 0) "Manter: ${file.name}" else file.name)
                                Text("${formatBytes(file.size)} • ${file.path}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun scanTree(resolver: android.content.ContentResolver, root: DocumentFile): List<FileItem> = withContext(Dispatchers.IO) {
    val output = mutableListOf<FileItem>()
    fun walk(dir: DocumentFile, prefix: String) {
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) walk(doc, "$prefix/${doc.name ?: "pasta"}")
            else if (doc.isFile && doc.length() > 0) {
                val hash = runCatching { sha256(resolver, doc.uri) }.getOrElse { "erro-${doc.uri}" }
                output.add(FileItem(doc.name ?: "sem_nome", doc.uri, prefix, doc.length(), doc.lastModified(), doc.type, hash))
            }
        }
    }
    walk(root, root.name ?: "pasta")
    output
}

fun sha256(resolver: android.content.ContentResolver, uri: Uri): String {
    val digest = MessageDigest.getInstance("SHA-256")
    resolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Arquivo inacessível" }
        BufferedInputStream(input).use { bis ->
            val buffer = ByteArray(1024 * 64)
            while (true) {
                val read = bis.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun suggestDuplicates(groups: List<DuplicateGroup>): Set<Uri> = groups.flatMap { it.files.drop(1).map { f -> f.uri } }.toSet()

suspend fun deleteSelected(context: android.content.Context, files: List<FileItem>, selected: Set<Uri>): Set<Uri> = withContext(Dispatchers.IO) {
    files.filter { selected.contains(it.uri) }.mapNotNull { item ->
        val doc = DocumentFile.fromSingleUri(context, item.uri)
        if (doc?.delete() == true) item.uri else null
    }.toSet()
}

suspend fun exportCsv(resolver: android.content.ContentResolver, uri: Uri, files: List<FileItem>): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.appendLine("nome;caminho;tamanho_bytes;tipo;ultima_modificacao;sha256")
            files.forEach { f -> writer.appendLine(listOf(f.name, f.path, f.size.toString(), f.mimeType ?: "", f.lastModified.toString(), f.hash).joinToString(";") { it.replace(";", ",") }) }
        }
    }.isSuccess
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var idx = 0
    while (value >= 1024 && idx < units.lastIndex) { value /= 1024; idx++ }
    return DecimalFormat("#,##0.##").format(value) + " " + units[idx]
}

fun duplicateGroups(files: List<FileItem>): List<DuplicateGroup> = files.groupBy { it.hash }.filter { it.value.size > 1 && !it.key.startsWith("erro-") }.map { DuplicateGroup(it.key, it.value.sortedBy { f -> f.lastModified }) }
