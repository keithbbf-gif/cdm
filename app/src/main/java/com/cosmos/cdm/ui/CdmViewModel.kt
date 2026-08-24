package com.cosmos.cdm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private const val SNAPSHOT_MS = 10_000L
private const val EVENTS_MS = 5_000L
private const val BACKOFF_CAP_MS = 60_000L
private const val BACKOFF_MAX_SHIFT = 4
private const val JITTER_MS = 1_000L
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
    private var eventCounter: Long = 0L
    private val eventsLock = Mutex()
    private val snapshotLock = Mutex()

    // Bumped on every connect()/disconnect(). An in-flight request captured an
    // older generation (old URL); its result is dropped instead of updating the
    // new connection's panels/state.
    private val generation = AtomicInteger(0)

    // True while any activity of the app is started. Poll loops suspend on
    // false (repeatOnLifecycle-equivalent gate) so a backgrounded app stops
    // hitting the network and draining battery; polling resumes on foreground.
    private val _foreground = MutableStateFlow(true)
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _foreground.value = true
            Lifecycle.Event.ON_STOP -> _foreground.value = false
            else -> Unit
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
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

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onCleared()
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
        // New generation: results still in flight against the old URL must not
        // land on this connection's panels.
        generation.incrementAndGet()
        if (url != prev) resetFeed()
        _conn.value = ConnState(ConnKind.Connecting, "connecting…", url)
        startLoops()
    }

    fun disconnect() {
        loops?.cancel()
        loops = null
        generation.incrementAndGet()
        _conn.value = ConnState(ConnKind.Idle, "not connected", _serverUrl.value)
    }

    fun runCommand(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (_conn.value.kind == ConnKind.Idle) {
            pushCommand(CommandEntry.Kind.ERR, "NOT CONNECTED", "connect to the API before running commands")
            return
        }
        // Wire-level busy guard: atomically claim the flag BEFORE anything is
        // sent. The UI disables its button, but IME "Go" and racing taps reach
        // this path while a command is in flight — the CAS is the real gate.
        if (!_commandBusy.compareAndSet(false, true)) {
            pushCommand(CommandEntry.Kind.ERR, "BUSY", "a command is already running — wait for it to finish")
            return
        }
        // Idempotency key: rides as request_id/X-Request-Id so a
        // timeout-then-retry cannot double-execute server-side.
        val requestId = UUID.randomUUID().toString()
        val gen = generation.get()
        pushCommand(CommandEntry.Kind.CMD, "", "> $t")
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    CosmosClient.postCommand(activeUrl, _bearer.value, t, requestId)
                }
                if (gen != generation.get()) {
                    // URL changed while in flight — result belongs to the old
                    // connection; don't report it as the new one's.
                    pushCommand(CommandEntry.Kind.ERR, "STALE", "connection changed while the command was in flight")
                    return@launch
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
            val snap = launch {
                var failures = 0
                while (true) {
                    _foreground.first { it } // suspend while backgrounded
                    val ok = refreshSnapshots()
                    failures = if (ok) 0 else minOf(failures + 1, BACKOFF_MAX_SHIFT)
                    delay(backoffDelay(SNAPSHOT_MS, failures))
                }
            }
            val ev = launch {
                var failures = 0
                while (true) {
                    _foreground.first { it }
                    val ok = pollEvents()
                    failures = if (ok) 0 else minOf(failures + 1, BACKOFF_MAX_SHIFT)
                    delay(backoffDelay(EVENTS_MS, failures))
                }
            }
            joinAll(snap, ev)
        }
    }

    /**
     * Base interval on success; exponential (base * 2^failures) on consecutive
     * failures, capped at [BACKOFF_CAP_MS]; plus 0..[JITTER_MS) of jitter so
     * retries from multiple clients don't synchronize.
     */
    private fun backoffDelay(baseMs: Long, failures: Int): Long {
        val backed = if (failures <= 0) baseMs else minOf(baseMs shl failures, BACKOFF_CAP_MS)
        return backed + Random.nextLong(JITTER_MS)
    }

    private fun resetFeed() {
        lastSeq = 0L
        _events.value = Panel(data = emptyList())
    }

    /** Returns true when the critical pair (status + health) both answered OK. */
    private suspend fun refreshSnapshots(): Boolean = snapshotLock.withLock {
        val gen = generation.get()
        val url = activeUrl
        val token = _bearer.value
        val t0 = System.currentTimeMillis()
        // Order matters: [0]=status, [1]=health are the critical pair.
        val kinds = coroutineScope {
            val s = async { fetch(gen, _status, ::parseStatus) { CosmosClient.getStatus(url, token) } }
            val h = async { fetch(gen, _health, ::parseHealth) { CosmosClient.getHealth(url, token) } }
            val p = async { fetch(gen, _spend, ::parseSpend) { CosmosClient.getSpend(url, token) } }
            val j = async { fetch(gen, _jobs, ::parseJobs) { CosmosClient.getJobs(url, token) } }
            val m = async { fetch(gen, _makers, ::parseMakers) { CosmosClient.getMakers(url, token) } }
            listOf(s.await(), h.await(), p.await(), j.await(), m.await())
        }
        val criticalOk = kinds[0] == ResultKind.Ok && kinds[1] == ResultKind.Ok
        // Stale generation (URL changed mid-flight): never touch the new
        // connection's headline state.
        if (gen != generation.get()) return@withLock criticalOk
        val rtt = System.currentTimeMillis() - t0
        _conn.value = summarize(kinds, url, rtt)
        criticalOk
    }

    /** Returns true when the events endpoint answered OK. */
    private suspend fun pollEvents(): Boolean = eventsLock.withLock {
        val gen = generation.get()
        val url = activeUrl
        val token = _bearer.value
        val json = withContext(Dispatchers.IO) { CosmosClient.getEvents(url, token, lastSeq) }
        // Stale generation: drop the result — it was fetched from the old URL.
        if (gen != generation.get()) return@withLock false
        when (classify(json)) {
            ResultKind.Offline -> {
                _events.update { it.copy(error = "offline") }
                false
            }
            ResultKind.Unauthorized -> {
                _events.update { it.copy(error = "UNAUTHORIZED — paste a bearer in Settings") }
                false
            }
            ResultKind.HttpError -> {
                _events.update { it.copy(error = errorMessage(json)) }
                false
            }
            ResultKind.Ok -> {
                val (head, incoming) = parseEvents(json)
                if (head != null && head < lastSeq) {
                    lastSeq = 0L
                    _events.value = Panel(data = emptyList(), measuredAtMs = null)
                    val again = withContext(Dispatchers.IO) { CosmosClient.getEvents(url, token, 0) }
                    if (gen == generation.get() && classify(again) == ResultKind.Ok) {
                        applyEvents(again)
                    }
                    return@withLock true
                }
                applyEvents(json, incoming)
                true
            }
        }
    }

    private fun applyEvents(json: JSONObject, incoming: List<LedgerEvent> = parseEvents(json).second) {
        // Server's own timestamp or null (age UNKNOWN) — never the client clock.
        val measured = extractMeasuredAtMs(json)
        _events.update { cur ->
            val existing = cur.data.orEmpty()
            val next = existing.toMutableList()
            for (ev in incoming) {
                val seq = ev.seq
                if (seq != null && seq <= lastSeq) continue
                if (seq != null && seq > lastSeq) lastSeq = seq
                // localId is the stable LazyColumn key — unique even when seq
                // is null, where hashCode() collided on identical rows.
                next.add(ev.copy(localId = ++eventCounter))
            }
            while (next.size > FEED_MAX) next.removeAt(0)
            Panel(data = next, measuredAtMs = measured, error = null)
        }
    }

    private suspend fun <T> fetch(
        gen: Int,
        dest: MutableStateFlow<Panel<T>>,
        parse: (JSONObject) -> T,
        call: () -> JSONObject,
    ): ResultKind {
        val json = withContext(Dispatchers.IO) { call() }
        val kind = classify(json)
        // Result from an older generation (URL changed while the request was
        // in flight) must not update the new connection's panel.
        if (gen != generation.get()) return kind
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

    /**
     * [kinds] order: [status, health, spend, jobs, makers]. The headline must
     * reflect reality: LIVE (Connected) requires the critical pair (status AND
     * health) OK with nothing failing. Critical pair OK but panels failing →
     * PARTIAL. Something answers but the critical pair doesn't → DEGRADED.
     * One lucky endpoint out of five is never "LIVE".
     */
    private fun summarize(kinds: List<ResultKind>, url: String, rtt: Long): ConnState {
        if (kinds.any { it == ResultKind.Unauthorized }) {
            return ConnState(ConnKind.Unauthorized, "UNAUTHORIZED — paste a bearer", url)
        }
        val okCount = kinds.count { it == ResultKind.Ok }
        val failing = kinds.size - okCount
        val criticalOk = kinds.size >= 2 &&
            kinds[0] == ResultKind.Ok && kinds[1] == ResultKind.Ok
        return when {
            okCount == 0 && kinds.any { it == ResultKind.Offline } ->
                ConnState(ConnKind.Offline, "offline — COSMOS is not answering", url)
            okCount == 0 ->
                ConnState(ConnKind.Offline, "SERVER DOWN", url)
            criticalOk && failing == 0 ->
                ConnState(ConnKind.Connected, "connected · $url · rtt ${rtt}ms", url)
            criticalOk ->
                ConnState(ConnKind.Partial, "PARTIAL — $failing/${kinds.size} panels failing", url)
            else ->
                ConnState(
                    ConnKind.Partial,
                    "DEGRADED — status/health failing · $okCount/${kinds.size} panels ok",
                    url,
                )
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
