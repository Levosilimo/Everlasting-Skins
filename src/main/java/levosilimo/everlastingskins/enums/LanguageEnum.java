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
}
