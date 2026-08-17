package com.compactmachinespor.core;

import com.compactmachinespor.Config;
import com.compactmachinespor.resource.ResourceKey;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Machine {
    public final Map<ResourceKey<?>, Data> InputData = new ConcurrentHashMap<>();
    public final Map<ResourceKey<?>, Data> OutputData = new ConcurrentHashMap<>();
    public final AtomicLong StartTick = new AtomicLong(-1);
    public final List<BlockPos> IOBlocks = new ArrayList<>();
    public final String RoomCode;
    public final BlockPos TargetPos;
    public long lastSpeed = 0;

    public static final int EVALUATE_SECONDS = Config.EVALUATE_SECONDS.get();

    public Machine(long startTick, String roomCode, BlockPos targetPos) {
        StartTick.set(startTick);
        RoomCode = roomCode;
        TargetPos = targetPos;
    }

    public void clear() {
        InputData.clear();
        OutputData.clear();
        StartTick.set(-1);
    }

    public Data newData() {
        return new Data(new long[EVALUATE_SECONDS]);
    }

    public void addData(DataSetType type, ResourceKey<?> id, long data, long currentTick) {
        Map<ResourceKey<?>, Data> set = switch (type) {
            case Input -> InputData;
            case Output -> OutputData;
        };
        Data dataInner = set.computeIfAbsent(id, k -> newData());
        dataAdd(dataInner, data, currentTick);
    }

    public void dataAdd(Data dataInner, long add, long currentTick) {
        int currentSecond = (int) ((currentTick - StartTick.get()) / 20);
        if (currentSecond > EVALUATE_SECONDS) {
            Core.finish(RoomCode, TargetPos);
            return;
        }
        if (currentSecond >= 0 && currentSecond < dataInner.data.length) {
            dataInner.data[currentSecond] += add;
            if (currentSecond > 0) {
                lastSpeed = dataInner.data[currentSecond - 1];
            }
        }
    }

    public enum DataSetType {
        Input, Output
    }

    public record Data(long[] data) {
    }
}
