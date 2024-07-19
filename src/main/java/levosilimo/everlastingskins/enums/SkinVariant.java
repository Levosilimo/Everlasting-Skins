package levosilimo.everlastingskins.enums;

public enum SkinVariant {

    CLASSIC("classic"),
    SLIM("slim"),
    ALL("all");

    private final String name;

    SkinVariant(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
