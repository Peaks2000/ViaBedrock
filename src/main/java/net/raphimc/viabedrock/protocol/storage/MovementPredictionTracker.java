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

    private static final float CORRECTION_EPSILON = 0.0001F;

    private final NavigableMap<Long, Snapshot> snapshots = new TreeMap<>();

    private long lastPositionSyncTick = -1;

    public void reset(final long tick) {
        this.snapshots.clear();
        this.lastPositionSyncTick = tick;
    }

    public boolean acceptsCorrection(final long tick, final long currentTick, final int rewindHistorySize) {
        return tick > this.lastPositionSyncTick && tick <= currentTick && tick >= currentTick - rewindHistorySize;
    }

    public void record(final long tick, final Position3f position, final Position3f movement, final boolean onGround,
                       final boolean horizontalCollision, final boolean continuousVerticalMovement,
                       final int rewindHistorySize) {
        this.snapshots.put(tick, new Snapshot(
            position, movement, onGround, horizontalCollision, continuousVerticalMovement, false, false
        ));

        // A rewind size of N permits the current tick and N earlier ticks.
        final int retainedSnapshots = Math.max(1, rewindHistorySize + 1);
        while (this.snapshots.size() > retainedSnapshots) {
            this.snapshots.pollFirstEntry();
        }
    }

    /**
     * Replays a correction by applying its delta at the referenced tick to later predictions.
     */
    public Correction replay(final long tick, final Position3f authoritativePosition, final Position3f authoritativeVelocity,
                             final boolean authoritativeOnGround, final long currentTick, final Position3f currentPosition,
                             final boolean currentOnGround, final boolean currentHorizontalCollision, final boolean verticalStabilizationInput,
                             final boolean currentContinuousVerticalMovement) {
        final Snapshot correctedSnapshot = this.snapshots.get(tick);
        if (correctedSnapshot == null || tick > currentTick) {
            return new Correction(authoritativePosition, authoritativeOnGround, false, false, false, false);
        }

        final boolean historical = tick < currentTick;
        final Position3f correctionDelta = authoritativePosition.subtract(correctedSnapshot.position());
        final Position3f laterMovement = currentPosition.subtract(correctedSnapshot.position());
        final boolean collisionReplay = correctedSnapshot.horizontalCollision() || currentHorizontalCollision;

        // On a shape Java predicts differently (such as a pane), Bedrock can stop a crouched
        // player before Java reports any collision. A zero server velocity and an offset
        // opposing the movement sent for that tick identify the rejected axis.
        final boolean blockedX = verticalStabilizationInput && rejectedMovement(correctedSnapshot.movement().x(),
            correctionDelta.x(), authoritativeVelocity.x(), laterMovement.x());
        final boolean blockedZ = verticalStabilizationInput && rejectedMovement(correctedSnapshot.movement().z(),
            correctionDelta.z(), authoritativeVelocity.z(), laterMovement.z());
        final boolean correctedX = collisionReplay && Math.abs(correctionDelta.x()) > CORRECTION_EPSILON;
        final boolean correctedZ = collisionReplay && Math.abs(correctionDelta.z()) > CORRECTION_EPSILON;
        final boolean stabilizeX = blockedX || (collisionReplay && (correctedSnapshot.correctedX() || correctedX));
        final boolean stabilizeZ = blockedZ || (collisionReplay && (correctedSnapshot.correctedZ() || correctedZ));

        final boolean laterVerticalDescent = laterMovement.y() < -CORRECTION_EPSILON;
        final boolean laterNonAscendingPosition = laterMovement.y() <= CORRECTION_EPSILON;
        final boolean continuousVerticalMovement = correctedSnapshot.continuousVerticalMovement()
            || currentContinuousVerticalMovement;
        // A pre-jump ground correction must not shift a crouch jump. An airborne correction
        // must not move a later grounded prediction through its new support (for example a
        // bridge block). Corrections for the current tick always use the server height.
        final boolean crouchTakeoff = verticalStabilizationInput && correctedSnapshot.onGround() && authoritativeOnGround && !currentOnGround;
        final boolean movedHorizontally = Math.abs(laterMovement.x()) > CORRECTION_EPSILON
            || Math.abs(laterMovement.z()) > CORRECTION_EPSILON;
        final boolean laterLanding = currentOnGround && (!correctedSnapshot.onGround()
            || (movedHorizontally && !authoritativeOnGround && correctionDelta.y() < -CORRECTION_EPSILON));
        final boolean stabilizeVertical = historical && (continuousVerticalMovement
            || (laterVerticalDescent && currentOnGround) || laterLanding
            || (laterNonAscendingPosition && verticalStabilizationInput) || crouchTakeoff);

        this.snapshots.tailMap(tick, true).replaceAll((snapshotTick, snapshot) -> {
            final Position3f position = snapshot.position().add(correctionDelta);
            final Position3f replayed = snapshotTick == tick ? authoritativePosition : new Position3f(
                stabilizeX ? authoritativePosition.x() : position.x(),
                stabilizeVertical ? snapshot.position().y() : position.y(),
                stabilizeZ ? authoritativePosition.z() : position.z()
            );
            return new Snapshot(replayed, snapshot.movement(),
                snapshotTick == tick ? authoritativeOnGround : snapshot.onGround(),
                snapshot.horizontalCollision(), snapshot.continuousVerticalMovement(),
                snapshot.correctedX() || correctedX, snapshot.correctedZ() || correctedZ);
        });

        final Position3f replayedPosition = new Position3f(
            stabilizeX ? authoritativePosition.x() : currentPosition.x() + correctionDelta.x(),
            stabilizeVertical ? currentPosition.y() : currentPosition.y() + correctionDelta.y(),
            stabilizeZ ? authoritativePosition.z() : currentPosition.z() + correctionDelta.z()
        );

        // An older correction must not overwrite the current collision state. A correction
        // for the current tick, however, carries the authoritative on-ground value for it.
        final boolean replayedOnGround = tick == currentTick ? authoritativeOnGround : currentOnGround;
        return new Correction(replayedPosition, replayedOnGround, true,
            stabilizeX && Math.abs(authoritativeVelocity.x()) <= CORRECTION_EPSILON,
            stabilizeZ && Math.abs(authoritativeVelocity.z()) <= CORRECTION_EPSILON, stabilizeVertical);
    }

    private static boolean rejectedMovement(final float movement, final float correction, final float velocity,
                                            final float laterMovement) {
        final float direction = Math.signum(movement);
        return Math.abs(movement) > CORRECTION_EPSILON
            && correction * direction < -CORRECTION_EPSILON
            && laterMovement * direction >= -CORRECTION_EPSILON
            && Math.abs(velocity) <= CORRECTION_EPSILON;
    }

    public record Correction(Position3f position, boolean onGround, boolean replayed, boolean stopX, boolean stopZ,
                             boolean preserveVertical) {

        public boolean changesClientMovement(final Position3f currentPosition, final boolean currentOnGround) {
            return this.stopX || this.stopZ || this.onGround != currentOnGround
                || this.position.distanceTo(currentPosition) > CORRECTION_EPSILON;
        }
    }

    private record Snapshot(Position3f position, Position3f movement, boolean onGround, boolean horizontalCollision,
                            boolean continuousVerticalMovement, boolean correctedX, boolean correctedZ) {
    }

}
