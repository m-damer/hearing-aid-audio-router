package com.mahmoud.audiorouter

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class RouteTile : TileService() {
    abstract val title: String
    abstract fun isHearing(): Boolean
    abstract fun toggle()

    override fun onStartListening() = refresh()

    override fun onClick() {
        toggle()
        refresh()
        val app = applicationContext
        Thread {
            if (Router.awaitBinder(3000)) Router.applyAll(app)
        }.start()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val hearing = isHearing()
        tile.label = title
        tile.subtitle = if (hearing) "السماعة الطبية" else "الجوال"
        tile.state = if (hearing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}

class MediaTile : RouteTile() {
    override val title = "الوسائط"
    override fun isHearing() = Prefs.mediaToHearing(this)
    override fun toggle() = Prefs.setMediaToHearing(this, !Prefs.mediaToHearing(this))
}

class CallTile : RouteTile() {
    override val title = "المكالمات"
    override fun isHearing() = Prefs.callToHearing(this)
    override fun toggle() = Prefs.setCallToHearing(this, !Prefs.callToHearing(this))
}
