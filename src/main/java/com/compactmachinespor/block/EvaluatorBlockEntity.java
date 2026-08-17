package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.core.Core;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EvaluatorBlockEntity extends RoomCodeBlockEntity {
    public EvaluatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(Cyumocompactmachinespor.EVALUATOR_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void trigger() {
        if (roomCode != null && !roomCode.isEmpty() && getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD) {
            Core.createMachine(serverLevel, roomCode, getBlockPos());
        }
    }
}
