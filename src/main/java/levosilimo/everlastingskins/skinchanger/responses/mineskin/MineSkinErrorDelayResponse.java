package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import javax.annotation.Nullable;
import java.util.Objects;

public final class MineSkinErrorDelayResponse {
    private final String error;
    private final Integer nextRequest;
    private final Integer delay;

    public MineSkinErrorDelayResponse(@Nullable String error, @Nullable Integer nextRequest, @Nullable Integer delay) {
        this.error = error;
        this.nextRequest = nextRequest;
        this.delay = delay;
    }

    @Nullable
    public String error() {
        return error;
    }

    @Nullable
    public Integer nextRequest() {
        return nextRequest;
    }

    @Nullable
    public Integer delay() {
        return delay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MineSkinErrorDelayResponse that = (MineSkinErrorDelayResponse) o;
        return Objects.equals(error, that.error) && Objects.equals(nextRequest, that.nextRequest)
            && Objects.equals(delay, that.delay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, nextRequest, delay);
    }

    @Override
    public String toString() {
        return "MineSkinErrorDelayResponse[error=" + error + ", nextRequest=" + nextRequest + ", delay=" + delay + "]";
    }
}
