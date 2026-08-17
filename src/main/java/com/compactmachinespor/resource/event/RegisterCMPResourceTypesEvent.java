package com.compactmachinespor.resource.event;

import com.compactmachinespor.resource.ResourceType;
import com.compactmachinespor.resource.ResourceTypeRegistry;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterCMPResourceTypesEvent extends Event implements IModBusEvent {
    public void register(ResourceType<?> resourceType) {
        ResourceTypeRegistry.register(resourceType);
    }
}
