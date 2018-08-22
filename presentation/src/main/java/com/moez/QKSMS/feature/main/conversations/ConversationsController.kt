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

import android.app.AlertDialog
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.base.QkController
import com.moez.QKSMS.common.util.Colors
import com.moez.QKSMS.common.util.extensions.autoScrollToStart
import com.moez.QKSMS.common.util.extensions.scrapViews
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.feature.main.conversations.injection.ConversationsModule
import com.moez.QKSMS.injection.appComponent
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.conversations_controller.*
import kotlinx.android.synthetic.main.toolbar.*
import javax.inject.Inject

class ConversationsController(
        val archived: Boolean = false
) : QkController<ConversationsView, ConversationsState, ConversationsPresenter>(), ConversationsView {

    @Inject lateinit var colors: Colors
    @Inject lateinit var adapter: ConversationsAdapter
    @Inject lateinit var itemTouchCallback: ConversationItemTouchCallback
    @Inject override lateinit var presenter: ConversationsPresenter

    private val itemTouchHelper by lazy { ItemTouchHelper(itemTouchCallback) }
    private val deleteConfirmedSubject: Subject<List<Long>> = PublishSubject.create()
    private val archiveUndoneSubject: Subject<Unit> = PublishSubject.create()
    private val backPressedSubject: Subject<Unit> = PublishSubject.create()

    init {
        appComponent
                .conversationsBuilder()
                .conversationsModule(ConversationsModule(this))
                .build()
                .inject(this)

        layoutRes = R.layout.conversations_controller
        menuRes = R.menu.conversations
    }

    override fun onViewCreated() {
        empty.setText(when (archived) {
            true -> R.string.inbox_empty_text
            false -> R.string.archived_empty_text
        })

        adapter.emptyView = empty
        adapter.autoScrollToStart(conversations)

        conversations.setHasFixedSize(true)
        conversations.adapter = adapter

        if (!archived) {
            itemTouchCallback.adapter = adapter
            itemTouchHelper.attachToRecyclerView(conversations)
        }

        colors.themeObservable()
                .autoDisposable(scope())
                .subscribe { theme ->
                    conversations.scrapViews()
                    compose.setBackgroundTint(theme.theme)
                    compose.setTint(theme.textPrimary)
                }
    }

    override fun onAttach(view: View) {
        presenter.bindIntents(this)
        showBackButton(false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.conversations, menu)
    }

    override fun handleBack(): Boolean {
        return when {
            adapter.isInSelectionMode() -> {
                backPressedSubject.onNext(Unit)
                true
            }

            else -> false
        }
    }

    override fun render(state: ConversationsState) {
        showBackButton(state.showClearButton)

        setTitle(when {
            state.selected != 0 -> activity?.getString(R.string.main_title_selected, state.selected)
            archived -> activity?.getString(R.string.title_archived)
            else -> activity?.getString(R.string.main_title)
        })

        themedActivity?.toolbar?.menu?.run {
            findItem(R.id.search)?.isVisible = state.selected == 0
            findItem(R.id.archive)?.isVisible = !archived && state.selected != 0
            findItem(R.id.unarchive)?.isVisible = archived && state.selected != 0
            findItem(R.id.delete)?.isVisible = state.selected != 0
            findItem(R.id.pin)?.isVisible = state.markPinned && state.selected != 0
            findItem(R.id.unpin)?.isVisible = !state.markPinned && state.selected != 0
            findItem(R.id.read)?.isVisible = state.markRead && state.selected != 0
            findItem(R.id.unread)?.isVisible = !state.markRead && state.selected != 0
            findItem(R.id.block)?.isVisible = state.selected != 0
        }

        adapter.updateData(state.conversations)
    }

    override fun optionsItemSelected(): Observable<Int> = optionsItemSubject

    override fun conversationClicks(): Observable<Long> = adapter.conversationClicks

    override fun archiveUndone(): Observable<*> = archiveUndoneSubject

    override fun conversationSwiped(): Observable<Pair<Long, Int>> = itemTouchCallback.swipes

    override fun selectionChanges(): Observable<List<Long>> = adapter.selectionChanges

    override fun deleteConfirmed(): Observable<List<Long>> = deleteConfirmedSubject

    override fun composeClicks(): Observable<*> = compose.clicks()

    override fun backClicks(): Observable<*> = backPressedSubject

    override fun clearSelection() {
        adapter.clearSelection()
    }

    override fun showDeleteDialog(conversations: List<Long>) {
        val count = conversations.size
        AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources?.getQuantityString(R.plurals.dialog_delete_message, count, count))
                .setPositiveButton(R.string.button_delete) { _, _ -> deleteConfirmedSubject.onNext(conversations) }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
    }

    override fun showArchivedSnackbar() {
        Snackbar.make(container, R.string.toast_archived, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.button_undo) { archiveUndoneSubject.onNext(Unit) }
        }.show()
    }

}