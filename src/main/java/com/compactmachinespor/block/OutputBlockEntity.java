package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class OutputBlockEntity extends BaseIOBlockEntity {
    public OutputBlockEntity(BlockPos pos, BlockState state) {
        super(Cyumocompactmachinespor.OUTPUT_BLOCK_ENTITY.get(), pos, state);
    }
}
