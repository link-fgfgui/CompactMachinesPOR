package com.compactmachinespor.resource;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.ui.IElement;

import java.util.Objects;

public class ResourceKey<T> {
    private final ResourceType<T> type;
    @Nullable
    private final T value;

    public ResourceKey(ResourceType<T> type, @Nullable T value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
    }

    public ResourceType<T> type() {
        return type;
    }

    @Nullable
    public T value() {
        return value;
    }

    public Component getDisplayName() {
        return type.getValueName(value);
    }

    public IElement getJadeIcon() {
        return type.getJadeIcon(value);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.getId().toString());
        if (value != null) {
            tag.put("value", type.serializeValue(value));
        }
        return tag;
    }

    @Nullable
    public static ResourceKey<?> deserialize(CompoundTag tag) {
        if (!tag.contains("type")) return null;
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("type"));
        if (typeId == null) return null;
        ResourceType<?> resourceType = ResourceTypeRegistry.get(typeId);
        if (resourceType == null) return null;
        return resourceType.deserializeKey(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceKey<?> that = (ResourceKey<?>) o;
        return type.getId().equals(that.type.getId()) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type.getId(), value);
    }

    @Override
    public String toString() {
        return type.getId() + "[" + (value != null ? value.toString() : "") + "]";
    }
}
