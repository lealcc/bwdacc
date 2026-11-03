package com.leal.comfyremote;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Resolves explicit character handles once, without expanding text from descriptions. */
final class CharacterMentions {
    static final Pattern TOKEN=Pattern.compile("(?<![\\p{L}\\p{N}_@])@([\\p{L}\\p{N}_-]+)");
    static String handle(String name){
        String slug=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
        return slug.isEmpty()?"personagem":slug;
    }
    static String expand(String prompt,Map<String,String> descriptions){
        Matcher m=TOKEN.matcher(prompt);StringBuffer out=new StringBuffer();
        while(m.find()){String description=descriptions.get(m.group(1).toLowerCase(Locale.ROOT));
            m.appendReplacement(out,Matcher.quoteReplacement(description==null?m.group():description));}
        m.appendTail(out);return out.toString();
    }
    static boolean contains(String prompt,Map<String,String> descriptions){
        Matcher m=TOKEN.matcher(prompt);while(m.find())if(descriptions.containsKey(m.group(1).toLowerCase(Locale.ROOT)))return true;return false;
    }
    static int start(String value,int cursor){
        if(cursor<0||cursor>value.length())return -1;
        int start=cursor;while(start>0&&(Character.isLetterOrDigit(value.charAt(start-1))||value.charAt(start-1)=='_'||value.charAt(start-1)=='-'))start--;
        if(start==0||value.charAt(start-1)!='@')return -1;
        int at=start-1;if(at>0&&(Character.isLetterOrDigit(value.charAt(at-1))||value.charAt(at-1)=='_'||value.charAt(at-1)=='@'))return -1;
        return at;
    }
}
