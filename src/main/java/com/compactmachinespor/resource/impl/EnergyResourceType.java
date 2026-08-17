package com.compactmachinespor.resource.impl;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.block.InputBlockEntity;
import com.compactmachinespor.block.OutputBlockEntity;
import com.compactmachinespor.resource.ResourceContainer;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.Optional;

public class EnergyResourceType extends ResourceType<Void> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Cyumocompactmachinespor.MODID, "energy");
    public static final EnergyResourceType INSTANCE = new EnergyResourceType();
    public static final ResourceKey<Void> KEY = new ResourceKey<>(INSTANCE, null);

    public EnergyResourceType() {
        super(ID, 5);
    }

    @Override
    public Tag serializeValue(Void value) {
        return new CompoundTag();
    }

    @Override
    public ResourceKey<Void> deserializeKey(CompoundTag tag) {
        return KEY;
    }

    @Override
    public Optional<Void> extractFromHand(ItemStack heldStack) {
        return Optional.empty();
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("chat.compactmachinespor.energy");
    }

    @Override
    public Component getValueName(@Nullable Void value) {
        return Component.translatable("chat.compactmachinespor.energy");
    }

    @Override
    public IElement getJadeIcon(@Nullable Void value) {
        return IElementHelper.get().text(Component.literal("⚡FE").withStyle(ChatFormatting.RED));
    }

    @Override
    public void registerCapabilities(
            RegisterCapabilitiesEvent event,
            BlockEntityType<InputBlockEntity> inputType,
            BlockEntityType<OutputBlockEntity> outputType,
            BlockEntityType<FactoryBlockEntity> factoryType
    ) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, inputType, (be, side) -> new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                if (be.isActive()) {
                    if (!simulate) {
                        be.recordIO(KEY, maxExtract);
                    }
                    return maxExtract;
                }
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int getMaxEnergyStored() {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean canExtract() {
                return be.isActive();
            }

            @Override
            public boolean canReceive() {
                return false;
            }
        });

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, outputType, (be, side) -> new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (be.isActive()) {
                    if (!simulate) {
                        be.recordIO(KEY, maxReceive);
                    }
                    return maxReceive;
                }
                return 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return 0;
            }

            @Override
            public int getMaxEnergyStored() {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        });

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, factoryType, (be, side) -> new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                ResourceContainer in = be.getContainer(true, KEY);
                if (in == null) return 0;
                long space = in.capacity - in.amount;
                int toAdd = (int) Math.min(space, maxReceive);
                if (!simulate && toAdd > 0) {
                    in.amount += toAdd;
                    be.setChanged();
                }
                return toAdd;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                ResourceContainer out = be.getContainer(false, KEY);
                if (out == null) return 0;
                int toExtract = (int) Math.min(out.amount, maxExtract);
                if (!simulate && toExtract > 0) {
                    out.amount -= toExtract;
                    be.setChanged();
                    if (!be.lastSuccess && be.isReady()) {
                        be.operate();
                    }
                }
                return toExtract;
            }

            @Override
            public int getEnergyStored() {
                ResourceContainer in = be.getContainer(true, KEY);
                ResourceContainer out = be.getContainer(false, KEY);
                long total = (in != null ? in.amount : 0) + (out != null ? out.amount : 0);
                return (int) Math.min(total, Integer.MAX_VALUE);
            }

            @Override
            public int getMaxEnergyStored() {
                ResourceContainer in = be.getContainer(true, KEY);
                ResourceContainer out = be.getContainer(false, KEY);
                long total = (in != null ? in.capacity : 0) + (out != null ? out.capacity : 0);
                return (int) Math.min(total, Integer.MAX_VALUE);
            }

            @Override
            public boolean canExtract() {
                return be.getContainer(false, KEY) != null;
            }

            @Override
            public boolean canReceive() {
                return be.getContainer(true, KEY) != null;
            }
        });
    }
}
