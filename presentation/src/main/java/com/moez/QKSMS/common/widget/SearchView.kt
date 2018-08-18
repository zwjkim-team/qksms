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
package com.moez.QKSMS.common.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.CollapsibleActionView
import androidx.appcompat.widget.Toolbar
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.R
import com.moez.QKSMS.common.util.extensions.dismissKeyboard
import com.moez.QKSMS.common.util.extensions.showKeyboard
import io.reactivex.Observable
import kotlinx.android.synthetic.main.search_view.view.*

class SearchView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs), CollapsibleActionView {

    val queryChanged: Observable<CharSequence>

    init {
        View.inflate(context, R.layout.search_view, this)
        isSaveEnabled = true
        layoutParams = Toolbar.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, Toolbar.LayoutParams.MATCH_PARENT)
        orientation = LinearLayout.HORIZONTAL

        queryChanged = query.textChanges()
    }

    fun setText(text: CharSequence) {
        query.setText(text)
    }

    override fun onActionViewExpanded() {
        // Focus on the query field and display the keyboard
        query.showKeyboard()
    }

    override fun onActionViewCollapsed() {
        // Dismiss the keyboard
        (context as? Activity)?.dismissKeyboard()
    }

}