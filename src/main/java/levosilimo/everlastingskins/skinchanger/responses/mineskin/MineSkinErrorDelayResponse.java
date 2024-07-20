/*
 * SkinsRestorer
 * Copyright (C) 2024  SkinsRestorer Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import java.util.Objects;

public final class MineSkinErrorDelayResponse {
    private final String error;
    private final Integer nextRequest;
    private final Integer delay;

    MineSkinErrorDelayResponse(String error, Integer nextRequest, Integer delay) {
        this.error = error;
        this.nextRequest = nextRequest;
        this.delay = delay;
    }

    public String error() {
        return error;
    }

    public Integer nextRequest() {
        return nextRequest;
    }

    public Integer delay() {
        return delay;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinErrorDelayResponse that = (MineSkinErrorDelayResponse) obj;
        return Objects.equals(this.error, that.error) &&
                Objects.equals(this.nextRequest, that.nextRequest) &&
                Objects.equals(this.delay, that.delay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, nextRequest, delay);
    }

    @Override
    public String toString() {
        return "MineSkinErrorDelayResponse[" +
                "error=" + error + ", " +
                "nextRequest=" + nextRequest + ", " +
                "delay=" + delay + ']';
    }


}
