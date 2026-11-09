package com.mahmoud.audiorouter

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** The single Quick Settings tile: on = calls and media go to the hearing aid, off = everything from the phone. */
class HearingTile : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        Prefs.setToHearing(this, !Prefs.toHearing(this))
        refresh()
        val app = applicationContext
        Thread {
            if (Router.awaitBinder(3000)) Router.applyAll(app)
        }.start()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Prefs.toHearing(this)
        tile.label = "السماعة الطبية"
        tile.subtitle = if (on) "مفعّل" else "من الجوال"
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
