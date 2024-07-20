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

public final class MineSkinUrlResponse {
    private final String id;
    private final String idStr;
    private final String uuid;
    private final String name;
    private final String variant;
    private final MineSkinData data;
    private final long timestamp;
    private final int duration;
    private final int account;
    private final String server;
    private final boolean private_;
    private final int views;
    private final int nextRequest;
    private final boolean duplicate;

    MineSkinUrlResponse(String id, String idStr, String uuid, String name, String variant, MineSkinData data, long timestamp, int duration, int account, String server, boolean private_, int views, int nextRequest, boolean duplicate) {
        this.id = id;
        this.idStr = idStr;
        this.uuid = uuid;
        this.name = name;
        this.variant = variant;
        this.data = data;
        this.timestamp = timestamp;
        this.duration = duration;
        this.account = account;
        this.server = server;
        this.private_ = private_;
        this.views = views;
        this.nextRequest = nextRequest;
        this.duplicate = duplicate;
    }

    public String id() {
        return id;
    }

    public String idStr() {
        return idStr;
    }

    public String uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String variant() {
        return variant;
    }

    public MineSkinData data() {
        return data;
    }

    public long timestamp() {
        return timestamp;
    }

    public int duration() {
        return duration;
    }

    public int account() {
        return account;
    }

    public String server() {
        return server;
    }

    public boolean private_() {
        return private_;
    }

    public int views() {
        return views;
    }

    public int nextRequest() {
        return nextRequest;
    }

    public boolean duplicate() {
        return duplicate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        MineSkinUrlResponse that = (MineSkinUrlResponse) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.idStr, that.idStr) &&
                Objects.equals(this.uuid, that.uuid) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.variant, that.variant) &&
                Objects.equals(this.data, that.data) &&
                this.timestamp == that.timestamp &&
                this.duration == that.duration &&
                this.account == that.account &&
                Objects.equals(this.server, that.server) &&
                this.private_ == that.private_ &&
                this.views == that.views &&
                this.nextRequest == that.nextRequest &&
                this.duplicate == that.duplicate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, idStr, uuid, name, variant, data, timestamp, duration, account, server, private_, views, nextRequest, duplicate);
    }

    @Override
    public String toString() {
        return "MineSkinUrlResponse[" +
                "id=" + id + ", " +
                "idStr=" + idStr + ", " +
                "uuid=" + uuid + ", " +
                "name=" + name + ", " +
                "variant=" + variant + ", " +
                "data=" + data + ", " +
                "timestamp=" + timestamp + ", " +
                "duration=" + duration + ", " +
                "account=" + account + ", " +
                "server=" + server + ", " +
                "private_=" + private_ + ", " +
                "views=" + views + ", " +
                "nextRequest=" + nextRequest + ", " +
                "duplicate=" + duplicate + ']';
    }

}
