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
package com.moez.QKSMS.common.base

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.autodispose.ControllerEvent
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.moez.QKSMS.common.RouterProvider
import com.uber.autodispose.LifecycleScopeProvider
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.*

abstract class QkController<ViewContract : QkViewContract<State>, State, Presenter : QkPresenter<ViewContract, State>>
    : Controller(), LayoutContainer, RouterProvider {

    abstract var presenter: Presenter

    private val qkActivity: QkActivity?
        get() = activity as? QkActivity

    protected val themedActivity: QkThemedActivity?
        get() = activity as? QkThemedActivity

    protected val optionsItemSubject: Subject<Int> = PublishSubject.create()

    override var containerView: View? = null

    @LayoutRes
    var layoutRes: Int = 0

    @MenuRes
    var menuRes: Int = 0
        set(value) {
            field = value
            setHasOptionsMenu(value != 0)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(layoutRes, container, false).also {
            containerView = it
            onViewCreated()
        }
    }

    open fun onViewCreated() = Unit

    override fun onChangeStarted(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        super.onChangeStarted(changeHandler, changeType)
        setOptionsMenuHidden(!changeType.isEnter)
    }

    fun setTitle(@StringRes titleId: Int) {
        setTitle(activity?.getString(titleId))
    }

    fun setTitle(title: CharSequence?) {
        activity?.title = title
    }

    fun showBackButton(show: Boolean) {
        qkActivity?.showBackButton(show)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(menuRes, menu)
    }

    @CallSuper
    override fun onPrepareOptionsMenu(menu: Menu) {
        themedActivity?.menu?.onNext(menu)
    }

    @CallSuper
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemSubject.onNext(item.itemId)
        return super.onOptionsItemSelected(item)
    }

    @CallSuper
    override fun onDestroyView(view: View) {
        containerView = null
        clearFindViewByIdCache()
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        presenter.onCleared()
    }

    fun scope(): LifecycleScopeProvider<ControllerEvent> {
        return ControllerScopeProvider.from(this)
    }

}