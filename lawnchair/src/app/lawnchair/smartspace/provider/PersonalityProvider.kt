package app.lawnchair.smartspace.provider

import android.content.Context
import android.content.Intent
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import com.android.launcher3.R
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

class PersonalityProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_personality,
        { smartspacePersonality },
    ) {
    private val morningStrings = context.resources.getStringArray(R.array.greetings_morning)
    private val eveningStrings = context.resources.getStringArray(R.array.greetings_evening)
    private val nightStrings = context.resources.getStringArray(R.array.greetings_night)

    override val internalTargets: Flow<List<SmartspaceTarget>> = tickerFlow()
        .distinctUntilChanged()

    /**
     * Emits on:
     * 1. Startup immediately
     * 2. Each hour boundary (greetings only change by hour of day)
     * 3. DATE_CHANGED / TIME_CHANGED / TIMEZONE_CHANGED broadcasts
     */
    private fun tickerFlow(): Flow<List<SmartspaceTarget>> {
        val hourTicker = flow {
            while (true) {
                emit(Unit)
                // Sleep until the next hour boundary (+1s buffer to land cleanly in the new hour)
                val now = System.currentTimeMillis()
                kotlinx.coroutines.delay(3_600_000L - now % 3_600_000L + 1_000L)
            }
        }

        val broadcastTrigger = broadcastReceiverFlow(
            context,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
        )

        return merge(hourTicker, broadcastTrigger.transformLatest { emit(Unit) })
            .transformLatest {
                emit(buildTargets())
            }
    }

    private fun buildTargets(): List<SmartspaceTarget> {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        // Use day-of-year as seed so the greeting is stable throughout the day
        // but changes each day — matching the Lawnchair 2 behaviour.
        val randomIndex = abs(Random(dayOfYear).nextInt())

        val message = when (hour) {
            in 5 until 9 -> morningStrings[randomIndex % morningStrings.size]
            in 19 until 21 -> eveningStrings[randomIndex % eveningStrings.size]
            in 22 until 24, in 0 until 4 -> nightStrings[randomIndex % nightStrings.size]
            else -> return emptyList() // daytime — no greeting card
        }

        return listOf(
            SmartspaceTarget(
                id = "personalityGreeting",
                headerAction = SmartspaceAction(
                    id = "personalityGreetingAction",
                    title = message,
                ),
                // Higher than SCORE_WEATHER so greeting always sorts to first pager card
                score = SmartspaceScores.SCORE_WEATHER + 1f,
                featureType = SmartspaceTarget.FeatureType.FEATURE_TIPS,
            ),
        )
    }
}
