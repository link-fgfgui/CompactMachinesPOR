package com.compactmachinespor.resource.impl;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.block.InputBlockEntity;
import com.compactmachinespor.block.OutputBlockEntity;
import com.compactmachinespor.block.RoomCodeBlockEntity;
import com.compactmachinespor.resource.ResourceContainer;
import com.compactmachinespor.resource.ResourceKey;
import com.compactmachinespor.resource.ResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;
import java.util.Optional;

public class ItemResourceType extends ResourceType<Item> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Cyumocompactmachinespor.MODID, "item");
    public static final ItemResourceType INSTANCE = new ItemResourceType();

    public ItemResourceType() {
        super(ID, 0);
    }

    @Override
    public Tag serializeValue(Item value) {
        return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(value).toString());
    }

    @Override
    public ResourceKey<Item> deserializeKey(CompoundTag tag) {
        if (!tag.contains("value")) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("value"));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        return new ResourceKey<>(this, BuiltInRegistries.ITEM.get(id));
    }

    @Override
    public Optional<Item> extractFromHand(ItemStack heldStack) {
        if (heldStack.isEmpty()) return Optional.empty();
        if (heldStack.is(RoomCodeBlockEntity.WRENCH) || heldStack.is(RoomCodeBlockEntity.WRENCH2) || heldStack.is(Cyumocompactmachinespor.LAUNCHER_STICK.get())) {
            return Optional.empty();
        }
        return Optional.of(heldStack.getItem());
    }

    @Override
    public Component getTypeName() {
        return Component.translatable("chat.compactmachinespor.item");
    }

    @Override
    public Component getValueName(@Nullable Item value) {
        if (value == null) return Component.empty();
        return new ItemStack(value).getHoverName();
    }

    @Override
    public IElement getJadeIcon(@Nullable Item value) {
        if (value == null) return IElementHelper.get().spacer(0, 0);
        return IElementHelper.get().item(new ItemStack(value), 0.5f).size(new Vec2(10, 10)).translate(new Vec2(0, -1));
    }

    @Override
    public void registerCapabilities(
            RegisterCapabilitiesEvent event,
            BlockEntityType<InputBlockEntity> inputType,
            BlockEntityType<OutputBlockEntity> outputType,
            BlockEntityType<FactoryBlockEntity> factoryType
    ) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, inputType, (be, side) -> new IItemHandler() {
            @Override
            public int getSlots() {
                return be.getWhitelistedKeys(ItemResourceType.this).size();
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                if (slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
                return new ItemStack(keys.get(slot).value(), Integer.MAX_VALUE);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!be.isActive()) return ItemStack.EMPTY;
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                if (slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
                ResourceKey<Item> key = keys.get(slot);
                ItemStack result = new ItemStack(key.value(), amount);
                if (!simulate) {
                    be.recordIO(key, amount);
                }
                return result;
            }

            @Override
            public int getSlotLimit(int slot) {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return false;
            }
        });

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, outputType, (be, side) -> new IItemHandler() {
            @Override
            public int getSlots() {
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                return keys.isEmpty() ? 1 : keys.size();
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                if (keys.isEmpty() || slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
                return new ItemStack(keys.get(slot).value(), 1);
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!be.isActive()) return stack;
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                boolean matches = keys.isEmpty();
                if (!matches) {
                    for (ResourceKey<Item> key : keys) {
                        if (key.value() == stack.getItem()) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    if (!simulate) {
                        be.recordIO(new ResourceKey<>(ItemResourceType.this, stack.getItem()), stack.getCount());
                    }
                    return ItemStack.EMPTY;
                }
                return stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                List<ResourceKey<Item>> keys = be.getWhitelistedKeys(ItemResourceType.this);
                if (keys.isEmpty()) return true;
                for (ResourceKey<Item> key : keys) {
                    if (key.value() == stack.getItem()) return true;
                }
                return false;
            }
        });

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, factoryType, (be, side) -> new IItemHandler() {
            @Override
            public int getSlots() {
                return be.getKeys(true, ItemResourceType.this).size() + be.getKeys(false, ItemResourceType.this).size();
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                List<ResourceKey<Item>> inputKeys = be.getKeys(true, ItemResourceType.this);
                if (slot < inputKeys.size()) {
                    ResourceKey<Item> key = inputKeys.get(slot);
                    ResourceContainer c = be.getContainer(true, key);
                    return c != null ? new ItemStack(key.value(), (int) Math.min(c.amount, Integer.MAX_VALUE)) : ItemStack.EMPTY;
                } else {
                    int outSlot = slot - inputKeys.size();
                    List<ResourceKey<Item>> outputKeys = be.getKeys(false, ItemResourceType.this);
                    if (outSlot < outputKeys.size()) {
                        ResourceKey<Item> key = outputKeys.get(outSlot);
                        ResourceContainer c = be.getContainer(false, key);
                        return c != null ? new ItemStack(key.value(), (int) Math.min(c.amount, Integer.MAX_VALUE)) : ItemStack.EMPTY;
                    }
                }
                return ItemStack.EMPTY;
            }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                List<ResourceKey<Item>> inputKeys = be.getKeys(true, ItemResourceType.this);
                if (slot < inputKeys.size()) {
                    ResourceKey<Item> key = inputKeys.get(slot);
                    if (key.value() == stack.getItem()) {
                        ResourceContainer c = be.getContainer(true, key);
                        if (c != null) {
                            long space = c.capacity - c.amount;
                            int toAdd = (int) Math.min(space, stack.getCount());
                            if (!simulate && toAdd > 0) {
                                c.amount += toAdd;
                                be.setChanged();
                            }
                            if (toAdd == stack.getCount()) return ItemStack.EMPTY;
                            ItemStack res = stack.copy();
                            res.shrink(toAdd);
                            return res;
                        }
                    }
                }
                return stack;
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                List<ResourceKey<Item>> inputKeys = be.getKeys(true, ItemResourceType.this);
                if (slot >= inputKeys.size()) {
                    int outSlot = slot - inputKeys.size();
                    List<ResourceKey<Item>> outputKeys = be.getKeys(false, ItemResourceType.this);
                    if (outSlot < outputKeys.size()) {
                        ResourceKey<Item> key = outputKeys.get(outSlot);
                        ResourceContainer c = be.getContainer(false, key);
                        if (c != null) {
                            int toExtract = (int) Math.min(c.amount, amount);
                            ItemStack result = new ItemStack(key.value(), toExtract);
                            if (!simulate && toExtract > 0) {
                                c.amount -= toExtract;
                                be.setChanged();
                                if (!be.lastSuccess && be.isReady()) {
                                    be.operate();
                                }
                            }
                            return result;
                        }
                    }
                }
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                List<ResourceKey<Item>> inputKeys = be.getKeys(true, ItemResourceType.this);
                if (slot < inputKeys.size()) {
                    ResourceContainer c = be.getContainer(true, inputKeys.get(slot));
                    return c != null ? (int) Math.min(c.capacity, Integer.MAX_VALUE) : 0;
                } else {
                    int outSlot = slot - inputKeys.size();
                    List<ResourceKey<Item>> outputKeys = be.getKeys(false, ItemResourceType.this);
                    if (outSlot < outputKeys.size()) {
                        ResourceContainer c = be.getContainer(false, outputKeys.get(outSlot));
                        return c != null ? (int) Math.min(c.capacity, Integer.MAX_VALUE) : 0;
                    }
                }
                return 0;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                List<ResourceKey<Item>> inputKeys = be.getKeys(true, ItemResourceType.this);
                if (slot < inputKeys.size()) {
                    return inputKeys.get(slot).value() == stack.getItem();
                }
                return false;
            }
        });
    }
}
