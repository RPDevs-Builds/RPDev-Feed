/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.impl

import android.content.Context
import com.saulhdev.feeder.plugins.ConfigFieldType
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.plugins.PluginConfigField
import com.saulhdev.feeder.plugins.models.HubAction
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.plugins.models.HubChip
import com.saulhdev.feeder.plugins.models.HubTimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubPlugin : HubPlugin {

    override val id: String = "plugin_github_pulse"
    override val name: String = "GitHub Pulse"
    override val description: String = "Live GitHub PR review requests, workflow CI runs, assigned issues, and commit activity."
    override val category: PluginCategory = PluginCategory.DEVELOPER
    override val iconName: String = "github"
    override val defaultRefreshMinutes: Int = 15

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun getConfigFields(): List<PluginConfigField> = listOf(
        PluginConfigField(
            key = "username",
            label = "GitHub Username / Org",
            description = "GitHub username or organization to monitor (e.g. RPDevs-Builds)",
            defaultValue = "RPDevs-Builds",
            isRequired = true
        ),
        PluginConfigField(
            key = "token",
            label = "Personal Access Token (Optional)",
            description = "GitHub PAT (classic or fine-grained) for unread notifications and private repos",
            type = ConfigFieldType.PASSWORD,
            defaultValue = ""
        ),
        PluginConfigField(
            key = "tracked_repos",
            label = "Tracked Repositories",
            description = "Comma-separated repositories for CI/CD checks (e.g. RPDevs-Builds/RPDev-Launcher, RPDevs-Builds/RPDev-Feed)",
            defaultValue = "RPDevs-Builds/RPDev-Launcher, RPDevs-Builds/RPDev-Feed"
        )
    )

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> = withContext(Dispatchers.IO) {
        try {
            val username = config["username"]?.takeIf { it.isNotBlank() } ?: "RPDevs-Builds"
            val token = config["token"]?.takeIf { it.isNotBlank() }
            val trackedReposStr = config["tracked_repos"]?.takeIf { it.isNotBlank() }
                ?: "RPDevs-Builds/RPDev-Launcher, RPDevs-Builds/RPDev-Feed"

            val trackedRepos = trackedReposStr.split(",").map { it.trim() }.filter { it.contains("/") }

            val chips = mutableListOf<HubChip>()
            val timelineItems = mutableListOf<HubTimelineItem>()
            var badgeText = "Active"

            // 1. Fetch user/org public activity events
            val eventsRequest = Request.Builder()
                .url("https://api.github.com/users/$username/events/public?per_page=10")
                .header("User-Agent", "RPDev-Feed/1.0.1")
                .apply {
                    if (token != null) header("Authorization", "Bearer $token")
                }
                .build()

            var pushEventsCount = 0
            httpClient.newCall(eventsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val eventsJson = JSONArray(body)
                        for (i in 0 until minOf(eventsJson.length(), 6)) {
                            val event = eventsJson.getJSONObject(i)
                            val type = event.optString("type")
                            val repo = event.optJSONObject("repo")?.optString("name") ?: ""
                            val repoShort = repo.substringAfterLast("/")
                            val createdAt = event.optString("created_at")

                            when (type) {
                                "PushEvent" -> {
                                    pushEventsCount++
                                    val payload = event.optJSONObject("payload")
                                    val commits = payload?.optJSONArray("commits")
                                    val firstCommit = commits?.optJSONObject(0)
                                    val msg = firstCommit?.optString("message")?.lines()?.firstOrNull() ?: "Pushed commits"
                                    val sha = firstCommit?.optString("sha")?.take(7) ?: ""

                                    timelineItems.add(
                                        HubTimelineItem(
                                            title = msg,
                                            subtitle = if (sha.isNotBlank()) "Commit $sha in $repoShort" else repoShort,
                                            tag = repoShort,
                                            timestamp = createdAt.take(10),
                                            iconName = "git_commit",
                                            clickUrl = "https://github.com/$repo",
                                            statusSuccess = true
                                        )
                                    )
                                }
                                "PullRequestEvent" -> {
                                    val action = event.optJSONObject("payload")?.optString("action") ?: "opened"
                                    val pr = event.optJSONObject("payload")?.optJSONObject("pull_request")
                                    val title = pr?.optString("title") ?: "Pull Request #$action"
                                    val prUrl = pr?.optString("html_url") ?: "https://github.com/$repo"

                                    timelineItems.add(
                                        HubTimelineItem(
                                            title = "PR: $title",
                                            subtitle = "$action in $repoShort",
                                            tag = "PR",
                                            timestamp = createdAt.take(10),
                                            iconName = "git_pull_request",
                                            clickUrl = prUrl,
                                            statusSuccess = action != "closed"
                                        )
                                    )
                                }
                                "IssuesEvent" -> {
                                    val action = event.optJSONObject("payload")?.optString("action") ?: "opened"
                                    val issue = event.optJSONObject("payload")?.optJSONObject("issue")
                                    val title = issue?.optString("title") ?: "Issue #$action"
                                    val issueUrl = issue?.optString("html_url") ?: "https://github.com/$repo"

                                    timelineItems.add(
                                        HubTimelineItem(
                                            title = "Issue: $title",
                                            subtitle = "$action in $repoShort",
                                            tag = "Issue",
                                            timestamp = createdAt.take(10),
                                            iconName = "git_issue",
                                            clickUrl = issueUrl,
                                            statusSuccess = action != "closed"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            chips.add(HubChip(label = "⚡ $pushEventsCount Pushes", colorHex = "#4e54c8"))

            // 2. Fetch CI / Workflow Runs for tracked repositories
            var passingWorkflows = 0
            var totalWorkflows = 0
            for (repo in trackedRepos.take(3)) {
                val runsRequest = Request.Builder()
                    .url("https://api.github.com/repos/$repo/actions/runs?per_page=1")
                    .header("User-Agent", "RPDev-Feed/1.0.1")
                    .apply {
                        if (token != null) header("Authorization", "Bearer $token")
                    }
                    .build()

                try {
                    httpClient.newCall(runsRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JSONObject(body)
                                val runs = json.optJSONArray("workflow_runs")
                                if (runs != null && runs.length() > 0) {
                                    totalWorkflows++
                                    val run = runs.getJSONObject(0)
                                    val conclusion = run.optString("conclusion")
                                    val status = run.optString("status")
                                    val runName = run.optString("name")
                                    val runUrl = run.optString("html_url")
                                    val isSuccess = conclusion == "success" || status == "completed"

                                    if (isSuccess) passingWorkflows++

                                    timelineItems.add(
                                        0,
                                        HubTimelineItem(
                                            title = "$runName: ${if (conclusion.isNotBlank()) conclusion else status}",
                                            subtitle = repo.substringAfterLast("/"),
                                            tag = "CI/CD",
                                            iconName = if (isSuccess) "check_circle" else "alert_circle",
                                            clickUrl = runUrl,
                                            statusSuccess = isSuccess
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (totalWorkflows > 0) {
                chips.add(
                    HubChip(
                        label = if (passingWorkflows == totalWorkflows) "✓ CI: All Passing" else "⚠️ CI: $passingWorkflows/$totalWorkflows Passing",
                        colorHex = if (passingWorkflows == totalWorkflows) "#28a745" else "#dc3545"
                    )
                )
                badgeText = if (passingWorkflows == totalWorkflows) "CI Passing" else "Action Required"
            }

            // 3. Authenticated Notifications check if token present
            if (token != null) {
                val notifRequest = Request.Builder()
                    .url("https://api.github.com/notifications?per_page=5")
                    .header("User-Agent", "RPDev-Feed/1.0.1")
                    .header("Authorization", "Bearer $token")
                    .build()

                try {
                    httpClient.newCall(notifRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                val notifs = JSONArray(body)
                                if (notifs.length() > 0) {
                                    chips.add(
                                        HubChip(
                                            label = "🔔 ${notifs.length()} Unread",
                                            colorHex = "#ff9800",
                                            clickUrl = "https://github.com/notifications"
                                        )
                                    )
                                    badgeText = "🔔 ${notifs.length()} New"
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val card = HubCardData.Composite(
                pluginId = id,
                title = "🐙 GitHub Pulse",
                subtitle = "Active as @$username",
                badge = badgeText,
                chips = chips,
                timelineItems = timelineItems.take(5),
                actions = listOf(
                    HubAction(label = "GitHub", url = "https://github.com/$username", isPrimary = true),
                    HubAction(label = "Notifications", url = "https://github.com/notifications")
                )
            )

            Result.success(card)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
