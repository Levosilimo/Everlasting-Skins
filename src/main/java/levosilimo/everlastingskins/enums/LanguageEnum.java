package levosilimo.everlastingskins.enums;

public enum LanguageEnum {

    English("en"),
    Russian("ru"),
    Ukrainian("uk");

    private final String name;
    LanguageEnum(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
