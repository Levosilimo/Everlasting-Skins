package net.luckperms.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface UserManager {
    boolean isLoaded(UUID uuid);
    User getUser(UUID uuid);
    CompletableFuture<User> loadUser(UUID uuid);
}
