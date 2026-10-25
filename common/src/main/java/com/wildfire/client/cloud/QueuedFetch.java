package com.wildfire.client.cloud;

import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

record QueuedFetch(UUID uuid, CompletableFuture<@Nullable JsonObject> future) {
}
