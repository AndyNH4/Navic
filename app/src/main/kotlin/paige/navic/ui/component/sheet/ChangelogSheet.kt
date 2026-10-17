/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousCapsule
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.manager.UpdateDownloadState
import paige.navic.domain.manager.UpdateManager
import paige.navic.ui.component.common.Markdown
import paige.navic.ui.component.dialog.LinkConfirmationDialog
import paige.navic.ui.theme.defaultFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet() {
	val context = LocalContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val updateManager = koinInject<UpdateManager>()
	val release by updateManager.release.collectAsStateWithLifecycle()
	val downloadState by updateManager.downloadState.collectAsStateWithLifecycle()
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	LaunchedEffect(Unit) {
		if (preferenceManager.checkForUpdates) {
			updateManager.checkForUpdates(
				context.packageManager
					.getPackageInfo(context.packageName, 0)
					.versionName.toString()
			)
		}
	}

	release?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { updateManager.clearRelease() },
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(R.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						stringResource(R.string.info_update, release.tag),
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body,
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				Spacer(Modifier.weight(1f))

				when (val state = downloadState) {
					UpdateDownloadState.Idle -> {
						Button(
							onClick = { updateManager.downloadAndInstall(release) },
							modifier = Modifier.fillMaxWidth(),
							shape = ContinuousCapsule
						) {
							Text(
								text = stringResource(R.string.action_update_app),
								fontFamily = defaultFont(100)
							)
						}

						OutlinedButton(
							onClick = {
								updateManager.clearRelease()
								preferenceManager.checkForUpdates = false
							},
							modifier = Modifier.fillMaxWidth(),
							shape = ContinuousCapsule
						) {
							Text(
								text = stringResource(R.string.action_dont_show_again),
								fontFamily = defaultFont(100)
							)
						}
					}

					is UpdateDownloadState.Downloading -> {
						if (state.progress >= 0f) {
							LinearProgressIndicator(
								progress = { state.progress.coerceIn(0f, 1f) },
								modifier = Modifier.fillMaxWidth()
							)
						} else {
							LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
						}
						Text(
							text = stringResource(R.string.info_downloading_update) +
								if (state.progress >= 0f) {
									" (${(state.progress * 100).toInt()}%)"
								} else {
									""
								},
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(vertical = 8.dp)
						)
						OutlinedButton(
							onClick = { updateManager.cancelDownload() },
							modifier = Modifier.fillMaxWidth(),
							shape = ContinuousCapsule
						) {
							Text(
								text = stringResource(R.string.action_cancel),
								fontFamily = defaultFont(100)
							)
						}
					}

					UpdateDownloadState.Failed -> {
						Text(
							text = stringResource(R.string.info_download_failed),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.error,
							modifier = Modifier.padding(vertical = 8.dp)
						)
						Button(
							onClick = { updateManager.downloadAndInstall(release) },
							modifier = Modifier.fillMaxWidth(),
							shape = ContinuousCapsule
						) {
							Text(
								text = stringResource(R.string.action_retry),
								fontFamily = defaultFont(100)
							)
						}
						OutlinedButton(
							onClick = { linkToOpen = release.url },
							modifier = Modifier.fillMaxWidth(),
							shape = ContinuousCapsule
						) {
							Text(
								text = stringResource(R.string.action_open_release_page),
								fontFamily = defaultFont(100)
							)
						}
					}
				}
			}
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
