package com.compactmachinespor.core;

import com.compactmachinespor.Config;
import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.EvaluatorBlockEntity;
import com.compactmachinespor.block.BaseIOBlock;
import com.compactmachinespor.block.BaseIOBlockEntity;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.core.Machine.InventorySnapshot;
import dev.compactmods.machines.api.CompactMachines;
import dev.compactmods.machines.api.component.CMDataComponents;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.machine.MachineColor;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.api.room.spatial.IRoomBoundaries;
import dev.compactmods.machines.server.CompactMachinesServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.compactmods.machines.machine.Machines.Items.BOUND_MACHINE;
import static net.minecraft.world.phys.shapes.Shapes.EPSILON;


public class Core {

    public enum CreateResult {
        SUCCESS,
        ABORTED_ROOM_ALREADY_EXISTS,
        ABORTED_SUSPICIOUS_BLOCKS
    }
    private static final Map<String, Machine> MACHINES = new ConcurrentHashMap<>();
    private static final Map<String, UUID> ROOM2UUID = new ConcurrentHashMap<>();

    public static Map<String, Machine> getMachines() {
        return MACHINES;
    }

    public static CreateResult createMachine(ServerLevel overworldLevel, String roomCode, BlockPos targetPos) {
        if (MACHINES.containsKey(roomCode)) return CreateResult.ABORTED_ROOM_ALREADY_EXISTS;

        ServerLevel compactWorld = overworldLevel.getServer().getLevel(CompactDimension.LEVEL_KEY);
        if (compactWorld == null) return CreateResult.ABORTED_SUSPICIOUS_BLOCKS;

        // 必须先生成 UUID，再 loadRoom（forceRoom 需要 UUID 来强制加载区块）
        UUID machineUUID = UUID.randomUUID();
        ROOM2UUID.put(roomCode, machineUUID);
        loadRoom(compactWorld, roomCode);

        // 第一层：黑名单检测
        if (hasSuspiciousBlocks(compactWorld, roomCode)) {
            unLoadRoom(compactWorld, roomCode);
            ROOM2UUID.remove(roomCode);
            return CreateResult.ABORTED_SUSPICIOUS_BLOCKS;
        }

        MACHINES.put(roomCode, new Machine(getTicks(overworldLevel), roomCode, targetPos));

        // 保存原机器的 neoforge:attachments（含 machine_color）用于工厂还原
        BlockEntity evBe = overworldLevel.getBlockEntity(targetPos);
        if (evBe instanceof EvaluatorBlockEntity evaluator) {
            CompoundTag savedNbt = evaluator.getSavedOriginalNbt();
            if (savedNbt != null && savedNbt.contains("neoforge:attachments")) {
                getMachine(roomCode).originalAttachments = savedNbt.getCompound("neoforge:attachments").copy();
            }
        }

        // 第二层：库存基线审计 — 记录 S0
        if (Config.ENABLE_INVENTORY_AUDIT.get()) {
            Machine machine = getMachine(roomCode);
            machine.inventoryStart = scanInventory(compactWorld, roomCode);
        }

        scanRoom(compactWorld, roomCode);
        return CreateResult.SUCCESS;
    }


    public static Machine getMachine(String roomCode) {
        return MACHINES.get(roomCode);
    }

    public static void setMachineData(String roomCode, Machine.DataSetType type, Holder<?> id, int data, long tickTime) {
        getMachine(roomCode).addData(type, id, data, tickTime);
    }

    public static long getTicks(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    public static void loadRoom(ServerLevel level, String roomCode) {
        forceRoom(level, roomCode, true);
    }

    public static void unLoadRoom(ServerLevel level, String roomCode) {
        forceRoom(level, roomCode, false);
    }

    public static void forceRoom(ServerLevel level, String roomCode, boolean add) {
        Objects.requireNonNull(getRoomBoundaries(level, roomCode)).innerChunkPositions().forEach(
                chunkPos -> CompactMachinesServer.CHUNK_TICKET_CONTROLLER.forceChunk(
                        level,
                        ROOM2UUID.get(roomCode),
                        chunkPos.x, chunkPos.z, add, true
                )
        );

    }

    public static void scanRoom(ServerLevel compactWorld, String roomCode) {
        AABB roomAABB = Objects.requireNonNull(getRoomBoundaries(compactWorld, roomCode)).outerBounds();
        int startX = (int) Math.floor(roomAABB.minX);
        int startY = (int) Math.floor(roomAABB.minY);
        int startZ = (int) Math.floor(roomAABB.minZ);
        int endX = (int) Math.floor(roomAABB.maxX - EPSILON);
        int endY = (int) Math.floor(roomAABB.maxY - EPSILON);
        int endZ = (int) Math.floor(roomAABB.maxZ - EPSILON);

        AntiCheat.runPreScan(compactWorld, roomCode, roomAABB);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    processBlock(compactWorld, x, y, z, roomCode);
                }
            }
        }

        AntiCheat.runPostScan(compactWorld, roomCode, roomAABB);

        Machine machine = getMachine(roomCode);
        if (machine != null) {
            for (BlockPos pos : machine.IOBlocks) {
                BlockState state = compactWorld.getBlockState(pos);
                if (state.hasProperty(BaseIOBlock.ACTIVE)) {
                    compactWorld.setBlock(pos, state.setValue(BaseIOBlock.ACTIVE, true), Block.UPDATE_ALL);
                }
            }
        }

        Objects.requireNonNull(getRoomBoundaries(compactWorld, roomCode)).innerChunkPositions().forEach(
                chunkPos -> compactWorld.getChunk(chunkPos.x, chunkPos.z).setUnsaved(true));
    }

    private static void processBlock(ServerLevel level, int x, int y, int z, String roomCode) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState blockState = level.getBlockState(pos);
        AntiCheat.runScanBlock(level, pos, blockState, roomCode);
        if (blockState.is(Cyumocompactmachinespor.INPUT_BLOCK) || blockState.is(Cyumocompactmachinespor.OUTPUT_BLOCK)) {
            getMachine(roomCode).IOBlocks.add(pos);
            ((BaseIOBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos))).setRoomCode(roomCode);
        }
    }

    @Nullable
    public static IRoomBoundaries getRoomBoundaries(ServerLevel level, String roomCode) {
        Optional<RoomInstance> room = CompactMachines.room(level.getServer(), roomCode);
        return room.map(RoomInstance::boundaries).orElse(null);
    }

    public static void finish(String roomCode, BlockPos overworldPos) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        ServerLevel compactWorld = server.getLevel(CompactDimension.LEVEL_KEY);
        if (compactWorld == null) {
            MACHINES.remove(roomCode);
            return;
        }
        Machine machine = getMachine(roomCode);

        // 库存基线审计 — 记录 S1 并修正产出
        if (Config.ENABLE_INVENTORY_AUDIT.get() && machine != null) {
            machine.inventoryEnd = scanInventory(compactWorld, roomCode);
        }

        Map<Holder<?>, Double> inputData = calculate(machine.InputData);
        if (machine.EnergyData != null) {
            inputData.put(Holder.direct(null), RateEvaluator.evaluateStableRate(machine.EnergyData.getFirst().data()));
        }
        Map<Holder<?>, Double> outputData = calculate(machine.OutputData);
        if (machine.EnergyData != null)
            outputData.put(Holder.direct(null), RateEvaluator.evaluateStableRate(machine.EnergyData.getLast().data()));

        // 用库存基线审计修正 input/output 数据
        if (Config.ENABLE_INVENTORY_AUDIT.get()) {
            auditProduction(machine, inputData, outputData);
        }

        machine.IOBlocks.forEach(
                pos ->
                        compactWorld.setBlock(
                                pos,
                                compactWorld.getBlockState(pos)
                                        .setValue(BaseIOBlock.ACTIVE, false),
                                Block.UPDATE_ALL
                        )
        );
        Objects.requireNonNull(getRoomBoundaries(compactWorld, roomCode)).innerChunkPositions().forEach(
                chunkPos -> compactWorld.getChunk(chunkPos.x, chunkPos.z).setUnsaved(true));
        unLoadRoom(compactWorld, roomCode);
        replaceBlock(overworld, overworldPos, Cyumocompactmachinespor.FACTORY_BLOCK);
        FactoryBlockEntity be = (FactoryBlockEntity) Objects.requireNonNull(overworld.getBlockEntity(overworldPos));
        be.setRoomCode(roomCode);
        be.initTanks(inputData, outputData);
        // 传递原始 machine_color 附件数据到工厂方块
        if (machine.originalAttachments != null) {
            be.setOriginalAttachments(machine.originalAttachments);
        }
        MACHINES.remove(roomCode);
        ROOM2UUID.remove(roomCode);
        compactWorld.getChunkSource().tick(() -> true, false);
    }

    public static Map<Holder<?>, Double> calculate(Map<Holder<?>, Machine.Data> dataMap) {
        return dataMap
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> RateEvaluator.evaluateStableRate(e.getValue().data())
                        )
                );
    }

    public static void replaceBlock(ServerLevel level, BlockPos pos, Holder<Block> target) {
        level.removeBlockEntity(pos);
        level.setBlockAndUpdate(pos, target.value().defaultBlockState());
    }

    public static ItemStack unpackToItem(String roomCode) {
        ItemStack stack = BOUND_MACHINE.toStack();
        stack.set(CMDataComponents.BOUND_ROOM_CODE, roomCode);
        stack.set(CMDataComponents.MACHINE_COLOR, MachineColor.fromARGB(0xFFC95B13));
        return stack;
    }

    // ========== 反作弊：黑名单检测 ==========

    /**
     * 扫描房间内是否包含黑名单中的可疑 Mod/方块
     */
    private static boolean hasSuspiciousBlocks(ServerLevel compactWorld, String roomCode) {
        var suspiciousMods = Config.SUSPICIOUS_MODS.get();
        var suspiciousBlocks = Config.SUSPICIOUS_BLOCKS.get();
        if (suspiciousMods.isEmpty() && suspiciousBlocks.isEmpty()) return false;

        AABB roomAABB = Objects.requireNonNull(getRoomBoundaries(compactWorld, roomCode)).outerBounds();
        int startX = (int) Math.floor(roomAABB.minX);
        int startY = (int) Math.floor(roomAABB.minY);
        int startZ = (int) Math.floor(roomAABB.minZ);
        int endX = (int) Math.floor(roomAABB.maxX - EPSILON);
        int endY = (int) Math.floor(roomAABB.maxY - EPSILON);
        int endZ = (int) Math.floor(roomAABB.maxZ - EPSILON);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = compactWorld.getBlockState(pos);
                    if (state.isAir()) continue;
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (suspiciousMods.contains(id.getNamespace())) return true;
                    if (suspiciousBlocks.contains(id.toString())) return true;
                }
            }
        }
        return false;
    }

    // ========== 反作弊：库存基线审计 ==========

    /**
     * 扫描房间内所有容器的库存，生成快照（S0 或 S1）
     * 使用 IdentityHashMap 对 IItemHandler 引用去重（多方块容器）
     */
    public static InventorySnapshot scanInventory(ServerLevel compactWorld, String roomCode) {
        var roomAABB = Objects.requireNonNull(getRoomBoundaries(compactWorld, roomCode)).outerBounds();
        int startX = (int) Math.floor(roomAABB.minX);
        int startY = (int) Math.floor(roomAABB.minY);
        int startZ = (int) Math.floor(roomAABB.minZ);
        int endX = (int) Math.floor(roomAABB.maxX - EPSILON);
        int endY = (int) Math.floor(roomAABB.maxY - EPSILON);
        int endZ = (int) Math.floor(roomAABB.maxZ - EPSILON);

        Map<Holder<?>, Long> items = new ConcurrentHashMap<>();
        Map<Holder<?>, Long> fluids = new ConcurrentHashMap<>();
        long energy = 0;

        // 引用恒等去重：同一个 handler 对象只扫一次
        Set<IItemHandler> seenItemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IFluidHandler> seenFluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IEnergyStorage> seenEnergyHandlers = Collections.newSetFromMap(new IdentityHashMap<>());

        // 本模组方块列表，跳過不扫
        var ownBlocks = Set.of(
                Cyumocompactmachinespor.INPUT_BLOCK.get(),
                Cyumocompactmachinespor.OUTPUT_BLOCK.get(),
                Cyumocompactmachinespor.EVALUATOR_BLOCK.get(),
                Cyumocompactmachinespor.FACTORY_BLOCK.get()
        );

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = compactWorld.getBlockState(pos);
                    if (state.isAir() || ownBlocks.contains(state.getBlock())) continue;

                    // 扫 IItemHandler
                    for (Direction dir : Direction.values()) {
                        IItemHandler itemHandler = compactWorld.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                        if (itemHandler != null && seenItemHandlers.add(itemHandler)) {
                            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                                var stack = itemHandler.getStackInSlot(slot);
                                if (!stack.isEmpty()) {
                                    items.merge(stack.getItemHolder(), (long) stack.getCount(), Long::sum);
                                }
                            }
                            break;
                        }
                    }

                    // 扫 IFluidHandler
                    for (Direction dir : Direction.values()) {
                        IFluidHandler fluidHandler = compactWorld.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
                        if (fluidHandler != null && seenFluidHandlers.add(fluidHandler)) {
                            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                                var fluidStack = fluidHandler.getFluidInTank(tank);
                                if (!fluidStack.isEmpty()) {
                                    fluids.merge(fluidStack.getFluidHolder(), (long) fluidStack.getAmount(), Long::sum);
                                }
                            }
                            break;
                        }
                    }

                    // 扫 IEnergyStorage
                    for (Direction dir : Direction.values()) {
                        IEnergyStorage energyStorage = compactWorld.getCapability(Capabilities.EnergyStorage.BLOCK, pos, dir);
                        if (energyStorage != null && seenEnergyHandlers.add(energyStorage)) {
                            energy += energyStorage.getEnergyStored();
                            break;
                        }
                    }
                }
            }
        }

        return new InventorySnapshot(items, fluids, energy);
    }

    /**
     * 用库存基线审计修正 RateEvaluator 的产出数据
     * realProduction[X] = S1[X] - S0[X] + O[X] - I[X]
     */
    private static void auditProduction(Machine machine, Map<Holder<?>, Double> inputData, Map<Holder<?>, Double> outputData) {
        if (machine.inventoryStart == null || machine.inventoryEnd == null) return;

        InventorySnapshot s0 = machine.inventoryStart;
        InventorySnapshot s1 = machine.inventoryEnd;

        // 处理物品
        for (var entry : outputData.entrySet()) {
            Holder<?> id = entry.getKey();
            if (id.value() == null) continue; // 能量已单独处理

            // 用 Holder 的 item 注册名作为 key 从 S0/S1 中查找
            long s0Count = s0.items().getOrDefault(id, 0L);
            long s1Count = s1.items().getOrDefault(id, 0L);
            long totalO = machine.totalOutput.getOrDefault(id, 0L);
            long totalI = machine.totalInput.getOrDefault(id, 0L);

            // realProduction = S1 - S0 + O - I
            long realTotal = s1Count - s0Count + totalO - totalI;

            if (realTotal <= 0) {
                // 该物品没有被真正产出，从 output 移除
                Cyumocompactmachinespor.LOGGER.debug("Audit: {} realProduction={}, removing from output", id, realTotal);
                entry.setValue(0.0);
            } else {
                // 用真实产出比例修正 RateEvaluator 算出的每秒速率
                double recordedTotal = totalO;
                if (recordedTotal > 0) {
                    double ratio = (double) realTotal / recordedTotal;
                    entry.setValue(entry.getValue() * ratio);
                    Cyumocompactmachinespor.LOGGER.debug("Audit: {} scaled by {} (real={}, recorded={})", id, ratio, realTotal, recordedTotal);
                }
            }
        }

        // 处理能量
        Holder<?> energyKey = Holder.direct(null);
        if (outputData.containsKey(energyKey)) {
            long s0Energy = s0.energy();
            long s1Energy = s1.energy();
            long totalOEnergy = machine.totalOutput.getOrDefault(energyKey, 0L);
            long totalIEnergy = machine.totalInput.getOrDefault(energyKey, 0L);
            long realEnergy = s1Energy - s0Energy + totalOEnergy - totalIEnergy;

            if (realEnergy <= 0) {
                outputData.put(energyKey, 0.0);
            } else if (totalOEnergy > 0) {
                double ratio = (double) realEnergy / totalOEnergy;
                outputData.put(energyKey, outputData.get(energyKey) * ratio);
            }
        }

        // 将消耗物（负 realProduction）归入 inputData
        for (var entry : s0.items().entrySet()) {
            Holder<?> id = entry.getKey();
            if (id.value() == null) continue;

            long s0Count = s0.items().getOrDefault(id, 0L);
            long s1Count = s1.items().getOrDefault(id, 0L);
            long totalO = machine.totalOutput.getOrDefault(id, 0L);
            long totalI = machine.totalInput.getOrDefault(id, 0L);
            long realTotal = s1Count - s0Count + totalO - totalI;

            if (realTotal < 0 && !outputData.containsKey(id)) {
                // 该物品被消耗了，且不是产出物 → 归入 inputData
                double rate = RateEvaluator.evaluateStableRate(
                        machine.InputData.getOrDefault(id, new Machine.Data(new int[1])).data()
                );
                if (rate <= 0) {
                    // RateEvaluator 没有记录到消耗速率，用平均速率近似
                    long totalTicks = getTicks(Objects.requireNonNull(
                            ServerLifecycleHooks.getCurrentServer().getLevel(Level.OVERWORLD)));
                    long elapsed = machine.StartTick.get() >= 0 ? totalTicks - machine.StartTick.get() : 1;
                    if (elapsed > 0) {
                        rate = (double) (-realTotal) / elapsed;
                    }
                }
                if (rate > 0) {
                    inputData.put(id, rate);
                }
            }
        }
    }

    // ========== 工厂方块 → 还原为紧凑空间机器 ==========

    /**
     * 将工厂方块还原为原来的紧缩空间机器方块
     * 由 FactoryBlock.useItemOn(启动棒) 调用
     * 通过 ItemStack.save 创建包含 data components 的 NBT，再用 loadWithComponents 载入
     */
    public static void revertToBoundMachine(ServerLevel level, BlockPos pos, String roomCode) {
        // 从当前位置的 FactoryBlockEntity 读取保存的原始 attachments
        CompoundTag savedAttachments = null;
        net.minecraft.world.level.block.entity.BlockEntity existingBe = level.getBlockEntity(pos);
        if (existingBe instanceof FactoryBlockEntity fbe) {
            savedAttachments = fbe.getOriginalAttachments();
        }

        Block machineBlock = ((net.minecraft.world.item.BlockItem) BOUND_MACHINE.get()).getBlock();
        if (machineBlock == null) {
            Cyumocompactmachinespor.LOGGER.error("Cannot find BoundCompactMachineBlock from BOUND_MACHINE item");
            return;
        }

        // 先放置方块，获取默认 BE 的类型 ID
        level.removeBlockEntity(pos);
        level.setBlockAndUpdate(pos, machineBlock.defaultBlockState());

        net.minecraft.world.level.block.entity.BlockEntity defaultBe = level.getBlockEntity(pos);
        if (defaultBe == null) {
            Cyumocompactmachinespor.LOGGER.warn("No BE created at {}", pos);
            return;
        }

        // 构建正确的 NBT：room_code 在根级，machine_color 在 neoforge:attachments
        String beTypeId = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(defaultBe.getType()).toString();
        CompoundTag beNbt = new CompoundTag();
        beNbt.putString("id", beTypeId);
        beNbt.putInt("x", pos.getX());
        beNbt.putInt("y", pos.getY());
        beNbt.putInt("z", pos.getZ());
        beNbt.putString("room_code", roomCode);

        if (savedAttachments != null) {
            beNbt.put("neoforge:attachments", savedAttachments.copy());
        } else {
            CompoundTag defaultAttachments = new CompoundTag();
            defaultAttachments.putString("compactmachines:machine_color", "#C95B13");
            beNbt.put("neoforge:attachments", defaultAttachments);
        }

        net.minecraft.world.level.block.entity.BlockEntity loaded =
                net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, level.getBlockState(pos), beNbt, level.registryAccess());
        if (loaded != null) {
            level.setBlockEntity(loaded);
            loaded.setChanged();
            Cyumocompactmachinespor.LOGGER.info("FactoryBlock reverted at {} (room={})", pos, roomCode);
        } else {
            Cyumocompactmachinespor.LOGGER.warn("loadStatic returned null at {}", pos);
        }

        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
    }

}