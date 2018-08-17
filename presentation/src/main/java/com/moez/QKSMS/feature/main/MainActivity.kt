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
package com.moez.QKSMS.feature.main

import android.Manifest
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.jakewharton.rxbinding2.view.clicks
import com.moez.QKSMS.R
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.androidxcompat.drawerOpen
import com.moez.QKSMS.common.androidxcompat.scope
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.extensions.dismissKeyboard
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.feature.main.conversations.ConversationsController
import com.moez.QKSMS.feature.main.search.SearchController
import com.moez.QKSMS.repository.SyncRepository
import com.uber.autodispose.kotlin.autoDisposable
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.drawer_view.*
import kotlinx.android.synthetic.main.main_activity.*
import javax.inject.Inject


class MainActivity : QkThemedActivity(), MainView, ControllerChangeHandler.ControllerChangeListener {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var router: Router

    private val activityResumedSubject: Subject<Unit> = PublishSubject.create()
    private val optionsItemSubject: Subject<Int> = PublishSubject.create()
    private val backPressedSubject: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[MainViewModel::class.java] }
    private val toggle by lazy { ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.main_drawer_open_cd, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        viewModel.bindView(this)

        toggle.syncState()
        toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            if (drawerLayout.getDrawerLockMode(Gravity.START) == DrawerLayout.LOCK_MODE_UNLOCKED) {
                drawerLayout.openDrawer(Gravity.START)
            } else {
                onBackPressed()
            }
        }

        // Don't allow clicks to pass through the drawer layout
        drawer.clicks().subscribe()

        // Set the theme color tint to the recyclerView, progressbar, and FAB
        colors.themeObservable()
                .autoDisposable(scope())
                .subscribe { theme ->
                    // Set the color for the drawer icons
                    val tintList = ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf(-android.R.attr.state_activated)),
                            intArrayOf(theme.theme, resolveThemeColor(android.R.attr.textColorSecondary)))
                    inboxIcon.imageTintList = tintList
                    archivedIcon.imageTintList = tintList

                    // Miscellaneous views
                    plusBadge.setBackgroundTint(theme.theme)
                    plusBadge.setTextColor(theme.textPrimary)
                    syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    plusIcon.setTint(theme.theme)
                    rateIcon.setTint(theme.theme)
                }

        // Set the hamburger icon color
        toggle.drawerArrowDrawable.color = resolveThemeColor(android.R.attr.textColorSecondary)

        router = Conductor.attachRouter(this, container, savedInstanceState)
        router.addChangeListener(this)

        if (!router.hasRootController()) {
            router.setRoot(RouterTransaction.with(ConversationsController()))
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumedSubject.onNext(Unit)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        optionsItemSubject.onNext(item.itemId)
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        router.removeChangeListener(this)
    }

    override fun showBackButton(show: Boolean) {
        // Add or remove the toggle drawer listener based on whether or not we'll enable to drawer
        when (show) {
            true -> drawerLayout.removeDrawerListener(toggle)
            false -> drawerLayout.addDrawerListener(toggle)
        }

        // Animate the toggle icon to the new position
        ObjectAnimator.ofFloat(toggle.drawerArrowDrawable, "progress", if (show) 1f else 0f).start()

        // Lock the drawer if we're showing a back button
        drawerLayout.setDrawerLockMode(when (show) {
            true -> DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            false -> DrawerLayout.LOCK_MODE_UNLOCKED
        }, Gravity.START)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START)
        } else if (!router.popCurrentController()) {
            finish()
        }
    }

    override fun render(state: MainState) {
        plusBadge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        plus.isVisible = state.upgraded
        plusBanner.isVisible = !state.upgraded
        rateLayout.setVisible(state.showRating)

        if (drawerLayout.isDrawerOpen(Gravity.START) && !state.drawerOpen) drawerLayout.closeDrawer(Gravity.START)
        else if (!drawerLayout.isDrawerVisible(Gravity.START) && state.drawerOpen) drawerLayout.openDrawer(Gravity.START)

        syncing.setVisible(state.syncing is SyncRepository.SyncProgress.Running)
        snackbar.setVisible(state.syncing is SyncRepository.SyncProgress.Idle
                && !state.defaultSms || !state.smsPermission || !state.contactPermission)

        when {
            !state.smsPermission -> {
                snackbarTitle.setText(R.string.main_permission_required)
                snackbarMessage.setText(R.string.main_permission_sms)
                snackbarButton.setText(R.string.main_permission_allow)
            }

            !state.defaultSms -> {
                snackbarTitle.setText(R.string.main_default_sms_title)
                snackbarMessage.setText(R.string.main_default_sms_message)
                snackbarButton.setText(R.string.main_default_sms_change)
            }

            !state.contactPermission -> {
                snackbarTitle.setText(R.string.main_permission_required)
                snackbarMessage.setText(R.string.main_permission_contacts)
                snackbarButton.setText(R.string.main_permission_allow)
            }
        }
    }

    override fun activityResumed(): Observable<*> = activityResumedSubject

    override fun drawerOpened(): Observable<Boolean> = drawerLayout
            .drawerOpen(Gravity.START)
            .doOnNext { dismissKeyboard() }

    override fun drawerItemSelected(): Observable<DrawerItem> = Observable.merge(listOf(
            inbox.clicks().map { DrawerItem.INBOX },
            archived.clicks().map { DrawerItem.ARCHIVED },
            scheduled.clicks().map { DrawerItem.SCHEDULED },
            blocking.clicks().map { DrawerItem.BLOCKING },
            settings.clicks().map { DrawerItem.SETTINGS },
            plus.clicks().map { DrawerItem.PLUS },
            help.clicks().map { DrawerItem.HELP },
            invite.clicks().map { DrawerItem.INVITE }))

    override fun optionsItemSelected(): Observable<Int> = optionsItemSubject

    override fun plusBannerClicked(): Observable<*> = plusBanner.clicks()

    override fun ratingDismissed(): Observable<*> = rateDismiss.clicks()

    override fun ratingClicked(): Observable<*> = rateOkay.clicks()

    override fun snackbarClicked(): Observable<*> = snackbarButton.clicks()

    override fun backPressed(): Observable<*> = backPressedSubject

    override fun getRouter(): Router = router

    override fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS), 0)
    }

    override fun onChangeStarted(to: Controller?, from: Controller?, isPush: Boolean, container: ViewGroup, handler: ControllerChangeHandler) {
        toolbar.menu.findItem(R.id.search)?.isVisible = to is ConversationsController || to is SearchController

        inbox.isActivated = to is ConversationsController && !to.archived
        archived.isActivated = to is ConversationsController && to.archived
    }

    override fun onChangeCompleted(to: Controller?, from: Controller?, isPush: Boolean, container: ViewGroup, handler: ControllerChangeHandler) = Unit

}
