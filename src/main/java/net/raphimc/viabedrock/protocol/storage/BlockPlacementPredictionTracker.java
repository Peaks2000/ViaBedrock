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
import com.viaversion.viaversion.api.minecraft.BlockPosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class BlockPlacementPredictionTracker implements StorableObject {

    public static final long DEFAULT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2L);

    private final long timeoutNanos;
    private final Deque<PendingPlacement> pendingPlacements = new ArrayDeque<>();
    private int highestRequestedAcknowledgement;
    private int lastSentAcknowledgement;

    public BlockPlacementPredictionTracker() {
        this(DEFAULT_TIMEOUT_NANOS);
    }

    public BlockPlacementPredictionTracker(final long timeoutNanos) {
        if (timeoutNanos <= 0L) {
            throw new IllegalArgumentException("timeoutNanos must be positive");
        }
        this.timeoutNanos = timeoutNanos;
    }

    public void track(final int sequence, final BlockPosition clickedPosition,
                      final BlockPosition expectedUpdatePosition, final long nowNanos) {
        this.track(sequence, clickedPosition, expectedUpdatePosition, nowNanos, PredictionKind.PLACEMENT);
    }

    private void track(final int sequence, final BlockPosition clickedPosition,
                       final BlockPosition expectedUpdatePosition, final long nowNanos,
                       final PredictionKind kind) {
        if (sequence <= this.lastSentAcknowledgement) {
            return;
        }
        for (PendingPlacement pending : this.pendingPlacements) {
            if (pending.sequence == sequence) {
                return;
            }
        }
        this.pendingPlacements.addLast(new PendingPlacement(
            sequence, clickedPosition, expectedUpdatePosition, nowNanos, kind
        ));
    }

    /**
     * Block breaking uses the same cumulative Java acknowledgement protocol as placement,
     * but only the broken position can confirm or resynchronize the prediction.
     */
    public void trackBreaking(final int sequence, final BlockPosition position, final long nowNanos) {
        this.track(sequence, position, position, nowNanos, PredictionKind.BREAKING);
    }

    /**
     * Confirms only the outcome Java predicted. Bedrock can reassert the original solid
     * block while its server-authoritative break is still in progress; that is not a break
     * confirmation and must not release Java's cumulative acknowledgement.
     */
    public boolean confirm(final BlockPosition position, final boolean authoritativeAir) {
        boolean matched = false;
        for (PendingPlacement pending : this.pendingPlacements) {
            if (!pending.resolved && pending.matches(position) && pending.accepts(authoritativeAir)) {
                pending.resolved = true;
                matched = true;
            }
        }
        return matched;
    }

    public boolean isPendingBreaking(final BlockPosition position) {
        for (PendingPlacement pending : this.pendingPlacements) {
            if (!pending.resolved && pending.kind == PredictionKind.BREAKING && pending.matches(position)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldSuppressBreakingReassertion(final BlockPosition position, final boolean authoritativeAir) {
        return !authoritativeAir && this.isPendingBreaking(position);
    }

    public int requestAcknowledgement(final int sequence) {
        this.highestRequestedAcknowledgement = Math.max(this.highestRequestedAcknowledgement, sequence);
        // Java acknowledgements are cumulative, so never pass an unresolved placement sequence.
        return this.pollAcknowledgement();
    }

    public int pollAcknowledgement() {
        while (!this.pendingPlacements.isEmpty() && this.pendingPlacements.peekFirst().resolved) {
            this.pendingPlacements.removeFirst();
        }

        int deliverable = this.highestRequestedAcknowledgement;
        if (!this.pendingPlacements.isEmpty()) {
            deliverable = Math.min(deliverable, this.pendingPlacements.peekFirst().sequence - 1);
        }
        if (deliverable <= this.lastSentAcknowledgement) {
            return -1;
        }

        this.lastSentAcknowledgement = deliverable;
        return deliverable;
    }

    public Expiration expire(final long nowNanos) {
        final Set<BlockPosition> resyncPositions = new LinkedHashSet<>();
        for (PendingPlacement pending : this.pendingPlacements) {
            if (!pending.resolved && nowNanos - pending.startedAtNanos >= this.timeoutNanos) {
                pending.resolved = true;
                resyncPositions.add(pending.clickedPosition);
                resyncPositions.add(pending.expectedUpdatePosition);
            }
        }
        return new Expiration(new ArrayList<>(resyncPositions));
    }

    public record Expiration(List<BlockPosition> resyncPositions) {

        public Expiration {
            resyncPositions = List.copyOf(resyncPositions);
        }
    }

    private static final class PendingPlacement {

        private final int sequence;
        private final BlockPosition clickedPosition;
        private final BlockPosition expectedUpdatePosition;
        private final long startedAtNanos;
        private final PredictionKind kind;
        private boolean resolved;

        private PendingPlacement(final int sequence, final BlockPosition clickedPosition,
                                 final BlockPosition expectedUpdatePosition, final long startedAtNanos,
                                 final PredictionKind kind) {
            this.sequence = sequence;
            this.clickedPosition = clickedPosition;
            this.expectedUpdatePosition = expectedUpdatePosition;
            this.startedAtNanos = startedAtNanos;
            this.kind = kind;
        }

        private boolean matches(final BlockPosition position) {
            return this.expectedUpdatePosition.equals(position);
        }

        private boolean accepts(final boolean authoritativeAir) {
            return this.kind == PredictionKind.BREAKING ? authoritativeAir : !authoritativeAir;
        }
    }

    private enum PredictionKind {
        PLACEMENT,
        BREAKING
    }
}
