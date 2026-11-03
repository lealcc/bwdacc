package com.leal.comfyremote;

import java.util.*;

public final class PromptTagsTest {
    static void check(boolean test, String label) { if (!test) throw new AssertionError(label); }
    public static void main(String[] args) {
        Map<String,String> tags = new HashMap<>();
        tags.put("@maya", "traveler holding $5 and #beach");
        tags.put("$film", "film grain \\ warm light");
        tags.put("#beach", "a beach at dawn");
        check(PromptTags.expand("@MAYA, $film at #beach.", tags).equals("traveler holding $5 and #beach, film grain \\ warm light at a beach at dawn."), "all types, case, punctuation, literal replacement, no recursion");
        String literal = "mail@maya.com $50 #unknown $film2 @@maya foo#beach \\$film";
        check(PromptTags.expand(literal, tags).equals(literal), "unknown tags, email, money, escaped tags and boundaries stay literal");
        for (String prefix : new String[]{"@", "$", "#"}) {
            check(PromptTags.start("Photo " + prefix, 7) == 6, "bare prefix opens suggestions");
            check(PromptTags.start("Photo " + prefix + "fi", 9) == 6, "partial tag");
            check(PromptTags.start("Photo " + prefix + "fi ", 10) == -1, "space closes suggestions");
        }
        check(PromptTags.start("mail@maya", 9) == -1 && PromptTags.start("\\$film", 6) == -1, "autocomplete boundaries");
        check(PromptTags.start("$film", 3) == 0 && PromptTags.end("$film rest", 3) == 5, "replace whole token when cursor is in middle");
        System.out.println("PASS: prompt tags");
    }
}
