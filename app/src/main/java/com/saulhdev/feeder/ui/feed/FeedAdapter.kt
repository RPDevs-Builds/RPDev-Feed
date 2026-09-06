/*
 * This file is part of Neo Feed
 * Copyright (c) 2024   Neo Feed Team
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

package com.saulhdev.feeder.ui.feed

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.recyclerview.widget.RecyclerView
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.components.hub.HubDashboardSection
import com.saulhdev.feeder.ui.feed.binders.StoryCardBinder

class FeedAdapter(
    private val onDismissStoryCallback: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        const val VIEW_TYPE_HUB_HEADER = 0
        const val VIEW_TYPE_STORY = 1
    }

    private var list = mutableListOf<FeedItem>()
    private val dismissedStoryIds = mutableSetOf<String>()
    private lateinit var layoutInflater: LayoutInflater
    private var theme: SparseIntArray? = null

    fun replace(new: List<FeedItem>) {
        val filtered = new.filter { it.id !in dismissedStoryIds }
        if (filtered != list) {
            list = filtered.toMutableList()
            notifyDataSetChanged()
        }
    }

    fun dismissStory(id: String) {
        dismissedStoryIds.add(id)
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list.removeAt(index)
            notifyItemRemoved(index + 1)
        }
        onDismissStoryCallback?.invoke(id)
    }

    fun dismissStoryAt(adapterPosition: Int) {
        val listIndex = adapterPosition - 1
        if (listIndex in 0 until list.size) {
            val item = list[listIndex]
            dismissStory(item.id)
        }
    }

    fun clearDismissed() {
        dismissedStoryIds.clear()
    }

    fun setTheme(theme: SparseIntArray) {
        this.theme = theme
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return list.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HUB_HEADER else VIEW_TYPE_STORY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (!::layoutInflater.isInitialized) layoutInflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_HUB_HEADER) {
            val layoutResource = R.layout.feed_hub_header
            HubHeaderViewHolder(layoutInflater.inflate(layoutResource, parent, false))
        } else {
            val layoutResource = R.layout.feed_card_story_large
            FeedViewHolder(viewType, layoutInflater.inflate(layoutResource, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HubHeaderViewHolder) {
            holder.bind()
        } else if (holder is FeedViewHolder && position > 0 && position <= list.size) {
            val item = list[position - 1]
            StoryCardBinder.bind(theme, item, holder.itemView) { storyId ->
                dismissStory(storyId)
            }
        }
    }

    class HubHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val composeView: ComposeView = itemView.findViewById(R.id.compose_hub_view)

        fun bind() {
            composeView.setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            composeView.setContent {
                HubDashboardSection()
            }
        }
    }

    inner class FeedViewHolder(val type: Int, itemView: View) : RecyclerView.ViewHolder(itemView)
}