/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Offline
import paige.navic.ui.component.common.SegmentedListItem
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.NestedTopBarDefaults
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsNavItem
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.ui.screen.settings.viewmodel.SettingsDataStorageViewModel
import paige.navic.util.PROXY_URL_REGEX
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun SettingsConnectionOptionsScreen() {
	val backStack = LocalNavStack.current
	val sizeClass = LocalSizeClass.current
	val hideBack = sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium &&
		backStack.any { it is Screen.Settings.Root }

	val viewModel = koinViewModel<SettingsDataStorageViewModel>()
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val activeInstanceUrl by sessionManager.activeInstanceUrl.collectAsStateWithLifecycle()
	val isUsingBackupUrl by sessionManager.isUsingBackupUrl.collectAsStateWithLifecycle()
	val serverCapabilities by sessionManager.serverCapabilities.collectAsStateWithLifecycle()
	val syncState by viewModel.syncState.collectAsStateWithLifecycle()
	val pendingActionCount by viewModel.pendingActionCount.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

	var primaryUrlField by mutableStateOf(sessionManager.primaryUrl())
	val primaryUrlHasErrors by derivedStateOf {
		primaryUrlField.isBlank() ||
			(!primaryUrlField.startsWith("https://") && !primaryUrlField.startsWith("http://"))
	}
	val updatePrimaryUrl: (String) -> Unit = { url ->
		primaryUrlField = url
		if (!primaryUrlHasErrors) {
			sessionManager.setPrimaryUrl(url.trim().removeSuffix("/"))
		}
	}

	var backupUrlField by mutableStateOf(sessionManager.backupUrl())
	val backupUrlHasErrors by derivedStateOf {
		backupUrlField.isNotBlank() &&
			!backupUrlField.startsWith("https://") &&
			!backupUrlField.startsWith("http://")
	}
	val updateBackupUrl: (String) -> Unit = { url ->
		backupUrlField = url
		if (url.isBlank() || !backupUrlHasErrors) {
			sessionManager.setBackupUrl(url.trim().removeSuffix("/"))
		}
	}

	var proxyUrlField by mutableStateOf(preferenceManager.proxyUrl)
	val proxyUrlHasErrors by derivedStateOf {
		if (proxyUrlField.isNotBlank()) {
			!PROXY_URL_REGEX.matches(proxyUrlField)
		} else {
			false
		}
	}
	val updateProxyUrl: (String) -> Unit = { url ->
		proxyUrlField = url
		if (PROXY_URL_REGEX.matches(proxyUrlField)) {
			preferenceManager.proxyUrl = url
			sessionManager.refreshClient()
		}
	}

	var sslNoopChecked by mutableStateOf(preferenceManager.dangerousSslNoopEnabled)
	val updateSslNoop: (Boolean) -> Unit = { bool ->
		sslNoopChecked = bool
		preferenceManager.dangerousSslNoopEnabled = bool
		sessionManager.refreshClient()
	}

	val smoothSyncProgress by animateFloatAsState(
		if (syncState.isSyncing) syncState.progress else 0f,
		animationSpec = tween(durationMillis = 250, easing = EaseOut)
	)
	val offlineIcon = @Composable {
		if (!isOnline) {
			Icon(
				Icons.Outlined.Offline,
				stringResource(R.string.info_not_available_offline),
				modifier = Modifier.size(20.dp)
			)
		}
	}

	@Composable
	fun timeSinceLastSync(): String {
		val time = preferenceManager.lastFullSyncTime
		if (time == 0L) return stringResource(R.string.info_sync_never)

		val duration = Clock.System.now() - Instant.fromEpochMilliseconds(time)
		val minutes = duration.inWholeMinutes.toInt()
		val hours = duration.inWholeHours.toInt()
		val days = duration.inWholeDays.toInt()
		return when {
			minutes < 1 -> stringResource(R.string.info_sync_just_now)
			hours < 1 -> pluralStringResource(R.plurals.count_minutes_ago, minutes, minutes)
			days < 1 -> pluralStringResource(R.plurals.count_hours_ago, hours, hours)
			else -> pluralStringResource(R.plurals.count_days_ago, days, days)
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(R.string.title_connection_sync)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				if (isLoggedIn) {
					SettingsGroup {
						OutlinedTextField(
							modifier = Modifier.fillMaxWidth(),
							singleLine = true,
							value = primaryUrlField,
							onValueChange = updatePrimaryUrl,
							isError = primaryUrlHasErrors,
							label = { Text(stringResource(R.string.option_account_instance)) },
							supportingText = {
								if (primaryUrlHasErrors) {
									Text(stringResource(R.string.info_incorrect_backup_url_format))
								}
							}
						)
					}
					SettingsGroup {
						OutlinedTextField(
							modifier = Modifier.fillMaxWidth(),
							singleLine = true,
							value = backupUrlField,
							onValueChange = updateBackupUrl,
							isError = backupUrlHasErrors,
							label = { Text(stringResource(R.string.option_backup_server)) },
							supportingText = {
								if (backupUrlHasErrors) {
									Text(stringResource(R.string.info_incorrect_backup_url_format))
								} else {
									Text(stringResource(R.string.info_backup_instance_description))
								}
							}
						)
					}
					if (backupUrlField.isNotBlank()) {
						SettingsGroup {
							SettingsNavItem(
								content = {
									Text(
										stringResource(
											if (isUsingBackupUrl) R.string.action_switch_to_primary
											else R.string.action_switch_to_backup
										)
									)
								},
								supportingContent = {
									Text(stringResource(R.string.info_active_server, activeInstanceUrl))
								},
								shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
								onClick = dropUnlessResumed {
									scope.launch {
										if (!sessionManager.switchToOtherUrl()) {
											snackBarManager.notify(R.string.notice_server_unreachable)
										}
									}
								}
							)
						}
					}
					SettingsGroup {
						SettingsToggleItem(
							checked = preferenceManager.navidromeEnhancementEnabled,
							onCheckedChange = { preferenceManager.navidromeEnhancementEnabled = it },
							content = { Text(stringResource(R.string.option_navidrome_enhancement)) },
							supportingContent = {
								val serverType = serverCapabilities.serverType?.let {
									if (it.equals("navidrome", ignoreCase = true)) "Navidrome" else it
								}
								val serverDescription = listOfNotNull(
									serverType,
									serverCapabilities.serverVersion
								).joinToString(" ")
								Text(
									when {
										serverDescription.isBlank() -> stringResource(
											R.string.info_navidrome_enhancement_not_detected
										)
										serverCapabilities.navidromeNativeAvailable
											&& preferenceManager.navidromeEnhancementEnabled -> stringResource(
											R.string.info_navidrome_enhancement_enabled,
											serverDescription
										)
										serverCapabilities.navidromeNativeAvailable -> stringResource(
											R.string.info_navidrome_enhancement_disabled,
											serverDescription
										)
										else -> stringResource(
											R.string.info_navidrome_enhancement_unavailable,
											serverDescription
										)
									}
								)
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
						)
					}
					SettingsGroup(title = { Text(stringResource(R.string.title_sync_control)) }) {
						SegmentedListItem(
							onClick = {},
							content = { Text(stringResource(R.string.option_live_status)) },
							supportingContent = {
								Column(Modifier.fillMaxWidth()) {
									Text(stringResource(syncState.message))
									AnimatedVisibility(
										syncState.isSyncing,
										enter = fadeIn() + expandVertically(clip = false),
										exit = fadeOut() + shrinkVertically(clip = false)
									) {
										LinearProgressIndicator(
											progress = {
												if (!syncState.isSyncing) 1f
												else smoothSyncProgress.coerceIn(0f, 1f)
											},
											modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
										)
									}
								}
							},
							trailingContent = offlineIcon,
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 5)
						)
						SegmentedListItem(
							onClick = viewModel::triggerManualSync,
							enabled = isOnline,
							content = { Text(stringResource(R.string.action_trigger_sync)) },
							supportingContent = { Text(stringResource(R.string.subtitle_trigger_sync)) },
							trailingContent = offlineIcon,
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 5)
						)
						SettingsToggleItem(
							checked = preferenceManager.syncServerPlayHistory,
							onCheckedChange = { enabled ->
								if (!serverCapabilities.navidromeNativeAvailable
									|| !preferenceManager.navidromeEnhancementEnabled
								) {
									snackBarManager.notify(R.string.notice_navidrome_enhancement_unavailable)
								} else {
									preferenceManager.syncServerPlayHistory = enabled
								}
							},
							content = { Text(stringResource(R.string.option_sync_server_play_history)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 5)
						)
						SegmentedListItem(
							onClick = {},
							content = { Text(stringResource(R.string.option_last_sync)) },
							supportingContent = { Text(timeSinceLastSync()) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 5)
						)
						SegmentedListItem(
							onClick = {},
							content = { Text(stringResource(R.string.option_pending_actions)) },
							supportingContent = {
								Text(stringResource(R.string.subtitle_pending_actions, pendingActionCount))
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 5)
						)
					}
				}
				SettingsGroup {
					OutlinedTextField(
						modifier = Modifier.fillMaxWidth(),
						singleLine = true,
						value = proxyUrlField,
						onValueChange = updateProxyUrl,
						isError = proxyUrlHasErrors,
						label = { Text(stringResource(R.string.option_proxy_url)) },
						placeholder = { Text(stringResource(R.string.info_proxy_url_placeholder)) },
						supportingText = {
							if (proxyUrlHasErrors) {
								Text(stringResource(R.string.info_incorrect_proxy_url_format))
							}
						}
					)
				}
				SettingsGroup {
					SettingsToggleItem(
						checked = sslNoopChecked,
						onCheckedChange = updateSslNoop,
						content = { Text(stringResource(R.string.option_ignore_ssl_certificates)) },
						supportingContent = { Text(stringResource(R.string.subtitle_ignore_ssl_certificates)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsNavItem(
						content = { Text(stringResource(R.string.option_custom_headers)) },
						supportingContent = { Text(stringResource(R.string.subtitle_custom_headers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
						onClick = dropUnlessResumed {
							if (Screen.Settings.CustomHeaders !in backStack) {
								backStack.add(Screen.Settings.CustomHeaders)
							}
						}
					)
				}
			}
		}
	}
}
