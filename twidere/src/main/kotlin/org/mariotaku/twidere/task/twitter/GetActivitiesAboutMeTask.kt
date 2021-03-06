/*
 *             Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.task.twitter

import android.content.Context
import android.net.Uri
import org.mariotaku.microblog.library.MicroBlog
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.microblog.library.twitter.model.*
import org.mariotaku.twidere.annotation.AccountType
import org.mariotaku.twidere.annotation.ReadPositionTag
import org.mariotaku.twidere.extension.model.isOfficial
import org.mariotaku.twidere.model.AccountDetails
import org.mariotaku.twidere.model.UserKey
import org.mariotaku.twidere.provider.TwidereDataStore.Activities
import org.mariotaku.twidere.util.ErrorInfoStore
import org.mariotaku.twidere.util.Utils

/**
 * Created by mariotaku on 16/2/11.
 */
class GetActivitiesAboutMeTask(context: Context) : GetActivitiesTask(context) {

    override val errorInfoKey: String
        get() = ErrorInfoStore.KEY_INTERACTIONS

    override fun saveReadPosition(accountKey: UserKey, details: AccountDetails, twitter: MicroBlog) {
        if (AccountType.TWITTER == details.type && details.isOfficial(context)) {
            try {
                val response = twitter.getActivitiesAboutMeUnread(true)
                val tag = Utils.getReadPositionTagWithAccount(ReadPositionTag.ACTIVITIES_ABOUT_ME,
                        accountKey)
                readStateManager.setPosition(tag, response.cursor, false)
            } catch (e: MicroBlogException) {
                // Ignore
            }
        }
    }

    @Throws(MicroBlogException::class)
    override fun getActivities(twitter: MicroBlog, details: AccountDetails, paging: Paging): ResponseList<Activity> {
        if (details.isOfficial(context)) {
            return twitter.getActivitiesAboutMe(paging)
        }
        val activities = ResponseList<Activity>()
        val statuses: ResponseList<Status>
        when (details.type) {
            AccountType.FANFOU -> {
                statuses = twitter.getMentions(paging)
            }
            else -> {
                statuses = twitter.getMentionsTimeline(paging)
            }
        }
        statuses.mapTo(activities) { InternalActivityCreator.status(details.key.id, it) }
        return activities
    }

    override val contentUri: Uri
        get() = Activities.AboutMe.CONTENT_URI
}
