package com.cosmos.cdm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cosmos.cdm.data.CommandEntry
import com.cosmos.cdm.data.ConnKind
import com.cosmos.cdm.data.ConnState
import com.cosmos.cdm.data.CosmosClient
import com.cosmos.cdm.data.HealthSnapshot
import com.cosmos.cdm.data.JobsSnapshot
import com.cosmos.cdm.data.LedgerEvent
import com.cosmos.cdm.data.Maker
import com.cosmos.cdm.data.Panel
import com.cosmos.cdm.data.SettingsStore
import com.cosmos.cdm.data.SpendSnapshot
import com.cosmos.cdm.data.StatusSnapshot
import com.cosmos.cdm.data.extractMeasuredAtMs
import com.cosmos.cdm.data.parseEvents
import com.cosmos.cdm.data.parseHealth
import com.cosmos.cdm.data.parseJobs
import com.cosmos.cdm.data.parseMakers
import com.cosmos.cdm.data.parseSpend
import com.cosmos.cdm.data.parseStatus
import com.cosmos.cdm.data.pretty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val SNAPSHOT_MS = 10_000L
private const val EVENTS_MS = 5_000L
private const val CONSOLE_MAX = 50
private const val FEED_MAX = 200

class CdmViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    // Bearer lives here and nowhere else — never written to SettingsStore.
    private val _bearer = MutableStateFlow("")
    val bearer: StateFlow<String> = _bearer.asStateFlow()

    // Draft in the Settings field. Polling uses [activeUrl], committed on CONNECT.
    private val _serverUrl = MutableStateFlow(store.serverUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    private var activeUrl: String = store.serverUrl().trim().trimEnd('/')

    private val _conn = MutableStateFlow(ConnState())
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _nowMs = MutableStateFlow(System.currentTimeMillis())
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    private val _status = MutableStateFlow(Panel<StatusSnapshot>())
    val status: StateFlow<Panel<StatusSnapshot>> = _status.asStateFlow()

    private val _health = MutableStateFlow(Panel<HealthSnapshot>())
    val health: StateFlow<Panel<HealthSnapshot>> = _health.asStateFlow()

    private val _spend = MutableStateFlow(Panel<SpendSnapshot>())
    val spend: StateFlow<Panel<SpendSnapshot>> = _spend.asStateFlow()

    private val _jobs = MutableStateFlow(Panel<JobsSnapshot>())
    val jobs: StateFlow<Panel<JobsSnapshot>> = _jobs.asStateFlow()

    private val _makers = MutableStateFlow(Panel<List<Maker>>())
    val makers: StateFlow<Panel<List<Maker>>> = _makers.asStateFlow()

    private val _events = MutableStateFlow(Panel<List<LedgerEvent>>(data = emptyList()))
    val events: StateFlow<Panel<List<LedgerEvent>>> = _events.asStateFlow()

    private val _commands = MutableStateFlow<List<CommandEntry>>(emptyList())
    val commands: StateFlow<List<CommandEntry>> = _commands.asStateFlow()

    private val _commandBusy = MutableStateFlow(false)
    val commandBusy: StateFlow<Boolean> = _commandBusy.asStateFlow()

    private var loops: Job? = null
    private var lastSeq: Long = 0L
    private val eventsLock = Mutex()
    private val snapshotLock = Mutex()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _nowMs.value = System.currentTimeMillis()
            }
        }
        // URL is persisted; start polling so the dashboard is live on launch.
        // Bearer is empty until pasted — --no-auth kernels work immediately.
        connect()
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url.trim()
    }

    fun setBearer(token: String) {
        _bearer.value = token
    }

    fun applyLanPreset() {
        _serverUrl.value = SettingsStore.LAN_URL
    }

    fun applyTailscalePreset() {
        _serverUrl.value = SettingsStore.TAILSCALE_URL
    }

    fun connect() {
        val url = _serverUrl.value.trim().trimEnd('/')
        if (url.isBlank()) {
            _conn.value = ConnState(ConnKind.Idle, "set a server URL", "")
            return
        }
        val prev = activeUrl
        store.setServerUrl(url)
        _serverUrl.value = url
        activeUrl = url
        if (url != prev) resetFeed()
        _conn.value = ConnState(ConnKind.Connecting, "connecting…", url)
        startLoops()
    }

    fun disconnect() {
        loops?.cancel()
        loops = null
        _conn.value = ConnState(ConnKind.Idle, "not connected", _serverUrl.value)
    }

    fun runCommand(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (_conn.value.kind == ConnKind.Idle) {
            pushCommand(CommandEntry.Kind.ERR, "NOT CONNECTED", "connect to the API before running commands")
            return
        }
        pushCommand(CommandEntry.Kind.CMD, "", "> $t")
        viewModelScope.launch {
            _commandBusy.value = true
            try {
                val json = withContext(Dispatchers.IO) {
                    CosmosClient.postCommand(activeUrl, _bearer.value, t)
                }
                when (classify(json)) {
                    ResultKind.Offline ->
                        pushCommand(CommandEntry.Kind.ERR, "OFFLINE", json.optString("detail", "server not answering"))
                    ResultKind.Unauthorized ->
                        pushCommand(
                            CommandEntry.Kind.ERR,
                            "UNAUTHORIZED",
                            "paste a bearer in Settings, or start COSMOS with --no-auth",
                        )
                    ResultKind.HttpError ->
                        pushCommand(
                            CommandEntry.Kind.ERR,
                            json.optString("error", "ERROR").uppercase(),
                            json.pretty(),
                        )
                    ResultKind.Ok -> {
                        if (json.has("error") && !json.isNull("error") && json.optString("error").isNotBlank()) {
                            pushCommand(CommandEntry.Kind.ERR, json.optString("error").uppercase(), json.pretty())
                        } else {
                            pushCommand(CommandEntry.Kind.OK, "OK", json.pretty())
                        }
                    }
                }
            } finally {
                _commandBusy.value = false
            }
        }
    }

    private fun startLoops() {
        loops?.cancel()
        loops = viewModelScope.launch {
            refreshSnapshots()
            pollEvents()
            val snap = launch {
                while (true) {
                    delay(SNAPSHOT_MS)
                    refreshSnapshots()
                }
            }
            val ev = launch {
                while (true) {
                    delay(EVENTS_MS)
                    pollEvents()
                }
            }
            joinAll(snap, ev)
        }
    }

    private fun resetFeed() {
        lastSeq = 0L
        _events.value = Panel(data = emptyList())
    }

    private suspend fun refreshSnapshots() {
        snapshotLock.withLock {
            val url = activeUrl
            val token = _bearer.value
            val t0 = System.currentTimeMillis()
            val kinds = coroutineScope {
                val s = async { fetch(_status, ::parseStatus) { CosmosClient.getStatus(url, token) } }
                val h = async { fetch(_health, ::parseHealth) { CosmosClient.getHealth(url, token) } }
                val p = async { fetch(_spend, ::parseSpend) { CosmosClient.getSpend(url, token) } }
                val j = async { fetch(_jobs, ::parseJobs) { CosmosClient.getJobs(url, token) } }
                val m = async { fetch(_makers, ::parseMakers) { CosmosClient.getMakers(url, token) } }
                listOf(s.await(), h.await(), p.await(), j.await(), m.await())
            }
            val rtt = System.currentTimeMillis() - t0
            _conn.value = summarize(kinds, url, rtt)
        }
    }

    private suspend fun pollEvents() {
        eventsLock.withLock {
            val url = activeUrl
            val token = _bearer.value
            val json = withContext(Dispatchers.IO) { CosmosClient.getEvents(url, token, lastSeq) }
            when (classify(json)) {
                ResultKind.Offline -> _events.update { it.copy(error = "offline") }
                ResultKind.Unauthorized -> _events.update {
                    it.copy(error = "UNAUTHORIZED — paste a bearer in Settings")
                }
                ResultKind.HttpError -> _events.update {
                    it.copy(error = errorMessage(json))
                }
                ResultKind.Ok -> {
                    val (head, incoming) = parseEvents(json)
                    if (head != null && head < lastSeq) {
                        lastSeq = 0L
                        _events.value = Panel(data = emptyList(), measuredAtMs = System.currentTimeMillis())
                        val again = withContext(Dispatchers.IO) { CosmosClient.getEvents(url, token, 0) }
                        if (classify(again) == ResultKind.Ok) {
                            applyEvents(again)
                        }
                        return@withLock
                    }
                    applyEvents(json, incoming)
                }
            }
        }
    }

    private fun applyEvents(json: JSONObject, incoming: List<LedgerEvent> = parseEvents(json).second) {
        val now = System.currentTimeMillis()
        _events.update { cur ->
            val existing = cur.data.orEmpty()
            val next = existing.toMutableList()
            for (ev in incoming) {
                val seq = ev.seq
                if (seq != null && seq <= lastSeq) continue
                if (seq != null && seq > lastSeq) lastSeq = seq
                next.add(ev)
            }
            while (next.size > FEED_MAX) next.removeAt(0)
            Panel(data = next, measuredAtMs = now, error = null)
        }
    }

    private suspend fun <T> fetch(
        dest: MutableStateFlow<Panel<T>>,
        parse: (JSONObject) -> T,
        call: () -> JSONObject,
    ): ResultKind {
        val json = withContext(Dispatchers.IO) { call() }
        val kind = classify(json)
        when (kind) {
            ResultKind.Offline -> dest.update { it.copy(error = "offline") }
            ResultKind.Unauthorized -> dest.update {
                it.copy(error = "UNAUTHORIZED — paste a bearer in Settings, or start COSMOS with --no-auth")
            }
            ResultKind.HttpError -> dest.update { it.copy(error = errorMessage(json)) }
            ResultKind.Ok -> dest.value = Panel(
                data = parse(json),
                measuredAtMs = extractMeasuredAtMs(json),
                error = null,
            )
        }
        return kind
    }

    private fun summarize(kinds: List<ResultKind>, url: String, rtt: Long): ConnState {
        if (kinds.any { it == ResultKind.Unauthorized }) {
            return ConnState(ConnKind.Unauthorized, "UNAUTHORIZED — paste a bearer", url)
        }
        val allBad = kinds.isNotEmpty() && kinds.all { it != ResultKind.Ok }
        val anyOk = kinds.any { it == ResultKind.Ok }
        return when {
            allBad && kinds.any { it == ResultKind.Offline } ->
                ConnState(ConnKind.Offline, "offline — COSMOS is not answering", url)
            allBad ->
                ConnState(ConnKind.Offline, "SERVER DOWN", url)
            anyOk ->
                ConnState(ConnKind.Connected, "connected · $url · rtt ${rtt}ms", url)
            else ->
                ConnState(ConnKind.Connecting, "connecting…", url)
        }
    }

    private fun classify(json: JSONObject): ResultKind {
        val code = json.optInt("http_status", 200)
        val err = json.optString("error")
        return when {
            code == 0 || err == "offline" -> ResultKind.Offline
            code == 401 || err == "UNAUTHORIZED" -> ResultKind.Unauthorized
            code !in 200..299 -> ResultKind.HttpError
            else -> ResultKind.Ok
        }
    }

    private fun errorMessage(json: JSONObject): String {
        val err = json.optString("error", "ERROR")
        val detail = json.optString("detail", "")
        return if (detail.isBlank()) err else "$err — $detail"
    }

    private fun pushCommand(kind: CommandEntry.Kind, head: String, body: String?) {
        _commands.update { cur ->
            val next = cur + CommandEntry(System.currentTimeMillis(), kind, head, body)
            if (next.size > CONSOLE_MAX) next.takeLast(CONSOLE_MAX) else next
        }
    }

    private enum class ResultKind { Ok, Offline, Unauthorized, HttpError }
}
