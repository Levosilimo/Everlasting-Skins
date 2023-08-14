package levosilimo.everlastingskins.enums;

public enum LanguageEnum {

    English("en_us"),
    Russian("ru_ru"),
    Ukrainian("uk_ua");

    private final String name;
    LanguageEnum(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static String[] getStringValues() {
        LanguageEnum[] values = values();
        String[] stringValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            stringValues[i] = values[i].name;
        }
        return stringValues;
    }

    public static LanguageEnum fromName(String name) {
        for (LanguageEnum value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("No enum constant with name: " + name);
    }
}
