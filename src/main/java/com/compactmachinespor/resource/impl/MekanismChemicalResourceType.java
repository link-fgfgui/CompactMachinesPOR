package com.compactmachinespor.resource.impl;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.block.InputBlockEntity;
import com.compactmachinespor.block.OutputBlockEntity;
import com.compactmachinespor.resource.ResourceContainer;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;
import java.util.Optional;

public class MekanismChemicalResourceType extends ResourceType<Chemical> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Cyumocompactmachinespor.MODID, "chemical");
    public static final MekanismChemicalResourceType INSTANCE = new MekanismChemicalResourceType();

    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_BLOCK_CAPABILITY =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public static final ItemCapability<IChemicalHandler, Void> CHEMICAL_ITEM_CAPABILITY =
            ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public MekanismChemicalResourceType() {
        super(ID, 15);
    }

    @Override
    public Tag serializeValue(Chemical value) {
        return StringTag.valueOf(MekanismAPI.CHEMICAL_REGISTRY.getKey(value).toString());
    }

    @Override
    public ResourceKey<Chemical> deserializeKey(CompoundTag tag) {
        if (!tag.contains("value")) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("value"));
        if (id == null || !MekanismAPI.CHEMICAL_REGISTRY.containsKey(id)) return null;
        return new ResourceKey<>(this, MekanismAPI.CHEMICAL_REGISTRY.get(id));
    }

    @Override
    public Optional<Chemical> extractFromHand(ItemStack heldStack) {
        if (heldStack.isEmpty()) return Optional.empty();
        IChemicalHandler handler = heldStack.getCapability(CHEMICAL_ITEM_CAPABILITY);
        if (handler != null && handler.getChemicalTanks() > 0) {
            ChemicalStack stack = handler.getChemicalInTank(0);
            if (!stack.isEmpty()) {
                return Optional.of(stack.getChemical());
            }
        }
        return Optional.empty();
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("chat.compactmachinespor.chemical");
    }

    @Override
    public Component getValueName(@Nullable Chemical value) {
        if (value == null) return Component.empty();
        return value.getTextComponent();
    }

    @Override
    public IElement getJadeIcon(@Nullable Chemical value) {
        if (value == null) return IElementHelper.get().spacer(0, 0);
        return IElementHelper.get().text(value.getTextComponent().copy().withStyle(ChatFormatting.AQUA));
    }

    @Override
    public void registerCapabilities(
            RegisterCapabilitiesEvent event,
            BlockEntityType<InputBlockEntity> inputType,
            BlockEntityType<OutputBlockEntity> outputType,
            BlockEntityType<FactoryBlockEntity> factoryType
    ) {
        event.registerBlockEntity(CHEMICAL_BLOCK_CAPABILITY, inputType, (be, side) -> new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return be.getWhitelistedKeys(MekanismChemicalResourceType.this).size();
            }

            @Override
            public @NotNull ChemicalStack getChemicalInTank(int tank) {
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                if (tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
                return keys.get(tank).value().getStack(Long.MAX_VALUE);
            }

            @Override
            public void setChemicalInTank(int tank, @NotNull ChemicalStack stack) {
            }

            @Override
            public long getChemicalTankCapacity(int tank) {
                return Long.MAX_VALUE;
            }

            @Override
            public boolean isValid(int tank, @NotNull ChemicalStack stack) {
                return false;
            }

            @Override
            public @NotNull ChemicalStack insertChemical(int tank, @NotNull ChemicalStack stack, @NotNull Action action) {
                return stack;
            }

            @Override
            public @NotNull ChemicalStack extractChemical(int tank, long amount, @NotNull Action action) {
                if (!be.isActive() || amount <= 0) return ChemicalStack.EMPTY;
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                if (tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
                ResourceKey<Chemical> key = keys.get(tank);
                ChemicalStack result = key.value().getStack(amount);
                if (action.execute()) {
                    be.recordIO(key, amount);
                }
                return result;
            }

            @Override
            public @NotNull ChemicalStack extractChemical(ChemicalStack stack, Action action) {
                if (!be.isActive() || stack.isEmpty()) return ChemicalStack.EMPTY;
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                for (ResourceKey<Chemical> key : keys) {
                    if (key.value() == stack.getChemical()) {
                        if (action.execute()) {
                            be.recordIO(key, stack.getAmount());
                        }
                        return stack.copy();
                    }
                }
                return ChemicalStack.EMPTY;
            }

            @Override
            public @NotNull ChemicalStack extractChemical(long amount, Action action) {
                if (!be.isActive() || amount <= 0) return ChemicalStack.EMPTY;
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                if (keys.isEmpty()) return ChemicalStack.EMPTY;
                ResourceKey<Chemical> firstKey = keys.getFirst();
                ChemicalStack result = firstKey.value().getStack(amount);
                if (action.execute()) {
                    be.recordIO(firstKey, amount);
                }
                return result;
            }
        });

        event.registerBlockEntity(CHEMICAL_BLOCK_CAPABILITY, outputType, (be, side) -> new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                return keys.isEmpty() ? 1 : keys.size();
            }

            @Override
            public @NotNull ChemicalStack getChemicalInTank(int tank) {
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                if (keys.isEmpty() || tank < 0 || tank >= keys.size()) return ChemicalStack.EMPTY;
                return keys.get(tank).value().getStack(1);
            }

            @Override
            public void setChemicalInTank(int tank, @NotNull ChemicalStack stack) {
            }

            @Override
            public long getChemicalTankCapacity(int tank) {
                return Long.MAX_VALUE;
            }

            @Override
            public boolean isValid(int tank, @NotNull ChemicalStack stack) {
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                if (keys.isEmpty()) return true;
                for (ResourceKey<Chemical> key : keys) {
                    if (key.value() == stack.getChemical()) return true;
                }
                return false;
            }

            @Override
            public @NotNull ChemicalStack insertChemical(int tank, @NotNull ChemicalStack stack, @NotNull Action action) {
                if (!be.isActive() || stack.isEmpty()) return stack;
                List<ResourceKey<Chemical>> keys = be.getWhitelistedKeys(MekanismChemicalResourceType.this);
                boolean matches = keys.isEmpty();
                if (!matches) {
                    for (ResourceKey<Chemical> key : keys) {
                        if (key.value() == stack.getChemical()) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    if (action.execute()) {
                        be.recordIO(new ResourceKey<>(MekanismChemicalResourceType.this, stack.getChemical()), stack.getAmount());
                    }
                    return ChemicalStack.EMPTY;
                }
                return stack;
            }

            @Override
            public @NotNull ChemicalStack extractChemical(int tank, long amount, @NotNull Action action) {
                return ChemicalStack.EMPTY;
            }
        });

        event.registerBlockEntity(CHEMICAL_BLOCK_CAPABILITY, factoryType, (be, side) -> new IChemicalHandler() {
            @Override
            public int getChemicalTanks() {
                return be.getKeys(true, MekanismChemicalResourceType.this).size() + be.getKeys(false, MekanismChemicalResourceType.this).size();
            }

            @Override
            public @NotNull ChemicalStack getChemicalInTank(int tank) {
                List<ResourceKey<Chemical>> inputKeys = be.getKeys(true, MekanismChemicalResourceType.this);
                if (tank < inputKeys.size()) {
                    ResourceKey<Chemical> key = inputKeys.get(tank);
                    ResourceContainer c = be.getContainer(true, key);
                    return c != null ? key.value().getStack(c.amount) : ChemicalStack.EMPTY;
                } else {
                    int outTank = tank - inputKeys.size();
                    List<ResourceKey<Chemical>> outputKeys = be.getKeys(false, MekanismChemicalResourceType.this);
                    if (outTank < outputKeys.size()) {
                        ResourceKey<Chemical> key = outputKeys.get(outTank);
                        ResourceContainer c = be.getContainer(false, key);
                        return c != null ? key.value().getStack(c.amount) : ChemicalStack.EMPTY;
                    }
                }
                return ChemicalStack.EMPTY;
            }

            @Override
            public void setChemicalInTank(int tank, @NotNull ChemicalStack stack) {
            }

            @Override
            public long getChemicalTankCapacity(int tank) {
                List<ResourceKey<Chemical>> inputKeys = be.getKeys(true, MekanismChemicalResourceType.this);
                if (tank < inputKeys.size()) {
                    ResourceContainer c = be.getContainer(true, inputKeys.get(tank));
                    return c != null ? c.capacity : 0;
                } else {
                    int outTank = tank - inputKeys.size();
                    List<ResourceKey<Chemical>> outputKeys = be.getKeys(false, MekanismChemicalResourceType.this);
                    if (outTank < outputKeys.size()) {
                        ResourceContainer c = be.getContainer(false, outputKeys.get(outTank));
                        return c != null ? c.capacity : 0;
                    }
                }
                return 0;
            }

            @Override
            public boolean isValid(int tank, @NotNull ChemicalStack stack) {
                List<ResourceKey<Chemical>> inputKeys = be.getKeys(true, MekanismChemicalResourceType.this);
                if (tank < inputKeys.size()) {
                    return inputKeys.get(tank).value() == stack.getChemical();
                }
                return false;
            }

            @Override
            public @NotNull ChemicalStack insertChemical(int tank, @NotNull ChemicalStack stack, @NotNull Action action) {
                List<ResourceKey<Chemical>> inputKeys = be.getKeys(true, MekanismChemicalResourceType.this);
                if (tank < inputKeys.size()) {
                    ResourceKey<Chemical> key = inputKeys.get(tank);
                    if (key.value() == stack.getChemical()) {
                        ResourceContainer c = be.getContainer(true, key);
                        if (c != null) {
                            long space = c.capacity - c.amount;
                            long toAdd = Math.min(space, stack.getAmount());
                            if (action.execute() && toAdd > 0) {
                                c.amount += toAdd;
                                be.setChanged();
                            }
                            if (toAdd == stack.getAmount()) return ChemicalStack.EMPTY;
                            ChemicalStack result = stack.copy();
                            result.shrink(toAdd);
                            return result;
                        }
                    }
                }
                return stack;
            }

            @Override
            public @NotNull ChemicalStack extractChemical(int tank, long amount, @NotNull Action action) {
                List<ResourceKey<Chemical>> inputKeys = be.getKeys(true, MekanismChemicalResourceType.this);
                if (tank >= inputKeys.size()) {
                    int outTank = tank - inputKeys.size();
                    List<ResourceKey<Chemical>> outputKeys = be.getKeys(false, MekanismChemicalResourceType.this);
                    if (outTank < outputKeys.size()) {
                        ResourceKey<Chemical> key = outputKeys.get(outTank);
                        ResourceContainer c = be.getContainer(false, key);
                        if (c != null) {
                            long toExtract = Math.min(c.amount, amount);
                            ChemicalStack result = key.value().getStack(toExtract);
                            if (action.execute() && toExtract > 0) {
                                c.amount -= toExtract;
                                be.setChanged();
                                if (!be.lastSuccess && be.isReady()) be.operate();
                            }
                            return result;
                        }
                    }
                }
                return ChemicalStack.EMPTY;
            }
        });
    }
}
