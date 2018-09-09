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

import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.moez.QKSMS.common.Navigator
import com.moez.QKSMS.common.RouterProvider
import com.moez.QKSMS.common.androidxcompat.scope
import com.moez.QKSMS.common.base.QkViewModel
import com.moez.QKSMS.common.util.BillingManager
import com.moez.QKSMS.feature.main.conversations.ConversationsController
import com.moez.QKSMS.feature.settings.SettingsController
import com.moez.QKSMS.interactor.MarkAllSeen
import com.moez.QKSMS.interactor.MigratePreferences
import com.moez.QKSMS.interactor.SyncMessages
import com.moez.QKSMS.manager.PermissionManager
import com.moez.QKSMS.manager.RatingManager
import com.moez.QKSMS.model.SyncLog
import com.moez.QKSMS.repository.SyncRepository
import com.uber.autodispose.kotlin.autoDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.realm.Realm
import javax.inject.Inject

class MainViewModel @Inject constructor(
        billingManager: BillingManager,
        markAllSeen: MarkAllSeen,
        migratePreferences: MigratePreferences,
        syncRepository: SyncRepository,
        private val navigator: Navigator,
        private val permissionManager: PermissionManager,
        private val routerProvider: RouterProvider,
        private val ratingManager: RatingManager,
        private val syncMessages: SyncMessages
) : QkViewModel<MainView, MainState>(MainState()) {

    private val router: Router get() = routerProvider.getRouter()

    init {
        disposables += markAllSeen
        disposables += migratePreferences
        disposables += syncMessages

        // Show the syncing UI
        disposables += syncRepository.syncProgress
                .distinctUntilChanged()
                .subscribe { syncing -> newState { copy(syncing = syncing) } }

        // Update the upgraded status
        disposables += billingManager.upgradeStatus
                .subscribe { upgraded -> newState { copy(upgraded = upgraded) } }

        // Show the rating UI
        disposables += ratingManager.shouldShowRating
                .subscribe { show -> newState { copy(showRating = show) } }


        // Migrate the preferences from 2.7.3
        migratePreferences.execute(Unit)


        // If we have all permissions and we've never run a sync, run a sync. This will be the case
        // when upgrading from 2.7.3, or if the app's data was cleared
        val lastSync = Realm.getDefaultInstance().use { realm -> realm.where(SyncLog::class.java)?.max("date") ?: 0 }
        if (lastSync == 0 && permissionManager.isDefaultSms() && permissionManager.hasReadSms() && permissionManager.hasContacts()) {
            syncMessages.execute(Unit)
        }

        ratingManager.addSession()
        markAllSeen.execute(Unit)
    }

    override fun bindView(view: MainView) {
        super.bindView(view)

        if (!permissionManager.hasReadSms() || !permissionManager.hasContacts()) {
            view.requestPermissions()
        }

        // If the default SMS state or permission states change, update the ViewState
        Observables.combineLatest(
                view.activityResumed().map { permissionManager.isDefaultSms() }.distinctUntilChanged(),
                view.activityResumed().map { permissionManager.hasReadSms() }.distinctUntilChanged(),
                view.activityResumed().map { permissionManager.hasContacts() }.distinctUntilChanged())
        { defaultSms, smsPermission, contactPermission ->
            newState { copy(defaultSms = defaultSms, smsPermission = smsPermission, contactPermission = contactPermission) }
        }
                .autoDisposable(view.scope())
                .subscribe()

        // If the SMS permission state changes from false to true, sync messages
        view.activityResumed()
                .map { permissionManager.hasReadSms() }
                .distinctUntilChanged()
                .skip(1)
                .filter { hasSms -> hasSms }
                .take(1)
                .autoDisposable(view.scope())
                .subscribe {
                    syncMessages.execute(Unit)
                    if (!permissionManager.isDefaultSms()) {
                        navigator.showDefaultSmsDialog()
                    }
                }

        view.drawerOpened()
                .autoDisposable(view.scope())
                .subscribe { open -> newState { copy(drawerOpen = open) } }

        view.drawerItemSelected()
                .doOnNext { newState { copy(drawerOpen = false) } }
                .doOnNext { if (it == DrawerItem.BACKUP) navigator.showBackup() }
                .doOnNext { if (it == DrawerItem.SCHEDULED) navigator.showScheduled() }
                .doOnNext { if (it == DrawerItem.BLOCKING) navigator.showBlockedConversations() }
                .doOnNext { if (it == DrawerItem.PLUS) navigator.showQksmsPlusActivity("main_menu") }
                .doOnNext { if (it == DrawerItem.HELP) navigator.showSupport() }
                .doOnNext { if (it == DrawerItem.INVITE) navigator.showInvite() }
                .autoDisposable(view.scope())
                .subscribe {
                    when (it) {
                        DrawerItem.INBOX -> router.replaceTopController(
                                RouterTransaction.with(ConversationsController())
                                        .popChangeHandler(FadeChangeHandler())
                                        .pushChangeHandler(FadeChangeHandler()))

                        DrawerItem.ARCHIVED -> router.replaceTopController(
                                RouterTransaction.with(ConversationsController(true))
                                        .popChangeHandler(FadeChangeHandler())
                                        .pushChangeHandler(FadeChangeHandler()))

                        DrawerItem.SETTINGS -> router.pushController(
                                RouterTransaction.with(SettingsController())
                                        .popChangeHandler(FadeChangeHandler())
                                        .pushChangeHandler(FadeChangeHandler()))
                    }
                }

        view.plusBannerClicked()
                .autoDisposable(view.scope())
                .subscribe {
                    newState { copy(drawerOpen = false) }
                    navigator.showQksmsPlusActivity("main_banner")
                }

        view.ratingClicked()
                .autoDisposable(view.scope())
                .subscribe {
                    navigator.showRating()
                    ratingManager.rate()
                }

        view.ratingDismissed()
                .autoDisposable(view.scope())
                .subscribe { ratingManager.dismiss() }

        view.snackbarClicked()
                .withLatestFrom(state) { _, state ->
                    when {
                        !state.smsPermission -> view.requestPermissions()
                        !state.defaultSms -> navigator.showDefaultSmsDialog()
                        !state.contactPermission -> view.requestPermissions()
                    }
                }
                .autoDisposable(view.scope())
                .subscribe()
    }

}