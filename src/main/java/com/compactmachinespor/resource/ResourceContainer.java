package com.compactmachinespor.resource;

import net.minecraft.nbt.CompoundTag;

public class ResourceContainer {
    public final long capacity;
    public long amount;

    public ResourceContainer(long capacity, long amount) {
        this.capacity = capacity;
        this.amount = amount;
    }

    public boolean isFull() {
        return amount >= capacity;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public void fill() {
        this.amount = this.capacity;
    }

    public void clear() {
        this.amount = 0;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("capacity", capacity);
        tag.putLong("amount", amount);
        return tag;
    }

    public static ResourceContainer deserialize(CompoundTag tag) {
        long capacity = tag.getLong("capacity");
        long amount = tag.getLong("amount");
        return new ResourceContainer(capacity, amount);
    }
}
