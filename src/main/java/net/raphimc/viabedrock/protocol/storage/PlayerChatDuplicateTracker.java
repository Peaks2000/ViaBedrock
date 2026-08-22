/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.StorableObject;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class PlayerChatDuplicateTracker implements StorableObject {

    private static final long DEFAULT_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100L);

    private final long duplicateWindowNanos;
    private String lastSourceName;
    private String lastMessage;
    private boolean lastLocalized;
    private long lastAcceptedNanos;

    public PlayerChatDuplicateTracker() {
        this(DEFAULT_WINDOW_NANOS);
    }

    public PlayerChatDuplicateTracker(final long duplicateWindowNanos) {
        if (duplicateWindowNanos <= 0L) {
            throw new IllegalArgumentException("duplicateWindowNanos must be positive");
        }
        this.duplicateWindowNanos = duplicateWindowNanos;
    }

    public boolean shouldSuppress(final String sourceName, final String message, final boolean localized, final long nowNanos) {
        final boolean duplicate = this.lastSourceName != null
            && Objects.equals(this.lastSourceName, sourceName)
            && Objects.equals(this.lastMessage, message)
            && this.lastLocalized == localized
            && nowNanos >= this.lastAcceptedNanos
            && nowNanos - this.lastAcceptedNanos <= this.duplicateWindowNanos;
        if (!duplicate) {
            this.lastSourceName = sourceName;
            this.lastMessage = message;
            this.lastLocalized = localized;
            this.lastAcceptedNanos = nowNanos;
        }
        return duplicate;
    }

}
