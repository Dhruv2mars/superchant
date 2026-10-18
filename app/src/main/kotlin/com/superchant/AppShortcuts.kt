package com.superchant

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.superchant.data.Goal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ways to start counting without opening Home first: the static "Start counting" launcher shortcut
 * (res/xml/shortcuts.xml), one dynamic shortcut per goal, and the Quick Settings tile. They all open
 * MainActivity with [ACTION_START_COUNTING], so starting still goes through Home's permission checks.
 */
object AppShortcuts {
    const val ACTION_START_COUNTING = "com.superchant.action.START_COUNTING"
    private const val EXTRA_GOAL_ID = "com.superchant.extra.GOAL_ID"
    private const val MAX_GOAL_SHORTCUTS = 3

    data class Request(val goalId: String?)

    fun parse(intent: Intent?): Request? =
        if (intent?.action == ACTION_START_COUNTING) Request(intent.getStringExtra(EXTRA_GOAL_ID)) else null

    /** Tells the launcher a shortcut was used, so it can rank and suggest it. */
    fun reportUsed(context: Context, request: Request) {
        ShortcutManagerCompat.reportShortcutUsed(context, request.goalId?.let { "goal_$it" } ?: STATIC_START_ID)
    }

    private const val STATIC_START_ID = "start_counting"

    fun startIntent(context: Context, goalId: String? = null): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_START_COUNTING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (goalId != null) putExtra(EXTRA_GOAL_ID, goalId) }

    // What this process last published, so a launch or recreation with unchanged goals makes no binder calls
    private var published: Any? = null

    /** Long-press shortcuts for the first goals, kept in step with the goal list. Runs off the main thread. */
    suspend fun updateGoalShortcuts(context: Context, goals: List<Goal>) = withContext(Dispatchers.Default) {
        // Labels are localized, so the locale is part of what was published
        val wanted = context.resources.configuration.locales.toLanguageTags() to goals.take(MAX_GOAL_SHORTCUTS).map { it.id to it.name }
        synchronized(this@AppShortcuts) {
            if (wanted == published) return@withContext
            published = wanted
        }
        val shortcuts = goals.take(MAX_GOAL_SHORTCUTS).mapIndexed { index, goal ->
            ShortcutInfoCompat.Builder(context, "goal_${goal.id}")
                .setShortLabel(goal.name.take(24))
                .setLongLabel(context.getString(R.string.shortcut_start_goal, goal.name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_goal))
                .setIntent(startIntent(context, goal.id))
                .setRank(index)
                .build()
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: Exception) {
            // Rate limiting or a launcher without shortcut support; nothing to do
            synchronized(this@AppShortcuts) { published = null }
        }
    }
}
