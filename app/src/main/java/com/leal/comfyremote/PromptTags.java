package com.leal.comfyremote;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands all tag kinds in one pass. Inserted descriptions are never interpreted as tags. */
final class PromptTags {
    private static final Pattern TOKEN = Pattern.compile("(?<![\\p{L}\\p{N}_@#$&\\\\])([@#$&][\\p{L}\\p{N}_-]+)");

    static java.util.Set<String> tokens(String prompt) {
        java.util.Set<String> result=new java.util.LinkedHashSet<>();Matcher matcher=TOKEN.matcher(prompt);
        while(matcher.find())result.add(matcher.group().toLowerCase(Locale.ROOT));return result;
    }

    static String expand(String prompt, Map<String, String> descriptions) {
        Matcher matcher = TOKEN.matcher(prompt);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = descriptions.get(matcher.group().toLowerCase(Locale.ROOT));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static boolean word(char value) { return Character.isLetterOrDigit(value) || value == '_' || value == '-'; }
    static boolean prefix(char value) { return value == '@' || value == '$' || value == '#' || value == '&'; }

    static int start(String value, int cursor) {
        if (cursor < 0 || cursor > value.length()) return -1;
        int start = cursor;
        while (start > 0 && word(value.charAt(start - 1))) start--;
        if (start == 0 || !prefix(value.charAt(start - 1))) return -1;
        int at = start - 1;
        if (at > 0) {
            char previous = value.charAt(at - 1);
            if (Character.isLetterOrDigit(previous) || previous == '_' || prefix(previous) || previous == '\\') return -1;
        }
        return at;
    }

    static int end(String value, int cursor) {
        while (cursor < value.length() && word(value.charAt(cursor))) cursor++;
        return cursor;
    }
}
