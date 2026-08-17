package com.compactmachinespor.block;

import com.compactmachinespor.core.Core;
import com.compactmachinespor.core.Machine.DataSetType;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public abstract class BaseIOBlockEntity extends RoomCodeBlockEntity {
    protected final Set<ResourceKey<?>> whitelist = new LinkedHashSet<>();

    public BaseIOBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @SuppressWarnings("unchecked")
    public <T> List<ResourceKey<T>> getWhitelistedKeys(ResourceType<T> type) {
        List<ResourceKey<T>> list = new ArrayList<>();
        for (ResourceKey<?> key : whitelist) {
            if (key.type().getId().equals(type.getId())) {
                list.add((ResourceKey<T>) key);
            }
        }
        return list;
    }

    public boolean hasResource(ResourceKey<?> key) {
        return whitelist.contains(key);
    }

    public void addResource(ResourceKey<?> key) {
        whitelist.add(key);
        setChanged();
    }

    public void removeResource(ResourceKey<?> key) {
        whitelist.remove(key);
        setChanged();
    }

    public Set<ResourceKey<?>> getWhitelist() {
        return Collections.unmodifiableSet(whitelist);
    }

    @Override
    protected void loadCommon(CompoundTag tag) {
        super.loadCommon(tag);
        whitelist.clear();
        if (tag.contains("whitelist", Tag.TAG_LIST)) {
            ListTag list = tag.getList("whitelist", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ResourceKey<?> key = ResourceKey.deserialize(list.getCompound(i));
                if (key != null) whitelist.add(key);
            }
        }
        // Legacy backward compatibility
        if (tag.contains("items", Tag.TAG_LIST)) {
            ListTag list = tag.getList("items", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                    whitelist.add(new ResourceKey<>(ItemResourceType.INSTANCE, BuiltInRegistries.ITEM.get(rl)));
                }
            }
        }
        if (tag.contains("fluids", Tag.TAG_LIST)) {
            ListTag list = tag.getList("fluids", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null && BuiltInRegistries.FLUID.containsKey(rl)) {
                    whitelist.add(new ResourceKey<>(FluidResourceType.INSTANCE, BuiltInRegistries.FLUID.get(rl)));
                }
            }
        }
    }

    @Override
    protected void saveCommon(CompoundTag tag) {
        super.saveCommon(tag);
        ListTag list = new ListTag();
        for (ResourceKey<?> key : whitelist) {
            list.add(key.serialize());
        }
        tag.put("whitelist", list);
    }

    public boolean isActive() {
        if (getBlockState().getValue(BaseIOBlock.ACTIVE)) {
            return checkAndDeactivate();
        }
        return false;
    }

    protected boolean checkAndDeactivate() {
        if (check()) {
            return true;
        } else {
            deactivate();
            return false;
        }
    }

    protected boolean check() {
        return roomCode != null && Core.getMachine(roomCode) != null;
    }

    protected void deactivate() {
        if (getLevel() != null) {
            getLevel().setBlock(getBlockPos(), getBlockState().setValue(BaseIOBlock.ACTIVE, false), Block.UPDATE_CLIENTS);
        }
    }

    protected void delete() {
        setRemoved();
        this.invalidateCapabilities();
        if (getLevel() != null) {
            getLevel().destroyBlock(getBlockPos(), true);
        }
    }

    protected DataSetType getDataSetType() {
        if (this instanceof InputBlockEntity) {
            return DataSetType.Input;
        } else if (this instanceof OutputBlockEntity) {
            return DataSetType.Output;
        } else {
            throw new RuntimeException("No such DataSetType");
        }
    }

    public void recordIO(ResourceKey<?> key, long amount) {
        if (!checkAndDeactivate()) return;
        if (getLevel() instanceof ServerLevel serverLevel) {
            Core.setMachineData(roomCode, getDataSetType(), key, amount, Core.getTicks(serverLevel));
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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
