/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.artist.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import paige.navic.data.database.entity.ArtistAliasEntity
import paige.navic.data.database.entity.ArtistGroupMemberEntity
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
fun ArtistAliasesDialog(
	initialDisplayName: String,
	primaryArtistId: String?,
	members: List<ArtistGroupMemberEntity>,
	aliases: List<ArtistAliasEntity>,
	onSetDisplayName: (String?) -> Unit,
	onAddAlias: (String, String) -> Unit,
	onUpdateAlias: (Long, String, String) -> Unit,
	onDeleteAlias: (Long) -> Unit,
	onSetPrimaryArtist: (String) -> Unit,
	onDismissRequest: () -> Unit
) {
	var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
	var aliasName by rememberSaveable { mutableStateOf("") }
	var languageTag by rememberSaveable { mutableStateOf("") }
	var editingAliasId by rememberSaveable { mutableStateOf<Long?>(null) }

	fun resetAliasEditor() {
		aliasName = ""
		languageTag = ""
		editingAliasId = null
	}

	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(stringResource(R.string.title_manage_artist_names)) },
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 560.dp)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = displayName,
					onValueChange = { displayName = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.label_artist_display_name)) },
					singleLine = true
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(
						enabled = displayName.isNotBlank(),
						onClick = { onSetDisplayName(displayName.trim()) }
					) {
						Text(stringResource(R.string.action_save))
					}
					TextButton(
						onClick = {
							displayName = ""
							onSetDisplayName(null)
						}
					) {
						Text(stringResource(R.string.action_use_automatic_name))
					}
				}
				HorizontalDivider()
				Text(
					text = stringResource(R.string.title_custom_aliases),
					style = MaterialTheme.typography.titleSmall
				)
				OutlinedTextField(
					value = aliasName,
					onValueChange = { aliasName = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.label_artist_alias_name)) },
					singleLine = true
				)
				OutlinedTextField(
					value = languageTag,
					onValueChange = { languageTag = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(R.string.label_language_tag)) },
					supportingText = {
						Text(stringResource(R.string.info_language_tag_hint))
					},
					singleLine = true
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(
						enabled = aliasName.isNotBlank(),
						onClick = {
							val aliasId = editingAliasId
							if (aliasId == null) {
								onAddAlias(aliasName.trim(), languageTag.trim())
							} else {
								onUpdateAlias(aliasId, aliasName.trim(), languageTag.trim())
							}
							resetAliasEditor()
						}
					) {
						Text(
							stringResource(
								if (editingAliasId == null) R.string.action_add_alias else R.string.action_save
							)
						)
					}
					if (editingAliasId != null) {
						TextButton(onClick = { resetAliasEditor() }) {
							Text(stringResource(R.string.action_cancel))
						}
					}
				}
				if (aliases.isEmpty()) {
					Text(stringResource(R.string.info_no_custom_aliases))
				} else {
					aliases.forEach { alias ->
						ListItem(
							content = { Text(alias.name) },
							supportingContent = {
								Text(
									alias.languageTag.ifBlank {
										stringResource(R.string.info_language_unspecified)
									}
								)
							},
							trailingContent = {
								Row {
									TextButton(
										onClick = {
											editingAliasId = alias.aliasId
											aliasName = alias.name
											languageTag = alias.languageTag
										}
									) {
										Text(stringResource(R.string.action_edit))
									}
									TextButton(
										onClick = {
											onDeleteAlias(alias.aliasId)
											if (editingAliasId == alias.aliasId) resetAliasEditor()
										}
									) {
										Text(stringResource(R.string.action_delete))
									}
								}
							},
							onClick = { displayName = alias.name }
						)
					}
				}
				if (members.isNotEmpty()) {
					HorizontalDivider()
					Text(
						text = stringResource(R.string.title_server_artist_names),
						style = MaterialTheme.typography.titleSmall
					)
					members.forEach { member ->
						ListItem(
							content = { Text(member.name) },
							supportingContent = if (member.artistId == primaryArtistId) {
								{ Text(stringResource(R.string.info_primary_artist)) }
							} else {
								null
							},
							trailingContent = if (member.artistId != primaryArtistId) {
								{
									TextButton(onClick = { onSetPrimaryArtist(member.artistId) }) {
										Text(stringResource(R.string.action_set_primary_artist))
									}
								}
							} else {
								null
							},
							onClick = { displayName = member.name }
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismissRequest) {
				Text(stringResource(R.string.action_close))
			}
		}
	)
}
