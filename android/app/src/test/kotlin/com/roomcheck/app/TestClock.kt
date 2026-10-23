package com.roomcheck.app

import com.roomcheck.app.data.AppViewModel
import com.roomcheck.app.data.NightStore
import java.time.LocalDateTime

/**
 * The evening every test's app takes as now: a Wednesday at 10:30, so tonight is one of the
 * standing-three nights and opens on its first round. Pinned so a test reads the same on whatever
 * day it runs - a Thursday would otherwise have 12:45 and 1:00 instead - and the screenshots do not
 * change every day for the date at the top.
 */
val PINNED_NOW: LocalDateTime = LocalDateTime.parse("2026-10-21T22:30")

fun testVm(store: NightStore, now: LocalDateTime = PINNED_NOW) = AppViewModel(store) { now }
