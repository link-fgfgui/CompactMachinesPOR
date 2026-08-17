package com.compactmachinespor.block;

import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import com.compactmachinespor.resource.ResourceTypeRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

public abstract class BaseIOBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected BaseIOBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected abstract MapCodec<? extends BaseIOBlock> codec();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BaseIOBlockEntity ioBe) {
            for (ResourceType<?> resourceType : ResourceTypeRegistry.getAll()) {
                ResourceType<Object> type = (ResourceType<Object>) resourceType;
                Optional<Object> extracted = type.extractFromHand(stack);
                if (extracted.isPresent()) {
                    Object value = extracted.get();
                    ResourceKey<Object> key = new ResourceKey<>(type, value);
                    MutableComponent action;
                    if (ioBe.hasResource(key)) {
                        ioBe.removeResource(key);
                        action = Component.translatable("chat.compactmachinespor.removed").withStyle(ChatFormatting.WHITE);
                    } else {
                        ioBe.addResource(key);
                        action = Component.translatable("chat.compactmachinespor.added").withStyle(ChatFormatting.WHITE);
                    }
                    MutableComponent name = type.getValueName(value).copy().withStyle(ChatFormatting.GRAY);
                    MutableComponent typeName = type.getTypeName().copy().withStyle(ChatFormatting.WHITE);
                    ioBe.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    player.displayClientMessage(Component.translatable("chat.compactmachinespor.io_update", name, typeName, action).withStyle(ChatFormatting.GRAY), true);
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
