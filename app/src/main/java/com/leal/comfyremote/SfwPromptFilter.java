package com.leal.comfyremote;

import org.json.*;
import java.util.*;
import java.util.regex.Pattern;

/** A local word list, not a content classifier. Only submitted positive text is changed. */
final class SfwPromptFilter {
    // Literal words/phrases, case-insensitive. Keep this list free of regular expressions.
    static final String WORDS =
        "nsfw|porn|porno|pornô|pornografia|pornografía|pornographic|pornography|pornografico|pornográfico|pornografica|pornográfica|"
        +"nude|nudes|nudity|naked|nakedness|nudez|nu|nua|nus|nuas|desnudo|desnuda|desnudos|desnudas|despido|despida|sem roupa|sem roupas|without clothes|"
        +"topless|bottomless|full frontal|see through lingerie|hentai|erotic|erotica|erótico|erótica|erotico|erotica|sexual|sexually|sex|sexo|sexuais|sexualmente|"
        +"intercourse|penetration|penetração|penetracao|masturbation|masturbating|masturbação|masturbacao|orgasm|orgasmo|orgasms|orgasmos|orgy|orgia|"
        +"blowjob|handjob|footjob|gangbang|deepthroat|bukkake|cumshot|cum|semen|sêmen|ejaculation|ejaculação|ejaculacao|"
        +"penis|pênis|vagina|vulva|genitals|genitalia|genitália|clitoris|clitóris|pussy|cock|dick|tits|boobs|nipples|nipple|mamilos|mamilo|"
        +"dildo|dildos|sex toy|sex toys|vibrador|vibradores|anal sex|oral sex|sexo anal|sexo oral|"
        +"nackt|nacktheit|pornografie|pornografisch|erotisch|geschlechtsverkehr|brustwarzen|"
        +"порно|порнография|обнаженная|обнажённая|обнаженный|обнажённый|нагота|голая|голый|секс|эротика|минет|оргазм|"
        +"desnudez|desnudarse|pezones|masturbación|eyaculación";
    static final String CJK = "裸体|裸體|裸露|全裸|ヌード|ポルノ|ヘンタイ|色情|性交|性爱|性愛|阴茎|陰莖|阴道|陰道|乳首|乳头|乳頭|射精";
    static final String REGEX = "(?<!\\w)(?:"+WORDS.replace(" ","[\\s_-]+")+")(?!\\w)|(?:"+CJK+")";
    // Android does not implement UNICODE_CHARACTER_CLASS; spell out its word boundary.
    private static final Pattern BLOCKED = Pattern.compile(REGEX.replace("\\w","[\\p{L}\\p{M}\\p{N}_]"),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final String PREFIX="bwdacc_sfw_prompt_", MARK="bwdacc_sfw_prompt_filter";

    static String clean(String text,boolean sfw){
        if(!sfw||text==null)return text;
        String filtered=BLOCKED.matcher(text).replaceAll("");
        return filtered.equals(text)?text:filtered.replaceAll("[\\t ]{2,}"," ").trim();
    }

    static void apply(JSONObject graph,boolean sfw)throws JSONException{
        strip(graph);
        if(!sfw)return;
        Set<String> negative=new HashSet<>();
        List<String> nodes=new ArrayList<>();
        for(Iterator<String> it=graph.keys();it.hasNext();){
            String id=it.next();nodes.add(id);JSONObject node=graph.optJSONObject(id),in=inputs(node);if(in==null)continue;
            JSONArray link=in.optJSONArray("negative");if(link!=null)ancestors(graph,link.optString(0),negative);
            JSONObject meta=node.optJSONObject("_meta");String title=meta==null?"":meta.optString("title").toLowerCase(Locale.ROOT);
            if(title.contains("negative")||title.contains("negativo"))negative.add(id);
        }
        Collections.sort(nodes);
        for(String id:nodes){
            JSONObject node=graph.optJSONObject(id),in=inputs(node);if(in==null||negative.contains(id))continue;
            String type=node.optString("class_type");
            if(type.startsWith("CLIPTextEncode")){
                for(String key:new String[]{"text","text_g","text_l"})filterInput(graph,id,key);
            }else if(type.equals("MiniMaxH3ImageToVideo")||type.equals("MiniMaxH3ReferenceToVideo"))filterInput(graph,id,"prompt");
            // Explicit prompt fields from imported workflows are supported without touching other values.
            JSONObject meta=node.optJSONObject("_meta");String key=meta==null?"":meta.optString("momentos_prompt_input");
            if(!key.isEmpty())filterInput(graph,id,key);
        }
    }

    private static void filterInput(JSONObject graph,String id,String key)throws JSONException{
        JSONObject in=inputs(graph.optJSONObject(id));Object value=in.opt(key);
        if(value instanceof String){in.put(key,clean((String)value,true));return;}
        if(!(value instanceof JSONArray))return;
        JSONArray link=(JSONArray)value;if(link.length()!=2||owned(graph.optJSONObject(link.optString(0))))return;
        String slot=PREFIX+id+"_"+key;int suffix=0;while(graph.has(slot))slot=PREFIX+id+"_"+key+"_"+(++suffix);
        // A native CPU text node also filters Agent output and dynamically composed boxes.
        // Place it at the positive input so a shared source never alters a negative prompt.
        JSONObject settings=new JSONObject().put("string",new JSONArray(link.toString())).put("regex_pattern",REGEX).put("replace","")
            .put("case_insensitive",true).put("multiline",false).put("dotall",false).put("count",0);
        graph.put(slot,new JSONObject().put("class_type","RegexReplace").put("inputs",settings).put("_meta",new JSONObject().put(MARK,true)));
        in.put(key,new JSONArray().put(slot).put(0));
    }

    static void strip(JSONObject graph)throws JSONException{
        Map<String,Object> originals=new HashMap<>();
        for(Iterator<String> it=graph.keys();it.hasNext();){String id=it.next();JSONObject n=graph.optJSONObject(id);if(owned(n))originals.put(id,inputs(n).opt("string"));}
        if(originals.isEmpty())return;
        for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject in=inputs(graph.optJSONObject(it.next()));if(in==null)continue;
            for(Iterator<String> keys=in.keys();keys.hasNext();){String key=keys.next();JSONArray link=in.optJSONArray(key);if(link!=null&&link.length()==2&&originals.containsKey(link.optString(0)))in.put(key,originals.get(link.optString(0)));}
        }
        for(String id:originals.keySet())graph.remove(id);
    }
    private static boolean owned(JSONObject node){return node!=null&&"RegexReplace".equals(node.optString("class_type"))&&node.optJSONObject("_meta")!=null&&node.optJSONObject("_meta").optBoolean(MARK)&&inputs(node)!=null;}
    private static JSONObject inputs(JSONObject node){return node==null?null:node.optJSONObject("inputs");}
    private static void ancestors(JSONObject graph,String id,Set<String> visited){
        if(!visited.add(id))return;JSONObject node=graph.optJSONObject(id),in=inputs(node);if(in==null)return;
        // Klein derives its empty negative conditioning from the positive encoder.
        if("ConditioningZeroOut".equals(node.optString("class_type")))return;
        for(Iterator<String> keys=in.keys();keys.hasNext();){JSONArray link=in.optJSONArray(keys.next());if(link!=null&&link.length()==2&&graph.has(link.optString(0)))ancestors(graph,link.optString(0),visited);}
    }
}
