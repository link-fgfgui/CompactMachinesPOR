package com.compactmachinespor.core;

import com.compactmachinespor.Config;
import dev.compactmods.machines.api.dimension.CompactDimension;
import dev.compactmods.machines.api.room.spatial.IRoomBoundaries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;

public class AntiCheat {
    public interface IAntiCheat {
        default void preScan(ServerLevel level, String roomCode, AABB roomAABB) {}
        default void scanBlock(ServerLevel level, BlockPos pos, BlockState state, String roomCode) {}
        default void postScan(ServerLevel level, String roomCode, AABB roomAABB) {}

        /**
         * Checks if converting a BoundCompactMachineBlock into an EvaluatorBlock is allowed.
         *
         * @param level    The server level.
         * @param pos      The position of the machine block.
         * @param state    The block state of the machine block.
         * @param roomCode The room code connected to the machine.
         * @param player   The player performing the conversion.
         * @param stack    The item stack (launcher stick) used.
         * @param hand     The interaction hand used.
         * @return true if conversion is allowed, false otherwise.
         */
        default boolean canConvertToEvaluator(ServerLevel level, BlockPos pos, BlockState state, String roomCode, Player player, ItemStack stack, InteractionHand hand) {
            return true;
        }
    }

    private static final List<IAntiCheat> ANTI_CHEATS = new ArrayList<>();
    private static TagKey<Block> BanBlocksTag = null;

    static {
        // Default anti-cheat implementation
        registerAntiCheat(new IAntiCheat() {
            @Override
            public boolean canConvertToEvaluator(ServerLevel level, BlockPos pos, BlockState state, String roomCode, Player player, ItemStack stack, InteractionHand hand) {
                if (Config.ENABLE_SCAN.get()) {
                    if (BanBlocksTag == null) {
                        ResourceLocation tagId = ResourceLocation.tryParse(Config.SCAN_TAG.get());
                        if (tagId != null) {
                            BanBlocksTag = BlockTags.create(tagId);
                        }
                    }
                    if (BanBlocksTag != null && roomCode != null && !roomCode.isEmpty()) {
                        ServerLevel compactWorld = level.getServer().getLevel(CompactDimension.LEVEL_KEY);
                        if (compactWorld != null) {
                            IRoomBoundaries boundaries = Core.getRoomBoundaries(compactWorld, roomCode);
                            if (boundaries != null) {
                                AABB roomAABB = boundaries.outerBounds();
                                int startX = (int) Math.floor(roomAABB.minX);
                                int startY = (int) Math.floor(roomAABB.minY);
                                int startZ = (int) Math.floor(roomAABB.minZ);
                                int endX = (int) Math.floor(roomAABB.maxX - Shapes.EPSILON);
                                int endY = (int) Math.floor(roomAABB.maxY - Shapes.EPSILON);
                                int endZ = (int) Math.floor(roomAABB.maxZ - Shapes.EPSILON);

                                BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
                                for (int x = startX; x <= endX; x++) {
                                    for (int y = startY; y <= endY; y++) {
                                        for (int z = startZ; z <= endZ; z++) {
                                            mutablePos.set(x, y, z);
                                            BlockState blockState = compactWorld.getBlockState(mutablePos);
                                            if (blockState.is(BanBlocksTag)) {
                                                player.displayClientMessage(
                                                        Component.translatable("chat.compactmachinespor.banned_block_detected")
                                                                .withStyle(ChatFormatting.RED),
                                                        true
                                                );
                                                return false;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
        });
    }

    public static void registerAntiCheat(IAntiCheat antiCheat) {
        ANTI_CHEATS.add(antiCheat);
    }

    public static void runPreScan(ServerLevel level, String roomCode, AABB roomAABB) {
        for (IAntiCheat antiCheat : ANTI_CHEATS) {
            antiCheat.preScan(level, roomCode, roomAABB);
        }
    }

    public static void runScanBlock(ServerLevel level, BlockPos pos, BlockState state, String roomCode) {
        for (IAntiCheat antiCheat : ANTI_CHEATS) {
            antiCheat.scanBlock(level, pos, state, roomCode);
        }
    }

    public static void runPostScan(ServerLevel level, String roomCode, AABB roomAABB) {
        for (IAntiCheat antiCheat : ANTI_CHEATS) {
            antiCheat.postScan(level, roomCode, roomAABB);
        }
    }

    /**
     * Checks all registered anti-cheat hooks using AND (&&) logic to determine if conversion to EvaluatorBlock is allowed.
     *
     * @return true if all registered hooks allow the conversion, false if any hook denies it.
     */
    public static boolean canConvertToEvaluator(ServerLevel level, BlockPos pos, BlockState state, String roomCode, Player player, ItemStack stack, InteractionHand hand) {
        for (IAntiCheat antiCheat : ANTI_CHEATS) {
            if (!antiCheat.canConvertToEvaluator(level, pos, state, roomCode, player, stack, hand)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Centralized unpack check for easy Mixin by add-on mods.
     * @return true if unpacking is allowed.
     */
    public static boolean checkUnpack(Player player, String roomCode, Level level, ItemStack stack, InteractionHand hand) {
        return player.hasPermissions(Config.UNPACK_PERMISSION_LEVEL.get());
    }
}
