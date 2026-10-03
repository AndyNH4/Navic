/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.R
import paige.navic.di.LocalBottomBarScrollManager
import paige.navic.di.LocalNavStack
import paige.navic.di.LocalSizeClass
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.model.DomainArtist
import paige.navic.domain.model.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.filled.EmojiEvents
import paige.navic.icons.filled.Headphones
import paige.navic.icons.filled.LocalFireDepartment
import paige.navic.icons.filled.Star
import paige.navic.icons.outlined.MoreVert
import paige.navic.icons.outlined.Note
import paige.navic.ui.component.common.TooltipBox
import paige.navic.ui.component.layout.ArtCarousel
import paige.navic.ui.component.layout.ArtCarouselItem
import paige.navic.ui.component.layout.NestedTopBar
import paige.navic.ui.component.layout.RootBottomBar
import paige.navic.ui.component.layout.RootTopBar
import paige.navic.ui.component.sheet.ArtistSheet
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screen.stats.component.ListeningHeatmap
import paige.navic.ui.screen.stats.component.StatSummaryCard
import paige.navic.ui.screen.stats.component.TopArtistItem
import paige.navic.ui.screen.stats.viewmodel.StatisticsViewModel
import paige.navic.ui.screen.stats.viewmodel.TopRange
import paige.navic.ui.theme.NavicTheme
import paige.navic.util.toSummaryString
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
	nested: Boolean = false
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<StatisticsViewModel>()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val localStats by viewModel.localStats.collectAsStateWithLifecycle()
	val topRange by viewModel.topRange.collectAsStateWithLifecycle()
	val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
	val topAlbums by viewModel.topAlbums.collectAsStateWithLifecycle()
	val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
	val starredSongs by viewModel.starredSongs.collectAsStateWithLifecycle()
	val backStack = LocalNavStack.current
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	var selectedArtist by remember { mutableStateOf<DomainArtist?>(null) }
	var menuExpanded by remember { mutableStateOf(false) }
	var confirmClear by remember { mutableStateOf(false) }

	val menuAction: @Composable () -> Unit = {
		Box {
			TooltipBox(stringResource(R.string.action_clear_statistics)) {
				IconButton(onClick = { menuExpanded = true }) {
					Icon(
						imageVector = Icons.Outlined.MoreVert,
						contentDescription = stringResource(R.string.action_clear_statistics)
					)
				}
			}
			DropdownMenu(
				expanded = menuExpanded,
				onDismissRequest = { menuExpanded = false }
			) {
				DropdownMenuItem(
					text = { Text(stringResource(R.string.action_clear_statistics)) },
					onClick = {
						menuExpanded = false
						confirmClear = true
					}
				)
			}
		}
	}

	NavicTheme {
		Scaffold(
			topBar = {
				if (!nested) {
					RootTopBar(
						title = { Text(stringResource(R.string.title_statistics)) },
						scrollBehavior = scrollBehavior,
						actions = { menuAction() }
					)
				} else {
					NestedTopBar(
						title = { Text(stringResource(R.string.title_statistics)) },
						actions = { menuAction() }
					)
				}
			},
			bottomBar = {
				val sizeClass = LocalSizeClass.current
				val scrollManager = LocalBottomBarScrollManager.current
				val preferVisible =
					preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
				if (!nested
					|| (sizeClass.widthSizeClass < WindowWidthSizeClass.Medium && preferVisible)) {
					RootBottomBar(scrolled = scrollManager.isTriggered)
				}
			}
		) { contentPadding ->
			if (state.isLoading) {
				Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
			} else {
				LazyVerticalGrid(
					modifier = Modifier
						.fillMaxSize()
						.let {
							if (!nested) it.nestedScroll(scrollBehavior.nestedScrollConnection) else it
						},
					contentPadding = contentPadding,
					columns = GridCells.Fixed(2),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					item(span = { GridItemSpan(maxLineSpan) }) {
						val color = MaterialTheme.colorScheme.primary
						val contentColor = MaterialTheme.colorScheme.onPrimary
						StatSummaryCard(
							color = color,
							contentColor = contentColor,
							horizontal = true,
							label = stringResource(R.string.info_total_listening_time),
							value = state.totalDuration.toSummaryString(),
							icon = {
								Surface(
									color = contentColor,
									contentColor = color,
									shape = MaterialShapes.Cookie9Sided.toShape()
								) {
									Icon(
										imageVector = Icons.Filled.Headphones,
										contentDescription = null,
										modifier = Modifier.padding(16.dp).size(32.dp)
									)
								}
							},
							modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
						)
					}

					item {
						val color = MaterialTheme.colorScheme.primaryContainer
						val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
						StatSummaryCard(
							color = color,
							contentColor = contentColor,
							label = stringResource(R.string.info_total_plays),
							value = state.totalPlays.toString(),
							icon = {
								Surface(
									color = contentColor,
									contentColor = color,
									shape = MaterialShapes.Clover4Leaf.toShape()
								) {
									Icon(
										imageVector = Icons.Outlined.Note,
										contentDescription = null,
										modifier = Modifier.padding(12.dp)
									)
								}
							},
							modifier = Modifier.padding(start = 16.dp)
						)
					}

					item {
						val color = MaterialTheme.colorScheme.onTertiaryContainer
						val contentColor = MaterialTheme.colorScheme.tertiaryContainer
						StatSummaryCard(
							color = color,
							contentColor = contentColor,
							label = stringResource(R.string.info_starred_songs)
								.toLowerCase(LocalLocale.current),
							value = starredSongs.count().toString(),
							icon = {
								Surface(
									color = contentColor,
									contentColor = color,
									shape = MaterialShapes.VerySunny.toShape()
								) {
									Icon(
										imageVector = Icons.Filled.Star,
										contentDescription = null,
										modifier = Modifier.padding(12.dp)
									)
								}
							},
							modifier = Modifier.padding(end = 16.dp)
						)
					}

					if (localStats.playsLastYear > 0) {
						item {
							val color = MaterialTheme.colorScheme.secondaryContainer
							val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
							StatSummaryCard(
								color = color,
								contentColor = contentColor,
								label = stringResource(R.string.info_current_streak),
								value = pluralStringResource(
									R.plurals.count_days,
									localStats.currentStreak,
									localStats.currentStreak
								),
								icon = {
									Surface(
										color = contentColor,
										contentColor = color,
										shape = MaterialShapes.SoftBurst.toShape()
									) {
										Icon(
											imageVector = Icons.Filled.LocalFireDepartment,
											contentDescription = null,
											modifier = Modifier.padding(12.dp)
										)
									}
								},
								modifier = Modifier.padding(start = 16.dp, top = 8.dp)
							)
						}

						item {
							val color = MaterialTheme.colorScheme.tertiaryContainer
							val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
							StatSummaryCard(
								color = color,
								contentColor = contentColor,
								label = stringResource(R.string.info_longest_streak),
								value = pluralStringResource(
									R.plurals.count_days,
									localStats.longestStreak,
									localStats.longestStreak
								),
								icon = {
									Surface(
										color = contentColor,
										contentColor = color,
										shape = MaterialShapes.Cookie12Sided.toShape()
									) {
										Icon(
											imageVector = Icons.Filled.EmojiEvents,
											contentDescription = null,
											modifier = Modifier.padding(12.dp)
										)
									}
								},
								modifier = Modifier.padding(end = 16.dp, top = 8.dp)
							)
						}

						item(span = { GridItemSpan(maxLineSpan) }) {
							Column(modifier = Modifier.padding(horizontal = 16.dp)) {
								Text(
									text = stringResource(R.string.title_listening_activity),
									style = MaterialTheme.typography.titleMediumEmphasized,
									fontWeight = FontWeight(600),
									modifier = Modifier
										.heightIn(min = 32.dp)
										.padding(top = 12.dp)
										.semantics { heading() }
								)
								Text(
									text = stringResource(
										R.string.info_plays_last_year,
										localStats.playsLastYear
									),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
								ListeningHeatmap(
									dayCounts = localStats.dayCounts,
									modifier = Modifier.padding(top = 12.dp)
								)
							}
						}

						item(span = { GridItemSpan(maxLineSpan) }) {
							SingleChoiceSegmentedButtonRow(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 16.dp)
							) {
								TopRange.entries.forEachIndexed { index, range ->
									SegmentedButton(
										shape = SegmentedButtonDefaults.itemShape(
											index = index,
											count = TopRange.entries.size
										),
										onClick = { viewModel.setTopRange(range) },
										selected = topRange == range,
										label = { Text(stringResource(range.displayName)) }
									)
								}
							}
						}

						if (topArtists.isNotEmpty()) {
							item(span = { GridItemSpan(maxLineSpan) }) {
								Text(
									text = stringResource(R.string.title_top_artists),
									style = MaterialTheme.typography.titleMediumEmphasized,
									fontWeight = FontWeight(600),
									modifier = Modifier
										.heightIn(min = 32.dp)
										.padding(top = 12.dp, start = 16.dp)
										.semantics { heading() }
								)
							}
						}
						items(
							items = topArtists,
							key = { it.artist.id },
							span = { GridItemSpan(maxLineSpan) }
						) { stats ->
							val maxPlays = topArtists.first().playCount.toFloat()
							TopArtistItem(
								stats = stats,
								ratio = stats.playCount / maxPlays,
								onClick = { backStack.add(Screen.ArtistDetail(stats.artist.id)) },
								onLongClick = { selectedArtist = stats.artist }
							)
						}

						item(span = { GridItemSpan(maxLineSpan) }) {
							ArtCarousel(
								title = stringResource(R.string.title_top_albums),
								items = topAlbums.toImmutableList()
							) { album ->
								ArtCarouselItem(
									coverArtId = album.coverArtId,
									title = album.albumTitle ?: "[unknown album]",
									subtitle = album.artistName,
									playCount = album.playCount,
									duration = album.listeningTimeMs.milliseconds,
									contentDescription = null,
									onClick = {
										backStack.add(Screen.CollectionDetail(album.albumId, "stats"))
									}
								)
							}
						}

						item(span = { GridItemSpan(maxLineSpan) }) {
							ArtCarousel(
								title = stringResource(R.string.title_top_songs),
								items = topSongs.toImmutableList()
							) { song ->
								ArtCarouselItem(
									coverArtId = song.coverArtId,
									title = song.songTitle.ifEmpty { "[unknown song]" },
									subtitle = song.artistName,
									playCount = song.playCount,
									duration = song.listeningTimeMs.milliseconds,
									contentDescription = null,
									onClick = {
										backStack.add(
											Screen.SongDetailScreen(
												song.songId,
												song.coverArtId
											)
										)
									}
								)
							}
						}
					} else {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Text(
								text = stringResource(R.string.info_stats_empty),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.padding(16.dp)
							)
						}
					}
				}
			}
		}

		selectedArtist?.let {
			ArtistSheet(
				onDismissRequest = { selectedArtist = null },
				artist = it
			)
		}

		if (confirmClear) {
			AlertDialog(
				title = { Text(stringResource(R.string.dialog_clear_statistics_title)) },
				text = { Text(stringResource(R.string.dialog_clear_statistics_message)) },
				onDismissRequest = { confirmClear = false },
				confirmButton = {
					Button(onClick = {
						viewModel.clearLocalStats()
						confirmClear = false
					}) {
						Text(stringResource(R.string.action_delete))
					}
				},
				dismissButton = {
					TextButton(onClick = { confirmClear = false }) {
						Text(stringResource(R.string.action_cancel))
					}
				}
			)
		}
	}
}
