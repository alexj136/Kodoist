package net.alexjeffery.kodoist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** A task's due date in the device's timezone. [time] is null for all-day tasks. */
data class Due(val date: LocalDate, val time: LocalTime?, val isRecurring: Boolean) {
    companion object {
        fun parse(json: JSONObject?): Due? {
            if (json == null) return null
            val raw = json.optString("date").takeIf { it.isNotEmpty() } ?: return null
            val recurring = json.optBoolean("is_recurring")
            return try {
                when {
                    raw.length == 10 -> Due(LocalDate.parse(raw), null, recurring)
                    // Fixed-timezone datetime (e.g. "2026-11-07T08:00:00Z"): convert to device time.
                    raw.endsWith("Z") || raw.lastIndexOf('+') > 10 || raw.lastIndexOf('-') > 10 -> {
                        val local = OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
                        Due(local.toLocalDate(), local.toLocalTime(), recurring)
                    }
                    // Floating datetime (e.g. "2026-11-07T10:00:00"): already local time.
                    else -> LocalDateTime.parse(raw).let { Due(it.toLocalDate(), it.toLocalTime(), recurring) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class Task(val id: String, val content: String, val due: Due?, val dayOrder: Int) {
    companion object {
        fun from(t: JSONObject) = Task(
            id = t.getString("id"),
            content = t.getString("content"),
            due = Due.parse(t.optJSONObject("due")),
            dayOrder = t.optInt("day_order", 0),
        )

        /**
         * Oldest due first. On a given day, timed tasks come first in time order, then all-day
         * tasks, as in Todoist's Today view. Ties keep Todoist's own day order.
         */
        val byDue: Comparator<Task> = compareBy<Task>(
            { it.due?.date ?: LocalDate.MAX },
            { it.due?.time ?: LocalTime.MAX },
            { it.dayOrder },
        )
    }
}

/** Thrown when Todoist rejects the token (HTTP 401/403). */
class AuthException : IOException("Todoist rejected the API token")

/**
 * Minimal client for the Todoist API v1 (https://developer.todoist.com/api/v1/).
 * Authenticates with a personal API token sent as a Bearer header.
 * Uses only the Android framework (HttpURLConnection + org.json).
 */
class TodoistApi(private val token: String) {

    /**
     * Active tasks that are overdue or due today, sorted oldest due first.
     * The filter runs server-side in the account's timezone, so it matches Todoist's Today view.
     */
    suspend fun activeTasks(): List<Task> = withContext(Dispatchers.IO) {
        paged("$BASE/tasks/filter?limit=200&query=" + enc(ACTIVE_FILTER), "results")
            .filter { !it.optBoolean("checked") && !it.optBoolean("is_deleted") }
            .map(Task::from)
            .sortedWith(Task.byDue)
    }

    /** Completed tasks whose due date is today in the device's timezone, sorted by due time. */
    suspend fun completedToday(): List<Task> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val since = today.atStartOfDay(zone).toInstant()
        val until = today.plusDays(1).atStartOfDay(zone).toInstant()
        paged(
            "$BASE/tasks/completed/by_due_date?limit=200&since=${enc(iso(since))}&until=${enc(iso(until))}",
            "items",
        )
            .filter { !it.optBoolean("is_deleted") }
            .map(Task::from)
            .sortedWith(Task.byDue)
    }

    /** Marks a task complete (recurring tasks move to their next occurrence, as in the official apps). */
    suspend fun closeTask(id: String) = withContext(Dispatchers.IO) {
        request("POST", "$BASE/tasks/${enc(id)}/close")
        Unit
    }

    /** Marks a completed task incomplete again. */
    suspend fun reopenTask(id: String) = withContext(Dispatchers.IO) {
        request("POST", "$BASE/tasks/${enc(id)}/reopen")
        Unit
    }

    /**
     * Creates a task due today. The API itself gives new tasks *no* due date,
     * so "today" is set explicitly. It is resolved in the account's timezone.
     */
    suspend fun addTask(content: String): Task = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("content", content)
            .put("due_string", "today")
            .put("due_lang", "en")
        Task.from(JSONObject(request("POST", "$BASE/tasks", body.toString())))
    }

    /** Fetches every page of a cursor-paginated endpoint and returns the objects under [key]. */
    private fun paged(url: String, key: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var cursor: String? = null
        do {
            val json = JSONObject(request("GET", if (cursor == null) url else "$url&cursor=${enc(cursor)}"))
            val arr: JSONArray = json.getJSONArray(key)
            for (i in 0 until arr.length()) out += arr.getJSONObject(i)
            cursor = if (json.isNull("next_cursor")) null else json.optString("next_cursor").ifEmpty { null }
        } while (cursor != null)
        return out
    }

    private fun request(method: String, url: String, jsonBody: String? = null): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (jsonBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(jsonBody.toByteArray()) }
            }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw AuthException()
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Todoist returned HTTP $code ${err.take(200)}".trim())
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** RFC 3339 UTC, e.g. 2026-11-07T00:00:00Z (ISO_INSTANT always prints seconds). */
    private fun iso(i: Instant) = i.toString()

    companion object {
        private const val BASE = "https://api.todoist.com/api/v1"

        /** Due today plus anything overdue, matching Todoist's own Today view. */
        const val ACTIVE_FILTER = "today | overdue"
    }
}
