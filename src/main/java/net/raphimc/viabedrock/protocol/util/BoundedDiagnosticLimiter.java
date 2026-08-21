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
package net.raphimc.viabedrock.protocol.util;

import com.viaversion.viaversion.libs.fastutil.longs.LongOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.longs.LongSet;

/**
 * Keeps high-frequency diagnostics useful without allowing malformed or unsupported world data
 * to turn the network event loop into a synchronous logging workload.
 */
public final class BoundedDiagnosticLimiter {

    private final int maximumUniqueKeys;
    private final LongSet loggedKeys = new LongOpenHashSet();

    public BoundedDiagnosticLimiter(final int maximumUniqueKeys) {
        if (maximumUniqueKeys <= 0) {
            throw new IllegalArgumentException("maximumUniqueKeys must be positive");
        }
        this.maximumUniqueKeys = maximumUniqueKeys;
    }

    public boolean shouldLog(final long key) {
        return this.loggedKeys.size() < this.maximumUniqueKeys && this.loggedKeys.add(key);
    }

    public boolean isFull() {
        return this.loggedKeys.size() >= this.maximumUniqueKeys;
    }

}
