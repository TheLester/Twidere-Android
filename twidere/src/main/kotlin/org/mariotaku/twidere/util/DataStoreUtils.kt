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

package org.mariotaku.twidere.util

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.BaseColumns
import android.support.annotation.WorkerThread
import android.text.TextUtils
import org.mariotaku.kpreferences.get
import org.mariotaku.ktextension.useCursor
import org.mariotaku.library.objectcursor.ObjectCursor
import org.mariotaku.microblog.library.MicroBlog
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.microblog.library.twitter.model.Activity
import org.mariotaku.sqliteqb.library.*
import org.mariotaku.sqliteqb.library.Columns.Column
import org.mariotaku.sqliteqb.library.query.SQLSelectQuery
import org.mariotaku.twidere.TwidereConstants.*
import org.mariotaku.twidere.constant.IntentConstants
import org.mariotaku.twidere.constant.databaseItemLimitKey
import org.mariotaku.twidere.extension.model.*
import org.mariotaku.twidere.extension.rawQuery
import org.mariotaku.twidere.model.*
import org.mariotaku.twidere.model.tab.extra.HomeTabExtras
import org.mariotaku.twidere.model.tab.extra.InteractionsTabExtras
import org.mariotaku.twidere.model.tab.extra.TabExtras
import org.mariotaku.twidere.model.util.AccountUtils
import org.mariotaku.twidere.model.util.ParcelableStatusUtils
import org.mariotaku.twidere.provider.TwidereDataStore
import org.mariotaku.twidere.provider.TwidereDataStore.*
import org.mariotaku.twidere.provider.TwidereDataStore.Messages.Conversations
import org.mariotaku.twidere.util.content.ContentResolverUtils
import java.io.IOException
import java.util.*

/**
 * Created by mariotaku on 15/11/28.
 */
object DataStoreUtils {

    val STATUSES_URIS = arrayOf(Statuses.CONTENT_URI, CachedStatuses.CONTENT_URI)
    val CACHE_URIS = arrayOf(CachedUsers.CONTENT_URI, CachedStatuses.CONTENT_URI, CachedHashtags.CONTENT_URI, CachedTrends.Local.CONTENT_URI)
    val MESSAGES_URIS = arrayOf(Messages.CONTENT_URI, Conversations.CONTENT_URI)
    val ACTIVITIES_URIS = arrayOf(Activities.AboutMe.CONTENT_URI)

    private val CONTENT_PROVIDER_URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH)

    init {
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Statuses.CONTENT_PATH,
                TABLE_ID_STATUSES)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Activities.AboutMe.CONTENT_PATH,
                TABLE_ID_ACTIVITIES_ABOUT_ME)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Drafts.CONTENT_PATH,
                TABLE_ID_DRAFTS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedUsers.CONTENT_PATH,
                TABLE_ID_CACHED_USERS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Filters.Users.CONTENT_PATH,
                TABLE_ID_FILTERED_USERS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Filters.Keywords.CONTENT_PATH,
                TABLE_ID_FILTERED_KEYWORDS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Filters.Sources.CONTENT_PATH,
                TABLE_ID_FILTERED_SOURCES)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Filters.Links.CONTENT_PATH,
                TABLE_ID_FILTERED_LINKS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Filters.Subscriptions.CONTENT_PATH,
                TABLE_ID_FILTERS_SUBSCRIPTIONS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Messages.CONTENT_PATH,
                TABLE_ID_MESSAGES)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Conversations.CONTENT_PATH,
                TABLE_ID_MESSAGES_CONVERSATIONS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedTrends.Local.CONTENT_PATH,
                TABLE_ID_TRENDS_LOCAL)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Tabs.CONTENT_PATH,
                TABLE_ID_TABS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedStatuses.CONTENT_PATH,
                TABLE_ID_CACHED_STATUSES)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedHashtags.CONTENT_PATH,
                TABLE_ID_CACHED_HASHTAGS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedRelationships.CONTENT_PATH,
                TABLE_ID_CACHED_RELATIONSHIPS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, SavedSearches.CONTENT_PATH,
                TABLE_ID_SAVED_SEARCHES)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, SearchHistory.CONTENT_PATH,
                TABLE_ID_SEARCH_HISTORY)

        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Permissions.CONTENT_PATH,
                VIRTUAL_TABLE_ID_PERMISSIONS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedUsers.CONTENT_PATH_WITH_RELATIONSHIP + "/*",
                VIRTUAL_TABLE_ID_CACHED_USERS_WITH_RELATIONSHIP)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, CachedUsers.CONTENT_PATH_WITH_SCORE + "/*",
                VIRTUAL_TABLE_ID_CACHED_USERS_WITH_SCORE)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Drafts.CONTENT_PATH_UNSENT,
                VIRTUAL_TABLE_ID_DRAFTS_UNSENT)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Drafts.CONTENT_PATH_NOTIFICATIONS,
                VIRTUAL_TABLE_ID_DRAFTS_NOTIFICATIONS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Drafts.CONTENT_PATH_NOTIFICATIONS + "/#",
                VIRTUAL_TABLE_ID_DRAFTS_NOTIFICATIONS)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Suggestions.AutoComplete.CONTENT_PATH,
                VIRTUAL_TABLE_ID_SUGGESTIONS_AUTO_COMPLETE)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, Suggestions.Search.CONTENT_PATH,
                VIRTUAL_TABLE_ID_SUGGESTIONS_SEARCH)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, TwidereDataStore.CONTENT_PATH_DATABASE_PREPARE,
                VIRTUAL_TABLE_ID_DATABASE_PREPARE)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, TwidereDataStore.CONTENT_PATH_NULL,
                VIRTUAL_TABLE_ID_NULL)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, TwidereDataStore.CONTENT_PATH_EMPTY,
                VIRTUAL_TABLE_ID_EMPTY)
        CONTENT_PROVIDER_URI_MATCHER.addURI(TwidereDataStore.AUTHORITY, TwidereDataStore.CONTENT_PATH_RAW_QUERY + "/*",
                VIRTUAL_TABLE_ID_RAW_QUERY)
    }

    fun getNewestStatusIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>): Array<String?> {
        return getStringFieldArray(context, uri, accountKeys, Statuses.ACCOUNT_KEY, Statuses.STATUS_ID,
                OrderBy(SQLFunctions.MAX(Statuses.STATUS_TIMESTAMP)), null, null)
    }

    fun getNewestMessageIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>, outgoing: Boolean): Array<String?> {
        val having: Expression = Expression.equals(Messages.IS_OUTGOING, if (outgoing) 1 else 0)
        return getStringFieldArray(context, uri, accountKeys, Messages.ACCOUNT_KEY, Messages.MESSAGE_ID,
                OrderBy(SQLFunctions.MAX(Messages.LOCAL_TIMESTAMP)), having, null)
    }

    fun getOldestMessageIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>, outgoing: Boolean): Array<String?> {
        if (accountKeys.all { it == null }) return arrayOfNulls(accountKeys.size)
        val having: Expression = Expression.equals(Messages.IS_OUTGOING, if (outgoing) 1 else 0)
        return getStringFieldArray(context, uri, accountKeys, Messages.ACCOUNT_KEY, Messages.MESSAGE_ID,
                OrderBy(SQLFunctions.MIN(Messages.LOCAL_TIMESTAMP)), having, null)
    }

    fun getOldestConversations(context: Context, uri: Uri, accountKeys: Array<UserKey?>): Array<ParcelableMessageConversation?> {
        if (accountKeys.all { it == null }) return arrayOfNulls(accountKeys.size)
        return getObjectFieldArray(context, uri, accountKeys, Conversations.ACCOUNT_KEY, Conversations.COLUMNS,
                OrderBy(SQLFunctions.MIN(Messages.LOCAL_TIMESTAMP)), null, null,
                { ObjectCursor.indicesFrom(it, ParcelableMessageConversation::class.java) },
                { arrayOfNulls<ParcelableMessageConversation>(it) })
    }

    fun getNewestConversations(context: Context, uri: Uri, accountKeys: Array<UserKey?>,
            extraWhere: Expression? = null, extraWhereArgs: Array<String>? = null): Array<ParcelableMessageConversation?> {
        if (accountKeys.all { it == null }) return arrayOfNulls(accountKeys.size)
        return getObjectFieldArray(context, uri, accountKeys, Conversations.ACCOUNT_KEY, Conversations.COLUMNS,
                OrderBy(SQLFunctions.MAX(Messages.LOCAL_TIMESTAMP)), extraWhere, extraWhereArgs,
                { ObjectCursor.indicesFrom(it, ParcelableMessageConversation::class.java) },
                { arrayOfNulls<ParcelableMessageConversation>(it) })
    }

    fun getNewestStatusSortIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>): LongArray {
        return getLongFieldArray(context, uri, accountKeys, Statuses.ACCOUNT_KEY, Statuses.SORT_ID,
                OrderBy(SQLFunctions.MAX(Statuses.STATUS_TIMESTAMP)), null, null)
    }


    fun getOldestStatusIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>): Array<String?> {
        return getStringFieldArray(context, uri, accountKeys, Statuses.ACCOUNT_KEY, Statuses.STATUS_ID,
                OrderBy(SQLFunctions.MIN(Statuses.STATUS_TIMESTAMP)), null, null)
    }


    fun getOldestStatusSortIds(context: Context, uri: Uri, accountKeys: Array<UserKey?>): LongArray {
        return getLongFieldArray(context, uri, accountKeys, Statuses.ACCOUNT_KEY, Statuses.SORT_ID,
                OrderBy(SQLFunctions.MIN(Statuses.STATUS_TIMESTAMP)), null, null)
    }

    fun getNewestActivityMaxPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>,
            extraWhere: Expression?, extraWhereArgs: Array<String>?): Array<String?> {
        return getStringFieldArray(context, uri, accountKeys, Activities.ACCOUNT_KEY,
                Activities.MAX_REQUEST_POSITION, OrderBy(SQLFunctions.MAX(Activities.TIMESTAMP)),
                extraWhere, extraWhereArgs)
    }

    fun getRefreshNewestActivityMaxPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>):
            Array<String?> {
        return getOfficialSeparatedIds(context, { accountKeys, isOfficial ->
            val (where, whereArgs) = getIdsWhere(isOfficial)
            DataStoreUtils.getNewestActivityMaxPositions(context, uri, accountKeys,
                    where, whereArgs)
        }, { arr1, arr2 ->
            Array(accountKeys.size) { arr1[it] ?: arr2[it] }
        }, accountKeys)
    }

    fun getOldestActivityMaxPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>,
            extraWhere: Expression?, extraWhereArgs: Array<String>?): Array<String?> {
        return getStringFieldArray(context, uri, accountKeys, Activities.ACCOUNT_KEY,
                Activities.MAX_REQUEST_POSITION, OrderBy(SQLFunctions.MIN(Activities.TIMESTAMP)),
                extraWhere, extraWhereArgs)
    }

    fun getRefreshOldestActivityMaxPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>):
            Array<String?> {
        return getOfficialSeparatedIds(context, { accountKeys, isOfficial ->
            val (where, whereArgs) = getIdsWhere(isOfficial)
            DataStoreUtils.getOldestActivityMaxPositions(context, uri, accountKeys,
                    where, whereArgs)
        }, { arr1, arr2 ->
            Array(accountKeys.size) { arr1[it] ?: arr2[it] }
        }, accountKeys)
    }

    fun getNewestActivityMaxSortPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>,
            extraWhere: Expression?, extraWhereArgs: Array<String>?): LongArray {
        return getLongFieldArray(context, uri, accountKeys, Activities.ACCOUNT_KEY,
                Activities.MAX_SORT_POSITION, OrderBy(SQLFunctions.MAX(Activities.TIMESTAMP)),
                extraWhere, extraWhereArgs)
    }

    fun getRefreshNewestActivityMaxSortPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>):
            LongArray {
        return getOfficialSeparatedIds(context, { accountKeys, isOfficial ->
            val (where, whereArgs) = getIdsWhere(isOfficial)
            DataStoreUtils.getNewestActivityMaxSortPositions(context, uri, accountKeys,
                    where, whereArgs)
        }, { arr1, arr2 ->
            LongArray(accountKeys.size) { arr1[it].takeIf { it > 0 } ?: arr2[it] }
        }, accountKeys)
    }

    fun getOldestActivityMaxSortPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>,
            extraWhere: Expression?, extraWhereArgs: Array<String>?): LongArray {
        return getLongFieldArray(context, uri, accountKeys, Activities.ACCOUNT_KEY,
                Activities.MAX_SORT_POSITION, OrderBy(SQLFunctions.MIN(Activities.TIMESTAMP)),
                extraWhere, extraWhereArgs)
    }

    fun getRefreshOldestActivityMaxSortPositions(context: Context, uri: Uri, accountKeys: Array<UserKey?>):
            LongArray {
        return getOfficialSeparatedIds(context, { accountKeys, isOfficial ->
            val (where, whereArgs) = getIdsWhere(isOfficial)
            DataStoreUtils.getOldestActivityMaxSortPositions(context, uri, accountKeys,
                    where, whereArgs)
        }, { arr1, arr2 ->
            LongArray(accountKeys.size) { arr1[it].takeIf { it > 0 } ?: arr2[it] }
        }, accountKeys)
    }

    fun getStatusCount(context: Context, uri: Uri, accountId: UserKey): Int {
        val where = Expression.equalsArgs(AccountSupportColumns.ACCOUNT_KEY).sql
        val whereArgs = arrayOf(accountId.toString())
        return queryCount(context.contentResolver, uri, where, whereArgs)
    }

    fun getActivitiesCount(context: Context, uri: Uri,
            accountKey: UserKey): Int {
        val where = Expression.equalsArgs(AccountSupportColumns.ACCOUNT_KEY).sql
        return queryCount(context.contentResolver, uri, where, arrayOf(accountKey.toString()))
    }


    @SuppressLint("Recycle")
    fun getFilteredUserIds(context: Context?): Array<UserKey> {
        if (context == null) return emptyArray()
        val resolver = context.contentResolver
        val projection = arrayOf(Filters.Users.USER_KEY)
        return resolver.query(Filters.Users.CONTENT_URI, projection, null, null, null)?.useCursor { cur ->
            return@useCursor Array(cur.count) { i ->
                cur.moveToPosition(i)
                UserKey.valueOf(cur.getString(0))
            }
        } ?: emptyArray()
    }

    fun getAccountDisplayName(context: Context, accountKey: UserKey, nameFirst: Boolean): String? {
        val name: String?
        if (nameFirst) {
            name = getAccountName(context, accountKey)
        } else {
            name = "@${getAccountScreenName(context, accountKey)}"
        }
        return name
    }

    fun getAccountName(context: Context, accountKey: UserKey): String? {
        val am = AccountManager.get(context)
        val account = AccountUtils.findByAccountKey(am, accountKey) ?: return null

        return account.getAccountUser(am).name
    }

    fun getAccountScreenName(context: Context, accountKey: UserKey): String? {
        val am = AccountManager.get(context)
        val account = AccountUtils.findByAccountKey(am, accountKey) ?: return null
        return account.getAccountUser(am).screen_name
    }

    fun getActivatedAccountKeys(context: Context): Array<UserKey> {
        val am = AccountManager.get(context)
        val keys = ArrayList<UserKey>()
        for (account in AccountUtils.getAccounts(am)) {
            if (account.isActivated(am)) {
                keys.add(account.getAccountKey(am))
            }
        }
        return keys.toTypedArray()
    }

    fun getStatusesCount(context: Context, preferences: SharedPreferences, uri: Uri,
            extraArgs: Bundle?, compare: Long, compareColumn: String, greaterThan: Boolean,
            accountKeys: Array<UserKey>?): Int {
        val keys = accountKeys ?: getActivatedAccountKeys(context)

        val expressions = ArrayList<Expression>()
        val expressionArgs = ArrayList<String>()

        expressions.add(Expression.inArgs(Column(Statuses.ACCOUNT_KEY), keys.size))
        for (accountKey in keys) {
            expressionArgs.add(accountKey.toString())
        }

        if (greaterThan) {
            expressions.add(Expression.greaterThanArgs(compareColumn))
        } else {
            expressions.add(Expression.lesserThanArgs(compareColumn))
        }
        expressionArgs.add(compare.toString())

        expressions.add(buildStatusFilterWhereClause(preferences, getTableNameByUri(uri)!!, null))

        if (extraArgs != null) {
            val extras = extraArgs.getParcelable<Parcelable>(EXTRA_EXTRAS)
            if (extras is HomeTabExtras) {
                processTabExtras(expressions, expressionArgs, extras)
            }
        }

        val selection = Expression.and(*expressions.toTypedArray())
        return queryCount(context.contentResolver, uri, selection.sql, expressionArgs.toTypedArray())
    }

    fun getActivitiesCount(context: Context, uri: Uri, compare: Long,
            compareColumn: String, greaterThan: Boolean, accountKeys: Array<UserKey>?): Int {
        val keys = accountKeys ?: getActivatedAccountKeys(context)
        val selection = Expression.and(
                Expression.inArgs(Column(Activities.ACCOUNT_KEY), keys.size),
                if (greaterThan) Expression.greaterThanArgs(compareColumn) else Expression.lesserThanArgs(compareColumn),
                buildActivityFilterWhereClause(getTableNameByUri(uri)!!, null)
        )
        val whereArgs = arrayListOf<String>()
        keys.mapTo(whereArgs) { it.toString() }
        whereArgs.add(compare.toString())
        return queryCount(context.contentResolver, uri, selection.sql, whereArgs.toTypedArray())
    }

    fun getActivitiesCount(context: Context, uri: Uri,
            extraWhere: Expression?, extraWhereArgs: Array<String>?,
            since: Long, sinceColumn: String, followingOnly: Boolean,
            accountKeys: Array<UserKey>?): Int {
        val keys = (accountKeys ?: getActivatedAccountKeys(context)).map { it.toString() }.toTypedArray()
        val expressions = ArrayList<Expression>()
        expressions.add(Expression.inArgs(Column(Activities.ACCOUNT_KEY), keys.size))
        expressions.add(Expression.greaterThanArgs(sinceColumn))
        expressions.add(buildActivityFilterWhereClause(getTableNameByUri(uri)!!, null))
        if (extraWhere != null) {
            expressions.add(extraWhere)
        }
        val selection = Expression.and(*expressions.toTypedArray())
        val selectionArgs: Array<String>
        if (extraWhereArgs != null) {
            selectionArgs = keys + since.toString() + extraWhereArgs
        } else {
            selectionArgs = keys + since.toString()
        }
        // If followingOnly option is on, we have to iterate over items
        val resolver = context.contentResolver
        if (followingOnly) {
            val projection = arrayOf(Activities.SOURCES)
            val cur = resolver.query(uri, projection, selection.sql, selectionArgs, null) ?: return -1
            cur.useCursor { cur ->
                var total = 0
                cur.moveToFirst()
                while (!cur.isAfterLast) {
                    val string = cur.getString(0)
                    if (TextUtils.isEmpty(string)) continue
                    var hasFollowing = false
                    try {
                        for (state in JsonSerializer.parseList(string, UserFollowState::class.java)) {
                            if (state.is_following) {
                                hasFollowing = true
                                break
                            }
                        }
                    } catch (e: IOException) {
                        continue
                    }

                    if (hasFollowing) {
                        total++
                    }
                    cur.moveToNext()
                }
                return total
            }
        }
        return queryCount(resolver, uri, selection.sql, selectionArgs)
    }

    fun getTableId(uri: Uri?): Int {
        if (uri == null) return -1
        return CONTENT_PROVIDER_URI_MATCHER.match(uri)
    }

    fun getTableNameById(id: Int): String? {
        when (id) {
            TABLE_ID_STATUSES -> return Statuses.TABLE_NAME
            TABLE_ID_ACTIVITIES_ABOUT_ME -> return Activities.AboutMe.TABLE_NAME
            TABLE_ID_DRAFTS -> return Drafts.TABLE_NAME
            TABLE_ID_FILTERED_USERS -> return Filters.Users.TABLE_NAME
            TABLE_ID_FILTERED_KEYWORDS -> return Filters.Keywords.TABLE_NAME
            TABLE_ID_FILTERED_SOURCES -> return Filters.Sources.TABLE_NAME
            TABLE_ID_FILTERED_LINKS -> return Filters.Links.TABLE_NAME
            TABLE_ID_FILTERS_SUBSCRIPTIONS -> return Filters.Subscriptions.TABLE_NAME
            TABLE_ID_MESSAGES -> return Messages.TABLE_NAME
            TABLE_ID_MESSAGES_CONVERSATIONS -> return Conversations.TABLE_NAME
            TABLE_ID_TRENDS_LOCAL -> return CachedTrends.Local.TABLE_NAME
            TABLE_ID_TABS -> return Tabs.TABLE_NAME
            TABLE_ID_CACHED_STATUSES -> return CachedStatuses.TABLE_NAME
            TABLE_ID_CACHED_USERS -> return CachedUsers.TABLE_NAME
            TABLE_ID_CACHED_HASHTAGS -> return CachedHashtags.TABLE_NAME
            TABLE_ID_CACHED_RELATIONSHIPS -> return CachedRelationships.TABLE_NAME
            TABLE_ID_SAVED_SEARCHES -> return SavedSearches.TABLE_NAME
            TABLE_ID_SEARCH_HISTORY -> return SearchHistory.TABLE_NAME
            else -> return null
        }
    }

    fun getTableNameByUri(uri: Uri?): String? {
        if (uri == null) return null
        return getTableNameById(getTableId(uri))
    }

    fun buildActivityFilterWhereClause(table: String, extraSelection: Expression?): Expression {
        val filteredUsersQuery = SQLQueryBuilder
                .select(Column(Table(Filters.Users.TABLE_NAME), Filters.Users.USER_KEY))
                .from(Tables(Filters.Users.TABLE_NAME))
                .build()
        val filteredUsersWhere = Expression.or(
                Expression.`in`(Column(Table(table), Activities.STATUS_USER_KEY), filteredUsersQuery),
                Expression.`in`(Column(Table(table), Activities.STATUS_RETWEETED_BY_USER_KEY), filteredUsersQuery),
                Expression.`in`(Column(Table(table), Activities.STATUS_QUOTED_USER_KEY), filteredUsersQuery)
        )
        val filteredIdsQueryBuilder = SQLQueryBuilder
                .select(Column(Table(table), Activities._ID))
                .from(Tables(table))
                .where(filteredUsersWhere)
                .union()
                .select(Columns(Column(Table(table), Activities._ID)))
                .from(Tables(table, Filters.Sources.TABLE_NAME))
                .where(Expression.or(
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_SOURCE),
                                "'%>'||${Filters.Sources.TABLE_NAME}.${Filters.Sources.VALUE}||'</a>%'"),
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_QUOTE_SOURCE),
                                "'%>'||${Filters.Sources.TABLE_NAME}.${Filters.Sources.VALUE}||'</a>%'")
                ))
                .union()
                .select(Columns(Column(Table(table), Activities._ID)))
                .from(Tables(table, Filters.Keywords.TABLE_NAME))
                .where(Expression.or(
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_TEXT_PLAIN),
                                "'%'||${Filters.Keywords.TABLE_NAME}.${Filters.Keywords.VALUE}||'%'"),
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_QUOTE_TEXT_PLAIN),
                                "'%'||${Filters.Keywords.TABLE_NAME}.${Filters.Keywords.VALUE}||'%'")
                ))
                .union()
                .select(Columns(Column(Table(table), Activities._ID)))
                .from(Tables(table, Filters.Links.TABLE_NAME))
                .where(Expression.or(
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_SPANS),
                                "'%'||${Filters.Links.TABLE_NAME}.${Filters.Links.VALUE}||'%'"),
                        Expression.likeRaw(Column(Table(table), Activities.STATUS_QUOTE_SPANS),
                                "'%'||${Filters.Links.TABLE_NAME}.${Filters.Links.VALUE}||'%'")
                ))
        val filterExpression = Expression.or(
                Expression.notIn(Column(Table(table), Activities._ID), filteredIdsQueryBuilder.build()),
                Expression.equals(Column(Table(table), Activities.IS_GAP), 1)
        )
        if (extraSelection != null) {
            return Expression.and(filterExpression, extraSelection)
        }
        return filterExpression
    }

    fun getAccountColors(context: Context, accountKeys: Array<UserKey>): IntArray {
        val am = AccountManager.get(context)
        val colors = IntArray(accountKeys.size)
        for (i in accountKeys.indices) {
            val account = AccountUtils.findByAccountKey(am, accountKeys[i])
            if (account != null) {
                colors[i] = account.getColor(am)
            }
        }
        return colors
    }

    fun findAccountKeyByScreenName(context: Context, screenName: String): UserKey? {
        val am = AccountManager.get(context)
        for (account in AccountUtils.getAccounts(am)) {
            val user = account.getAccountUser(am)
            if (screenName.equals(user.screen_name, ignoreCase = true)) {
                return user.key
            }
        }
        return null
    }

    fun getAccountKeys(context: Context): Array<UserKey> {
        val am = AccountManager.get(context)
        val accounts = AccountUtils.getAccounts(am)
        val keys = ArrayList<UserKey>(accounts.size)
        for (account in accounts) {
            val keyString = am.getUserData(account, ACCOUNT_USER_DATA_KEY) ?: continue
            keys.add(UserKey.valueOf(keyString))
        }
        return keys.toTypedArray()
    }

    fun findAccountKey(context: Context, accountId: String): UserKey? {
        val am = AccountManager.get(context)
        for (account in AccountUtils.getAccounts(am)) {
            val key = account.getAccountKey(am)
            if (accountId == key.id) {
                return key
            }
        }
        return null
    }

    fun hasAccount(context: Context): Boolean {
        return AccountUtils.getAccounts(AccountManager.get(context)).isNotEmpty()
    }

    @Synchronized fun cleanDatabasesByItemLimit(context: Context) {
        val resolver = context.contentResolver
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val itemLimit = preferences[databaseItemLimitKey]

        for (accountKey in getAccountKeys(context)) {
            // Clean statuses.
            for (uri in STATUSES_URIS) {
                if (CachedStatuses.CONTENT_URI == uri) {
                    continue
                }
                val table = getTableNameByUri(uri)
                val qb = SQLSelectQuery.Builder()
                qb.select(Column(Statuses._ID))
                        .from(Tables(table))
                        .where(Expression.equalsArgs(Statuses.ACCOUNT_KEY))
                        .orderBy(OrderBy(Statuses.POSITION_KEY, false))
                        .limit(itemLimit)
                val where = Expression.and(
                        Expression.notIn(Column(Statuses._ID), qb.build()),
                        Expression.equalsArgs(Statuses.ACCOUNT_KEY)
                )
                val whereArgs = arrayOf(accountKey.toString(), accountKey.toString())
                resolver.delete(uri, where.sql, whereArgs)
            }
            for (uri in ACTIVITIES_URIS) {
                val table = getTableNameByUri(uri)
                val qb = SQLSelectQuery.Builder()
                qb.select(Column(Activities._ID))
                        .from(Tables(table))
                        .where(Expression.equalsArgs(Activities.ACCOUNT_KEY))
                        .orderBy(OrderBy(Activities.TIMESTAMP, false))
                        .limit(itemLimit)
                val where = Expression.and(
                        Expression.notIn(Column(Activities._ID), qb.build()),
                        Expression.equalsArgs(Activities.ACCOUNT_KEY)
                )
                val whereArgs = arrayOf(accountKey.toString(), accountKey.toString())
                resolver.delete(uri, where.sql, whereArgs)
            }
        }
        // Clean cached values.
        for (uri in CACHE_URIS) {
            val table = getTableNameByUri(uri) ?: continue
            val qb = SQLSelectQuery.Builder()
            qb.select(Column(BaseColumns._ID))
                    .from(Tables(table))
                    .orderBy(OrderBy(BaseColumns._ID, false))
                    .limit(itemLimit * 20)
            val where = Expression.notIn(Column(BaseColumns._ID), qb.build())
            resolver.delete(uri, where.sql, null)
        }
    }

    fun isFilteringUser(context: Context, userKey: UserKey): Boolean {
        return isFilteringUser(context, userKey.toString())
    }

    fun isFilteringUser(context: Context, userKey: String): Boolean {
        val cr = context.contentResolver
        val where = Expression.equalsArgs(Filters.Users.USER_KEY)
        val c = cr.query(Filters.Users.CONTENT_URI, arrayOf(SQLFunctions.COUNT()),
                where.sql, arrayOf(userKey), null) ?: return false
        try {
            if (c.moveToFirst()) {
                return c.getLong(0) > 0
            }
        } finally {
            c.close()
        }
        return false
    }

    private fun <T> getObjectFieldArray(context: Context, uri: Uri, keys: Array<UserKey?>,
            keyField: String, valueFields: Array<String>, sortExpression: OrderBy?, extraWhere: Expression?,
            extraWhereArgs: Array<String>?, createIndices: (Cursor) -> ObjectCursor.CursorIndices<T>,
            createArray: (Int) -> Array<T?>): Array<T?> {
        return getFieldsArray(context, uri, keys, keyField, valueFields, sortExpression,
                extraWhere, extraWhereArgs, object : FieldArrayCreator<Array<T?>, ObjectCursor.CursorIndices<T>> {
            override fun newArray(size: Int): Array<T?> {
                return createArray(size)
            }

            override fun newIndex(cur: Cursor): ObjectCursor.CursorIndices<T> {
                return createIndices(cur)
            }

            override fun assign(array: Array<T?>, arrayIdx: Int, cur: Cursor, colIdx: ObjectCursor.CursorIndices<T>) {
                array[arrayIdx] = colIdx.newObject(cur)
            }
        })
    }

    private fun getStringFieldArray(context: Context, uri: Uri, keys: Array<UserKey?>,
            keyField: String, valueField: String, sortExpression: OrderBy?, extraWhere: Expression?,
            extraWhereArgs: Array<String>?): Array<String?> {
        return getFieldsArray(context, uri, keys, keyField, arrayOf(valueField), sortExpression,
                extraWhere, extraWhereArgs, object : FieldArrayCreator<Array<String?>, Int> {
            override fun newArray(size: Int): Array<String?> {
                return arrayOfNulls(size)
            }

            override fun newIndex(cur: Cursor): Int {
                return cur.getColumnIndex(valueField)
            }

            override fun assign(array: Array<String?>, arrayIdx: Int, cur: Cursor, colIdx: Int) {
                array[arrayIdx] = cur.getString(colIdx)
            }
        })
    }

    private fun getLongFieldArray(context: Context, uri: Uri, keys: Array<UserKey?>,
            keyField: String, valueField: String, sortExpression: OrderBy?, extraWhere: Expression?,
            extraWhereArgs: Array<String>?): LongArray {
        return getFieldsArray(context, uri, keys, keyField, arrayOf(valueField), sortExpression,
                extraWhere, extraWhereArgs, object : FieldArrayCreator<LongArray, Int> {
            override fun newArray(size: Int): LongArray {
                return LongArray(size)
            }

            override fun newIndex(cur: Cursor): Int {
                return cur.getColumnIndex(valueField)
            }

            override fun assign(array: LongArray, arrayIdx: Int, cur: Cursor, colIdx: Int) {
                array[arrayIdx] = cur.getLong(colIdx)
            }
        })
    }

    @SuppressLint("Recycle")
    private fun <T, I> getFieldsArray(
            context: Context, uri: Uri,
            keys: Array<UserKey?>, keyField: String,
            valueFields: Array<String>, sortExpression: OrderBy?,
            extraWhere: Expression?, extraWhereArgs: Array<String>?,
            creator: FieldArrayCreator<T, I>
    ): T {
        val resolver = context.contentResolver
        val resultArray = creator.newArray(keys.size)
        val nonNullKeys = keys.mapNotNull { it?.toString() }.toTypedArray()
        val tableName = getTableNameByUri(uri) ?: throw NullPointerException()
        val having = Expression.inArgs(keyField, nonNullKeys.size)
        val bindingArgs: Array<String>
        if (extraWhereArgs != null) {
            bindingArgs = extraWhereArgs + nonNullKeys
        } else {
            bindingArgs = nonNullKeys
        }
        val builder = SQLQueryBuilder.select(Columns(keyField, *valueFields))
        builder.from(Table(tableName))
        if (extraWhere != null) {
            builder.where(extraWhere)
        }
        builder.groupBy(Column(keyField))
        builder.having(having)
        if (sortExpression != null) {
            builder.orderBy(sortExpression)
        }
        resolver.rawQuery(builder.buildSQL(), bindingArgs)?.useCursor { cur ->
            cur.moveToFirst()
            val colIdx = creator.newIndex(cur)
            while (!cur.isAfterLast) {
                val keyString = cur.getString(cur.getColumnIndex(keyField))
                if (keyString != null) {
                    val accountKey = UserKey.valueOf(keyString)
                    val arrayIdx = keys.indexOfFirst {
                        accountKey == it
                    }
                    if (arrayIdx >= 0) {
                        creator.assign(resultArray, arrayIdx, cur, colIdx)
                    }
                }
                cur.moveToNext()
            }
        }
        return resultArray
    }

    fun deleteStatus(cr: ContentResolver, accountKey: UserKey,
            statusId: String, status: ParcelableStatus?) {

        val host = accountKey.host
        val deleteWhere: String
        val updateWhere: String
        val deleteWhereArgs: Array<String>
        val updateWhereArgs: Array<String>
        if (host != null) {
            deleteWhere = Expression.and(
                    Expression.likeRaw(Column(Statuses.ACCOUNT_KEY), "'%@'||?"),
                    Expression.or(
                            Expression.equalsArgs(Statuses.STATUS_ID),
                            Expression.equalsArgs(Statuses.RETWEET_ID)
                    )).sql
            deleteWhereArgs = arrayOf(host, statusId, statusId)
            updateWhere = Expression.and(
                    Expression.likeRaw(Column(Statuses.ACCOUNT_KEY), "'%@'||?"),
                    Expression.equalsArgs(Statuses.MY_RETWEET_ID)
            ).sql
            updateWhereArgs = arrayOf(host, statusId)
        } else {
            deleteWhere = Expression.or(
                    Expression.equalsArgs(Statuses.STATUS_ID),
                    Expression.equalsArgs(Statuses.RETWEET_ID)
            ).sql
            deleteWhereArgs = arrayOf(statusId, statusId)
            updateWhere = Expression.equalsArgs(Statuses.MY_RETWEET_ID).sql
            updateWhereArgs = arrayOf(statusId)
        }
        for (uri in STATUSES_URIS) {
            cr.delete(uri, deleteWhere, deleteWhereArgs)
            if (status != null) {
                val values = ContentValues()
                values.putNull(Statuses.MY_RETWEET_ID)
                values.put(Statuses.RETWEET_COUNT, status.retweet_count - 1)
                cr.update(uri, values, updateWhere, updateWhereArgs)
            }
        }
    }


    fun processTabExtras(expressions: MutableList<Expression>, expressionArgs: MutableList<String>, extras: HomeTabExtras) {
        if (extras.isHideRetweets) {
            expressions.add(Expression.equalsArgs(Statuses.IS_RETWEET))
            expressionArgs.add("0")
        }
        if (extras.isHideQuotes) {
            expressions.add(Expression.equalsArgs(Statuses.IS_QUOTE))
            expressionArgs.add("0")
        }
        if (extras.isHideReplies) {
            expressions.add(Expression.isNull(Column(Statuses.IN_REPLY_TO_STATUS_ID)))
        }
    }

    fun prepareDatabase(context: Context) {
        val cr = context.contentResolver
        val cursor = cr.query(TwidereDataStore.CONTENT_URI_DATABASE_PREPARE, null, null,
                null, null) ?: return
        cursor.close()
    }

    internal interface FieldArrayCreator<T, I> {
        fun newArray(size: Int): T

        fun newIndex(cur: Cursor): I

        fun assign(array: T, arrayIdx: Int, cur: Cursor, colIdx: I)
    }

    fun queryCount(cr: ContentResolver, uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val projection = arrayOf(SQLFunctions.COUNT())
        val cur = cr.query(uri, projection, selection, selectionArgs, null) ?: return -1
        try {
            if (cur.moveToFirst()) {
                return cur.getInt(0)
            }
            return -1
        } finally {
            cur.close()
        }
    }

    fun getInteractionsCount(context: Context, extraArgs: Bundle?,
            accountIds: Array<UserKey>, since: Long, sinceColumn: String): Int {
        var extraWhere: Expression? = null
        var extraWhereArgs: Array<String>? = null
        var followingOnly = false
        if (extraArgs != null) {
            val extras = extraArgs.getParcelable<TabExtras>(IntentConstants.EXTRA_EXTRAS)
            if (extras is InteractionsTabExtras) {
                if (extras.isMentionsOnly) {
                    extraWhere = Expression.inArgs(Activities.ACTION, 3)
                    extraWhereArgs = arrayOf(Activity.Action.MENTION, Activity.Action.REPLY, Activity.Action.QUOTE)
                }
                if (extras.isMyFollowingOnly) {
                    followingOnly = true
                }
            }
        }
        return getActivitiesCount(context, Activities.AboutMe.CONTENT_URI, extraWhere, extraWhereArgs,
                since, sinceColumn, followingOnly, accountIds)
    }

    fun addToFilter(context: Context, users: Collection<ParcelableUser>, filterAnywhere: Boolean) {
        val cr = context.contentResolver

        try {
            val baseCreator = ObjectCursor.valuesCreatorFrom(FiltersData.BaseItem::class.java)
            val userCreator = ObjectCursor.valuesCreatorFrom(FiltersData.UserItem::class.java)
            val userValues = ArrayList<ContentValues>()
            val keywordValues = ArrayList<ContentValues>()
            val linkValues = ArrayList<ContentValues>()
            for (user in users) {
                val userItem = FiltersData.UserItem()
                userItem.userKey = user.key
                userItem.screenName = user.screen_name
                userItem.name = user.name
                userValues.add(userCreator.create(userItem))

                val keywordItem = FiltersData.BaseItem()
                keywordItem.value = "@" + user.screen_name
                keywordItem.userKey = user.key
                keywordValues.add(baseCreator.create(keywordItem))

                // Insert user link (without scheme) to links
                val linkItem = FiltersData.BaseItem()
                val userLink = LinkCreator.getUserWebLink(user)
                val linkWithoutScheme = userLink.toString().substringAfter("://")
                linkItem.value = linkWithoutScheme
                linkItem.userKey = user.key
                linkValues.add(baseCreator.create(linkItem))
            }

            ContentResolverUtils.bulkInsert(cr, Filters.Users.CONTENT_URI, userValues)
            if (filterAnywhere) {
                // Insert to filtered users
                ContentResolverUtils.bulkInsert(cr, Filters.Keywords.CONTENT_URI, keywordValues)
                // Insert user mention to keywords
                ContentResolverUtils.bulkInsert(cr, Filters.Links.CONTENT_URI, linkValues)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun removeFromFilter(context: Context, users: Collection<ParcelableUser>) {
        val cr = context.contentResolver
        // Delete from filtered users
        val userKeyValues = users.map { it.key.toString() }
        ContentResolverUtils.bulkDelete(cr, Filters.Users.CONTENT_URI, Filters.Users.USER_KEY,
                false, userKeyValues, null, null)
        ContentResolverUtils.bulkDelete(cr, Filters.Keywords.CONTENT_URI, Filters.Keywords.USER_KEY,
                false, userKeyValues, null, null)
        ContentResolverUtils.bulkDelete(cr, Filters.Links.CONTENT_URI, Filters.Links.USER_KEY,
                false, userKeyValues, null, null)
    }

    @WorkerThread
    fun findStatusInDatabases(context: Context,
            accountKey: UserKey,
            statusId: String): ParcelableStatus? {
        val resolver = context.contentResolver
        var status: ParcelableStatus? = null
        val where = Expression.and(Expression.equalsArgs(Statuses.ACCOUNT_KEY),
                Expression.equalsArgs(Statuses.STATUS_ID)).sql
        val whereArgs = arrayOf(accountKey.toString(), statusId)
        for (uri in DataStoreUtils.STATUSES_URIS) {
            val cur = resolver.query(uri, Statuses.COLUMNS, where, whereArgs, null) ?: continue
            try {
                if (cur.moveToFirst()) {
                    val indices = ObjectCursor.indicesFrom(cur, ParcelableStatus::class.java)
                    status = indices.newObject(cur)
                }
            } finally {
                cur.close()
            }
        }
        return status
    }


    @WorkerThread
    @Throws(MicroBlogException::class)
    fun findStatus(context: Context, accountKey: UserKey, statusId: String): ParcelableStatus {
        val cached = findStatusInDatabases(context, accountKey, statusId)
        if (cached != null) return cached
        val details = AccountUtils.getAccountDetails(AccountManager.get(context), accountKey,
                true) ?: throw MicroBlogException("No account")
        val microBlog = details.newMicroBlogInstance(context, MicroBlog::class.java)
        val result = microBlog.showStatus(statusId)
        val where = Expression.and(Expression.equalsArgs(Statuses.ACCOUNT_KEY),
                Expression.equalsArgs(Statuses.STATUS_ID)).sql
        val whereArgs = arrayOf(accountKey.toString(), statusId)
        val resolver = context.contentResolver
        val status = ParcelableStatusUtils.fromStatus(result, accountKey, details.type, false)
        resolver.delete(CachedStatuses.CONTENT_URI, where, whereArgs)
        resolver.insert(CachedStatuses.CONTENT_URI, ObjectCursor.valuesCreatorFrom(ParcelableStatus::class.java).create(status))
        return status
    }

    @WorkerThread
    fun findMessageConversation(context: Context, accountKey: UserKey, conversationId: String): ParcelableMessageConversation? {
        val resolver = context.contentResolver
        val where = Expression.and(Expression.equalsArgs(Conversations.ACCOUNT_KEY),
                Expression.equalsArgs(Conversations.CONVERSATION_ID)).sql
        val whereArgs = arrayOf(accountKey.toString(), conversationId)
        val cur = resolver.query(Conversations.CONTENT_URI, Conversations.COLUMNS, where, whereArgs, null) ?: return null
        try {
            if (cur.moveToFirst()) {
                val indices = ObjectCursor.indicesFrom(cur, ParcelableMessageConversation::class.java)
                return indices.newObject(cur)
            }
        } finally {
            cur.close()
        }
        return null
    }


    private fun getIdsWhere(official: Boolean): Pair<Expression?, Array<String>?> {
        if (official) return Pair(null, null)
        return Pair(Expression.inArgs(Activities.ACTION, Activity.Action.MENTION_ACTIONS.size)
                , Activity.Action.MENTION_ACTIONS)
    }

    private fun <T> getOfficialSeparatedIds(context: Context, getFromDatabase: (Array<UserKey?>, Boolean) -> T,
            mergeResult: (T, T) -> T, accountKeys: Array<UserKey?>): T {
        val officialKeys = Array(accountKeys.size) {
            val key = accountKeys[it]
            if (Utils.isOfficialCredentials(context, key)) {
                return@Array key
            }
            return@Array null
        }
        val notOfficialKeys = Array(accountKeys.size) {
            val key = accountKeys[it]
            if (Utils.isOfficialCredentials(context, key)) {
                return@Array null
            }
            return@Array key
        }

        val officialMaxPositions = getFromDatabase(officialKeys, true)
        val notOfficialMaxPositions = getFromDatabase(notOfficialKeys, false)
        return mergeResult(officialMaxPositions, notOfficialMaxPositions)
    }
}
