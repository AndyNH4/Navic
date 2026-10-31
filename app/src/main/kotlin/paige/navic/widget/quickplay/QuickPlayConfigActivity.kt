/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.widget.quickplay

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import paige.navic.R
import paige.navic.ui.theme.NavicTheme

@OptIn(ExperimentalMaterial3Api::class)
class QuickPlayConfigActivity : ComponentActivity(), KoinComponent {
	private val repository: QuickPlayRepository by inject()
	private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		appWidgetId = intent.getIntExtra(
			AppWidgetManager.EXTRA_APPWIDGET_ID,
			AppWidgetManager.INVALID_APPWIDGET_ID
		)
		if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish()
			return
		}
		setResult(RESULT_CANCELED, resultIntent())
		setContent {
			NavicTheme {
				ConfigScreen()
			}
		}
	}

	@Composable
	private fun ConfigScreen() {
		var sources by remember { mutableStateOf<List<QuickPlaySourceInfo>>(emptyList()) }
		var selected by remember { mutableStateOf<QuickPlaySourceInfo?>(null) }
		val scope = rememberCoroutineScope()
		LaunchedEffect(Unit) {
			sources = repository.getAvailableSources()
			selected = sources.firstOrNull()
		}
		Scaffold(
			topBar = {
				CenterAlignedTopAppBar(title = { Text(stringResource(R.string.widget_select_source)) })
			},
			bottomBar = {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = stringResource(R.string.widget_quick_play_limit),
						style = MaterialTheme.typography.bodySmall,
						modifier = Modifier.padding(bottom = 12.dp)
					)
					Button(
						onClick = { scope.launch { selected?.let { save(it) } } },
						enabled = selected != null,
						modifier = Modifier.fillMaxWidth()
					) {
						Text(stringResource(R.string.widget_add))
					}
				}
			}
		) { innerPadding ->
			LazyColumn(contentPadding = innerPadding) {
				items(sources, key = { "${it.type.name}:${it.id.orEmpty()}" }) { source ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { selected = source }
							.padding(horizontal = 16.dp, vertical = 12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						RadioButton(
							selected = selected == source,
							onClick = null
						)
						Column(modifier = Modifier.padding(start = 12.dp)) {
							Text(source.title, style = MaterialTheme.typography.bodyLarge)
							Text(
								source.subtitle,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
			}
		}
	}

	private suspend fun save(source: QuickPlaySourceInfo) {
		val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
		updateAppWidgetState(this, glanceId) { prefs ->
			prefs[QuickPlayKeys.SOURCE_TYPE] = source.type.name
			prefs[QuickPlayKeys.SOURCE_ID] = source.id.orEmpty()
			prefs[QuickPlayKeys.SOURCE_NAME] = source.title
			prefs[QuickPlayKeys.PENDING] = false
			prefs[QuickPlayKeys.ERROR] = false
		}
		QuickPlayWidget().update(this, glanceId)
		setResult(RESULT_OK, resultIntent())
		finish()
	}

	private fun resultIntent() = Intent().putExtra(
		AppWidgetManager.EXTRA_APPWIDGET_ID,
		appWidgetId
	)
}
