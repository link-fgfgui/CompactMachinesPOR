package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.core.Core;
import com.compactmachinespor.core.Core.CreateResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EvaluatorBlockEntity extends RoomCodeBlockEntity {
    // 原机器方块信息，用于评估失败时还原
    private ResourceLocation originalMachineBlock;
    private CompoundTag savedOriginalNbt;  // 原 BE 的完整 NBT（含 data components）

    public EvaluatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(Cyumocompactmachinespor.EVALUATOR_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void saveOriginalMachine(ResourceLocation blockId, CompoundTag beNbt) {
        this.originalMachineBlock = blockId;
        this.savedOriginalNbt = beNbt;
        setChanged();
    }

    /**
     * 供 Core.createMachine 获取原始机器 NBT（用于提取附件数据）
     */
    public CompoundTag getSavedOriginalNbt() {
        return savedOriginalNbt;
    }

    public void trigger() {
        if (roomCode != null && !roomCode.isEmpty() && getLevel() instanceof ServerLevel serverLevel) {
            CreateResult result = Core.createMachine(serverLevel, roomCode, getBlockPos());
            if (result == CreateResult.ABORTED_SUSPICIOUS_BLOCKS) {
                serverLevel.players().forEach(p ->
                        p.displayClientMessage(
                                Component.translatable("chat.compactmachinespor.suspicious_blocks"),
                                false
                        )
                );
                restoreOriginalMachine(serverLevel);
            }
        }
    }

    /**
     * 还原为原来的紧缩空间机器方块
     * 延后到 neighborChanged 执行完毕后再执行，避免冲突
     */
    private void restoreOriginalMachine(ServerLevel level) {
        if (originalMachineBlock == null || savedOriginalNbt == null) return;

        final BlockPos pos = getBlockPos();
        final Block machineBlock = BuiltInRegistries.BLOCK.get(originalMachineBlock);
        final CompoundTag nbt = savedOriginalNbt.copy();
        if (machineBlock == null || machineBlock == net.minecraft.world.level.block.Blocks.AIR) return;

        Cyumocompactmachinespor.LOGGER.info("Will restore original machine at {} (delayed)", pos);

        // 使用 server.execute 在邻居更新完成后执行还原
        // 避免与 EvaluatorBlock.neighborChanged 中的 setBlockAndUpdate 冲突
        level.getServer().execute(() -> {
            if (!(level.getBlockEntity(pos) instanceof EvaluatorBlockEntity)) {
                return;
            }

            // 1. 替换方块（会创建默认 BE）
            level.removeBlockEntity(pos);
            level.setBlockAndUpdate(pos, machineBlock.defaultBlockState());

            // 2. 用 loadStatic 从保存的 NBT 创建完整的 BE（包含 neoforge:attachments）
            //    loadWithComponents 在已有 BE 上调用可能不覆盖全部数据，
            //    loadStatic 直接从 NBT 创建全新 BE，能正确恢复所有数据
            BlockEntity loaded = BlockEntity.loadStatic(pos, level.getBlockState(pos), nbt, level.registryAccess());
            if (loaded != null) {
                level.setBlockEntity(loaded);
                loaded.setChanged();
                Cyumocompactmachinespor.LOGGER.info("Machine restored at {} (via loadStatic)", pos);
            } else {
                Cyumocompactmachinespor.LOGGER.warn("loadStatic returned null at {}", pos);
            }

            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
        });
    }

    @Override
    protected void loadCommon(CompoundTag tag) {
        super.loadCommon(tag);
        this.originalMachineBlock = null;
        this.savedOriginalNbt = null;
        if (tag.contains("original_machine_block")) {
            String id = tag.getString("original_machine_block");
            this.originalMachineBlock = ResourceLocation.tryParse(id);
        }
        if (tag.contains("original_machine_nbt")) {
            this.savedOriginalNbt = tag.getCompound("original_machine_nbt");
        }
    }

    @Override
    protected void saveCommon(CompoundTag tag) {
        super.saveCommon(tag);
        if (originalMachineBlock != null) {
            tag.putString("original_machine_block", originalMachineBlock.toString());
        }
        if (savedOriginalNbt != null) {
            tag.put("original_machine_nbt", savedOriginalNbt);
        }
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
