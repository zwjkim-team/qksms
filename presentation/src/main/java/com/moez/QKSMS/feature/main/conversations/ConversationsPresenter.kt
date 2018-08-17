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

import androidx.recyclerview.widget.ItemTouchHelper
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.base.QkPresenter
import com.moez.QKSMS.feature.main.search.SearchController
import com.moez.QKSMS.interactor.DeleteConversations
import com.moez.QKSMS.interactor.MarkArchived
import com.moez.QKSMS.interactor.MarkBlocked
import com.moez.QKSMS.interactor.MarkPinned
import com.moez.QKSMS.interactor.MarkRead
import com.moez.QKSMS.interactor.MarkUnarchived
import com.moez.QKSMS.interactor.MarkUnpinned
import com.moez.QKSMS.interactor.MarkUnread
import com.moez.QKSMS.repository.ConversationRepository
import com.moez.QKSMS.util.Preferences
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import javax.inject.Inject
import javax.inject.Named

class ConversationsPresenter @Inject constructor(
        @Named("archived") private val archived: Boolean,
        private val conversationRepo: ConversationRepository,
        private val deleteConversations: DeleteConversations,
        private val markArchived: MarkArchived,
        private val markBlocked: MarkBlocked,
        private val markPinned: MarkPinned,
        private val markRead: MarkRead,
        private val markUnarchived: MarkUnarchived,
        private val markUnpinned: MarkUnpinned,
        private val markUnread: MarkUnread,
        private val navigator: Navigator,
        private val prefs: Preferences
) : QkPresenter<ConversationsView, ConversationsState>(ConversationsState(
        conversations = conversationRepo.getConversations(archived))
) {

    init {
        disposables += deleteConversations
        disposables += markArchived
        disposables += markUnarchived
    }

    override fun bindIntents(view: ConversationsView) {
        super.bindIntents(view)

        view.composeClicks()
                .autoDisposable(view.scope())
                .subscribe { navigator.showCompose() }

        view.optionsItemSelected()
                .withLatestFrom(view.selectionChanges()) { itemId, conversations ->
                    when (itemId) {
                        R.id.search -> {
                            view.getRouter().pushController(RouterTransaction.with(SearchController())
                                    .popChangeHandler(FadeChangeHandler())
                                    .pushChangeHandler(FadeChangeHandler()))
                        }

                        R.id.archive -> {
                            markArchived.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.unarchive -> {
                            markUnarchived.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.delete -> view.showDeleteDialog(conversations)

                        R.id.pin -> {
                            markPinned.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.unpin -> {
                            markUnpinned.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.read -> {
                            markRead.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.unread -> {
                            markUnread.execute(conversations)
                            view.clearSelection()
                        }

                        R.id.block -> {
                            markBlocked.execute(conversations)
                            view.clearSelection()
                        }
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()

        view.selectionChanges()
                .autoDisposable(view.scope())
                .subscribe { selection ->
                    val pin = selection
                            .mapNotNull(conversationRepo::getConversation)
                            .sumBy { if (it.pinned) -1 else 1 } >= 0
                    val read = selection
                            .mapNotNull(conversationRepo::getConversation)
                            .sumBy { if (it.read) -1 else 1 } >= 0
                    val selected = selection.size

                    newState { copy(markPinned = pin, markRead = read, selected = selected, showClearButton = selected > 0) }
                }

        // Delete the conversation
        view.deleteConfirmed()
                .autoDisposable(view.scope())
                .subscribe { conversations ->
                    deleteConversations.execute(conversations)
                    view.clearSelection()
                }

        view.conversationSwiped()
                .autoDisposable(view.scope())
                .subscribe { (threadId, direction) ->
                    val action = if (direction == ItemTouchHelper.RIGHT) prefs.swipeRight.get() else prefs.swipeLeft.get()
                    when (action) {
                        Preferences.SWIPE_ACTION_ARCHIVE -> markArchived.execute(listOf(threadId)) { view.showArchivedSnackbar() }
                        Preferences.SWIPE_ACTION_DELETE -> view.showDeleteDialog(listOf(threadId))
                        Preferences.SWIPE_ACTION_CALL -> conversationRepo.getConversation(threadId)?.recipients?.firstOrNull()?.address?.let(navigator::makePhoneCall)
                        Preferences.SWIPE_ACTION_READ -> markRead.execute(listOf(threadId))
                    }
                }

        view.archiveUndone()
                .withLatestFrom(view.conversationSwiped()) { _, pair -> pair.first }
                .autoDisposable(view.scope())
                .subscribe { threadId -> markUnarchived.execute(listOf(threadId)) }

        view.backClicks()
                .autoDisposable(view.scope())
                .subscribe { view.clearSelection() }
    }

}