package com.saulnunez.compass.domain

/** Which views of the Track being read Practice mode shows; Edit mode follows the same choice. */
enum class ShownViews {
    TABLATURE,
    STANDARD_NOTATION,
    BOTH,
    ;

    /**
     * The views Edit mode shows for this Practice mode choice: the same, except that Notes are
     * written on the Tablature, so Standard notation alone gets the Tablature beside it.
     */
    fun inEditMode(): ShownViews = if (this == STANDARD_NOTATION) BOTH else this
}
