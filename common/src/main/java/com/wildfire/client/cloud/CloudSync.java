package com.wildfire.client.cloud;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.HttpDiscoveryService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.serialization.JsonOps;
import com.mojang.util.InstantTypeAdapter;
import com.wildfire.client.config.ClientConfig;
import com.wildfire.client.contributors.Contributor;
import com.wildfire.common.LoaderAgnostics;
import com.wildfire.common.WildfireGender;
import com.wildfire.common.WildfireLang;
import com.wildfire.common.config.enums.SyncVerbosity;
import com.wildfire.common.entitydata.PlayerConfig;
import com.wildfire.common.entitydata.PlayerConfigHolder;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jspecify.annotations.Nullable;

public final class CloudSync {
   private static Instant lastSync = Instant.EPOCH;
   private static final List<Instant> fetchErrors = new ArrayList<>();
   private static @Nullable Instant disableFetchingUntil;
   private static @Nullable CloudAuth auth;
   private static final Object AUTH_LOCK = new Object();
   private static final Object SYNC_LOCK = new Object();
   private static final Executor EXECUTOR = Util.ioPool().forName("female_gender_mod$cloudSync");
   private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantTypeAdapter()).create();
   private static final HttpClient CLIENT = (HttpClient)Util.make(() -> {
      Builder builder = HttpClient.newBuilder();
      if (LoaderAgnostics.INSTANCE.isDevelopmentEnv() && getCloudServer().startsWith("http://")) {
         builder.version(Version.HTTP_1_1);
      }

      builder.connectTimeout(Duration.ofSeconds(5L)).followRedirects(Redirect.NORMAL);
      return builder.build();
   });
   private static final String USER_AGENT = "WildfireGender/"
      + StringUtils.split(LoaderAgnostics.INSTANCE.getModVersion("female_gender_mod"), '+')[0]
      + " Minecraft/"
      + LoaderAgnostics.INSTANCE.getModVersion("minecraft");
   private static final Queue<QueuedFetch> QUEUED = new ConcurrentLinkedDeque<>();
   private static final Cache<UUID, Optional<JsonObject>> FETCH_CACHE = CacheBuilder.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(10L))
      .concurrencyLevel(6)
      .build();
   private static final String DEFAULT_CLOUD_URL = "https://wfgm.celestialfault.dev";
   private static final Duration SYNC_COOLDOWN = Duration.ofSeconds(10L);

   private CloudSync() {
      throw new UnsupportedOperationException();
   }

   public static boolean syncOnCooldown() {
      return lastSync.plus(SYNC_COOLDOWN).isAfter(Instant.now());
   }

   public static @Nullable SyncUnavailable unavailableReason() {
      UUID sessionUuid = Minecraft.getInstance().getUser().getProfileId();
      if (sessionUuid.version() != 4) {
         return SyncUnavailable.INVALID_ACCOUNT;
      } else if (CloudUtils.hasTheSessionServiceBeenTamperedWith()) {
         return SyncUnavailable.INVALID_ACCOUNT;
      } else {
         Minecraft client = Minecraft.getInstance();
         ClientPacketListener connection = client.getConnection();
         if (connection == null) {
            return null;
         } else {
            return !client.isLocalServer() && !connection.onlineMode() ? SyncUnavailable.OFFLINE_SERVER : null;
         }
      }
   }

   public static boolean isAvailable() {
      return unavailableReason() == null;
   }

   public static boolean isEnabled() {
      return isAvailable() && ClientConfig.config().cloudSync().enabled().get();
   }

   public static String getCloudServer() {
      String url = ClientConfig.config().cloudSync().server().get();
      return url.isBlank() ? "https://wfgm.celestialfault.dev" : url;
   }

   private static void markFetchError() {
      fetchErrors.add(Instant.now());
      fetchErrors.removeIf(e -> e.plus(30L, ChronoUnit.SECONDS).isBefore(Instant.now()));
      if (fetchErrors.size() >= 5) {
         WildfireGender.LOGGER.error("Too many recent sync errors, disabling future lookups for 5 minutes");
         disableFetchingUntil = Instant.now().plus(5L, ChronoUnit.MINUTES);
      }
   }

   private static boolean isFetchingDisabled(boolean ignoreConfig) {
      if (!isAvailable()) {
         return true;
      } else {
         return !ignoreConfig && !isEnabled() ? true : disableFetchingUntil != null && disableFetchingUntil.isAfter(Instant.now());
      }
   }

   private static java.net.http.HttpRequest.Builder createRequest(URI uri) {
      WildfireGender.LOGGER.debug("Connecting to {}", uri);
      return HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).header("Accept", "application/json").timeout(Duration.ofSeconds(5L));
   }

   @Internal
   public static CompletableFuture<Map<UUID, Contributor>> getContributors() {
      return CompletableFuture.supplyAsync(
         () -> {
            HttpRequest request = createRequest(URI.create(getCloudServer() + "/contributors")).GET().build();

            HttpResponse<String> response;
            try {
               response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
               if (response.statusCode() != 200) {
                  WildfireGender.LOGGER.warn("Couldn't fetch contributor list: server responded {}", response.statusCode());
                  return Map.of();
               }
            } catch (Exception e) {
               WildfireGender.LOGGER.warn("Couldn't fetch contributor list", e);
               return Map.of();
            }

            try {
               JsonObject json = (JsonObject)GSON.fromJson(response.body(), JsonObject.class);
               Map<UUID, Contributor> interim = json.asMap()
                  .entrySet()
                  .stream()
                  .collect(
                     Collectors.toMap(
                        entry -> UUID.fromString((String)entry.getKey()), entry -> (Contributor)GSON.fromJson((JsonElement)entry.getValue(), Contributor.class)
                     )
                  );
               return Collections.unmodifiableMap(
                  (Map<? extends UUID, ? extends Contributor>)(interim.size() <= 8 ? new Object2ObjectArrayMap(interim) : new Object2ObjectOpenHashMap(interim))
               );
            } catch (Exception e) {
               WildfireGender.LOGGER.error("Failed to parse contributor list", e);
               return Map.of();
            }
         },
         EXECUTOR
      );
   }

   private static String generateServerId() {
      BigInteger intA = new BigInteger(128, new Random());
      BigInteger intB = new BigInteger(128, new Random(System.identityHashCode(new Object())));
      return intA.xor(intB).toString(16);
   }

   @Blocking
   private static String getAuthToken() {
      synchronized (AUTH_LOCK) {
         Minecraft client = Minecraft.getInstance();
         if (client.player == null) {
            SyncLog.add(WildfireLang.SYNC_LOG_AUTH_FAILED);
            throw new IllegalStateException("Cannot get a new auth token while the client player is unset");
         }

         if (auth == null || auth.isExpired() || auth.isInvalidForClientPlayer()) {
            WildfireGender.LOGGER.info("Authenticating with Mojang session servers");
            SyncLog.add(WildfireLang.SYNC_LOG_AUTH_MOJANG);
            String serverId = generateServerId();
            User session = client.getUser();

            try {
               CloudUtils.getSessionService().joinServer(Objects.requireNonNull(session.getProfileId()), session.getAccessToken(), serverId);
            } catch (AuthenticationException e) {
               SyncLog.add(WildfireLang.SYNC_LOG_AUTH_FAILED);
               throw new RuntimeException(e);
            }

            WildfireGender.LOGGER.info("Obtaining new authentication token from the cloud sync server");
            SyncLog.add(WildfireLang.SYNC_LOG_AUTH_SYNC);
            String query = HttpDiscoveryService.buildQuery(Map.of("serverId", serverId, "username", session.getName()));
            URI uri = URI.create(getCloudServer() + "/auth?" + query);
            HttpRequest request = createRequest(uri).GET().build();
            HttpResponse<String> response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
            if (response.statusCode() >= 400) {
               SyncLog.add(WildfireLang.SYNC_LOG_AUTH_FAILED);
               throw new RuntimeException("Failed to authenticate with sync server: " + response.body());
            }

            auth = (CloudAuth)GSON.fromJson(response.body(), CloudAuth.class);
            if (auth.isInvalidForClientPlayer()) {
               WildfireGender.LOGGER
                  .warn(
                     "Authenticated account {} does not match the current player ({}); you likely have a misbehaving account switcher mod installed!",
                     auth.account(),
                     client.player.getUUID()
                  );
            }

            WildfireGender.LOGGER.info("Obtained authentication token for {}, expiry {}", auth.account(), auth.expires());
         }
      }

      return auth.token();
   }

   public static CompletableFuture<Void> sync(PlayerConfigHolder config) {
      if (!isEnabled()) {
         return CompletableFuture.completedFuture(null);
      }

      synchronized (SYNC_LOCK) {
         if (syncOnCooldown()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new SyncingTooFrequentlyException());
            return future;
         }

         lastSync = Instant.now();
      }

      return syncInternal(config, false);
   }

   private static CompletableFuture<Void> syncInternal(PlayerConfigHolder config, boolean resyncing) {
      return CompletableFuture.runAsync(
         () -> {
            String token = getAuthToken();
            URI url = URI.create(getCloudServer() + "/" + config.uuid);
            String json = ((JsonElement)PlayerConfig.CLOUD_SYNC_CODEC
                  .encodeStart(JsonOps.INSTANCE, config.config())
                  .resultOrPartial()
                  .orElseGet(JsonObject::new))
               .toString();
            SyncLog.add(WildfireLang.SYNC_LOG_START);
            HttpRequest request = createRequest(url)
               .PUT(BodyPublishers.ofString(json))
               .header("Content-Type", "application/json; charset=UTF-8")
               .header("Auth-Token", token)
               .build();
            HttpResponse<String> response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
            if (response.statusCode() == 401 && !resyncing) {
               SyncLog.add(WildfireLang.SYNC_LOG_REAUTH);
               WildfireGender.LOGGER.warn("Auth token is invalid, attempting to reauth...");
               auth = null;
               syncInternal(config, true).join();
            } else {
               if (response.statusCode() >= 400) {
                  throw new RuntimeException("Server responded " + response.statusCode() + ": " + response.body());
               }

               WildfireGender.LOGGER.debug("Server responded to update: {}", response.body());
               SyncLog.add(WildfireLang.SYNC_LOG_SUCCESS);
            }
         },
         EXECUTOR
      );
   }

   public static CompletableFuture<Void> deleteProfile(PlayerConfigHolder config) {
      return CompletableFuture.runAsync(() -> {
         String token = getAuthToken();
         URI url = URI.create(getCloudServer() + "/" + config.uuid);
         HttpRequest request = createRequest(url).DELETE().header("Auth-Token", token).build();
         HttpResponse<String> response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
         if (response.statusCode() == 404) {
            SyncLog.add(WildfireLang.SYNC_LOG_NO_PROFILE);
         } else {
            if (response.statusCode() >= 400) {
               SyncLog.add(WildfireLang.SYNC_LOG_PROFILE_DELETION_FAILED);
               throw new RuntimeException("Server responded " + response.statusCode() + ": " + response.body());
            }

            WildfireGender.LOGGER.debug("Deleted cloud sync profile");
            SyncLog.add(WildfireLang.SYNC_LOG_PROFILE_DELETED);
         }
      }, EXECUTOR);
   }

   public static CompletableFuture<@Nullable JsonObject> getProfile(UUID uuid) {
      return getProfile(uuid, false);
   }

   public static CompletableFuture<@Nullable JsonObject> getProfile(UUID uuid, boolean ignoreConfig) {
      if (isFetchingDisabled(ignoreConfig)) {
         return CompletableFuture.completedFuture(null);
      }

      if (uuid.version() != 4) {
         return CompletableFuture.completedFuture(null);
      }

      Optional<JsonObject> cached = (Optional<JsonObject>)FETCH_CACHE.getIfPresent(uuid);
      return cached != null ? CompletableFuture.completedFuture(cached.orElse(null)) : CompletableFuture.supplyAsync(() -> {
         URI url = URI.create(getCloudServer() + "/" + uuid);
         HttpRequest request = createRequest(url).GET().build();
         HttpResponse<String> response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
         if (response.statusCode() == 404 || response.statusCode() == 204) {
            WildfireGender.LOGGER.debug("Server replied no data for {}", uuid);
            FETCH_CACHE.put(uuid, Optional.empty());
            return null;
         } else if (response.statusCode() >= 400) {
            markFetchError();
            throw new RuntimeException("Server responded " + response.statusCode() + ": " + response.body());
         } else {
            SyncLog.add(WildfireLang.SYNC_LOG_SINGLE_PROFILE, SyncVerbosity.SHOW_FETCHES);
            JsonObject data = (JsonObject)GSON.fromJson(response.body(), JsonObject.class);
            FETCH_CACHE.put(uuid, Optional.of(data));
            return data;
         }
      }, EXECUTOR);
   }

   public static CompletableFuture<Map<UUID, JsonObject>> getMultiple(Collection<UUID> uuids) {
      return CompletableFuture.supplyAsync(
         () -> {
            if (isFetchingDisabled(false)) {
               return Collections.emptyMap();
            } else {
               URI url = URI.create(getCloudServer() + "/");
               JsonArray json = new JsonArray();
               uuids.forEach(uuid -> json.add(uuid.toString()));
               HttpRequest request = createRequest(url)
                  .POST(BodyPublishers.ofString(json.toString()))
                  .header("Content-Type", "application/json; charset=UTF-8")
                  .build();
               HttpResponse<String> response = CLIENT.sendAsync(request, BodyHandlers.ofString()).join();
               if (response.statusCode() >= 400) {
                  markFetchError();
                  throw new RuntimeException("Server responded " + response.statusCode() + ": " + response.body());
               } else {
                  SyncLog.add(WildfireLang.SYNC_LOG_MULTIPLE_PROFILES, SyncVerbosity.SHOW_FETCHES);
                  Map<UUID, JsonObject> data = ((BulkFetch)GSON.fromJson(response.body(), BulkFetch.class)).users();
                  uuids.forEach(uuid -> FETCH_CACHE.put(uuid, Optional.ofNullable(data.get(uuid))));
                  return data;
               }
            }
         },
         EXECUTOR
      );
   }

   public static CompletableFuture<JsonObject> queueFetch(UUID uuid) {
      if (!isEnabled()) {
         return CompletableFuture.completedFuture(null);
      }

      Optional<JsonObject> cached = (Optional<JsonObject>)FETCH_CACHE.getIfPresent(uuid);
      if (cached != null) {
         return CompletableFuture.completedFuture(cached.orElse(null));
      }

      if (uuid.version() != 4) {
         return CompletableFuture.completedFuture(null);
      }

      CompletableFuture<JsonObject> future = new CompletableFuture<>();
      QUEUED.add(new QueuedFetch(uuid, future));
      return future;
   }

   @Internal
   public static void sendNextQueueBatch() {
      if (!QUEUED.isEmpty()) {
         ArrayList<QueuedFetch> toFetch = new ArrayList<>();

         for (int i = 0; i < 20; i++) {
            QueuedFetch next = QUEUED.poll();
            if (next == null) {
               break;
            }

            toFetch.add(next);
         }

         if (toFetch.size() < 4) {
            WildfireGender.LOGGER.debug("Sending {} queued sync queries", toFetch.size());
            toFetch.forEach(queued -> CompletableFuture.runAsync(() -> {
               try {
                  queued.future().complete(getProfile(queued.uuid()).join());
               } catch (Exception e) {
                  Throwable actualException = e instanceof CompletionException ce ? ce.getCause() : e;
                  queued.future().completeExceptionally(actualException);
               }
            }));
         } else {
            WildfireGender.LOGGER.debug("Fetching sync data for {} players in bulk", toFetch.size());
            CompletableFuture.runAsync(() -> {
               Map<UUID, JsonObject> result;
               try {
                  result = getMultiple(toFetch.stream().map(QueuedFetch::uuid).toList()).join();
               } catch (Exception e) {
                  Throwable actualException = e instanceof CompletionException ce ? ce.getCause() : e;
                  toFetch.forEach(queued -> queued.future().completeExceptionally(actualException));
                  return;
               }

               toFetch.forEach(queued -> queued.future().complete(result.get(queued.uuid())));
            });
         }
      }
   }
}
