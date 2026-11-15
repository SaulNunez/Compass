package com.saulnunez.compass.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.saulnunez.compass.R

/** The top bar button that leaves a screen for the Library. */
@Composable
fun BackToLibraryButton(onBack: () -> Unit) {
    IconAction(R.drawable.ic_arrow_back, stringResource(R.string.back_to_library), onBack)
}
