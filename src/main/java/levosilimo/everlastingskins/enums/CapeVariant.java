package levosilimo.everlastingskins.enums;

public enum CapeVariant {
    CAPE("cape"),
    NO_CAPE("nocape"),
    ANY("any");

    private final String name;

    CapeVariant(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static String[] getStringValues() {
        CapeVariant[] values = values();
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = values[i].name;
        }
        return stringValues;
    }

    public static boolean contains(String value) {
        for (CapeVariant option : values()) {
            if (option.name.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static CapeVariant fromName(String name) {
        for (CapeVariant value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No enum constant with name: " + name);
    }
}
