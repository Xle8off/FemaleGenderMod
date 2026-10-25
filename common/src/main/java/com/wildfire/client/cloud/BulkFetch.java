package com.wildfire.client.cloud;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;

record BulkFetch(boolean success, Map<UUID, JsonObject> users) {
}
