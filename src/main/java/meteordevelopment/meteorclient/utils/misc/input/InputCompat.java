/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc.input;

import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;

public final class InputCompat {
    public static final InputConstants.Type KEYBOARD = type("KEYBOARD", "KEYSYM");
    public static final InputConstants.Type SCANCODE = type("SCANCODE", "KEYBOARD", "KEYSYM");

    private InputCompat() {
    }

    public static int keyCode(String name) {
        try {
            Field field = InputConstants.class.getField(name);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputConstants.Type type(String... names) {
        for (String name : names) {
            try {
                Field field = InputConstants.Type.class.getField(name);
                return (InputConstants.Type) field.get(null);
            } catch (ReflectiveOperationException ignored) {}
        }

        throw new IllegalStateException("No input type available");
    }
}
