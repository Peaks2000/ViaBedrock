/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.rewriter.resourcepack;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.viaversion.api.minecraft.item.data.ItemModel;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.util.Key;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.api.resourcepack.content.InMemoryContent;
import net.raphimc.viabedrock.api.resourcepack.definition.BlockDefinitions.BlockDefinition;
import net.raphimc.viabedrock.api.util.MoLangEngine;
import net.raphimc.viabedrock.api.util.StringUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ResourcePackRewriter;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage.CustomBlockModel;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.converter.enums.RotationType;
import org.cube.converter.util.element.Direction;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CustomBlockResourceRewriter extends ItemModelResourceRewriter {

    private static final int MAX_NETWORK_MODELS = 16_384;
    private static final String FOLDER = "custom_blocks";
    private static final Map<String, String> BEDROCK_FACES = Map.of(
        "down", "down", "up", "up", "north", "north", "south", "south", "west", "west", "east", "east");

    public CustomBlockResourceRewriter() {
        super(FOLDER);
    }

    public static ItemModel getItemModel(final String identifier) {
        return new ItemModel(Key.of("viabedrock", FOLDER + '/' + StringUtil.makeIdentifierValueSafe(identifier)));
    }

    public static ItemModel getNetworkItemModel(final String identifier) {
        return getItemModel("network/" + identifier);
    }

    public static Content convertNetworkBlocks(final ResourcePackStorage packs, final BlockProperties[] definitions,
                                               final BlockStateRewriter rewriter) {
        final var converter = new CustomBlockResourceRewriter();
        final Content merged = new InMemoryContent();
        final Set<String> seen = new HashSet<>();
        int models = 0;
        for (final BlockProperties definition : definitions) {
            final String identifier = definition.name().toLowerCase(Locale.ROOT);
            if (identifier.startsWith("minecraft:") || !seen.add(identifier)) continue;
            final var states = rewriter.validBlockStates(identifier);
            if (states == null) continue;
            final CompoundTag base = definition.properties().getCompoundTag("components");
            final List<Permutation> permutations = permutations(definition.properties());
            if (base == null && permutations.isEmpty()) continue;

            final ItemModelContent output = converter.new ItemModelContent("network/" + identifier);
            final Map<CompoundTag, CustomBlockModel> variants = new HashMap<>();
            for (final int stateId : states) {
                final CompoundTag components = effectiveComponents(base, permutations, rewriter.blockState(stateId));
                final CompoundTag renderComponents = renderComponents(components);
                CustomBlockModel model = variants.get(renderComponents);
                if (model == null && !variants.containsKey(renderComponents)) {
                    if (models >= MAX_NETWORK_MODELS) break;
                    try {
                        model = converter.addNetworkModel(packs, output, identifier, renderComponents, "state_" + variants.size());
                    } catch (final RuntimeException ignored) {
                        // A malformed optional model falls back to the first resource pack.
                    }
                    variants.put(renderComponents, model);
                    if (model != null) models++;
                }
                if (model != null) packs.addCustomBlockModel(stateId, model);
            }
            if (variants.values().stream().anyMatch(java.util.Objects::nonNull)) {
                output.generateItemDefinition();
                merged.putAll(output);
            }
            if (models >= MAX_NETWORK_MODELS) break;
        }
        if (models == 0) return null;
        merged.putJson("pack.mcmeta", ResourcePackRewriter.createPackManifest());
        ViaBedrock.getPlatform().getLogger().info("Converted " + models + " Bedrock block geometry and material variants");
        return merged;
    }

    private static CompoundTag renderComponents(final CompoundTag components) {
        final CompoundTag render = new CompoundTag();
        for (final String key : List.of("minecraft:geometry", "minecraft:material_instances", "minecraft:transformation", "minecraft:collision_box")) {
            final var component = components.get(key);
            if (component != null) render.put(key, component);
        }
        return render;
    }

    private static List<Permutation> permutations(final CompoundTag properties) {
        final var list = properties.getListTag("permutations", CompoundTag.class);
        if (list == null) return List.of();
        final List<Permutation> permutations = new ArrayList<>();
        for (final CompoundTag entry : list) {
            final String condition = entry.getString("condition", "");
            final CompoundTag components = entry.getCompoundTag("components");
            if (condition.isEmpty() || condition.length() > 4096 || components == null) continue;
            try {
                permutations.add(new Permutation(MoLangEngine.parse(condition), components));
            } catch (final IOException | RuntimeException ignored) {
                // Optional expressions using unsupported syntax leave the base model available.
            }
        }
        return permutations;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static CompoundTag effectiveComponents(final CompoundTag base, final List<Permutation> permutations, final BlockState state) {
        if (permutations.isEmpty() && base != null) return base;
        final CompoundTag result = new CompoundTag();
        if (base != null) base.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        if (permutations.isEmpty()) return result;
        final Scope scope = Scope.create();
        final MutableObjectBinding queries = new MutableObjectBinding();
        final Function<Void> blockState = (context, arguments) -> {
            if (arguments.length() != 1) return Value.nil();
            final String property = arguments.next().eval().getAsString();
            if (state instanceof net.raphimc.viabedrock.api.model.BedrockBlockState bedrockState) {
                final var states = bedrockState.blockStateTag().getCompoundTag("states");
                final var value = states != null ? states.get(property) : null;
                if (value instanceof NumberTag number) return Value.of(number.asDouble());
                if (value instanceof com.viaversion.nbt.tag.StringTag string) return Value.of(string.getValue());
            }
            final String value = state.properties().get(property);
            if (value == null) return Value.nil();
            if (value.equals("true") || value.equals("false")) return Value.of(Boolean.parseBoolean(value));
            try {
                return Value.of(Integer.parseInt(value));
            } catch (final NumberFormatException ignored) {
                return Value.of(value);
            }
        };
        queries.set("block_state", blockState);
        queries.set("block_property", blockState);
        queries.block();
        scope.set("q", queries);
        scope.set("query", queries);
        for (final Permutation permutation : permutations) {
            try {
                if (MoLangEngine.eval(scope, permutation.condition()).getAsBoolean()) {
                    permutation.components().entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue()));
                }
            } catch (final RuntimeException ignored) {
                // Unsupported queries must not interrupt connection setup.
            }
        }
        return result;
    }

    private CustomBlockModel addNetworkModel(final ResourcePackStorage packs, final ItemModelContent output,
                                           final String id, final CompoundTag components, final String variant) {
        final CompoundTag geometry = components.getCompoundTag("minecraft:geometry");
        final CompoundTag instances = components.getCompoundTag("minecraft:material_instances");
        if (geometry == null || instances == null) return null;
        final CompoundTag materials = instances.getCompoundTag("materials");
        if (materials == null) return null;
        final CompoundTag mappings = instances.getCompoundTag("mappings");
        final Map<Direction, String> textures = new EnumMap<>(Direction.class);
        for (final Direction side : Direction.values()) {
            final String face = side.name().toLowerCase(Locale.ROOT);
            final String mapped = mappings != null ? mappings.getString(face, face) : face;
            final CompoundTag material = materials.getCompoundTag(mapped) != null
                ? materials.getCompoundTag(mapped) : materials.getCompoundTag("*");
            if (material == null) continue;
            final String texture = this.putTexture(packs, output, material.getString("texture", ""));
            if (texture != null) textures.put(side, texture);
        }
        if (textures.isEmpty()) return null;
        final String fallback = textures.values().iterator().next();
        for (final Direction side : Direction.values()) textures.putIfAbsent(side, fallback);

        final String geometryId = geometry.getString("identifier", "");
        final CompoundTag transform = components.getCompoundTag("minecraft:transformation");
        final int rotationY = rotationY(transform);
        final JsonObject json;
        final float scale;
        if (geometryId.startsWith("minecraft:geometry.full_block")) {
            json = cubeModel(textures);
            scale = 1F;
        } else {
            final var model = packs.getModels().getBlockModel(geometryId);
            if (model == null) return null;
            final var converted = model.toJavaItemModel(textures, RotationType.POST_1_21_11);
            json = converted.compile();
            json.getAsJsonObject("textures").addProperty("particle", textures.get(Direction.NORTH));
            scale = converted.getScale();
        }
        output.putModel(variant, json);
        final String carrier = carrier(components);
        final int carrierId = BedrockProtocol.MAPPINGS.getJavaBlockStates().getOrDefault(BlockState.fromString(carrier), 0);
        return new CustomBlockModel(id, variant, scale, carrierId, rotationY, carrier.startsWith("minecraft:petrified_oak_slab["));
    }

    private static JsonObject cubeModel(final Map<Direction, String> textures) {
        final JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube");
        final JsonObject faces = new JsonObject();
        textures.forEach((face, texture) -> faces.addProperty(face.name().toLowerCase(Locale.ROOT), texture));
        faces.addProperty("particle", textures.get(Direction.NORTH));
        model.add("textures", faces);
        return model;
    }

    private static int rotationY(final CompoundTag transform) {
        if (transform == null) return 0;
        final CompoundTag rotation = transform.getCompoundTag("rotation");
        return rotation == null ? 0 : (int) rotation.getFloat("y");
    }

    private static String carrier(final CompoundTag components) {
        final CompoundTag collision = components.getCompoundTag("minecraft:collision_box");
        String carrier = "minecraft:barrier[waterlogged=false]";
        if (collision != null) {
            if (collision.get("enabled") instanceof NumberTag enabled && enabled.asInt() == 0) {
                return "minecraft:structure_void";
            }
            final var boxes = collision.getListTag("boxes", CompoundTag.class);
            if (boxes != null && boxes.size() == 1) {
                final CompoundTag box = boxes.get(0);
                if (box.getFloat("minX") == 0 && box.getFloat("minZ") == 0 && box.getFloat("maxX") == 16 && box.getFloat("maxZ") == 16) {
                    if (box.getFloat("minY") == 0 && box.getFloat("maxY") == 8) {
                        carrier = "minecraft:petrified_oak_slab[type=bottom,waterlogged=false]";
                    } else if (box.getFloat("minY") == 8 && box.getFloat("maxY") == 16) {
                        carrier = "minecraft:petrified_oak_slab[type=top,waterlogged=false]";
                    }
                }
            }
        }
        return carrier;
    }

    private record Permutation(List<Expression> condition, CompoundTag components) { }

    public static boolean hasModel(final ResourcePackStorage storage, final String identifier, final String variant) {
        return storage.isLoadedOnJavaClient() && storage.getConverterData().containsKey("cb_" + identifier + '_' + variant);
    }

    @Override
    public void submitTasks(final ResourcePackStorage storage, final Consumer<Supplier<Content>> submitter) {
        for (final BlockDefinition block : storage.getBlocks().blocks().values()) {
            if (!block.identifier().startsWith("minecraft:") && !block.textures().isEmpty()) {
                submitter.accept(() -> this.convert(storage, block));
            }
        }
    }

    private Content convert(final ResourcePackStorage storage, final BlockDefinition block) {
        final ItemModelContent output = new ItemModelContent(block.identifier());
        final boolean world = this.addModel(storage, output, block, "world", block.textures());
        final boolean carried = this.addModel(storage, output, block, "carried",
            block.carriedTextures().isEmpty() ? block.textures() : block.carriedTextures());
        if (world || carried) output.generateItemDefinition();
        return output;
    }

    private boolean addModel(final ResourcePackStorage storage, final ItemModelContent output, final BlockDefinition block,
                             final String variant, final Map<String, String> aliases) {
        final JsonObject textures = new JsonObject();
        for (final String face : BEDROCK_FACES.keySet()) {
            final String alias = aliases.getOrDefault(face, aliases.getOrDefault(
                face.equals("down") || face.equals("up") ? "*" : "side", aliases.get("*")));
            final String texture = this.putTexture(storage, output, alias);
            if (texture == null) return false;
            textures.addProperty(face, texture);
        }
        final JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube");
        textures.addProperty("particle", textures.get("north").getAsString());
        model.add("textures", textures);
        output.putModel(variant, model);
        storage.getConverterData().put("cb_" + block.identifier() + '_' + variant, true);
        return true;
    }

    private String putTexture(final ResourcePackStorage storage, final ItemModelContent output, final String alias) {
        if (alias == null || alias.isEmpty()) return null;
        final var entries = storage.getTextures().terrainTextures().get(alias);
        if (entries == null || entries.isEmpty()) return null;
        final String path = entries.get(0).texturePath();
        for (final ResourcePack pack : storage.getPackStackTopToBottom()) {
            final Content.LazyImage image = pack.content().getShortnameImage(path);
            if (image == null) continue;
            final String javaPath = this.getJavaTexturePath(path);
            output.putPngImage("assets/viabedrock/textures/" + javaPath + ".png", image);
            return "viabedrock:" + javaPath;
        }
        return null;
    }

}
