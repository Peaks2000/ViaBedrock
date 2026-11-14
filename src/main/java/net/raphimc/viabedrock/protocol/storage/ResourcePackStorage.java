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
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.definition.*;
import net.raphimc.viabedrock.protocol.BedrockProtocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResourcePackStorage implements StorableObject {

    private final Set<ResourcePack> packStackBottomToTop = Collections.newSetFromMap(new LinkedHashMap<>(128, 0.75F, true));
    private final Set<ResourcePack> packStackTopToBottom = Collections.newSetFromMap(new LinkedHashMap<>(128, 0.75F, false));

    private UUID blockPackId;
    private Runnable finishBlockPack;
    private boolean blockPackLoaded;
    private final Map<Integer, CustomBlockModel> customBlockModels = new HashMap<>();
    private final Map<String, CustomBlockModel> customBlockItems = new HashMap<>();
    private boolean loadedOnJavaClient;
    private final AtomicBoolean bedrockStackFinishedSent = new AtomicBoolean();
    private final Map<String, Object> converterData = new ConcurrentHashMap<>();

    private final TextDefinitions texts;
    private final BlockDefinitions blocks;
    private final ItemDefinitions items;
    private final AttachableDefinitions attachables;
    private final TextureDefinitions textures;
    private final SoundDefinitions sounds;
    private final ParticleDefinitions particles;
    private final EntityDefinitions entities;
    private final ModelDefinitions models;
    private final FogDefinitions fogs;
    private final BiomeDefinitions biomes;
    private final RenderControllerDefinitions renderControllers;

    public ResourcePackStorage(final List<ResourcePack> resourcePacksTopToBottom) {
        if (BedrockProtocol.MAPPINGS.getBedrockVanillaResourcePacks() != null) { // null if ran from ResourcePackConverterTest
            this.packStackBottomToTop.addAll(BedrockProtocol.MAPPINGS.getBedrockVanillaResourcePacks());
        }
        for (int i = resourcePacksTopToBottom.size() - 1; i >= 0; i--) {
            this.packStackBottomToTop.add(resourcePacksTopToBottom.get(i));
        }
        this.packStackTopToBottom.addAll(resourcePacksTopToBottom);
        if (BedrockProtocol.MAPPINGS.getBedrockVanillaResourcePacks() != null) { // null if ran from ResourcePackConverterTest
            for (int i = BedrockProtocol.MAPPINGS.getBedrockVanillaResourcePacks().size() - 1; i >= 0; i--) {
                this.packStackTopToBottom.add(BedrockProtocol.MAPPINGS.getBedrockVanillaResourcePacks().get(i));
            }
        }

        this.texts = new TextDefinitions(this);
        this.blocks = new BlockDefinitions(this);
        this.items = new ItemDefinitions(this);
        this.attachables = new AttachableDefinitions(this);
        this.textures = new TextureDefinitions(this);
        this.sounds = new SoundDefinitions(this);
        this.particles = new ParticleDefinitions(this);
        this.entities = new EntityDefinitions(this);
        this.models = new ModelDefinitions(this);
        this.fogs = new FogDefinitions(this);
        this.biomes = new BiomeDefinitions(this);
        this.renderControllers = new RenderControllerDefinitions(this);
    }

    public Collection<ResourcePack> getPackStackBottomToTop() {
        return this.packStackBottomToTop;
    }

    public Collection<ResourcePack> getPackStackTopToBottom() {
        return this.packStackTopToBottom;
    }

    public boolean isLoadedOnJavaClient() {
        return this.loadedOnJavaClient;
    }

    public void setLoadedOnJavaClient() {
        this.loadedOnJavaClient = true;
    }

    public void addCustomBlockModel(final int stateId, final CustomBlockModel model) {
        this.customBlockModels.put(stateId, model);
        this.customBlockItems.putIfAbsent(model.identifier(), model);
    }

    public CustomBlockModel customBlockModel(final int stateId) {
        return this.blockPackLoaded ? this.customBlockModels.get(stateId) : null;
    }

    public CustomBlockModel customBlockItem(final String identifier) {
        return this.blockPackLoaded ? this.customBlockItems.get(identifier) : null;
    }

    public void waitForBlockPack(final UUID id, final Runnable finish) {
        this.blockPackId = id;
        this.finishBlockPack = finish;
    }

    public boolean isWaitingForBlockPack() {
        return this.finishBlockPack != null;
    }

    public boolean isBlockPack(final UUID id) {
        return id.equals(this.blockPackId);
    }

    public boolean isBlockPackLoaded() {
        return this.blockPackLoaded;
    }

    public void finishBlockPack(final boolean loaded) {
        final Runnable finish = this.finishBlockPack;
        if (finish == null) {
            return;
        }
        this.finishBlockPack = null;
        this.blockPackLoaded = loaded;
        finish.run();
    }

    public boolean markBedrockStackFinishedSent() {
        return this.bedrockStackFinishedSent.compareAndSet(false, true);
    }

    public Map<String, Object> getConverterData() {
        return this.converterData;
    }

    public TextDefinitions getTexts() {
        return this.texts;
    }

    public BlockDefinitions getBlocks() {
        return this.blocks;
    }

    public ItemDefinitions getItems() {
        return this.items;
    }

    public AttachableDefinitions getAttachables() {
        return this.attachables;
    }

    public TextureDefinitions getTextures() {
        return this.textures;
    }

    public SoundDefinitions getSounds() {
        return this.sounds;
    }

    public ParticleDefinitions getParticles() {
        return this.particles;
    }

    public EntityDefinitions getEntities() {
        return this.entities;
    }

    public ModelDefinitions getModels() {
        return this.models;
    }

    public FogDefinitions getFogs() {
        return this.fogs;
    }

    public BiomeDefinitions getBiomes() {
        return this.biomes;
    }

    public RenderControllerDefinitions getRenderControllers() {
        return this.renderControllers;
    }

    public record CustomBlockModel(String identifier, String variant, float scale, int carrier, int rotationY, boolean slabCarrier) { }

}
