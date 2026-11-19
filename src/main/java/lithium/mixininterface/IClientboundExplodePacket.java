/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package lithium.mixininterface;

public interface IClientboundExplodePacket {
    void meteor$setVelocityX(float velocity);

    void meteor$setVelocityY(float velocity);

    void meteor$setVelocityZ(float velocity);
}
