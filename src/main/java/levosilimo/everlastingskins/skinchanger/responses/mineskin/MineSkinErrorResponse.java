package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import java.util.Objects;

public final class MineSkinErrorResponse {
    private final String errorCode;
    private final String error;

    public MineSkinErrorResponse(String errorCode, String error) {
        this.errorCode = errorCode;
        this.error = error;
    }

    public String errorCode() {
        return errorCode;
    }

    public String error() {
        return error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinErrorResponse that = (MineSkinErrorResponse) o;
        return Objects.equals(errorCode, that.errorCode) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, error);
    }

    @Override
    public String toString() {
        return "MineSkinErrorResponse[errorCode=" + errorCode + ", error=" + error + "]";
    }
}
