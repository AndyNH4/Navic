/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.artist.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import paige.navic.R
import paige.navic.domain.model.DomainArtist

@Composable
fun ArtistMergeDialog(
	candidates: List<DomainArtist>,
	onSearch: (String) -> Unit,
	onDismissRequest: () -> Unit,
	onMerge: (DomainArtist) -> Unit
) {
	var query by rememberSaveable { mutableStateOf("") }
	var selectedArtistId by rememberSaveable { mutableStateOf<String?>(null) }
	val selectedArtist = candidates.firstOrNull { it.id == selectedArtistId }

	LaunchedEffect(Unit) {
		onSearch("")
	}

	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(R.string.action_merge_artists)) },
		text = {
			Column(
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = query,
					onValueChange = {
						query = it
						selectedArtistId = null
						onSearch(it)
					},
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.label_artist_merge_search)) },
					singleLine = true
				)
				if (candidates.isEmpty()) {
					Text(stringResource(R.string.info_no_artist_merge_candidates))
				} else {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(max = 300.dp)
							.verticalScroll(rememberScrollState())
					) {
						candidates.forEach { artist ->
							ListItem(
								content = { Text(artist.name) },
								trailingContent = {
									RadioButton(
										selected = artist.id == selectedArtistId,
										onClick = null
									)
								},
								onClick = { selectedArtistId = artist.id }
							)
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(
				enabled = selectedArtist != null,
				onClick = { selectedArtist?.let(onMerge) }
			) {
				Text(stringResource(R.string.action_merge))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismissRequest) {
				Text(stringResource(R.string.action_cancel))
			}
		}
	)
}

@Composable
fun ArtistDisplayNameDialog(
	initialName: String,
	candidateNames: List<String>,
	onDismissRequest: () -> Unit,
	onConfirm: (String) -> Unit
) {
	var name by remember(initialName) { mutableStateOf(initialName) }

	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(R.string.title_artist_display_name)) },
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 350.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.label_artist_display_name)) },
					singleLine = true
				)
				candidateNames.distinct().forEach { candidate ->
					ListItem(
						content = { Text(candidate) },
						onClick = { name = candidate }
					)
				}
			}
		},
		confirmButton = {
			TextButton(
				enabled = name.isNotBlank(),
				onClick = { onConfirm(name.trim()) }
			) {
				Text(stringResource(R.string.action_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismissRequest) {
				Text(stringResource(R.string.action_cancel))
			}
		}
	)
}
