package com.example.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;

import java.util.Collection;

public class PlotUtils {

    public static String getPlotName(Minecraft client) {
        if (client.level == null || client.player == null)
            return "Unknown";

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null)
            return "None";

        // 1. Check Scoreboard Sidebar
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(sidebar);
            for (PlayerScoreEntry entry : scores) {
                String entryName = entry.owner();
                PlayerTeam team = scoreboard.getPlayersTeam(entryName);
                String fullText = entryName;
                if (team != null) {
                    fullText = team.getPlayerPrefix().getString() + entryName + team.getPlayerSuffix().getString();
                }

                String plot = extractPlot(fullText);
                if (plot != null)
                    return plot;
            }
        }

        // 2. Fallback to Tablist Header
        if (client.gui.getTabList() != null) {
            Component header = ((com.example.mod.mixin.PlayerTabOverlayAccessor) client.gui.getTabList()).getHeader();
            if (header != null) {
                String plot = extractPlot(header.getString());
                if (plot != null)
                    return plot;
            }
        }

        return "Not in Plot";
    }

    private static String extractPlot(String text) {
        if (text == null || text.isEmpty())
            return null;

        // Strip color codes
        String clean = text.replaceAll("§[0-9a-fk-or]", "").trim();

        // Hypixel format is often "Plot - [Name]" or "Plot: [Name]"
        if (clean.toLowerCase().contains("plot")) {
            // Split by common delimiters
            String[] parts = clean.split("[:\\-\\ ]");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equalsIgnoreCase("plot")) {
                    String val = parts[i + 1].trim();
                    if (!val.isEmpty())
                        return val;
                }
            }

            // Regex fallback
            String fallback = clean.replaceAll("(?i).*Plot[\\s:\\-]*", "").trim().split(" ")[0];
            if (!fallback.isEmpty())
                return fallback;
        }
        return null;
    }
}
