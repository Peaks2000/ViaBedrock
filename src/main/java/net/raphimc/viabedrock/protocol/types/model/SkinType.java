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
package net.raphimc.viabedrock.protocol.types.model;

import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.SharedTypes_persona_PieceType;
import net.raphimc.viabedrock.protocol.model.SkinData;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class SkinType extends Type<SkinData> {

    private static final int MAX_SKIN_ARRAY_ENTRIES = 4_096;

    public SkinType() {
        super(SkinData.class);
    }

    @Override
    public SkinData read(ByteBuf buffer) {
        final String skinId = BedrockTypes.STRING.read(buffer);
        final String playFabId = BedrockTypes.STRING.read(buffer);
        final String skinResourcePatch = BedrockTypes.STRING.read(buffer);
        final BufferedImage skinData = BedrockTypes.IMAGE.read(buffer);

        // Protocol 2168 uses unsigned-varint array lengths and enum values here. Reading the
        // former fixed-width layout shifts the cursor into creator-skin pixels and eventually
        // treats arbitrary payload bytes as an image length.
        final int animationCount = readArrayLength(buffer, "skin animations");
        final List<SkinData.AnimationData> animations = new ArrayList<>(animationCount);
        for (int i = 0; i < animationCount; i++) {
            final BufferedImage image = BedrockTypes.IMAGE.read(buffer);
            final int type = BedrockTypes.UNSIGNED_VAR_INT.read(buffer);
            final float frames = buffer.readFloatLE();
            final int expression = BedrockTypes.UNSIGNED_VAR_INT.read(buffer);
            animations.add(new SkinData.AnimationData(image, type, frames, expression));
        }

        final BufferedImage capeData = BedrockTypes.IMAGE.read(buffer);
        final String geometryData = BedrockTypes.STRING.read(buffer);
        final String geometryDataEngineVersion = BedrockTypes.STRING.read(buffer);
        final String animationData = BedrockTypes.STRING.read(buffer);
        final String capeId = BedrockTypes.STRING.read(buffer);
        final String fullSkinId = BedrockTypes.STRING.read(buffer);

        final String armSize = buffer.readUnsignedByte() == 1 ? "Wide" : "Slim";
        final String skinColor = new Color(buffer.readIntLE(), true).toString();

        final int piecesLength = readArrayLength(buffer, "persona pieces");
        final List<SkinData.PersonaPieceData> personaPieces = new ArrayList<>(piecesLength);
        for (int i = 0; i < piecesLength; i++) {
            final String id = BedrockTypes.STRING.read(buffer);
            final int rawType = buffer.readIntLE();
            final SharedTypes_persona_PieceType pieceType = SharedTypes_persona_PieceType.getByValue(rawType);
            final String type = pieceType != null ? pieceType.name() : Integer.toString(rawType);
            final String packId = BedrockTypes.UUID.read(buffer).toString();
            final boolean defaultPiece = buffer.readBoolean();
            final String productId = BedrockTypes.STRING.read(buffer);
            personaPieces.add(new SkinData.PersonaPieceData(id, type, packId, defaultPiece, productId));
        }

        final int tintsLength = readArrayLength(buffer, "persona tint colors");
        final List<SkinData.PersonaPieceTintData> tintColors = new ArrayList<>(tintsLength);
        for (int i = 0; i < tintsLength; i++) {
            final String type = BedrockTypes.STRING.read(buffer);
            final List<String> colors = new ArrayList<>(4);
            for (int i2 = 0; i2 < 4; i2++) {
                colors.add(new Color(buffer.readIntLE(), true).toString());
            }
            tintColors.add(new SkinData.PersonaPieceTintData(type, colors));
        }

        final boolean premium = buffer.readBoolean();
        final boolean persona = buffer.readBoolean();
        final boolean capeOnClassic = buffer.readBoolean();
        final boolean primaryUser = buffer.readBoolean();
        final boolean overridingPlayerAppearance = buffer.readBoolean();

        // These fields are intentionally consumed even though the Java-facing SkinData model
        // does not expose them. Leaving either behind corrupts the following PLAYER_SKIN fields.
        BedrockTypes.STRING.read(buffer); // trusted flag
        BedrockTypes.STRING.read(buffer); // profile hash

        return new SkinData(skinId, playFabId, skinResourcePatch, skinData, animations, capeData, geometryData, geometryDataEngineVersion, animationData, premium, persona, capeOnClassic, primaryUser, capeId, fullSkinId, armSize, skinColor, personaPieces, tintColors, overridingPlayerAppearance);
    }

    private static int readArrayLength(final ByteBuf buffer, final String field) {
        final int length = BedrockTypes.UNSIGNED_VAR_INT.read(buffer);
        if (length < 0 || length > MAX_SKIN_ARRAY_ENTRIES) {
            throw new IllegalArgumentException("Invalid " + field + " length: " + Integer.toUnsignedString(length));
        }
        return length;
    }

    @Override
    public void write(ByteBuf buffer, SkinData value) { // TODO: I havent bothered updating this as it isnt used
        BedrockTypes.STRING.write(buffer, value.skinId());
        BedrockTypes.STRING.write(buffer, value.playFabId());
        BedrockTypes.STRING.write(buffer, value.skinResourcePatch());
        BedrockTypes.IMAGE.write(buffer, value.skinData());

        buffer.writeIntLE(value.animations().size());
        for (SkinData.AnimationData animation : value.animations()) {
            BedrockTypes.IMAGE.write(buffer, animation.image());
            buffer.writeIntLE(animation.type());
            buffer.writeFloatLE(animation.frames());
            buffer.writeIntLE(animation.expression());
        }

        BedrockTypes.IMAGE.write(buffer, value.capeData());
        BedrockTypes.STRING.write(buffer, value.geometryData());
        BedrockTypes.STRING.write(buffer, value.geometryDataEngineVersion());
        BedrockTypes.STRING.write(buffer, value.animationData());
        BedrockTypes.STRING.write(buffer, value.capeId());
        BedrockTypes.STRING.write(buffer, value.fullSkinId());
        BedrockTypes.STRING.write(buffer, value.armSize());
        BedrockTypes.STRING.write(buffer, value.skinColor());

        buffer.writeIntLE(value.personaPieces().size());
        for (SkinData.PersonaPieceData piece : value.personaPieces()) {
            BedrockTypes.STRING.write(buffer, piece.id());
            BedrockTypes.STRING.write(buffer, piece.type());
            BedrockTypes.STRING.write(buffer, piece.packId());
            buffer.writeBoolean(piece.defaultPiece());
            BedrockTypes.STRING.write(buffer, piece.productId());
        }

        buffer.writeIntLE(value.tintColors().size());
        for (SkinData.PersonaPieceTintData tint : value.tintColors()) {
            BedrockTypes.STRING.write(buffer, tint.type());
            buffer.writeIntLE(tint.colors().size());
            for (String color : tint.colors()) {
                BedrockTypes.STRING.write(buffer, color);
            }
        }

        buffer.writeBoolean(value.premium());
        buffer.writeBoolean(value.persona());
        buffer.writeBoolean(value.capeOnClassic());
        buffer.writeBoolean(value.primaryUser());
        buffer.writeBoolean(value.overridingPlayerAppearance());
    }

}
