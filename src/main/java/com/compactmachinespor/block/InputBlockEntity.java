package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class InputBlockEntity extends BaseIOBlockEntity {
    public InputBlockEntity(BlockPos pos, BlockState state) {
        super(Cyumocompactmachinespor.INPUT_BLOCK_ENTITY.get(), pos, state);
    }
}
