package levosilimo.everlastingskins.enums;

public enum SkinVariant {

    CLASSIC("classic"),
    SLIM("slim"),
    ANY("any");

    private final String name;

    SkinVariant(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static String[] getStringValues() {
        SkinVariant[] values = values();
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = values[i].name;
        }
        return stringValues;
    }

    public static boolean contains(String value) {
        for (SkinVariant option : values()) {
            if (option.name.equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static SkinVariant fromName(String name) {
        for (SkinVariant value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No enum constant with name: " + name);
    }
}
