/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device.display;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.device.R;

public class HBMTileService extends TileService {

    private static final String TAG = "HBMTileService";
    private static final String HBM_MODE_KEY = "hbm_mode";
    private static final String AUTO_HBM_KEY = "auto_hbm";

    private static final int STATE_OFF = 0;
    private static final int STATE_ON = 1;
    private static final int STATE_AUTO = 2;

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        int currentState = getCurrentState();
        int nextState = getNextState(currentState);

        applyState(nextState);
        updateTile();
    }

    private int getCurrentState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hbmEnabled = prefs.getBoolean(HBM_MODE_KEY, false);
        boolean autoHBMEnabled = prefs.getBoolean(AUTO_HBM_KEY, false);

        if (autoHBMEnabled) {
            return STATE_AUTO;
        } else if (hbmEnabled) {
            return STATE_ON;
        } else {
            return STATE_OFF;
        }
    }

    private int getNextState(int currentState) {
        switch (currentState) {
            case STATE_OFF:
                return STATE_ON;
            case STATE_ON:
                return STATE_AUTO;
            case STATE_AUTO:
            default:
                return STATE_OFF;
        }
    }

    private void applyState(int state) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        Intent serviceIntent = new Intent(this, AutoHBMService.class);

        switch (state) {
            case STATE_OFF:
                editor.putBoolean(HBM_MODE_KEY, false);
                editor.putBoolean(AUTO_HBM_KEY, false);
                editor.apply();
                HBMController.setHBMEnabled(false);
                stopService(serviceIntent);
                Log.d(TAG, "HBM turned OFF");
                break;

            case STATE_ON:
                editor.putBoolean(HBM_MODE_KEY, true);
                editor.putBoolean(AUTO_HBM_KEY, false);
                editor.apply();
                HBMController.setHBMEnabled(true);
                stopService(serviceIntent);
                Log.d(TAG, "HBM turned ON");
                break;

            case STATE_AUTO:
                editor.putBoolean(HBM_MODE_KEY, false);
                editor.putBoolean(AUTO_HBM_KEY, true);
                editor.apply();
                HBMController.setHBMEnabled(false);
                startService(serviceIntent);
                Log.d(TAG, "Auto HBM enabled");
                break;
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        int currentState = getCurrentState();

        switch (currentState) {
            case STATE_OFF:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel(getString(R.string.hbm_tile_label));
                tile.setSubtitle(getString(R.string.hbm_tile_state_off));
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_hbm_off));
                break;

            case STATE_ON:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel(getString(R.string.hbm_tile_label));
                tile.setSubtitle(getString(R.string.hbm_tile_state_on));
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_hbm_on));
                break;

            case STATE_AUTO:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel(getString(R.string.hbm_tile_label));
                tile.setSubtitle(getString(R.string.hbm_tile_state_auto));
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_hbm_auto));
                break;
        }

        tile.updateTile();
    }
}
