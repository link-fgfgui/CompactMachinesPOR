package com.compactmachinespor.plugin.providers.server;

import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.plugin.CMPJadePlugin;
import com.compactmachinespor.resource.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

public enum FactoryComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public void appendTooltip(
            ITooltip tooltip,
            BlockAccessor accessor,
            IPluginConfig config
    ) {
        CompoundTag serverData = accessor.getServerData();
        ListTag inList = serverData.getList("in", Tag.TAG_COMPOUND);
        ListTag outList = serverData.getList("out", Tag.TAG_COMPOUND);
        if (inList.isEmpty() && outList.isEmpty()) return;

        tooltip.add(Component.literal(""));
        List<ResourceKey<?>> validIn = new ArrayList<>();
        for (int i = 0; i < inList.size(); i++) {
            ResourceKey<?> key = ResourceKey.deserialize(inList.getCompound(i));
            if (key != null) validIn.add(key);
        }
        for (int i = 0; i < validIn.size(); i++) {
            tooltip.append(validIn.get(i).getJadeIcon());
            if (i < validIn.size() - 1) {
                tooltip.append(Component.literal(" + "));
            }
        }

        tooltip.append(Component.literal(" -> "));

        List<ResourceKey<?>> validOut = new ArrayList<>();
        for (int i = 0; i < outList.size(); i++) {
            ResourceKey<?> key = ResourceKey.deserialize(outList.getCompound(i));
            if (key != null) validOut.add(key);
        }
        for (int i = 0; i < validOut.size(); i++) {
            tooltip.append(validOut.get(i).getJadeIcon());
            if (i < validOut.size() - 1) {
                tooltip.append(Component.literal(" + "));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return CMPJadePlugin.PRODUCE;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        if (blockAccessor.getBlockEntity() instanceof FactoryBlockEntity be) {
            ListTag inList = new ListTag();
            for (ResourceKey<?> key : be.getContainers(true).keySet()) {
                inList.add(key.serialize());
            }
            data.put("in", inList);

            ListTag outList = new ListTag();
            for (ResourceKey<?> key : be.getContainers(false).keySet()) {
                outList.add(key.serialize());
            }
            data.put("out", outList);
        }
    }
}
