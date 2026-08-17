package com.compactmachinespor.resource;

import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.block.InputBlockEntity;
import com.compactmachinespor.block.OutputBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.ui.IElement;

import java.util.Optional;

public abstract class ResourceType<T> {
    private final ResourceLocation id;
    private final int priority;

    public ResourceType(ResourceLocation id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    public ResourceType(ResourceLocation id) {
        this(id, 0);
    }

    public ResourceLocation getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Serializes a non-null resource value to NBT tag.
     */
    public abstract Tag serializeValue(T value);

    /**
     * Deserializes a ResourceKey from a CompoundTag containing the serialized key data.
     */
    public abstract ResourceKey<T> deserializeKey(CompoundTag tag);

    /**
     * Extracts a resource value from player's held ItemStack (e.g. bucket -> fluid, tank -> chemical, item -> item).
     */
    public abstract Optional<T> extractFromHand(ItemStack heldStack);

    /**
     * Localized name for the type category (e.g. "Item", "Fluid", "Energy", "Chemical").
     */
    public abstract Component getTypeName();

    /**
     * Localized display name for a specific resource value (e.g. "Iron Ingot", "Water", "Hydrogen").
     */
    public abstract Component getValueName(@Nullable T value);

    /**
     * Creates Jade HUD tooltip element for this resource.
     */
    public abstract IElement getJadeIcon(@Nullable T value);

    /**
     * Registers NeoForge capabilities for InputBlock, OutputBlock, and FactoryBlock.
     */
    public abstract void registerCapabilities(
            RegisterCapabilitiesEvent event,
            BlockEntityType<InputBlockEntity> inputType,
            BlockEntityType<OutputBlockEntity> outputType,
            BlockEntityType<FactoryBlockEntity> factoryType
    );
}
