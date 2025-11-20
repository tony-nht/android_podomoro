package com.ahastack.poromodo.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahastack.poromodo.model.PomodoroPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Extension property to create a DataStore instance
val Context.dataStore by preferencesDataStore(name = "pomodoro_settings")
const val PODOMORO_DEFAULT_DURATION_MIN = 25
const val BREAK_DEFAULT_DURATION_MIN = 5
const val LONG_BREAK_DEFAULT_DURATION_MIN = 15

const val TERMINAL_FOCUS_TASK_ID = -1L

var POMODORO_CYCLE =
    arrayOf<PomodoroPhase>(
        PomodoroPhase.PODOMORO,
        PomodoroPhase.BREAK,
        PomodoroPhase.PODOMORO,
        PomodoroPhase.BREAK,
        PomodoroPhase.PODOMORO,
        PomodoroPhase.BREAK,
        PomodoroPhase.PODOMORO,
        PomodoroPhase.BREAK,
        PomodoroPhase.LONG_BREAK
    )

data class Settings(
    val podomoroDuration: Int = PODOMORO_DEFAULT_DURATION_MIN,
    val breakTime: Int = BREAK_DEFAULT_DURATION_MIN,
    val longBreakTime: Int = LONG_BREAK_DEFAULT_DURATION_MIN,
    val notiSoundTrack: String = ""
)

data class TimerInstance(
    val timeLeft: Int = 0,
    val timeTotal: Int = PODOMORO_DEFAULT_DURATION_MIN,
    val currentCycleIdx: Int = 0,
    val currentPhase: PomodoroPhase = PomodoroPhase.PODOMORO,
    val isRunning: Boolean = false
)


object DataStoreManager {
    // Settings preference keys
    private val ST_POMODORO_DURATION = intPreferencesKey("pomodoro_duration") // In minutes
    private val ST_BREAK_TIME = intPreferencesKey("break_time") // In minutes
    private val ST_BREAK_LONG_TIME = intPreferencesKey("long_break_time") // In minutes
    private val ST_NOTIFICATION_SOUND =
        stringPreferencesKey("notification_sound") //Serialized Soundtrack file name

    // Runtime Datastore keys
    private val TI_CURRENT_CYCLE_INDEX = intPreferencesKey("POMODORO_CYCLE_INDEX")
    private val TI_TIME_REMAINING = intPreferencesKey("TIME_REMAINING")
    private val TI_IS_RUNNING = booleanPreferencesKey("IS_RUNNING")
    private val TI_CURRENT_FOCUSED_TASK_ID = longPreferencesKey("FOCUSED_TASK")

    private var timerJob: Job? = null
    private val _timeRemaining = MutableStateFlow<Int>(0)
    val timerState: StateFlow<Int> = _timeRemaining.asStateFlow()

    private fun cancelTickingJob() {
        timerJob?.cancel()
        timerJob = null // Reset the job reference
    }

    // TODO: Maybe bug in concurreny. As the timerJob maybe set before the edit update the values
    suspend fun startTheClock(context: Context) {
        cancelTickingJob()
        context.dataStore.edit { p ->
            p[TI_IS_RUNNING] = true
            _timeRemaining.value = p[TI_TIME_REMAINING] ?: 0
        }

        timerJob = CoroutineScope(Dispatchers.Default).launch {
            flow {
                while (_timeRemaining.value > 0) {
                    delay(1000)
                    emit(Unit)
                }
            }.collect {
                if (_timeRemaining.value > 0)
                    _timeRemaining.value = _timeRemaining.value - 1
            }
        }
    }

    suspend fun stopTheClock(context: Context) {
        cancelTickingJob()
        context.dataStore.edit { p ->
            p[TI_IS_RUNNING] = false
        }
    }

    private fun getDurationFromCycleIndex(p: Preferences, idx: Int?): Int {
        val cIdx = idx ?: 0
        val ph = POMODORO_CYCLE[cIdx]
        val d = when (ph) {
            PomodoroPhase.PODOMORO -> p[ST_POMODORO_DURATION]
            PomodoroPhase.BREAK -> p[ST_BREAK_TIME]
            PomodoroPhase.LONG_BREAK -> p[ST_BREAK_LONG_TIME]
        }
        val s = if (d == null) 0 else d * 60
        _timeRemaining.value = s
        return s
    }


    // SETTINGS
    fun getSettings(context: Context): Flow<Settings> {
        return context.dataStore.data.map { preferences ->
            Settings(
                podomoroDuration = preferences[ST_POMODORO_DURATION]
                    ?: PODOMORO_DEFAULT_DURATION_MIN,
                breakTime = preferences[ST_BREAK_TIME] ?: BREAK_DEFAULT_DURATION_MIN,
                longBreakTime = preferences[ST_BREAK_LONG_TIME]
                    ?: LONG_BREAK_DEFAULT_DURATION_MIN,
                notiSoundTrack = preferences[ST_NOTIFICATION_SOUND] ?: ""
            )
        }
    }

    suspend fun saveSettings(context: Context, s: Settings) {
        context.dataStore.edit { preferences ->
            preferences[ST_POMODORO_DURATION] = s.podomoroDuration
            preferences[ST_BREAK_TIME] = s.breakTime
            preferences[ST_BREAK_LONG_TIME] = s.longBreakTime
            preferences[ST_NOTIFICATION_SOUND] = s.notiSoundTrack
        }
    }



    //  TIMER INSTANCE
    fun getTimerInstance(context: Context) : Flow<TimerInstance> {
        return context.dataStore.data.map { p ->
            val currCycleIdx = p[TI_CURRENT_CYCLE_INDEX] ?: 0
            val currCycleTotalTime = getDurationFromCycleIndex(p ,currCycleIdx)
            TimerInstance(
                timeLeft = p[TI_TIME_REMAINING] ?: currCycleTotalTime,
                timeTotal = currCycleTotalTime,
                currentCycleIdx = currCycleIdx,
                currentPhase = POMODORO_CYCLE[currCycleIdx],
                isRunning = p[TI_IS_RUNNING] == true
            )
        }
    }

    fun getFocusedTaskId(context: Context): Flow<Long> {
        return context.dataStore.data
            .map { preferences ->
                preferences[TI_CURRENT_FOCUSED_TASK_ID] ?: TERMINAL_FOCUS_TASK_ID
            }
    }

    suspend fun saveFocusTaskId(context: Context, taskId: Long) {
        context.dataStore.edit { preferences ->
            preferences[TI_CURRENT_FOCUSED_TASK_ID] = taskId
        }
    }

    suspend fun switchToNextDesiredPhase(context: Context, phase: PomodoroPhase) {
        context.dataStore.edit { preferences ->
            val curr = preferences[TI_CURRENT_CYCLE_INDEX] ?: 0
            val s = POMODORO_CYCLE.size
            // This loop may not find a podomoro phase if we are currently after the last short break
            for (idx in (curr + 1) % s..<s) {
                if (POMODORO_CYCLE[idx] == phase) {
                    preferences[TI_CURRENT_CYCLE_INDEX] = idx
                    val seconds = getDurationFromCycleIndex(preferences, idx)
                    preferences[TI_TIME_REMAINING] = seconds
                    return@edit
                }
            }

            // Therefore: We must Wrap around
            for (idx in 0..<s) {
                if (POMODORO_CYCLE[idx] == phase) {
                    preferences[TI_CURRENT_CYCLE_INDEX] = idx
                    val seconds = getDurationFromCycleIndex(preferences, idx)
                    preferences[TI_TIME_REMAINING] = seconds
                    return@edit
                }
            }
        }
    }

}