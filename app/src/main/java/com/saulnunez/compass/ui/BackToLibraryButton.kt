package com.saulnunez.compass.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.saulnunez.compass.R

/** The top bar button that leaves a screen for the Library. */
@Composable
fun BackToLibraryButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_library)) }
}
