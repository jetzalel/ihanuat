package com.ihanuat.mod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RestStateManager {
    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getGameDir().resolve("macro_rest_state.json");

    public static void saveReconnectTime(long epochSeconds, boolean shouldResume) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("reconnectAt", epochSeconds);
            obj.addProperty("shouldResume", shouldResume);
            Files.writeString(STATE_FILE, new Gson().toJson(obj));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long loadReconnectTime() {
        if (!Files.exists(STATE_FILE))
            return 0;
        try {
            JsonObject obj = JsonParser.parseString(
                    Files.readString(STATE_FILE)).getAsJsonObject();
            return obj.get("reconnectAt").getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean shouldResume() {
        if (!Files.exists(STATE_FILE))
            return false;
        try {
            JsonObject obj = JsonParser.parseString(
                    Files.readString(STATE_FILE)).getAsJsonObject();
            return obj.has("shouldResume") && obj.get("shouldResume").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public static void clearState() {
        try {
            Files.deleteIfExists(STATE_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
