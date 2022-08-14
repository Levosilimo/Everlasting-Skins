package levosilimo.everlastingskins.enums;

public enum SkinActionType {
    clear("clear"),
    url("url"),
    nickname("nickname"),
    random("random"),
    NEW("new");

    private final String name;

    SkinActionType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
