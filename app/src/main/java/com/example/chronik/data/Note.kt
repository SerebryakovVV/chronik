package com.example.chronik.data

/** A note's stable identity stays the same when its text or position changes. */
data class Note(
    val id: String,
    val text: String,
)
