package com.compactmachinespor.resource;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ResourceTypeRegistry {
    private static final Map<ResourceLocation, ResourceType<?>> REGISTRY = new ConcurrentHashMap<>();
    private static final List<ResourceType<?>> SORTED_TYPES = new ArrayList<>();

    public static synchronized void register(ResourceType<?> type) {
        REGISTRY.put(type.getId(), type);
        SORTED_TYPES.clear();
        SORTED_TYPES.addAll(REGISTRY.values());
        SORTED_TYPES.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }

    public static ResourceType<?> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<ResourceType<?>> getAll() {
        return Collections.unmodifiableCollection(SORTED_TYPES);
    }
}
