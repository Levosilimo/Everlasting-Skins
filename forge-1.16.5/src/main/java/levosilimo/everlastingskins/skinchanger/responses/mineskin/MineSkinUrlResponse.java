/*
 * SPDX-License-Identifier: MIT
 */

package levosilimo.everlastingskins.skinchanger.responses.mineskin;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class MineSkinUrlResponse {
    private String id;
    private String idStr;
    private String uuid;
    private String name;
    private String variant;
    private MineSkinData data;
    private long timestamp;
    private int duration;
    private int account;
    private String server;
    @SerializedName("private")
    private boolean private_;
    private int views;
    private int nextRequest;
    private boolean duplicate;

    public MineSkinUrlResponse() {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MineSkinUrlResponse that)) return false;
        return timestamp == that.timestamp && duration == that.duration
            && account == that.account && private_ == that.private_
            && views == that.views && nextRequest == that.nextRequest
            && duplicate == that.duplicate && Objects.equals(id, that.id)
            && Objects.equals(idStr, that.idStr) && Objects.equals(uuid, that.uuid)
            && Objects.equals(name, that.name) && Objects.equals(variant, that.variant)
            && Objects.equals(data, that.data) && Objects.equals(server, that.server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, idStr, uuid, name, variant, data, timestamp, duration,
            account, server, private_, views, nextRequest, duplicate);
    }

    @Override
    public String toString() {
        return "MineSkinUrlResponse[id=" + id + ", idStr=" + idStr + ", uuid=" + uuid
            + ", name=" + name + ", variant=" + variant + ", data=" + data
            + ", timestamp=" + timestamp + ", duration=" + duration + ", account=" + account
            + ", server=" + server + ", private_=" + private_ + ", views=" + views
            + ", nextRequest=" + nextRequest + ", duplicate=" + duplicate + "]";
    }
}
