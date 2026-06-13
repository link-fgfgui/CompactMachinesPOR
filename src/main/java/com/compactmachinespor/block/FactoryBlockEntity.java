package com.compactmachinespor.block;

import com.compactmachinespor.Cyumocompactmachinespor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FactoryBlockEntity extends RoomCodeBlockEntity {
    public static class Container {
        public final int capacity;
        public int amount;

        public Container(int capacity, int amount) {
            this.capacity = capacity;
            this.amount = amount;
        }
    }

    private int tickCount = 0;
    private boolean lastSuccess = true;
    private final Map<Item, Container> inputItems = new LinkedHashMap<>();
    private final Map<Item, Container> outputItems = new LinkedHashMap<>();

    private final Map<Fluid, Container> inputFluids = new LinkedHashMap<>();
    private final Map<Fluid, Container> outputFluids = new LinkedHashMap<>();

    private final List<Item> inputItemList = new ArrayList<>();
    private final List<Item> outputItemList = new ArrayList<>();
    private final List<Fluid> inputFluidList = new ArrayList<>();
    private final List<Fluid> outputFluidList = new ArrayList<>();

    private EnergyStorage inputEnergy = null;
    private EnergyStorage outputEnergy = null;

    private void updateLists() {
        inputItemList.clear();
        inputItemList.addAll(inputItems.keySet());
        outputItemList.clear();
        outputItemList.addAll(outputItems.keySet());
        inputFluidList.clear();
        inputFluidList.addAll(inputFluids.keySet());
        outputFluidList.clear();
        outputFluidList.addAll(outputFluids.keySet());
    }

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return inputItemList.size() + outputItemList.size();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            int inputSize = inputItemList.size();
            if (slot < inputSize) {
                Item item = inputItemList.get(slot);
                Container c = inputItems.get(item);
                return new ItemStack(item, c.amount);
            } else {
                int outputSlot = slot - inputSize;
                if (outputSlot < outputItemList.size()) {
                    Item item = outputItemList.get(outputSlot);
                    Container c = outputItems.get(item);
                    return new ItemStack(item, c.amount);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            int inputSize = inputItemList.size();
            if (slot < inputSize) {
                Item item = inputItemList.get(slot);
                if (item == stack.getItem()) {
                    Container c = inputItems.get(item);
                    int space = c.capacity - c.amount;
                    int toAdd = Math.min(space, stack.getCount());
                    if (!simulate && toAdd > 0) {
                        c.amount += toAdd;
                        setChanged();
                    }
                    if (toAdd == stack.getCount()) return ItemStack.EMPTY;
                    ItemStack result = stack.copy();
                    result.shrink(toAdd);
                    return result;
                }
            }
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            int inputSize = inputItemList.size();
            if (slot >= inputSize) {
                int outputSlot = slot - inputSize;
                if (outputSlot < outputItemList.size()) {
                    Item item = outputItemList.get(outputSlot);
                    Container c = outputItems.get(item);
                    int toExtract = Math.min(c.amount, amount);
                    ItemStack result = new ItemStack(item, toExtract);
                    if (!simulate && toExtract > 0) {
                        c.amount -= toExtract;
                        setChanged();
                        if (!lastSuccess && isReady(FactoryBlockEntity.this)) {
                            operate(FactoryBlockEntity.this);
                        }
                    }
                    return result;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int inputSize = inputItemList.size();
            if (slot < inputSize) {
                return inputItems.get(inputItemList.get(slot)).capacity;
            } else {
                int outputSlot = slot - inputSize;
                if (outputSlot < outputItemList.size()) {
                    return outputItems.get(outputItemList.get(outputSlot)).capacity;
                }
            }
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            int inputSize = inputItemList.size();
            if (slot < inputSize) {
                return inputItemList.get(slot) == stack.getItem();
            }
            return false;
        }
    };

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return inputFluidList.size() + outputFluidList.size();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            int inputSize = inputFluidList.size();
            if (tank < inputSize) {
                Fluid fluid = inputFluidList.get(tank);
                Container c = inputFluids.get(fluid);
                return new FluidStack(fluid, c.amount);
            } else {
                int outputTank = tank - inputSize;
                if (outputTank < outputFluidList.size()) {
                    Fluid fluid = outputFluidList.get(outputTank);
                    Container c = outputFluids.get(fluid);
                    return new FluidStack(fluid, c.amount);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            int inputSize = inputFluidList.size();
            if (tank < inputSize) {
                return inputFluids.get(inputFluidList.get(tank)).capacity;
            } else {
                int outputTank = tank - inputSize;
                if (outputTank < outputFluidList.size()) {
                    return outputFluids.get(outputFluidList.get(outputTank)).capacity;
                }
            }
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            int inputSize = inputFluidList.size();
            if (tank < inputSize) {
                return inputFluidList.get(tank) == stack.getFluid();
            }
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int totalFilled = 0;
            FluidStack toFill = resource.copy();
            for (Map.Entry<Fluid, Container> entry : inputFluids.entrySet()) {
                if (entry.getKey() == toFill.getFluid()) {
                    Container c = entry.getValue();
                    int space = c.capacity - c.amount;
                    int filled = Math.min(space, toFill.getAmount());
                    if (action.execute() && filled > 0) {
                        c.amount += filled;
                        setChanged();
                    }
                    totalFilled += filled;
                    toFill.shrink(filled);
                    if (toFill.isEmpty()) break;
                }
            }
            return totalFilled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            int totalDrained = 0;
            for (Map.Entry<Fluid, Container> entry : outputFluids.entrySet()) {
                if (entry.getKey() == resource.getFluid()) {
                    Container c = entry.getValue();
                    int drained = Math.min(c.amount, resource.getAmount() - totalDrained);
                    if (action.execute() && drained > 0) {
                        c.amount -= drained;
                        setChanged();
                        if (!lastSuccess) operate(FactoryBlockEntity.this);
                    }
                    totalDrained += drained;
                    if (totalDrained >= resource.getAmount()) break;
                }
            }
            return new FluidStack(resource.getFluid(), totalDrained);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            for (Map.Entry<Fluid, Container> entry : outputFluids.entrySet()) {
                Container c = entry.getValue();
                if (c.amount > 0) {
                    int toDrain = Math.min(c.amount, maxDrain);
                    FluidStack result = new FluidStack(entry.getKey(), toDrain);
                    if (action.execute()) {
                        c.amount -= toDrain;
                        setChanged();
                    }
                    return result;
                }
            }
            return FluidStack.EMPTY;
        }
    };

    private final IEnergyStorage energyHandler = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (inputEnergy == null) return 0;
            int received = inputEnergy.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) setChanged();
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (outputEnergy == null) return 0;
            int extracted = outputEnergy.extractEnergy(maxExtract, simulate);
            if (!simulate && extracted > 0) {
                setChanged();
                if (!lastSuccess) operate(FactoryBlockEntity.this);
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            int total = 0;
            if (inputEnergy != null) total += inputEnergy.getEnergyStored();
            if (outputEnergy != null) total += outputEnergy.getEnergyStored();
            return total;
        }

        @Override
        public int getMaxEnergyStored() {
            int total = 0;
            if (inputEnergy != null) total += inputEnergy.getMaxEnergyStored();
            if (outputEnergy != null) total += outputEnergy.getMaxEnergyStored();
            return total;
        }

        @Override
        public boolean canExtract() {
            return outputEnergy != null;
        }

        @Override
        public boolean canReceive() {
            return inputEnergy != null;
        }
    };

    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(Cyumocompactmachinespor.FACTORY_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void loadCommon(CompoundTag tag) {
        super.loadCommon(tag);
        inputItems.clear();
        loadItemMap(tag, "input_items", inputItems);
        outputItems.clear();
        loadItemMap(tag, "output_items", outputItems);
        inputFluids.clear();
        loadFluidMap(tag, "input_fluids", inputFluids);
        outputFluids.clear();
        loadFluidMap(tag, "output_fluids", outputFluids);
        inputEnergy = loadEnergy(tag, "input_energy");
        outputEnergy = loadEnergy(tag, "output_energy");
        if (tag.contains("original_attachments")) {
            originalAttachments = tag.getCompound("original_attachments");
        }
        updateLists();
    }

    @Override
    protected void saveCommon(CompoundTag tag) {
        super.saveCommon(tag);
        saveItemMap(tag, "input_items", inputItems);
        saveItemMap(tag, "output_items", outputItems);
        saveFluidMap(tag, "input_fluids", inputFluids);
        saveFluidMap(tag, "output_fluids", outputFluids);
        saveEnergy(tag, "input_energy", inputEnergy);
        saveEnergy(tag, "output_energy", outputEnergy);
        if (originalAttachments != null) {
            tag.put("original_attachments", originalAttachments);
        }
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public IEnergyStorage getEnergyHandler() {
        return energyHandler;
    }

    // Only call it once for the block, so it's safe to do no clear.
    public void initTanks(Map<Holder<?>, Double> inputMap, Map<Holder<?>, Double> outputMap) {
        parseConfigMap(inputMap, true);
        parseConfigMap(outputMap, false);
        updateLists();
        setChanged();
    }

    private void parseConfigMap(Map<Holder<?>, Double> configMap, boolean isInput) {
        for (Map.Entry<Holder<?>, Double> entry : configMap.entrySet()) {
            Holder<?> holder = entry.getKey();
            Object value = holder.value();
            int capacity = (int) Math.floor(entry.getValue() * 20);
            if (capacity <= 0) continue;

            switch (value) {
                case null -> {
                    if (isInput) {
                        inputEnergy = new EnergyStorage(capacity);
                    } else {
                        outputEnergy = new EnergyStorage(capacity);
                    }
                }
                case Item item -> {
                    if (isInput) {
                        inputItems.put(item, new Container(capacity, 0));
                    } else {
                        outputItems.put(item, new Container(capacity, 0));
                    }
                }
                case Fluid fluid -> {
                    if (isInput) {
                        inputFluids.put(fluid, new Container(capacity, 0));
                    } else {
                        outputFluids.put(fluid, new Container(capacity, 0));
                    }
                }
                default -> {
                }
            }
        }
    }

    public static boolean checkMap(Map<?, Container> map, boolean fullOrEmpty) {
        if (checkNull(map)) return true;
        for (Container c : map.values()) {
            if (c.amount != (fullOrEmpty ? c.capacity : 0)) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkEnergy(EnergyStorage energy, boolean fullOrEmpty) {
        if (checkNull(energy)) return true;
        return fullOrEmpty ? energy.getEnergyStored() == energy.getMaxEnergyStored() : energy.getEnergyStored() == 0;
    }

    public static boolean checkNull(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return obj == null;
    }

    public static boolean isReady(FactoryBlockEntity be) {
        return checkMap(be.inputItems, true) &&
                checkMap(be.inputFluids, true) &&
                checkEnergy(be.inputEnergy, true) &&
                checkMap(be.outputItems, false) &&
                checkMap(be.outputFluids, false) &&
                checkEnergy(be.outputEnergy, false);
    }

    public static void operate(FactoryBlockEntity be) {
        for (Container c : be.inputItems.values()) {
            c.amount = 0;
        }
        for (Container c : be.inputFluids.values()) {
            c.amount = 0;
        }
        if (be.inputEnergy != null) {
            be.inputEnergy.extractEnergy(be.inputEnergy.getEnergyStored(), false);
        }

        for (Container c : be.outputItems.values()) {
            c.amount = c.capacity;
        }
        for (Container c : be.outputFluids.values()) {
            c.amount = c.capacity;
        }
        if (be.outputEnergy != null) {
            be.outputEnergy.receiveEnergy(be.outputEnergy.getMaxEnergyStored(), false);
        }
        be.setChanged();
        be.lastSuccess = true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FactoryBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickCount++;
        if (be.tickCount == 20) {
            be.tickCount = 0;
            if (isReady(be)) {
                operate(be);
            } else {
                be.lastSuccess = false;
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        CustomData customData = componentInput.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            loadCommon(customData.copyTag());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        CompoundTag tag = new CompoundTag();
        saveCommon(tag);
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void saveItemMap(CompoundTag tag, String key, Map<Item, Container> map) {
        CompoundTag mapTag = new CompoundTag();
        for (Map.Entry<Item, Container> entry : map.entrySet()) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(entry.getKey());
            CompoundTag c = new CompoundTag();
            c.putInt("capacity", entry.getValue().capacity);
            c.putInt("amount", entry.getValue().amount);
            mapTag.put(rl.toString(), c);
        }
        tag.put(key, mapTag);
    }

    public static void loadItemMap(CompoundTag tag, String key, Map<Item, Container> map) {
        CompoundTag mapTag = tag.getCompound(key);
        for (String k : mapTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(k);
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                Item item = BuiltInRegistries.ITEM.get(id);
                CompoundTag c = mapTag.getCompound(k);
                map.put(item, new Container(c.getInt("capacity"), c.getInt("amount")));
            }
        }
    }

    public static void saveFluidMap(CompoundTag tag, String key, Map<Fluid, Container> map) {
        CompoundTag mapTag = new CompoundTag();
        for (Map.Entry<Fluid, Container> entry : map.entrySet()) {
            ResourceLocation rl = BuiltInRegistries.FLUID.getKey(entry.getKey());
            CompoundTag c = new CompoundTag();
            c.putInt("capacity", entry.getValue().capacity);
            c.putInt("amount", entry.getValue().amount);
            mapTag.put(rl.toString(), c);
        }
        tag.put(key, mapTag);
    }

    public static void loadFluidMap(CompoundTag tag, String key, Map<Fluid, Container> map) {
        CompoundTag mapTag = tag.getCompound(key);
        for (String k : mapTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(k);
            if (BuiltInRegistries.FLUID.containsKey(id)) {
                Fluid fluid = BuiltInRegistries.FLUID.get(id);
                CompoundTag c = mapTag.getCompound(k);
                map.put(fluid, new Container(c.getInt("capacity"), c.getInt("amount")));
            }
        }
    }

    public static void saveEnergy(CompoundTag tag, String key, EnergyStorage energy) {
        if (energy != null) {
            CompoundTag c = new CompoundTag();
            c.putInt("capacity", energy.getMaxEnergyStored());
            c.putInt("energy", energy.getEnergyStored());
            tag.put(key, c);
        }
    }

    public static EnergyStorage loadEnergy(CompoundTag tag, String key) {
        if (tag.contains(key)) {
            CompoundTag c = tag.getCompound(key);
            EnergyStorage e = new EnergyStorage(c.getInt("capacity"));
            e.receiveEnergy(c.getInt("energy"), false);
            return e;
        }
        return null;
    }

    // 原始 machine_color 所在 neoforge:attachments，由 Core.finish() 设置
    private CompoundTag originalAttachments;

    public CompoundTag getOriginalAttachments() {
        return originalAttachments;
    }

    public void setOriginalAttachments(CompoundTag attachments) {
        this.originalAttachments = attachments;
        setChanged();
    }

    public List<List<?>> getForShow() {
        return List.of(inputItemList, inputFluidList, outputItemList, outputFluidList);
    }
}