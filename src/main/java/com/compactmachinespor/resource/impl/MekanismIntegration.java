package com.compactmachinespor.resource.impl;

import com.compactmachinespor.resource.ResourceTypeRegistry;
import net.neoforged.fml.ModList;

public class MekanismIntegration {
    public static void init() {
        if (ModList.get().isLoaded("mekanism")) {
            ResourceTypeRegistry.register(MekanismChemicalResourceType.INSTANCE);
        }
    }
}
