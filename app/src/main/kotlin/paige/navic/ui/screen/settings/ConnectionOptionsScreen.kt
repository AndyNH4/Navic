/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.di.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.SessionManager
import paige.navic.domain.manager.SnackBarManager
import paige.navic.ui.component.common.SegmentedListItemDefaults
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.settings.component.SettingsGroup
import paige.navic.ui.screen.settings.component.SettingsGroupDefaults
import paige.navic.ui.screen.settings.component.SettingsNavItem
import paige.navic.ui.screen.settings.component.SettingsToggleItem
import paige.navic.util.PROXY_URL_REGEX

@Composable
fun SettingsConnectionOptionsScreen() {
	val backStack = LocalNavStack.current

	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val activeInstanceUrl by sessionManager.activeInstanceUrl.collectAsStateWithLifecycle()
	val isUsingBackupUrl by sessionManager.isUsingBackupUrl.collectAsStateWithLifecycle()
	val serverCapabilities by sessionManager.serverCapabilities.collectAsStateWithLifecycle()

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

	var audioMuseUrlField by mutableStateOf(preferenceManager.audioMuseUrl)
	val audioMuseUrlHasErrors by derivedStateOf {
		audioMuseUrlField.isNotBlank() &&
			!audioMuseUrlField.startsWith("https://") &&
			!audioMuseUrlField.startsWith("http://")
	}
	val updateAudioMuseUrl: (String) -> Unit = { url ->
		audioMuseUrlField = url
		if (url.isBlank() || !audioMuseUrlHasErrors) {
			preferenceManager.audioMuseUrl = url.trim().removeSuffix("/")
		}
	}

	var sslNoopChecked by mutableStateOf(preferenceManager.dangerousSslNoopEnabled)
	val updateSslNoop: (Boolean) -> Unit = { bool ->
		sslNoopChecked = bool
		preferenceManager.dangerousSslNoopEnabled = bool
		sessionManager.refreshClient()
	}

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(R.string.option_connection_options)) }) }
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
								shapes = SegmentedListItemDefaults.segmentedShapes(
									index = 0,
									count = 1
								),
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
				}
				if (isLoggedIn) {
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
					OutlinedTextField(
						modifier = Modifier.fillMaxWidth(),
						singleLine = true,
						value = audioMuseUrlField,
						onValueChange = updateAudioMuseUrl,
						isError = audioMuseUrlHasErrors,
						label = { Text(stringResource(R.string.option_audiomuse_url)) },
						placeholder = { Text(stringResource(R.string.info_audiomuse_url_placeholder)) },
						supportingText = {
							if (audioMuseUrlHasErrors) {
								Text(stringResource(R.string.info_incorrect_backup_url_format))
							} else {
								Text(stringResource(R.string.info_audiomuse_url_description))
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
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 0,
							count = 2
						)
					)
					SettingsNavItem(
						content = { Text(stringResource(R.string.option_custom_headers)) },
						supportingContent = { Text(stringResource(R.string.subtitle_custom_headers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(
							index = 1,
							count = 2
						),
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
