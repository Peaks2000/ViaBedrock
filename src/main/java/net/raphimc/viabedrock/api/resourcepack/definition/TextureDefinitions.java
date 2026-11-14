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
package net.raphimc.viabedrock.api.resourcepack.definition;

import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;

import java.util.*;
import java.util.logging.Level;

public class TextureDefinitions {

    private final Map<String, List<ItemTextureDefinition>> itemTextures = new HashMap<>();
    private final Map<String, List<ItemTextureDefinition>> terrainTextures = new HashMap<>();

    public TextureDefinitions(final ResourcePackStorage resourcePackStorage) {
        for (ResourcePack pack : resourcePackStorage.getPackStackBottomToTop()) {
            this.readAtlas(pack, "textures/item_texture.json", "atlas.items", this.itemTextures);
            this.readAtlas(pack, "textures/terrain_texture.json", "atlas.terrain", this.terrainTextures);
        }
    }

    private void readAtlas(final ResourcePack pack, final String path, final String atlas,
                           final Map<String, List<ItemTextureDefinition>> target) {
        if (!pack.content().contains(path)) {
            return;
        }
        try {
            final JsonObject definition = pack.content().getJson(path);
            if (definition.has("texture_name") && !atlas.equals(definition.get("texture_name").getAsString())) {
                return;
            }
            final JsonObject textureData = definition.getAsJsonObject("texture_data");
            if (textureData == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : textureData.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                final String name = entry.getKey();
                final JsonElement textures = entry.getValue().getAsJsonObject().get("textures");
                final List<ItemTextureDefinition> resolved = new ArrayList<>();
                if (textures != null && textures.isJsonArray()) {
                    for (JsonElement texture : textures.getAsJsonArray()) {
                        final String texturePath = texturePath(texture);
                        if (texturePath != null) {
                            resolved.add(new ItemTextureDefinition(name, texturePath));
                        }
                    }
                } else {
                    final String texturePath = texturePath(textures);
                    if (texturePath != null) {
                        resolved.add(new ItemTextureDefinition(name, texturePath));
                    }
                }
                if (!resolved.isEmpty()) {
                    target.put(name, List.copyOf(resolved));
                }
            }
        } catch (final RuntimeException e) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Failed to parse " + path + " in pack " + pack.key(), e);
        }
    }

    private static String texturePath(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            return texturePath(element.getAsJsonObject().get("path"));
        }
        return null;
    }

    public Map<String, List<ItemTextureDefinition>> itemTextures() {
        return Collections.unmodifiableMap(this.itemTextures);
    }

    public Map<String, List<ItemTextureDefinition>> terrainTextures() {
        return Collections.unmodifiableMap(this.terrainTextures);
    }

    public record ItemTextureDefinition(String name, String texturePath) {
    }

}
