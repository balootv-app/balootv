package com.nuvio.tv.ui.theme

import androidx.annotation.DrawableRes
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppTheme

/** BalooTV: un único logo (la tele con las orejas de Balú), sea cual sea el tema heredado. */
@get:DrawableRes
@Suppress("UnusedReceiverParameter")
val AppTheme.brandWordmarkResource: Int
    get() = R.drawable.balootv_logo
