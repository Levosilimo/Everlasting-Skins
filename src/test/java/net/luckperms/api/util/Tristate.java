package net.luckperms.api.util;

public enum Tristate {
    TRUE, FALSE, UNDEFINED;

    public boolean asBoolean() {
        return this == TRUE;
    }
}
