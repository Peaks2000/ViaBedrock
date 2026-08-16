/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.storage;

import net.raphimc.viabedrock.protocol.model.Position3f;

import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Keeps the positions sent in PlayerAuthInput packets so Bedrock corrections can be
 * replayed at their original tick instead of snapping the current Java prediction back
 * to a historical position.
 */
public final class MovementPredictionTracker {

    private static final float AXIS_CORRECTION_EPSILON = 0.0001F;

    private final NavigableMap<Long, Snapshot> snapshots = new TreeMap<>();

    public void record(final long tick, final Position3f position, final boolean onGround,
                       final boolean horizontalCollision, final int rewindHistorySize) {
        this.snapshots.put(tick, new Snapshot(position, onGround, horizontalCollision, false, false));

        // A rewind size of N permits the current tick and N earlier ticks.
        final int retainedSnapshots = Math.max(1, rewindHistorySize + 1);
        while (this.snapshots.size() > retainedSnapshots) {
            this.snapshots.pollFirstEntry();
        }
    }

    /**
     * Replays a correction by applying its delta at the referenced tick to every later
     * prediction, including the position currently shown by Java.
     */
    public Correction replay(final long tick, final Position3f authoritativePosition, final boolean authoritativeOnGround,
                             final long currentTick, final Position3f currentPosition, final boolean currentOnGround,
                             final boolean currentHorizontalCollision, final boolean verticalStabilizationInput) {
        final Snapshot correctedSnapshot = this.snapshots.get(tick);
        if (correctedSnapshot == null || tick > currentTick) {
            return new Correction(authoritativePosition, authoritativeOnGround, false);
        }

        final Position3f correctionDelta = authoritativePosition.subtract(correctedSnapshot.position());
        final boolean collisionReplay = correctedSnapshot.horizontalCollision() || currentHorizontalCollision;
        final boolean correctedX = collisionReplay && Math.abs(correctionDelta.x()) > AXIS_CORRECTION_EPSILON;
        final boolean correctedZ = collisionReplay && Math.abs(correctionDelta.z()) > AXIS_CORRECTION_EPSILON;
        this.snapshots.tailMap(tick, true).replaceAll((snapshotTick, snapshot) ->
            new Snapshot(
                snapshot.position().add(correctionDelta), snapshot.onGround(), snapshot.horizontalCollision(),
                snapshot.correctedX() || correctedX, snapshot.correctedZ() || correctedZ
            )
        );

        // Java resolves wall collisions locally and can already have walked back up to the wall
        // by the time Bedrock corrects an older input tick. Adding that old horizontal delta to
        // the newer Java position makes every correction push the player away again, producing
        // an oscillating backward/forward loop while a movement key is held. Remember and settle
        // only the axis Bedrock actually corrected: settling both axes discards valid tangential
        // movement and snaps the player backwards while walking alongside a wall. A corner
        // correction can still settle both axes. Keep replaying Y so a later jump or step is not
        // discarded.
        final boolean stabilizeX = collisionReplay && (correctedSnapshot.correctedX() || correctedX);
        final boolean stabilizeZ = collisionReplay && (correctedSnapshot.correctedZ() || correctedZ);
        // Replaying an old Y correction onto a newer downward position makes slopes,
        // crouched block edges, and creative-flight descent snap on every rewind. Grounded
        // movement retains the established measurable-descent guard. Java can briefly clear
        // onGround at a crouched edge, and consecutive flight packets can carry the same Y,
        // so Shift additionally stabilizes a non-ascending position in both modes. This remains
        // historical-only: same-tick corrections stay authoritative, while a genuinely newer
        // upward position still replays the delta so jumps and steps are preserved.
        final boolean laterVerticalDescent = tick < currentTick
            && currentPosition.y() < correctedSnapshot.position().y() - 0.0001F;
        final boolean laterNonAscendingPosition = tick < currentTick
            && currentPosition.y() <= correctedSnapshot.position().y() + 0.0001F;
        final boolean stabilizeVertical = (laterVerticalDescent && currentOnGround)
            || (laterNonAscendingPosition && verticalStabilizationInput);
        final Position3f replayedPosition = new Position3f(
            stabilizeX ? authoritativePosition.x() : currentPosition.x() + correctionDelta.x(),
            stabilizeVertical ? currentPosition.y() : currentPosition.y() + correctionDelta.y(),
            stabilizeZ ? authoritativePosition.z() : currentPosition.z() + correctionDelta.z()
        );

        // An older correction must not overwrite the current collision state. A correction
        // for the current tick, however, carries the authoritative on-ground value for it.
        final boolean replayedOnGround = tick == currentTick ? authoritativeOnGround : currentOnGround;
        return new Correction(replayedPosition, replayedOnGround, true);
    }

    public record Correction(Position3f position, boolean onGround, boolean replayed) {
    }

    private record Snapshot(Position3f position, boolean onGround, boolean horizontalCollision,
                            boolean correctedX, boolean correctedZ) {
    }
}
