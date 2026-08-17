package com.compactmachinespor.core;

import com.compactmachinespor.Cyumocompactmachinespor;
import com.compactmachinespor.block.BaseIOBlock;
import com.compactmachinespor.block.BaseIOBlockEntity;
import com.compactmachinespor.block.FactoryBlockEntity;
import com.compactmachinespor.resource.ResourceKey;
import dev.compactmods.machines.api.CompactMachines;
import dev.compactmods.machines.api.component.CMDataComponents;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.machine.MachineColor;
import dev.compactmods.machines.api.room.RoomInstance;
import dev.compactmods.machines.api.room.spatial.IRoomBoundaries;
import dev.compactmods.machines.server.CompactMachinesServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dev.compactmods.machines.machine.Machines.Items.BOUND_MACHINE;
import static net.minecraft.world.phys.shapes.Shapes.EPSILON;

public class Core {
    private static final Map<String, Machine> MACHINES = new ConcurrentHashMap<>();
    private static final Map<String, UUID> ROOM2UUID = new ConcurrentHashMap<>();

    public static Map<String, Machine> getMachines() {
        return MACHINES;
    }

    public static void createMachine(ServerLevel overworldLevel, String roomCode, BlockPos targetPos) {
        if (MACHINES.containsKey(roomCode)) return;
        MACHINES.put(roomCode, new Machine(getTicks(overworldLevel), roomCode, targetPos));
        ROOM2UUID.put(roomCode, UUID.randomUUID());
        ServerLevel compactWorld = overworldLevel.getServer().getLevel(CompactDimension.LEVEL_KEY);
        if (compactWorld != null) {
            loadRoom(compactWorld, roomCode);
            scanRoom(compactWorld, roomCode);
        }
    }

    public static Machine getMachine(String roomCode) {
        return MACHINES.get(roomCode);
    }

    public static void setMachineData(String roomCode, Machine.DataSetType type, ResourceKey<?> id, long data, long tickTime) {
        Machine machine = getMachine(roomCode);
        if (machine != null) {
            machine.addData(type, id, data, tickTime);
        }
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
        IRoomBoundaries boundaries = getRoomBoundaries(level, roomCode);
        if (boundaries == null) return;
        UUID uuid = ROOM2UUID.get(roomCode);
        if (uuid == null) return;
        boundaries.innerChunkPositions().forEach(
                chunkPos -> CompactMachinesServer.CHUNK_TICKET_CONTROLLER.forceChunk(
                        level,
                        uuid,
                        chunkPos.x, chunkPos.z, add, true
                )
        );
    }

    public static void scanRoom(ServerLevel compactWorld, String roomCode) {
        IRoomBoundaries boundaries = getRoomBoundaries(compactWorld, roomCode);
        if (boundaries == null) return;
        AABB roomAABB = boundaries.outerBounds();
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

        boundaries.innerChunkPositions().forEach(
                chunkPos -> compactWorld.getChunk(chunkPos.x, chunkPos.z).setUnsaved(true));
    }

    private static void processBlock(ServerLevel level, int x, int y, int z, String roomCode) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState blockState = level.getBlockState(pos);
        AntiCheat.runScanBlock(level, pos, blockState, roomCode);
        if (blockState.is(Cyumocompactmachinespor.INPUT_BLOCK) || blockState.is(Cyumocompactmachinespor.OUTPUT_BLOCK)) {
            Machine machine = getMachine(roomCode);
            if (machine != null) {
                machine.IOBlocks.add(pos);
            }
            if (level.getBlockEntity(pos) instanceof BaseIOBlockEntity ioBe) {
                ioBe.setRoomCode(roomCode);
            }
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
        if (machine == null) return;

        Map<ResourceKey<?>, Double> inputData = calculate(machine.InputData);
        Map<ResourceKey<?>, Double> outputData = calculate(machine.OutputData);

        machine.IOBlocks.forEach(
                pos -> {
                    BlockState st = compactWorld.getBlockState(pos);
                    if (st.hasProperty(BaseIOBlock.ACTIVE)) {
                        compactWorld.setBlock(
                                pos,
                                st.setValue(BaseIOBlock.ACTIVE, false),
                                Block.UPDATE_ALL
                        );
                    }
                }
        );
        IRoomBoundaries boundaries = getRoomBoundaries(compactWorld, roomCode);
        if (boundaries != null) {
            boundaries.innerChunkPositions().forEach(
                    chunkPos -> compactWorld.getChunk(chunkPos.x, chunkPos.z).setUnsaved(true));
        }
        unLoadRoom(compactWorld, roomCode);
        replaceBlock(overworld, overworldPos, Cyumocompactmachinespor.FACTORY_BLOCK);
        if (overworld.getBlockEntity(overworldPos) instanceof FactoryBlockEntity be) {
            be.setRoomCode(roomCode);
            be.init(inputData, outputData);
        }
        MACHINES.remove(roomCode);
        ROOM2UUID.remove(roomCode);
        compactWorld.getChunkSource().tick(() -> true, false);
    }

    public static Map<ResourceKey<?>, Double> calculate(Map<ResourceKey<?>, Machine.Data> dataMap) {
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
}
