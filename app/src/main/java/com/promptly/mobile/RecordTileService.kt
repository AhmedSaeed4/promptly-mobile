package com.promptly.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecordTileService : TileService() {

    private val prefs: SharedPreferences
        get() = getSharedPreferences("promptly", Context.MODE_PRIVATE)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        sendAction(OverlayService.ACTION_TOGGLE_RECORDING)
        refresh()
    }

    private fun sendAction(action: String) {
        startForegroundService(
            Intent(this, OverlayService::class.java).apply { this.action = action }
        )
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val recording = prefs.getBoolean("recording", false)
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (recording) R.string.tile_stop_record else R.string.tile_start_record
        )
        tile.updateTile()
    }
}