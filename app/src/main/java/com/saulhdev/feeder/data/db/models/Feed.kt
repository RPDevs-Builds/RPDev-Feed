/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import java.net.URL
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    tableName = "Feeds",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["id", "url", "title"], unique = true)
    ]
)
data class Feed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNSET,
    val title: String = "",
    val description: String = "",
    val url: URL = sloppyLinkToStrictURL(""),
    val feedImage: URL = sloppyLinkToStrictURL(""),
    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val lastSync: Instant = Clock.System.now(),
    val alternateId: Boolean = false,
    val fullTextByDefault: Boolean = false,
    val tag: String = "",
    val currentlySyncing: Boolean = false,
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "rss")
    val sourceType: String = "rss",
    @ColumnInfo(defaultValue = "0")
    val requireLink: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val requireImage: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val excludeReplies: Boolean = true,
    @ColumnInfo(defaultValue = "")
    val preferredPackage: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Feed

        if (id != other.id) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (url.toExternalForm() != other.url.toExternalForm()) return false
        if (feedImage.toExternalForm() != other.feedImage.toExternalForm()) return false
        if (lastSync != other.lastSync) return false
        if (alternateId != other.alternateId) return false
        if (fullTextByDefault != other.fullTextByDefault) return false
        if (tag != other.tag) return false
        if (currentlySyncing != other.currentlySyncing) return false
        if (isEnabled != other.isEnabled) return false
        if (sourceType != other.sourceType) return false
        if (requireLink != other.requireLink) return false
        if (requireImage != other.requireImage) return false
        if (excludeReplies != other.excludeReplies) return false
        if (preferredPackage != other.preferredPackage) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + url.toExternalForm().hashCode()
        result = 31 * result + feedImage.toExternalForm().hashCode()
        result = 31 * result + lastSync.hashCode()
        result = 31 * result + alternateId.hashCode()
        result = 31 * result + fullTextByDefault.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + currentlySyncing.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + sourceType.hashCode()
        result = 31 * result + requireLink.hashCode()
        result = 31 * result + requireImage.hashCode()
        result = 31 * result + excludeReplies.hashCode()
        result = 31 * result + preferredPackage.hashCode()
        return result
    }
}