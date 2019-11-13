/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
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
package com.moez.QKSMS.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class Interactor<in Params> {

    // TODO: Catch errors

    abstract suspend fun execute(params: Params)

    fun launch(
        params: Params,
        context: CoroutineContext = Dispatchers.Default,
        callback: ((Throwable?) -> Unit)? = null
    ) {
        val job = GlobalScope.launch(context) {
            execute(params)
        }

        if (callback != null) {
            job.invokeOnCompletion(callback)
        }
    }

}
