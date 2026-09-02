/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saulhdev.feeder.ui.pages

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.ui.components.PreferenceGroup
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.dialog.BaseDialog
import com.saulhdev.feeder.ui.components.dialog.StringSelectionPrefDialogUI
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PreferencesPage(
    prefs: FeedPreferences = koinInject(),
) {
    val context = LocalContext.current
    val title = stringResource(id = R.string.title_settings)

    val servicePrefs = listOf(
        prefs.itemsPerFeed,
        prefs.syncFrequency,
        prefs.syncRange,
        prefs.syncOnlyOnWifi,
        prefs.openInBrowser,
        prefs.offlineReader,
        prefs.removeDuplicates,
    )
    val filterPrefs = listOf(
        prefs.blockedWords,
    )
    val themePrefs = listOf(
        prefs.dynamicColor,
        prefs.overlayTheme,
        prefs.overlayTransparency,
    )
    val debugPrefs = listOf(
        prefs.about,
    )

    val openDialog = remember { mutableStateOf(false) }
    var dialogPref by remember { mutableStateOf<Any?>(null) }
    val onPrefDialog = { pref: Any ->
        dialogPref = pref
        openDialog.value = true
    }

    ViewWithActionBar(
        title = title,
        showBackButton = false,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = paddingValues.calculateTopPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "hub_plugins_card") {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "🔌 Hub Plugins & Context Modules",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.Text(
                            text = "Configure GitHub Pulse, Privacy Weather, Calendar Agenda, Hardware Telemetry, and Custom REST JSON endpoints.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                        val navController = com.saulhdev.feeder.ui.navigation.LocalNavController.current
                        androidx.compose.material3.Button(
                            onClick = { navController.navigate(com.saulhdev.feeder.ui.navigation.NavRoute.Plugins) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Text("Manage Plugins & Cards")
                        }
                    }
                }
            }
            item(key = R.string.title_service) {
                PreferenceGroup(
                    stringResource(id = R.string.title_service),
                    prefs = servicePrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_filters) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_filters),
                    prefs = filterPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_overlay) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_overlay),
                    prefs = themePrefs,
                    onPrefDialog = onPrefDialog
                )

                if (!Settings.canDrawOverlays(context)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(R.string.draw_permission_required))
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            ) {
                                Text(text = stringResource(R.string.go_to_settings))
                            }
                        }
                    }
                }
            }
            item(key = R.string.title_other) {
                PreferenceGroup(
                    stringResource(id = R.string.title_other),
                    prefs = debugPrefs,
                    onPrefDialog = onPrefDialog
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (openDialog.value) {
            BaseDialog(openDialogCustom = openDialog) {
                when (dialogPref) {
                    is StringSelectionPref -> StringSelectionPrefDialogUI(
                        pref = dialogPref as StringSelectionPref,
                        openDialogCustom = openDialog
                    )
                }
            }
        }
    }
}
