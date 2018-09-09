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
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
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
import com.moez.QKSMS.common.QkChangeHandler
import com.moez.QKSMS.common.RouterProvider
import com.moez.QKSMS.common.androidxcompat.drawerOpen
import com.moez.QKSMS.common.androidxcompat.scope
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.extensions.dismissKeyboard
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.feature.compose.ComposeController
import com.moez.QKSMS.feature.main.conversations.ConversationsController
import com.moez.QKSMS.feature.main.search.SearchController
import com.moez.QKSMS.model.Attachment
import com.moez.QKSMS.repository.SyncRepository
import com.uber.autodispose.kotlin.autoDisposable
import dagger.android.AndroidInjection
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.drawer_view.*
import kotlinx.android.synthetic.main.main_activity.*
import java.net.URLDecoder
import javax.inject.Inject


class MainActivity : QkThemedActivity(), MainView, ControllerChangeHandler.ControllerChangeListener, RouterProvider {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var drawerBadgesExperiment: DrawerBadgesExperiment
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var router: Router
    private val pendingRouterTransactions: MutableList<RouterTransaction> = ArrayList()

    private val activityResumedSubject: Subject<Unit> = PublishSubject.create()
    private val backPressedSubject: Subject<Unit> = PublishSubject.create()

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[MainViewModel::class.java] }
    private val toggle by lazy { ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.main_drawer_open_cd, 0) }
    private val progressAnimator by lazy { ObjectAnimator.ofInt(syncingProgress, "progress", 0, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        viewModel.bindView(this)

        router = Conductor.attachRouter(this, container, savedInstanceState)
        router.addChangeListener(this)

        if (!router.hasRootController()) {
            router.setRoot(RouterTransaction.with(ConversationsController()))
            pendingRouterTransactions.forEach(router::pushController)
            pendingRouterTransactions.clear()
        }

        toggle.syncState()
        toolbar.setNavigationOnClickListener {
            dismissKeyboard()
            if (drawerLayout.getDrawerLockMode(GravityCompat.START) == DrawerLayout.LOCK_MODE_UNLOCKED) {
                drawerLayout.openDrawer(GravityCompat.START)
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
                    listOf(plusBadge1, plusBadge2).forEach { badge ->
                        badge.setBackgroundTint(theme.theme)
                        badge.setTextColor(theme.textPrimary)
                    }
                    syncingProgress.indeterminateTintList = ColorStateList.valueOf(theme.theme)
                    plusIcon.setTint(theme.theme)
                    rateIcon.setTint(theme.theme)
                }

        // Set the hamburger icon color
        toggle.drawerArrowDrawable.color = resolveThemeColor(android.R.attr.textColorSecondary)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        val query = intent.extras?.getString("query") ?: ""
        val threadId = intent.extras?.getLong("threadId") ?: 0L
        val address = intent.data?.let {
            val data = it.toString()
            val scheme = it.scheme ?: ""
            when {
                scheme.startsWith("smsto") -> data.replace("smsto:", "")
                scheme.startsWith("mmsto") -> data.replace("mmsto:", "")
                scheme.startsWith("sms") -> data.replace("sms:", "")
                scheme.startsWith("mms") -> data.replace("mms:", "")
                else -> ""
            }
        }?.let { if (it.contains('%')) URLDecoder.decode(it, "UTF-8") else it }
                ?: ""// The dialer app on Oreo sends a URL encoded string, make sure to decode it
        val sharedText = intent.extras?.getString(Intent.EXTRA_TEXT) ?: ""
        val sharedAttachments = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                ?.plus(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                ?.map { Attachment(it) }
                ?: listOf()

        if (threadId != 0L || address.isNotEmpty() || sharedText.isNotEmpty() || sharedAttachments.isNotEmpty()) {
            pushController(RouterTransaction
                    .with(ComposeController(query, threadId, address, sharedText, sharedAttachments))
                    .pushChangeHandler(QkChangeHandler())
                    .popChangeHandler(QkChangeHandler()))
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumedSubject.onNext(Unit)
    }

    override fun onDestroy() {
        super.onDestroy()
        router.removeChangeListener(this)
    }

    override fun showBackButton(show: Boolean) {
        // Animate the toggle icon to the new position
        ObjectAnimator.ofFloat(toggle.drawerArrowDrawable, "progress", if (show) 1f else 0f).start()

        // Lock the drawer if we're showing a back button
        drawerLayout.setDrawerLockMode(when (show) {
            true -> DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            false -> DrawerLayout.LOCK_MODE_UNLOCKED
        }, GravityCompat.START)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (!router.popCurrentController()) {
            finish()
        }
    }

    private fun pushController(transaction: RouterTransaction) {
        if (::router.isInitialized) {
            router.pushController(transaction)
        } else {
            pendingRouterTransactions += transaction
        }
    }

    override fun render(state: MainState) {
        listOf(plusBadge1, plusBadge2).forEach { badge ->
            badge.isVisible = drawerBadgesExperiment.variant && !state.upgraded
        }
        plus.isVisible = state.upgraded
        plusBanner.isVisible = !state.upgraded
        rateLayout.setVisible(state.showRating)

        if (drawerLayout.isDrawerOpen(GravityCompat.START) && !state.drawerOpen) drawerLayout.closeDrawer(GravityCompat.START)
        else if (!drawerLayout.isDrawerVisible(GravityCompat.START) && state.drawerOpen) drawerLayout.openDrawer(GravityCompat.START)

        when (state.syncing) {
            is SyncRepository.SyncProgress.Idle -> {
                syncing.isVisible = false
                snackbar.isVisible = !state.defaultSms || !state.smsPermission || !state.contactPermission
            }

            is SyncRepository.SyncProgress.Running -> {
                syncing.isVisible = true
                syncingProgress.max = state.syncing.max
                progressAnimator.apply { setIntValues(syncingProgress.progress, state.syncing.progress) }.start()
                syncingProgress.isIndeterminate = state.syncing.indeterminate
                snackbar.isVisible = false
            }
        }

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
            backup.clicks().map { DrawerItem.BACKUP },
            scheduled.clicks().map { DrawerItem.SCHEDULED },
            blocking.clicks().map { DrawerItem.BLOCKING },
            settings.clicks().map { DrawerItem.SETTINGS },
            plus.clicks().map { DrawerItem.PLUS },
            help.clicks().map { DrawerItem.HELP },
            invite.clicks().map { DrawerItem.INVITE }))

    override fun plusBannerClicked(): Observable<*> = plusBanner.clicks()

    override fun ratingDismissed(): Observable<*> = rateDismiss.clicks()

    override fun ratingClicked(): Observable<*> = rateOkay.clicks()

    override fun snackbarClicked(): Observable<*> = snackbarButton.clicks()

    override fun backPressed(): Observable<*> = backPressedSubject

    override fun getRouter(): Router = router

    override fun requestPermissions() = ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS), 0)

    override fun onChangeStarted(to: Controller?, from: Controller?, isPush: Boolean, container: ViewGroup, handler: ControllerChangeHandler) {
        toolbar.menu.findItem(R.id.search)?.isVisible = to is ConversationsController || to is SearchController

        inbox.isActivated = to is ConversationsController && !to.archived
        archived.isActivated = to is ConversationsController && to.archived
    }

    override fun onChangeCompleted(to: Controller?, from: Controller?, isPush: Boolean, container: ViewGroup, handler: ControllerChangeHandler) = Unit

}
