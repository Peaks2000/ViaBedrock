/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.protocol.rewriter;

import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormText {

    private static final String SELECTOR_LAYOUT = "ui/cubecraft/cubecraft_game_selector.json";
    private static final String SELECTOR_TITLE_MARKER = "&game_selector";
    private static final Pattern SELECTOR_MARKER = Pattern.compile(
        "&selector_(?:favourite_empty|favourite_selected|featured|hub|passes|profile|settings)(?![a-zA-Z0-9_])");
    private static final Pattern SELECTOR_PADDING = Pattern.compile("`{3,}");

    public static boolean isCubeCraftSelector(final ResourcePackStorage packs, final String title) {
        return (title.contains(SELECTOR_TITLE_MARKER) || title.strip().equals("Favourite"))
            && packs.getPackStackTopToBottom().stream().anyMatch(pack -> pack.content().contains(SELECTOR_LAYOUT));
    }

    public static String selectorTitle(final String title) {
        final String label = title.replace(SELECTOR_TITLE_MARKER, "").strip();
        return label.isEmpty() ? "Game Selector" : label;
    }

    public static String selectorButton(final String text) {
        final Matcher marker = SELECTOR_MARKER.matcher(text);
        final String label = marker.replaceAll("");
        final String[] fields = SELECTOR_PADDING.split(label);
        final StringBuilder readable = new StringBuilder();
        for (String field : fields) {
            final String part = field.strip();
            if (!part.isEmpty()) {
                if (!readable.isEmpty()) {
                    readable.append(" · ");
                }
                readable.append(part);
            }
        }
        final String result = readable.toString().replace("`", "").strip();
        if (result.isEmpty()) {
            if (text.contains("&selector_favourite_selected")) {
                return "Remove favourite";
            }
            if (text.contains("&selector_favourite_empty")) {
                return "Add favourite";
            }
        }
        return result;
    }

    private FormText() {
    }

}
