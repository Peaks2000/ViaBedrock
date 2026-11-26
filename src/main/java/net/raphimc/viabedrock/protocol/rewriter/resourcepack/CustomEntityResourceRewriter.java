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
package net.raphimc.viabedrock.protocol.rewriter.resourcepack;

import com.viaversion.viaversion.api.minecraft.item.data.ItemModel;
import com.viaversion.viaversion.util.Key;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.api.resourcepack.definition.EntityDefinitions;
import net.raphimc.viabedrock.api.util.StringUtil;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.converter.enums.RotationType;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.model.impl.java.JavaItemModel;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class CustomEntityResourceRewriter extends ItemModelResourceRewriter {

    private static final String SUB_FOLDER = "entities";
    public static final String LEGACY_MODEL_VARIANT = "replacement";

    // Old packs use Bedrock's built-in model and atlas names without a client_entity JSON.
    private static final Map<String, LegacyBinding> LEGACY_MOBS = Map.ofEntries(
        legacy("zombie_pigman", "pigzombie", "pig/pigzombie"),
        legacy("drowned", "zombie.drowned", "zombie/drowned"),
        legacy("husk", "zombie.husk", "zombie/husk"),
        legacy("zombie", "zombie", "zombie/zombie"),
        legacy("skeleton", "skeleton", "skeleton/skeleton"),
        legacy("stray", "skeleton.stray", "skeleton/stray"),
        legacy("wither_skeleton", "skeleton.wither", "skeleton/wither_skeleton"),
        legacy("vindicator", "vindicator", "vindicator"),
        legacy("witch", "witch", "witch"),
        legacy("chicken", "chicken", "chicken"),
        legacy("spider", "spider", "spider/spider"),
        legacy("cave_spider", "spider", "spider/cave_spider")
    );

    public static ItemModel getItemModel(final String entityIdentifier) {
        return new ItemModel(Key.of("viabedrock", SUB_FOLDER + '/' + StringUtil.makeIdentifierValueSafe(entityIdentifier)));
    }

    public CustomEntityResourceRewriter() {
        super(SUB_FOLDER);
    }

    @Override
    public void submitTasks(final ResourcePackStorage resourcePackStorage, final Consumer<Supplier<Content>> submitter) {
        for (EntityDefinitions.EntityDefinition entityDefinition : resourcePackStorage.getEntities().entities().values()) {
            submitter.accept(() -> this.handleEntityDefinition(resourcePackStorage, entityDefinition));
        }
        LEGACY_MOBS.forEach((identifier, binding) -> {
            final var replacement = resourcePackStorage.getModels().getLegacyMobModel(binding.geometry());
            if (replacement == null) {
                return;
            }
            final Content.LazyImage image = replacement.pack().content().getShortnameImage(binding.texture());
            if (image != null) {
                submitter.accept(() -> this.convertLegacyModel(resourcePackStorage, identifier, replacement.geometry(), image));
            }
        });
    }

    private Content convertLegacyModel(final ResourcePackStorage storage, final String identifier,
                                       final BedrockGeometryModel geometry, final Content.LazyImage image) {
        final String key = "legacy_" + identifier;
        final ItemModelContent content = new ItemModelContent(key);
        try {
            final String texture = this.getJavaTexturePath("legacy/" + identifier);
            final JavaItemModel model = geometry.toJavaItemModel("viabedrock:" + texture, RotationType.POST_1_21_11);
            final var json = model.compile();
            if (!json.has("elements") || json.getAsJsonArray("elements").isEmpty()
                || !Float.isFinite(model.getScale()) || model.getScale() <= 0F) {
                return new ItemModelContent(key);
            }
            content.putPngImage("assets/viabedrock/textures/" + texture + ".png", image);
            content.putModel(LEGACY_MODEL_VARIANT, json);
            content.generateItemDefinition();
            storage.getConverterData().put(key, new LegacyEntityModel(getItemModel(key), model.getScale()));
        } catch (RuntimeException e) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Failed to convert legacy model for " + identifier, e);
            return new ItemModelContent(key);
        }
        return content;
    }

    public static LegacyEntityModel legacyModel(final ResourcePackStorage storage, final String identifier) {
        if (!storage.isLoadedOnJavaClient()) {
            return null;
        }
        return (LegacyEntityModel) storage.getConverterData().get("legacy_" + identifier);
    }

    private static Map.Entry<String, LegacyBinding> legacy(final String mob, final String geometry, final String texture) {
        return Map.entry("minecraft:" + mob, new LegacyBinding("geometry." + geometry, "textures/entity/" + texture));
    }

    private record LegacyBinding(String geometry, String texture) {
    }

    public record LegacyEntityModel(ItemModel itemModel, float scale) {
    }

    private Content handleEntityDefinition(final ResourcePackStorage resourcePackStorage, final EntityDefinitions.EntityDefinition entityDefinition) {
        final ItemModelContent javaContent = new ItemModelContent(entityDefinition.identifier());
        for (String bedrockPath : entityDefinition.entityData().getTextures().values()) {
            for (ResourcePack pack : resourcePackStorage.getPackStackTopToBottom()) {
                final Content.LazyImage texture = pack.content().getShortnameImage(bedrockPath);
                if (texture != null) {
                    javaContent.putPngImage("assets/viabedrock/textures/" + this.getJavaTexturePath(bedrockPath) + ".png", texture);
                    break;
                }
            }
        }
        for (Map.Entry<String, String> modelEntry : entityDefinition.entityData().getGeometries().entrySet()) {
            final BedrockGeometryModel bedrockGeometry = resourcePackStorage.getModels().entityModels().get(modelEntry.getValue());
            if (bedrockGeometry != null) {
                for (Map.Entry<String, String> textureEntry : entityDefinition.entityData().getTextures().entrySet()) {
                    final String modelKey = modelEntry.getKey() + "_" + textureEntry.getKey();
                    final JavaItemModel itemModelData = bedrockGeometry.toJavaItemModel("viabedrock:" + this.getJavaTexturePath(textureEntry.getValue()), RotationType.POST_1_21_11);
                    javaContent.putModel(modelKey, itemModelData.compile());
                    resourcePackStorage.getConverterData().put("ce_" + entityDefinition.identifier() + '_' + modelKey + "_scale", itemModelData.getScale());
                }
            }
        }
        javaContent.generateItemDefinition();
        return javaContent;
    }

}
