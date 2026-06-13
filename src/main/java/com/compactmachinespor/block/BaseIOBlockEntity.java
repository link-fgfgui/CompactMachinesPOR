package com.compactmachinespor.block;

import com.compactmachinespor.core.Core;
import com.compactmachinespor.core.Machine.DataSetType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseIOBlockEntity extends RoomCodeBlockEntity {
    protected List<ResourceLocation> items = new ArrayList<>();
    protected List<ResourceLocation> fluids = new ArrayList<>();

    public BaseIOBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void loadCommon(CompoundTag tag) {
        super.loadCommon(tag);
        items.clear();
        if (tag.contains("items", Tag.TAG_LIST)) {
            ListTag list = tag.getList("items", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) items.add(rl);
            }
        }
        fluids.clear();
        if (tag.contains("fluids", Tag.TAG_LIST)) {
            ListTag list = tag.getList("fluids", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
                if (rl != null && BuiltInRegistries.FLUID.containsKey(rl)) fluids.add(rl);
            }
        }
    }

    @Override
    protected void saveCommon(CompoundTag tag) {
        super.saveCommon(tag);
        ListTag itemsTag = new ListTag();
        for (ResourceLocation id : items) {
            itemsTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put("items", itemsTag);
        ListTag fluidsTag = new ListTag();
        for (ResourceLocation id : fluids) {
            fluidsTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put("fluids", fluidsTag);
    }

    public abstract IItemHandler getItemHandler();

    public abstract IFluidHandler getFluidHandler();

    public abstract IEnergyStorage getEnergyHandler();

    protected boolean isActive() {
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

    protected void handle(ItemStack itemStack) {
        handle(itemStack.getItemHolder(), itemStack.getCount());
    }

    protected void handle(FluidStack fluidStack) {
        handle(fluidStack.getFluidHolder(), fluidStack.getAmount());
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

    protected void handle(Holder<?> holder, int count) {
        if (!checkAndDeactivate()) return;
        if (getLevel() instanceof ServerLevel serverLevel) {
            DataSetType type = getDataSetType();
            long ticks = Core.getTicks(serverLevel);
            Core.setMachineData(roomCode, type, holder, count, ticks);
            // 累计 IO 总量（用于库存基线审计）
            Core.getMachine(roomCode).addTotal(type, holder, count);
        }
    }

    protected void handle(int energy) {
        if (!checkAndDeactivate()) return;
        if (getLevel() instanceof ServerLevel serverLevel) {
            DataSetType type = getDataSetType();
            Core.getMachine(roomCode).addEnergyData(type, energy, Core.getTicks(serverLevel));
            // 累计 IO 总量（用于库存基线审计）
            Core.getMachine(roomCode).addTotalEnergy(type, energy);
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
