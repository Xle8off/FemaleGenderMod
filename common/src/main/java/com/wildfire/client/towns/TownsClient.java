package com.wildfire.client.towns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class TownsClient {
   private static Consumer<byte[]> sender = bytes -> {};
   private static BooleanSupplier available = () -> false;
   private static TownsClient.Snapshot snapshot;
   private static long requestedAt;
   private static long receivedAt;
   private static long waitingSince;
   private static int requestProtocol = 4;
   private static LongSupplier clock = System::currentTimeMillis;
   private static final Logger LOG = System.getLogger("SpringPlus-Towns");
   private static String error = "";
   private static Runnable openHandler = () -> {};
   private static Runnable imageTicket = () -> {};
   private static long announcedAt;

   public static void configureImageTicket(Runnable handler) {
      imageTicket = handler;
   }

   public static void requestImageTicket() {
      imageTicket.run();
   }

   public static void setOpenHandler(Runnable handler) {
      openHandler = handler;
   }

   public static void announce() {
      if (available.getAsBoolean() && System.currentTimeMillis() - announcedAt > 60000L && action(9, "")) {
         announcedAt = System.currentTimeMillis();
      }
   }

   private TownsClient() {
   }

   public static void configure(Consumer<byte[]> send, BooleanSupplier supported) {
      sender = send;
      available = supported;
   }

   public static TownsClient.Snapshot snapshot() {
      return !available.getAsBoolean() ? null : snapshot;
   }

   public static String status() {
      if (!available.getAsBoolean()) {
         return "На этом сервере интеграция Towns недоступна";
      } else if (!error.isEmpty()) {
         return error;
      } else {
         return waitingSince != 0L && clock.getAsLong() - waitingSince > 6000L ? "Нет ответа от Towns. Повторяем запрос…" : "Загружаем города…";
      }
   }

   public static void refresh() {
      long now = clock.getAsLong();
      if (waitingSince == 0L || now - waitingSince >= 6000L) {
         if (waitingSince != 0L && receivedAt == 0L) {
            requestProtocol = Math.max(2, requestProtocol - 1);
         }

         if (now - requestedAt >= 3000L) {
            action(0, "");
         }
      }
   }

   public static boolean action(int action, String target) {
      return action(action, target, "");
   }

   public static boolean action(int action, String target, String argument) {
      if (action >= 0 && action <= 16 && target.length() <= 36 && argument.length() <= (action >= 14 && action <= 15 ? 64 : 16) && available.getAsBoolean()) {
         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            if (action > 10 && requestProtocol < 3) {
               return false;
            }

            if (action != 16 || snapshot != null && snapshot.protocol() >= 4) {
               out.writeByte(requestProtocol);
               out.writeByte(action);
               out.writeUTF(target);
               out.writeUTF(argument);
               long now = clock.getAsLong();
               if (action != 9) {
                  requestedAt = now;
                  if (waitingSince == 0L || now - waitingSince >= 6000L) {
                     waitingSince = now;
                  }
               }

               sender.accept(bytes.toByteArray());
               if (action != 9) {
                  LOG.log(Level.INFO, "[towns-ui] request action=" + action + " bytes=" + bytes.size());
               }

               return true;
            } else {
               return false;
            }
         } catch (Exception e) {
            error = "Не удалось отправить действие Towns";
            return false;
         }
      } else {
         return false;
      }
   }

   public static void accept(byte[] bytes) {
      if(bytes.length>0&&bytes[0]==127){try{TerritoryPresence.accept(bytes);}catch(java.io.IOException ignored){}return;}
      try {
         snapshot = parse(bytes);
         receivedAt = clock.getAsLong();
         requestProtocol = Math.max(2, snapshot.protocol());
         LOG.log(
            Level.INFO,
            "[towns-ui] response bytes="
               + bytes.length
               + " latencyMs="
               + (waitingSince == 0L ? 0L : receivedAt - waitingSince)
               + " cities="
               + snapshot.cities().size()
         );
         waitingSince = 0L;
         error = "";
         if (bytes[0] >= 2 && bytes[1] != 0) {
            openHandler.run();
         }
      } catch (Exception e) {
         error = "Не удалось прочитать данные Towns";
         LOG.log(Level.WARNING, "[towns-ui] invalid response bytes=" + bytes.length, e);
      }
   }

   public static TownsClient.Snapshot parse(byte[] bytes) throws IOException {
      if (bytes.length > 30000) {
         throw new IOException("Size");
      }

      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      int version = in.readUnsignedByte();
      if (version >= 1 && version <= 4) {
         if (version >= 2) {
            in.readBoolean();
         }

         boolean passport = in.readBoolean();
         TownsClient.City own = in.readBoolean() ? city(in, version) : null;
         String role = text(in, 40);
         int count = count(in, 100);
         ArrayList<TownsClient.Resident> residents = new ArrayList<>();

         for (int i = 0; i < count; i++) {
            residents.add(new TownsClient.Resident(version >= 2 ? id(in) : "", text(in, 36), text(in, 40), in.readBoolean(), in.readLong(), in.readLong()));
         }

         List<TownsClient.City> cities = cities(in, 200, version);
         List<TownsClient.City> invitations = cities(in, 100, version);
         boolean invite = false;
         boolean kick = false;
         boolean roles = false;
         boolean founder = false;
         ArrayList<TownsClient.Candidate> candidates = new ArrayList<>();
         String feedback = "";
         if (version >= 2) {
            invite = in.readBoolean();
            kick = in.readBoolean();
            roles = in.readBoolean();
            founder = in.readBoolean();
            int candidateCount = count(in, 50);

            for (int i = 0; i < candidateCount; i++) {
               candidates.add(new TownsClient.Candidate(id(in), text(in, 36)));
            }

            feedback = text(in, 160);
         }

         ArrayList<TownsClient.Candidate> applications = new ArrayList<>();
         ArrayList<String> requested = new ArrayList<>();
         if (version >= 3) {
            int n = count(in, 100);

            for (int i = 0; i < n; i++) {
               applications.add(new TownsClient.Candidate(id(in), text(in, 36)));
            }

            n = count(in, 5);

            for (int i = 0; i < n; i++) {
               requested.add(id(in));
            }
         }

         if (in.available() != 0) {
            throw new IOException("Trailing bytes");
         } else {
            return new TownsClient.Snapshot(
               passport,
               own,
               role,
               List.copyOf(residents),
               cities,
               invitations,
               invite,
               kick,
               roles,
               founder,
               List.copyOf(candidates),
               feedback,
               version,
               List.copyOf(applications),
               List.copyOf(requested)
            );
         }
      } else {
         throw new IOException("Version");
      }
   }

   private static TownsClient.City city(DataInputStream in, int version) throws IOException {
      String id = id(in);
      String name = text(in, 64);
      String type = text(in, 40);
      int population = in.readInt();
      int capacity = in.readInt();
      if (population >= 0 && capacity >= 1 && population <= 10000 && capacity <= 10000) {
         boolean capital = in.readBoolean();
         int city = -1;
         int limit = -1;
         int farm = -1;
         int farmLimit = -1;
         if (version >= 3) {
            city = countValue(in);
            limit = countValue(in);
            farm = countValue(in);
            farmLimit = countValue(in);
         }

         String avatar = version >= 3 ? image(in) : "";
         String cover = version >= 3 ? image(in) : "";
         return new TownsClient.City(id, name, type, population, capacity, capital, city, limit, farm, farmLimit, avatar, cover);
      } else {
         throw new IOException("Population");
      }
   }

   private static String image(DataInputStream in) throws IOException {
      String value = text(in, 64);
      if (!value.isEmpty() && !value.matches("[a-f0-9]{64}")) {
         throw new IOException("Image ID");
      } else {
         return value;
      }
   }

   private static int countValue(DataInputStream in) throws IOException {
      int n = in.readInt();
      if (n >= 0 && n <= 100000) {
         return n;
      } else {
         throw new IOException("Chunk count");
      }
   }

   private static String id(DataInputStream in) throws IOException {
      String value = text(in, 36);

      try {
         UUID.fromString(value);
         return value;
      } catch (Exception e) {
         throw new IOException("ID");
      }
   }

   private static List<TownsClient.City> cities(DataInputStream in, int max, int version) throws IOException {
      int count = count(in, max);
      ArrayList<TownsClient.City> result = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         result.add(city(in, version));
      }

      return List.copyOf(result);
   }

   private static int count(DataInputStream in, int max) throws IOException {
      int n = in.readInt();
      if (n >= 0 && n <= max) {
         return n;
      } else {
         throw new IOException("Count");
      }
   }

   private static String text(DataInputStream in, int max) throws IOException {
      String value = in.readUTF();
      if (value.length() <= max && value.indexOf(167) < 0 && !value.codePoints().anyMatch(Character::isISOControl)) {
         return value;
      } else {
         throw new IOException("Text");
      }
   }

   public static void reset() {
      TerritoryPresence.reset();
      snapshot = null;
      waitingSince = 0L;
      announcedAt = 0L;
      receivedAt = 0L;
      requestedAt = 0L;
      requestProtocol = 4;
      error = "";
   }

   public static List<TownsClient.City> pendingCities(TownsClient.Snapshot data) {
      if (data == null) {
         return List.of();
      }

      HashMap<String, TownsClient.City> byId = new HashMap<>();
      data.cities().forEach(c -> byId.put(c.id(), c));
      return data.requestedTowns()
         .stream()
         .distinct()
         .map(id -> byId.getOrDefault(id, new TownsClient.City(id, "Город", "Заявка отправлена", 0, 0, false, -1, -1, -1, -1, "", "")))
         .toList();
   }

   public record Candidate(String id, String name) {
   }

   public record City(
      String id,
      String name,
      String type,
      int population,
      int capacity,
      boolean capital,
      int cityChunks,
      int cityLimit,
      int farmChunks,
      int farmLimit,
      String avatar,
      String cover
   ) {
   }

   public record Resident(String id, String name, String role, boolean online, long joined, long lastSeen) {
   }

   public record Snapshot(
      boolean passport,
      TownsClient.City own,
      String role,
      List<TownsClient.Resident> residents,
      List<TownsClient.City> cities,
      List<TownsClient.City> invitations,
      boolean canInvite,
      boolean canKick,
      boolean canRole,
      boolean founder,
      List<TownsClient.Candidate> candidates,
      String feedback,
      int protocol,
      List<TownsClient.Candidate> applications,
      List<String> requestedTowns
   ) {
   }
}
