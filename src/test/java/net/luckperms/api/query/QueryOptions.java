package net.luckperms.api.query;

public interface QueryOptions {
    static QueryOptions defaultContextualOptions() {
        return new DefaultQueryOptions();
    }
}
