package com.compactmachinespor.plugin.providers.server;

import com.compactmachinespor.block.RoomCodeBlockEntity;
import com.compactmachinespor.plugin.CMPJadePlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import static com.compactmachinespor.block.RoomCodeBlockEntity.WRENCH;
import static com.compactmachinespor.block.RoomCodeBlockEntity.WRENCH2;


public enum RoomCodeComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    ) {
        ItemStack itemInHand = accessor.getPlayer().getItemInHand(InteractionHand.MAIN_HAND);
        if ((itemInHand.is(WRENCH) || itemInHand.is(WRENCH2)))
            if (!accessor.getServerData().contains("rc")) {
                tooltip.add(Component.translatable("tooltip.compactmachinespor.room_code", Component.translatable("tooltip.compactmachinespor.noroomcode")));
            } else {
                tooltip.add(Component.translatable("tooltip.compactmachinespor.room_code", accessor.getServerData().getString("rc")));
            }
    }

    @Override
    public ResourceLocation getUid() {
        return CMPJadePlugin.ROOM_CODE;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof RoomCodeBlockEntity be) {
            String rc = be.roomCode;
            if (rc != null && !rc.isEmpty()) {
                data.putString("rc", rc);
            }
        }
    }
}
