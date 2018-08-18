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
package com.moez.QKSMS.feature.main.search

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import com.moez.QKSMS.R
import com.moez.QKSMS.common.base.QkController
import com.moez.QKSMS.injection.appComponent
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.search_controller.*
import javax.inject.Inject

class SearchController : QkController<SearchView, SearchState, SearchPresenter>(), SearchView, MenuItem.OnActionExpandListener {

    @Inject lateinit var adapter: SearchAdapter
    @Inject override lateinit var presenter: SearchPresenter

    private val queryChangedSubject: Subject<CharSequence> = PublishSubject.create()

    // Hack: Conductor recreates the entire menu every time there's a transaction, meaning that our
    // search is lost when we navigate back to this Controller. Store the query here so we can set
    // it in the new menu once it's recreated
    private var query: CharSequence = ""

    init {
        appComponent.inject(this)
        layoutRes = R.layout.search_controller
        menuRes = R.menu.search
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        showBackButton(true)
    }

    override fun onViewCreated() {
        results.adapter = adapter
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        // Find the MenuItem and ActionView
        val search = menu.findItem(R.id.search)
        val searchView = search?.actionView as? com.moez.QKSMS.common.widget.SearchView

        // Expand the ActionView and start listening for when it closes
        search?.expandActionView()
        search?.setOnActionExpandListener(this)

        searchView?.setText(query)

        // Forward text changes to our Presenter
        searchView?.queryChanged
                ?.doOnNext { query = it }
                ?.autoDisposable(scope())
                ?.subscribe(queryChangedSubject)
    }

    override fun render(state: SearchState) {
        adapter.data = state.data ?: listOf()
        empty.isVisible = state.loading == false && state.data?.isEmpty() == true
    }

    override fun queryChanges(): Observable<CharSequence> = queryChangedSubject

    override fun onMenuItemActionExpand(item: MenuItem?): Boolean = true

    override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
        router.popCurrentController()
        return true
    }

}