## Integration compatibility

PlaceholderAPI and DiscordSRV integrations require a hybrid Forge+Bukkit server
(Mohist, Magma, Arclight, CatServer). On pure Forge servers, these integrations
soft-fail (no-op) via ClassNotFoundException reflection detection.
