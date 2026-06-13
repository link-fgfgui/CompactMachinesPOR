package com.compactmachinespor.core;

import com.compactmachinespor.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Machine {
    public final Map<Holder<?>, Data> InputData = new HashMap<>();
    public final Map<Holder<?>, Data> OutputData = new HashMap<>();
    public final AtomicLong StartTick = new AtomicLong(-1);
    public final List<BlockPos> IOBlocks = new ArrayList<>();
    public final String RoomCode;
    public final BlockPos TargetPos;
    public int lastSpeed = 0;

    public List<Data> EnergyData = null;

    // 库存基线审计字段
    public final Map<Holder<?>, Long> totalInput = new ConcurrentHashMap<>();
    public final Map<Holder<?>, Long> totalOutput = new ConcurrentHashMap<>();
    public InventorySnapshot inventoryStart;
    public InventorySnapshot inventoryEnd;

    // 原机器的 neoforge:attachments（含 machine_color），由 createMachine 从 EvaluatorBlockEntity 读取
    public CompoundTag originalAttachments;

    public static final int EVALUATE_SECONDS = Config.EVALUATE_SECONDS.get();

    public Machine(long startTick, String roomCode, BlockPos targetPos) {
        StartTick.set(startTick);
        RoomCode = roomCode;
        TargetPos = targetPos;
    }

    public void clear() {
        InputData.clear();
        OutputData.clear();
        EnergyData = null;
        StartTick.set(-1);
    }

    public void initEnergy() {
        EnergyData = List.of(newData(), newData());
    }

    public Data newData() {
        return new Data(new int[EVALUATE_SECONDS]);
    }

    public void addData(DataSetType type, Holder<?> id, int data, long currentTick) {
        Map<Holder<?>, Data> set = switch (type) {
            case Input -> InputData;
            case Output -> OutputData;
        };
        if (!set.containsKey(id)) {
            set.put(id, newData());
        }
        Data dataInner = set.get(id);
        dataAdd(dataInner, data, currentTick);
    }

    public void dataAdd(Data dataInner, int add, long currentTick) {
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

    public void addEnergyData(DataSetType type, int data, long currentTick) {
        if (EnergyData == null) {
            initEnergy();
        }
        switch (type) {
            case Input:
                dataAdd(EnergyData.getFirst(), data, currentTick);
                break;
            case Output:
                dataAdd(EnergyData.getLast(), data, currentTick);
        }
    }

    /**
     * 记录累计 IO 总量（用于库存基线审计）
     */
    public void addTotal(DataSetType type, Holder<?> id, long amount) {
        Map<Holder<?>, Long> target = switch (type) {
            case Input -> totalInput;
            case Output -> totalOutput;
        };
        target.merge(id, amount, Long::sum);
    }

    public void addTotalEnergy(DataSetType type, long amount) {
        Map<Holder<?>, Long> target = switch (type) {
            case Input -> totalInput;
            case Output -> totalOutput;
        };
        target.merge(Holder.direct(null), amount, Long::sum);
    }

    /**
     * 房间库存快照，由 Core.scanInventory() 生成
     */
    public record InventorySnapshot(Map<Holder<?>, Long> items, Map<Holder<?>, Long> fluids, long energy) {
        public static InventorySnapshot empty() {
            return new InventorySnapshot(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), 0);
        }
    }

    public enum DataSetType {
        Input, Output
    }

    public record Data(int[] data) {
    }
}
