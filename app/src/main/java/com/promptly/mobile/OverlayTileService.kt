package com.promptly.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OverlayTileService : TileService() {

    private val prefs: SharedPreferences
        get() = getSharedPreferences("promptly", Context.MODE_PRIVATE)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        sendAction(OverlayService.ACTION_TOGGLE_OVERLAY)
        refresh()
    }

    private fun sendAction(action: String) {
        startForegroundService(
            Intent(this, OverlayService::class.java).apply { this.action = action }
        )
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val visible = prefs.getBoolean("overlay_visible", true)
        tile.state = if (visible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (visible) R.string.tile_hide_overlay else R.string.tile_show_overlay
        )
        tile.updateTile()
    }
}