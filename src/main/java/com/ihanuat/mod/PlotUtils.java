package com.ihanuat.mod;

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
            Component header = ((com.ihanuat.mod.mixin.PlayerTabOverlayAccessor) client.gui.getTabList()).getHeader();
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

        // 1. Precise regex for "Plot - [ID]" or "Plot: [ID]" or "Plot [ID]"
        // We match "Plot" followed by any non-alphanumeric junk, then capture the
        // actual ID
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)Plot[\\s:\\-]*([\\w\\d]+)").matcher(clean);
        if (m.find()) {
            return m.group(1).trim();
        }

        // 2. Fallback for strings that just contain "Plot" and some digits
        if (clean.toLowerCase().contains("plot")) {
            String digits = clean.replaceAll("(?i).*?(\\d+).*", "$1");
            if (digits.length() < 3 && !digits.equals(clean)) { // Sanity check for plot IDs
                return digits;
            }
        }

        return null;
    }
}
