package com.compactmachinespor.resource.impl;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.block.InputBlockEntity;
import com.compactmachinespor.block.OutputBlockEntity;
import com.compactmachinespor.resource.ResourceContainer;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;
import java.util.Optional;

public class FluidResourceType extends ResourceType<Fluid> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Cyumocompactmachinespor.MODID, "fluid");
    public static final FluidResourceType INSTANCE = new FluidResourceType();

    public FluidResourceType() {
        super(ID, 10);
    }

    @Override
    public Tag serializeValue(Fluid value) {
        return StringTag.valueOf(BuiltInRegistries.FLUID.getKey(value).toString());
    }

    @Override
    public ResourceKey<Fluid> deserializeKey(CompoundTag tag) {
        if (!tag.contains("value")) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("value"));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
        return new ResourceKey<>(this, BuiltInRegistries.FLUID.get(id));
    }

    @Override
    public Optional<Fluid> extractFromHand(ItemStack heldStack) {
        if (heldStack.isEmpty()) return Optional.empty();
        if (heldStack.getItem() instanceof BucketItem bucket && !bucket.content.isSame(Fluids.EMPTY)) {
            return Optional.of(bucket.content);
        }
        var handler = heldStack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null && handler.getTanks() > 0) {
            FluidStack fs = handler.getFluidInTank(0);
            if (!fs.isEmpty()) return Optional.of(fs.getFluid());
        }
        return Optional.empty();
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("chat.compactmachinespor.fluid");
    }

    @Override
    public Component getValueName(@Nullable Fluid value) {
        if (value == null) return Component.empty();
        return Component.translatable(value.getFluidType().getDescriptionId());
    }

    @Override
    public IElement getJadeIcon(@Nullable Fluid value) {
        if (value == null) return IElementHelper.get().spacer(0, 0);
        return IElementHelper.get().fluid(JadeFluidObject.of(value)).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
    }

    @Override
    public void registerCapabilities(
            RegisterCapabilitiesEvent event,
            BlockEntityType<InputBlockEntity> inputType,
            BlockEntityType<OutputBlockEntity> outputType,
            BlockEntityType<FactoryBlockEntity> factoryType
    ) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, inputType, (be, side) -> new IFluidHandler() {
            @Override
            public int getTanks() {
                return be.getWhitelistedKeys(FluidResourceType.this).size();
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                if (tank < 0 || tank >= keys.size()) return FluidStack.EMPTY;
                return new FluidStack(keys.get(tank).value(), Integer.MAX_VALUE);
            }

            @Override
            public int getTankCapacity(int tank) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (!be.isActive()) return FluidStack.EMPTY;
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                for (ResourceKey<Fluid> key : keys) {
                    if (key.value() == resource.getFluid()) {
                        if (action == FluidAction.EXECUTE) {
                            be.recordIO(key, resource.getAmount());
                        }
                        return resource.copy();
                    }
                }
                return FluidStack.EMPTY;
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                if (!be.isActive() || maxDrain <= 0) return FluidStack.EMPTY;
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                if (keys.isEmpty()) return FluidStack.EMPTY;
                ResourceKey<Fluid> firstKey = keys.getFirst();
                FluidStack result = new FluidStack(firstKey.value(), maxDrain);
                if (action == FluidAction.EXECUTE) {
                    be.recordIO(firstKey, maxDrain);
                }
                return result;
            }
        });

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, outputType, (be, side) -> new IFluidHandler() {
            @Override
            public int getTanks() {
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                return keys.isEmpty() ? 1 : keys.size();
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                if (keys.isEmpty() || tank < 0 || tank >= keys.size()) return FluidStack.EMPTY;
                return new FluidStack(keys.get(tank).value(), 1);
            }

            @Override
            public int getTankCapacity(int tank) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                if (keys.isEmpty()) return true;
                for (ResourceKey<Fluid> key : keys) {
                    if (key.value() == stack.getFluid()) return true;
                }
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (!be.isActive()) return 0;
                List<ResourceKey<Fluid>> keys = be.getWhitelistedKeys(FluidResourceType.this);
                boolean matches = keys.isEmpty();
                if (!matches) {
                    for (ResourceKey<Fluid> key : keys) {
                        if (key.value() == resource.getFluid()) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    if (action == FluidAction.EXECUTE) {
                        be.recordIO(new ResourceKey<>(FluidResourceType.this, resource.getFluid()), resource.getAmount());
                    }
                    return resource.getAmount();
                }
                return 0;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        });

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, factoryType, (be, side) -> new IFluidHandler() {
            @Override
            public int getTanks() {
                return be.getKeys(true, FluidResourceType.this).size() + be.getKeys(false, FluidResourceType.this).size();
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                List<ResourceKey<Fluid>> inputKeys = be.getKeys(true, FluidResourceType.this);
                if (tank < inputKeys.size()) {
                    ResourceKey<Fluid> key = inputKeys.get(tank);
                    ResourceContainer c = be.getContainer(true, key);
                    return c != null ? new FluidStack(key.value(), (int) Math.min(c.amount, Integer.MAX_VALUE)) : FluidStack.EMPTY;
                } else {
                    int outTank = tank - inputKeys.size();
                    List<ResourceKey<Fluid>> outputKeys = be.getKeys(false, FluidResourceType.this);
                    if (outTank < outputKeys.size()) {
                        ResourceKey<Fluid> key = outputKeys.get(outTank);
                        ResourceContainer c = be.getContainer(false, key);
                        return c != null ? new FluidStack(key.value(), (int) Math.min(c.amount, Integer.MAX_VALUE)) : FluidStack.EMPTY;
                    }
                }
                return FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                List<ResourceKey<Fluid>> inputKeys = be.getKeys(true, FluidResourceType.this);
                if (tank < inputKeys.size()) {
                    ResourceContainer c = be.getContainer(true, inputKeys.get(tank));
                    return c != null ? (int) Math.min(c.capacity, Integer.MAX_VALUE) : 0;
                } else {
                    int outTank = tank - inputKeys.size();
                    List<ResourceKey<Fluid>> outputKeys = be.getKeys(false, FluidResourceType.this);
                    if (outTank < outputKeys.size()) {
                        ResourceContainer c = be.getContainer(false, outputKeys.get(outTank));
                        return c != null ? (int) Math.min(c.capacity, Integer.MAX_VALUE) : 0;
                    }
                }
                return 0;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                List<ResourceKey<Fluid>> inputKeys = be.getKeys(true, FluidResourceType.this);
                if (tank < inputKeys.size()) {
                    return inputKeys.get(tank).value() == stack.getFluid();
                }
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                List<ResourceKey<Fluid>> inputKeys = be.getKeys(true, FluidResourceType.this);
                int totalFilled = 0;
                FluidStack toFill = resource.copy();
                for (ResourceKey<Fluid> key : inputKeys) {
                    if (key.value() == toFill.getFluid()) {
                        ResourceContainer c = be.getContainer(true, key);
                        if (c != null) {
                            long space = c.capacity - c.amount;
                            int filled = (int) Math.min(space, toFill.getAmount());
                            if (action.execute() && filled > 0) {
                                c.amount += filled;
                                be.setChanged();
                            }
                            totalFilled += filled;
                            toFill.shrink(filled);
                            if (toFill.isEmpty()) break;
                        }
                    }
                }
                return totalFilled;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                List<ResourceKey<Fluid>> outputKeys = be.getKeys(false, FluidResourceType.this);
                int totalDrained = 0;
                for (ResourceKey<Fluid> key : outputKeys) {
                    if (key.value() == resource.getFluid()) {
                        ResourceContainer c = be.getContainer(false, key);
                        if (c != null) {
                            int drained = (int) Math.min(c.amount, resource.getAmount() - totalDrained);
                            if (action.execute() && drained > 0) {
                                c.amount -= drained;
                                be.setChanged();
                                if (!be.lastSuccess && be.isReady()) be.operate();
                            }
                            totalDrained += drained;
                            if (totalDrained >= resource.getAmount()) break;
                        }
                    }
                }
                return new FluidStack(resource.getFluid(), totalDrained);
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                List<ResourceKey<Fluid>> outputKeys = be.getKeys(false, FluidResourceType.this);
                for (ResourceKey<Fluid> key : outputKeys) {
                    ResourceContainer c = be.getContainer(false, key);
                    if (c != null && c.amount > 0) {
                        int toDrain = (int) Math.min(c.amount, maxDrain);
                        FluidStack result = new FluidStack(key.value(), toDrain);
                        if (action.execute()) {
                            c.amount -= toDrain;
                            be.setChanged();
                            if (!be.lastSuccess && be.isReady()) be.operate();
                        }
                        return result;
                    }
                }
                return FluidStack.EMPTY;
            }
        });
    }
}
