/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.feature.main.conversations

import com.bluelinelabs.conductor.Router
import com.moez.QKSMS.common.base.QkViewContract
import io.reactivex.Observable

interface ConversationsView : QkViewContract<ConversationsState> {

    fun optionsItemSelected(): Observable<Int>
    fun conversationClicks(): Observable<Long>
    fun conversationSwiped(): Observable<Pair<Long, Int>>
    fun archiveUndone(): Observable<*>
    fun selectionChanges(): Observable<List<Long>>
    fun deleteConfirmed(): Observable<List<Long>>
    fun composeClicks(): Observable<*>
    fun backClicks(): Observable<*>

    fun getRouter(): Router

    fun clearSelection()
    fun showDeleteDialog(conversations: List<Long>)
    fun showArchivedSnackbar()

}