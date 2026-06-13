package com.compactmachinespor.block;

import com.compactmachinespor.Config;
import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.core.Core;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FactoryBlock extends BaseEntityBlock {
    public static final MapCodec<FactoryBlock> CODEC = simpleCodec(FactoryBlock::new);

    public FactoryBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3.0f, 6.0f));
    }

    public FactoryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 启动棒右键工厂方块 → 还原为原来的紧凑空间机器方块
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Cyumocompactmachinespor.LAUNCHER_STICK)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!Config.ENABLE_FACTORY_REVERT.get()) {
            player.displayClientMessage(
                    Component.literal("[CM] 工厂还原功能已被禁用").withStyle(ChatFormatting.GRAY),
                    true);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity factoryBe) {
            String roomCode = factoryBe.getRoomCode();
            if (roomCode != null && !roomCode.isEmpty() && level instanceof ServerLevel serverLevel) {
                Core.revertToBoundMachine(serverLevel, pos, roomCode);
                stack.shrink(1);
                player.inventoryMenu.broadcastChanges();
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactoryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Cyumocompactmachinespor.FACTORY_BLOCK_ENTITY.get(), FactoryBlockEntity::tick);
    }
}
