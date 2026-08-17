package com.compactmachinespor.plugin.providers.server;

import com.compactmachinespor.block.EvaluatorBlockEntity;
import com.compactmachinespor.core.Core;
import com.compactmachinespor.core.Machine;
import com.compactmachinespor.plugin.CMPJadePlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum EvalComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    ) {
        CompoundTag serverData = accessor.getServerData();
        if (serverData.getInt("pg") <= 0) {
            if (accessor.getLevel().dimension() == Level.OVERWORLD) {
                tooltip.add(Component.translatable("tooltip.compactmachinespor.ready"));
            } else {
                tooltip.add(Component.translatable("tooltip.compactmachinespor.need_overworld"));
            }
            return;
        }
        tooltip.add(Component.translatable("tooltip.compactmachinespor.progress", serverData.getInt("pg")));
        tooltip.add(Component.translatable("tooltip.compactmachinespor.speed", serverData.getLong("s")));
    }

    @Override
    public ResourceLocation getUid() {
        return CMPJadePlugin.PROGRESS;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof EvaluatorBlockEntity be) {
            if (Core.getMachine(be.roomCode) instanceof Machine machine) {
                int pg = Math.round(
                        (be.getLevel().getServer().getTickCount() - machine.StartTick.get())
                                / (Machine.EVALUATE_SECONDS * 20f) * 100);
                long lastSpeed = machine.lastSpeed;
                data.putInt("pg", pg);
                data.putLong("s", lastSpeed);
            }
        }
    }
}
