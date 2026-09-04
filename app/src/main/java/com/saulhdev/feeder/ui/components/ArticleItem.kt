package com.saulhdev.feeder.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowSquareOut
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.Bookmarks
import com.saulhdev.feeder.ui.icons.phosphor.Browser
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraight
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraightFill
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.ui.navigation.Routes
import com.saulhdev.feeder.utils.RelativeTimeHelper
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.extensions.safeStartActivity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleItem(
    article: FeedItem,
    onBookmark: suspend (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val content = article.toStoryCardContent()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showContextMenu by remember { mutableStateOf(false) }
    var bookmarked by remember(article.bookmarked) {
        mutableStateOf(article.bookmarked)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick() },
                    onLongClick = { showContextMenu = true }
                ),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
        ) {
            val prefs: com.saulhdev.feeder.data.content.FeedPreferences = org.koin.compose.koinInject()
            val displayMode by prefs.articleDisplayMode.get().collectAsState(initial = "image_auto")

            if (displayMode != "text_only" && content.backgroundUrl.isNotEmpty()
                && !content.backgroundUrl.contains(".rss")
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(content.backgroundUrl)
                        .crossfade(true)
                        .crossfade(300)
                        .build(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                    contentDescription = ""
                )
            }

            Text(
                text = content.title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 5,
            )

            if (content.text.isNotEmpty()) {
                Text(
                    text = Html.fromHtml(content.text, 0).toString(),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(2f)
                ) {
                    Text(
                        text = content.source.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = RelativeTimeHelper.getDateFormattedRelative(
                            LocalContext.current,
                            (article.timeMillis / 1000) - 1000
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    var bookmarked by remember(article.bookmarked) {
                        mutableStateOf(article.bookmarked)
                    }

                    FavoriteButton(bookmarked = bookmarked) {
                        scope.launch {
                            onBookmark(!bookmarked)
                            bookmarked = !bookmarked
                        }
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    ShareButton {
                        val intent = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, content.link)
                                putExtra(Intent.EXTRA_TITLE, content.title)
                                type = "text/plain"
                            },
                            null,
                        )
                        context.startActivity(intent)
                    }
                }
            }
        }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_read_offline)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.BookBookmark,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    context.safeStartActivity(
                        MainActivity.navigateIntent(
                            context,
                            "${Routes.ARTICLE_VIEW}/${article.id}"
                        )
                    )
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_open_in_browser)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.Browser,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    val prefPkg = article.feed.preferredPackage.takeIf { it.isNotBlank() }
                    context.launchView(content.link, prefPkg)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_open_with)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.ArrowSquareOut,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    val chooser = Intent.createChooser(
                        Intent(Intent.ACTION_VIEW, Uri.parse(content.link)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        context.getString(R.string.menu_open_with)
                    )
                    context.safeStartActivity(chooser)
                }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        if (bookmarked) stringResource(id = R.string.bookmark_remove)
                        else stringResource(id = R.string.bookmark)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (bookmarked) Phosphor.HeartStraightFill else Phosphor.HeartStraight,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    scope.launch {
                        onBookmark(!bookmarked)
                        bookmarked = !bookmarked
                    }
                }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_copy_link)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.Bookmarks,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Article Link", content.link)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.link_copied_toast, Toast.LENGTH_SHORT).show()
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_share_link)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.ShareNetwork,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    val intent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, content.link)
                            putExtra(Intent.EXTRA_TITLE, content.title)
                            type = "text/plain"
                        },
                        null
                    )
                    context.startActivity(intent)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.menu_feed_settings)) },
                leadingIcon = {
                    Icon(
                        imageVector = Phosphor.GearSix,
                        contentDescription = null
                    )
                },
                onClick = {
                    showContextMenu = false
                    context.safeStartActivity(
                        MainActivity.navigateIntent(
                            context,
                            "${Routes.MAIN}/2"
                        )
                    )
                }
            )
        }
    }
}
