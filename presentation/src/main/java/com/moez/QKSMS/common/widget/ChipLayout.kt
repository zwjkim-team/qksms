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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.CollapsibleActionView
import com.google.android.flexbox.FlexboxLayoutManager
import com.moez.QKSMS.R
import com.moez.QKSMS.feature.compose.ChipsAdapter
import com.moez.QKSMS.injection.appComponent
import com.moez.QKSMS.model.Contact
import kotlinx.android.synthetic.main.chip_layout.view.*
import javax.inject.Inject

class ChipLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs), CollapsibleActionView {

    @Inject lateinit var adapter: ChipsAdapter

    val textChanges by lazy { adapter.textChanges }
    val backspaces by lazy { adapter.backspaces }
    val actions by lazy { adapter.actions }
    val chipDeleted by lazy { adapter.chipDeleted }

    init {
        appComponent.inject(this)

        View.inflate(context, R.layout.chip_layout, this)
        orientation = LinearLayout.HORIZONTAL

        adapter.view = chips

        chips.itemAnimator = null
        chips.layoutManager = FlexboxLayoutManager(context)
    }

    fun setChips(contacts: List<Contact>) {
        adapter.data = contacts
    }

    override fun onActionViewExpanded() {
        layoutParams = layoutParams.apply {
            width = LayoutParams.MATCH_PARENT
            height = LayoutParams.WRAP_CONTENT
        }

        chips.adapter = adapter
    }

    override fun onActionViewCollapsed() {
        chips.adapter = null
    }

}