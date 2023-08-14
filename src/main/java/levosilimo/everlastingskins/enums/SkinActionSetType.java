package levosilimo.everlastingskins.enums;

public enum SkinActionSetType {
    random("random"),
    mojang("mojang"),
    web("web");

    private final String name;

    SkinActionSetType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static String[] getStringValues() {
        SkinActionSetType[] values = values();
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = values[i].name;
        }
        return stringValues;
    }
}
