package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.resource.ResourceContainer;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import com.compactmachinespor.resource.impl.EnergyResourceType;
import com.compactmachinespor.resource.impl.FluidResourceType;
import com.compactmachinespor.resource.impl.ItemResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import java.util.*;

public class FactoryBlockEntity extends RoomCodeBlockEntity {
    private int tickCount = 0;
    public boolean lastSuccess = true;
    private final Map<ResourceKey<?>, ResourceContainer> inputContainers = new LinkedHashMap<>();
    private final Map<ResourceKey<?>, ResourceContainer> outputContainers = new LinkedHashMap<>();

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(Cyumocompactmachinespor.FACTORY_BLOCK_ENTITY.get(), pos, state);
    }

    public ResourceContainer getContainer(boolean isInput, ResourceKey<?> key) {
        return (isInput ? inputContainers : outputContainers).get(key);
    }

    public Map<ResourceKey<?>, ResourceContainer> getContainers(boolean isInput) {
        return Collections.unmodifiableMap(isInput ? inputContainers : outputContainers);
    }

    @SuppressWarnings("unchecked")
    public <T> List<ResourceKey<T>> getKeys(boolean isInput, ResourceType<T> type) {
        List<ResourceKey<T>> list = new ArrayList<>();
        Map<ResourceKey<?>, ResourceContainer> map = isInput ? inputContainers : outputContainers;
        for (ResourceKey<?> key : map.keySet()) {
            if (key.type().getId().equals(type.getId())) {
                list.add((ResourceKey<T>) key);
            }
        }
        return list;
    }

    public void init(Map<ResourceKey<?>, Double> inputMap, Map<ResourceKey<?>, Double> outputMap) {
        inputContainers.clear();
        for (Map.Entry<ResourceKey<?>, Double> entry : inputMap.entrySet()) {
            long capacity = (long) Math.floor(entry.getValue() * 20);
            if (capacity > 0) {
                inputContainers.put(entry.getKey(), new ResourceContainer(capacity, 0));
            }
        }
        outputContainers.clear();
        for (Map.Entry<ResourceKey<?>, Double> entry : outputMap.entrySet()) {
            long capacity = (long) Math.floor(entry.getValue() * 20);
            if (capacity > 0) {
                outputContainers.put(entry.getKey(), new ResourceContainer(capacity, 0));
            }
        }
        setChanged();
    }

    public boolean isReady() {
        for (ResourceContainer c : inputContainers.values()) {
            if (c.amount < c.capacity) {
                return false;
            }
        }
        for (ResourceContainer c : outputContainers.values()) {
            if (c.amount > 0) {
                return false;
            }
        }
        return true;
    }

    public void operate() {
        for (ResourceContainer c : inputContainers.values()) {
            c.amount = 0;
        }
        for (ResourceContainer c : outputContainers.values()) {
            c.amount = c.capacity;
        }
        setChanged();
        this.lastSuccess = true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickCount++;
        if (be.tickCount == 20) {
            be.tickCount = 0;
            if (be.isReady()) {
                be.operate();
            } else {
                be.lastSuccess = false;
            }
        }
    }

    @Override
    protected void loadCommon(CompoundTag tag) {
        super.loadCommon(tag);
        inputContainers.clear();
        outputContainers.clear();
        if (tag.contains("inputs", Tag.TAG_LIST)) {
            loadContainers(tag.getList("inputs", Tag.TAG_COMPOUND), inputContainers);
        }
        if (tag.contains("outputs", Tag.TAG_LIST)) {
            loadContainers(tag.getList("outputs", Tag.TAG_COMPOUND), outputContainers);
        }
        loadLegacySaves(tag);
    }

    @Override
    protected void saveCommon(CompoundTag tag) {
        super.saveCommon(tag);
        tag.put("inputs", saveContainers(inputContainers));
        tag.put("outputs", saveContainers(outputContainers));
    }

    private static ListTag saveContainers(Map<ResourceKey<?>, ResourceContainer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<?>, ResourceContainer> entry : map.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("key", entry.getKey().serialize());
            entryTag.put("container", entry.getValue().serialize());
            list.add(entryTag);
        }
        return list;
    }

    private static void loadContainers(ListTag list, Map<ResourceKey<?>, ResourceContainer> map) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceKey<?> key = ResourceKey.deserialize(entryTag.getCompound("key"));
            if (key != null) {
                ResourceContainer container = ResourceContainer.deserialize(entryTag.getCompound("container"));
                map.put(key, container);
            }
        }
    }

    private void loadLegacySaves(CompoundTag tag) {
        if (tag.contains("input_items")) {
            CompoundTag mapTag = tag.getCompound("input_items");
            for (String k : mapTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    CompoundTag c = mapTag.getCompound(k);
                    inputContainers.put(new ResourceKey<>(ItemResourceType.INSTANCE, item), new ResourceContainer(c.getInt("capacity"), c.getInt("amount")));
                }
            }
        }
        if (tag.contains("output_items")) {
            CompoundTag mapTag = tag.getCompound("output_items");
            for (String k : mapTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    CompoundTag c = mapTag.getCompound(k);
                    outputContainers.put(new ResourceKey<>(ItemResourceType.INSTANCE, item), new ResourceContainer(c.getInt("capacity"), c.getInt("amount")));
                }
            }
        }
        if (tag.contains("input_fluids")) {
            CompoundTag mapTag = tag.getCompound("input_fluids");
            for (String k : mapTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id != null && BuiltInRegistries.FLUID.containsKey(id)) {
                    Fluid fluid = BuiltInRegistries.FLUID.get(id);
                    CompoundTag c = mapTag.getCompound(k);
                    inputContainers.put(new ResourceKey<>(FluidResourceType.INSTANCE, fluid), new ResourceContainer(c.getInt("capacity"), c.getInt("amount")));
                }
            }
        }
        if (tag.contains("output_fluids")) {
            CompoundTag mapTag = tag.getCompound("output_fluids");
            for (String k : mapTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(k);
                if (id != null && BuiltInRegistries.FLUID.containsKey(id)) {
                    Fluid fluid = BuiltInRegistries.FLUID.get(id);
                    CompoundTag c = mapTag.getCompound(k);
                    outputContainers.put(new ResourceKey<>(FluidResourceType.INSTANCE, fluid), new ResourceContainer(c.getInt("capacity"), c.getInt("amount")));
                }
            }
        }
        if (tag.contains("input_energy")) {
            CompoundTag c = tag.getCompound("input_energy");
            inputContainers.put(EnergyResourceType.KEY, new ResourceContainer(c.getInt("capacity"), c.getInt("energy")));
        }
        if (tag.contains("output_energy")) {
            CompoundTag c = tag.getCompound("output_energy");
            outputContainers.put(EnergyResourceType.KEY, new ResourceContainer(c.getInt("capacity"), c.getInt("energy")));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        CustomData customData = componentInput.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            loadCommon(customData.copyTag());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        CompoundTag tag = new CompoundTag();
        saveCommon(tag);
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}