/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.network;

public class OnlinePlayers {

    private OnlinePlayers() {
    }

    public static void update() {
        // Telemetry suppressed: outgoing pings to meteorclient.com would reveal
        // the client identity to anyone monitoring network traffic.
    }

    public static void leave() {
        // Telemetry suppressed: see update().
    }
}
