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

package org.lineageos.settings.device.zram;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class ZramTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!ZramController.isZramSupported()) {
            getQsTile().setState(Tile.STATE_UNAVAILABLE);
            getQsTile().updateTile();
            return;
        }

        boolean currentState = ZramController.isZramEnabled();
        boolean newState = !currentState;

        if (ZramController.setZramEnabled(newState)) {
            updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        if (!ZramController.isZramSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            boolean enabled = ZramController.isZramEnabled();
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }

        tile.updateTile();
    }
}
