package levosilimo.everlastingskins.enums;

public enum SkinVariant {

    classic("classic"),
    slim("slim"),
    all("all");

    private final String name;

    SkinVariant(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
