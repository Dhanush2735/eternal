package com.eternalengine.brain;

/**
 * Text-only context supplied to the server-local brain chat.
 *
 * Attachments are advisory context only. They must never be treated as commands
 * to mutate Minecraft state, edit saves, or bypass Eternal Engine validation.
 */
public record BrainChatAttachment(String name, String content) {

    public BrainChatAttachment {
        name = sanitizeName(name);
        content = content == null ? "" : content;
    }

    private static String sanitizeName(String value) {
        if (value == null || value.isBlank()) {
            return "attachment.txt";
        }
        String trimmed = value.trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
    }
}
