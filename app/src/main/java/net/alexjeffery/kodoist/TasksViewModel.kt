package net.alexjeffery.kodoist

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("todoist", Context.MODE_PRIVATE)

    var token by mutableStateOf(prefs.getString(KEY_TOKEN, null))
        private set
    /** Overdue and due-today tasks that are still open, oldest due first. */
    val tasks = mutableStateListOf<Task>()
    /** Tasks due today that have been completed. */
    val completed = mutableStateListOf<Task>()
    /** IDs of tasks with a complete or reopen request in flight; shown with the checkbox flipped. */
    val pending = mutableStateListOf<String>()
    var loading by mutableStateOf(false)
        private set
    var adding by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val api get() = token?.let { TodoistApi(it) }

    /** Validates a token by fetching tasks before saving it. */
    fun signIn(newToken: String) {
        val t = newToken.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                load(TodoistApi(t))
                prefs.edit().putString(KEY_TOKEN, t).apply()
                token = t
            } catch (e: AuthException) {
                error = "That token was rejected. Check it and try again."
            } catch (e: Exception) {
                error = "Couldn't reach Todoist: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).apply()
        token = null
        tasks.clear()
        completed.clear()
        pending.clear()
        error = null
    }

    fun refresh() {
        val api = api ?: return
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                load(api)
            } catch (e: Exception) {
                handle(e)
            } finally {
                loading = false
            }
        }
    }

    private suspend fun load(api: TodoistApi) = coroutineScope {
        val active = async { api.activeTasks() }
        val done = async { api.completedToday() }
        val (a, d) = active.await() to done.await()
        tasks.replaceWith(a)
        completed.replaceWith(d)
    }

    fun complete(task: Task) {
        val api = api ?: return
        if (task.id in pending) return
        pending += task.id
        viewModelScope.launch {
            try {
                api.closeTask(task.id)
                tasks.removeAll { it.id == task.id }
                // A recurring task is rescheduled rather than completed, and an overdue task
                // isn't due today, so only a plain task due today joins the completed list.
                val due = task.due
                if (due != null && !due.isRecurring && due.date == LocalDate.now()) {
                    completed.insertSorted(task)
                }
            } catch (e: Exception) {
                handle(e)
            } finally {
                pending.remove(task.id)
            }
        }
    }

    fun reopen(task: Task) {
        val api = api ?: return
        if (task.id in pending) return
        pending += task.id
        viewModelScope.launch {
            try {
                api.reopenTask(task.id)
                completed.removeAll { it.id == task.id }
                tasks.insertSorted(task)
            } catch (e: Exception) {
                handle(e)
            } finally {
                pending.remove(task.id)
            }
        }
    }

    /** Returns immediately; [onAdded] runs once the task exists on the server. */
    fun add(content: String, onAdded: () -> Unit) {
        val api = api ?: return
        val c = content.trim()
        if (c.isEmpty() || adding) return
        viewModelScope.launch {
            adding = true
            error = null
            try {
                tasks.insertSorted(api.addTask(c))
                onAdded()
            } catch (e: Exception) {
                handle(e)
            } finally {
                adding = false
            }
        }
    }

    fun dismissError() { error = null }

    private fun handle(e: Exception) {
        if (e is AuthException) {
            signOut()
            error = "Your token is no longer valid. Please enter it again."
        } else {
            error = "Sync failed: ${e.message}"
        }
    }

    private fun SnapshotStateList<Task>.replaceWith(items: List<Task>) {
        clear(); addAll(items)
    }

    private fun SnapshotStateList<Task>.insertSorted(task: Task) {
        removeAll { it.id == task.id }
        val i = indexOfFirst { Task.byDue.compare(task, it) < 0 }
        if (i < 0) add(task) else add(i, task)
    }

    private companion object {
        const val KEY_TOKEN = "api_token"
    }
}
