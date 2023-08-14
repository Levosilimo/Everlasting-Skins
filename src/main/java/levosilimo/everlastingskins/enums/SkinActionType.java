package levosilimo.everlastingskins.enums;

public enum SkinActionType {
    clear("clear"),
    set("set"),
    source("source");

    private final String name;

    SkinActionType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static String[] getStringValues() {
        SkinActionType[] values = values();
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = values[i].name;
        }
        return stringValues;
    }
}
