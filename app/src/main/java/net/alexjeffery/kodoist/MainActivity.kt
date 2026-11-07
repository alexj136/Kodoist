@file:OptIn(ExperimentalMaterial3Api::class)

package net.alexjeffery.kodoist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                val vm: TasksViewModel = viewModel()
                if (vm.token == null) TokenScreen(vm) else TodayScreen(vm)
            }
        }
    }
}

@Composable
private fun TokenScreen(vm: TasksViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBarMMD(title = { TextMMD("Todoist") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextMMD(
                "Paste your Todoist API token. Find it in Todoist under " +
                    "Settings → Integrations → Developer.",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextFieldMMD(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { TextMMD("API token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.signIn(input) }),
            )
            ButtonMMD(
                onClick = { vm.signIn(input) },
                enabled = !vm.loading && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextMMD(if (vm.loading) "Checking…" else "Sign in")
            }
            vm.error?.let { TextMMD(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun TodayScreen(vm: TasksViewModel) {
    // Re-sync whenever the app comes to the foreground, so changes made elsewhere show up.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out?",
            message = "You'll need to paste your API token again to sign back in.",
            confirmLabel = "Log out",
            onConfirm = { confirmLogout = false; vm.signOut() },
            onDismiss = { confirmLogout = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBarMMD(
                title = { TextMMD("Today") },
                actions = {
                    // Not disabled while syncing: a greyed-out icon dithers badly on E Ink.
                    // refresh() ignores taps while a sync is already running.
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(AppIcons.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { confirmLogout = true }) {
                        Icon(AppIcons.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
        bottomBar = { AddTaskBar(vm) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            vm.error?.let {
                TextMMD(
                    "$it  (tap to dismiss)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.dismissError() }
                        .padding(16.dp),
                )
                HorizontalDividerMMD()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.tasks.isEmpty() && vm.completed.isEmpty()) {
                    TextMMD(
                        if (vm.loading) "Loading…" else "Nothing due today.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    TaskList(vm)
                }
            }
        }
    }
}

/**
 * Pure black-on-white dialog. Material's AlertDialog renders with a transparent container
 * under ThemeMMD and dims the screen grey, which dithers on E Ink.
 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(2.dp, Color.Black),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextMMD(title, style = MaterialTheme.typography.titleLarge)
                TextMMD(message, style = MaterialTheme.typography.bodyLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButtonMMD(onClick = onDismiss) { TextMMD("Cancel") }
                    ButtonMMD(onClick = onConfirm) { TextMMD(confirmLabel) }
                }
            }
        }
    }
}

@Composable
private fun TaskList(vm: TasksViewModel) {
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
        if (vm.tasks.isEmpty()) {
            item(key = "all-done") {
                TextMMD(
                    "All done for today.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDividerMMD()
            }
        }
        items(vm.tasks, key = { it.id }) { task ->
            TaskRow(task, checked = task.id in vm.pending, onToggle = { vm.complete(task) })
            HorizontalDividerMMD()
        }
        if (vm.completed.isNotEmpty()) {
            item(key = "completed-header") {
                TextMMD(
                    (if (showCompleted) "▾ " else "▸ ") + "Completed today (${vm.completed.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCompleted = !showCompleted }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDividerMMD()
            }
            if (showCompleted) {
                items(vm.completed, key = { "done-" + it.id }) { task ->
                    TaskRow(
                        task,
                        checked = task.id !in vm.pending,
                        strikethrough = true,
                        onToggle = { vm.reopen(task) },
                    )
                    HorizontalDividerMMD()
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, checked: Boolean, strikethrough: Boolean = false, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckboxMMD(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            TextMMD(
                task.content,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (strikethrough || checked) TextDecoration.LineThrough else null,
            )
            dueLabel(task.due)?.let {
                TextMMD(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/** "14:00" for a timed task today; "Overdue · Thu 5 Nov" (plus time, if any) for older tasks. */
private fun dueLabel(due: Due?): String? {
    if (due == null) return null
    val time = due.time?.format(timeFormat)
    return if (due.date.isBefore(LocalDate.now())) {
        "Overdue · " + due.date.format(dateFormat) + (time?.let { " $it" } ?: "")
    } else {
        time
    }
}

@Composable
private fun AddTaskBar(vm: TasksViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { vm.add(text) { text = "" } }
    Column {
        HorizontalDividerMMD()
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextFieldMMD(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !vm.adding,
                placeholder = { TextMMD("New task") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButtonMMD(onClick = submit, enabled = !vm.adding && text.isNotBlank()) {
                TextMMD(if (vm.adding) "…" else "Add")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
