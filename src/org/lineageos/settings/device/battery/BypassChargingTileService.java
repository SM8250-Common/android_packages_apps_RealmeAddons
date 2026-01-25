/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.battery;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import org.lineageos.settings.device.R;

public class BypassChargingTileService extends TileService
        implements BypassChargingController.StateChangeListener {

    private BypassChargingController mController;

    @Override
    public void onStartListening() {
        super.onStartListening();
        mController = BypassChargingController.getInstance(this);
        mController.registerListener(this);
        updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (mController != null) {
            mController.unregisterListener(this);
        }
    }

    @Override
    public void onStateChanged(boolean bypassEnabled, boolean powerConnected) {
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!BypassChargingUtils.isSupported()) {
            getQsTile().setState(Tile.STATE_UNAVAILABLE);
            getQsTile().updateTile();
            return;
        }

        if (!mController.isPowerConnected()) {
            Toast.makeText(this, R.string.bypass_charging_unavailable_summary, Toast.LENGTH_SHORT).show();
            updateTile();
            return;
        }

        boolean currentState = mController.isBypassEnabled();

        // If trying to enable but battery is below threshold, show warning
        if (!currentState && !mController.canActivateBypass()) {
            Toast.makeText(this,
                    getString(R.string.bypass_charging_low_battery,
                            mController.getThreshold()),
                    Toast.LENGTH_SHORT).show();
        }

        BypassChargingUtils.setEnabled(this, !currentState);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        if (!BypassChargingUtils.isSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(null);
        } else if (!mController.isPowerConnected()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(null);
        } else {
            boolean enabled = mController.isBypassEnabled();
            boolean paused = mController.isBypassPaused();

            if (enabled) {
                tile.setState(Tile.STATE_ACTIVE);
                if (paused) {
                    // Show that bypass is enabled but paused due to low battery
                    tile.setSubtitle(getString(R.string.bypass_charging_paused));
                } else {
                    tile.setSubtitle(null);
                }
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setSubtitle(null);
            }
        }
        tile.updateTile();
    }
}
