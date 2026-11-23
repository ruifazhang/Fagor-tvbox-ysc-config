package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Fagor CMS aggregator. Original implementation for Fagor's curated TVBox config.
 * ext JSON: {"sites":[{"name":"source name","api":"https://host/api.php/provide/vod/"}]}
 */
public class Fagor extends Spider {
    private static class Source {
        String name, api;
        Source(String n, String a) { name=n; api=a; }
    }
    private final List<Source> sources = new ArrayList<>();

    @Override public void init(Context context, String extend) {
        try {
            String raw = extend == null ? "" : extend.trim();
            if (raw.startsWith("http://") || raw.startsWith("https://")) raw = get(raw);
            JSONObject cfg = new JSONObject(raw);
            JSONArray arr = cfg.optJSONArray("sites");
            if (arr == null) return;
            for (int i=0;i<arr.length();i++) {
                JSONObject o=arr.optJSONObject(i);
                if (o==null) continue;
                String api=o.optString("api","").trim();
                if (!api.isEmpty()) sources.add(new Source(o.optString("name","源"+(i+1)),api));
            }
        } catch (Exception ignored) { }
    }

    @Override public String homeContent(boolean filter) {
        JSONArray classes=new JSONArray(), list=new JSONArray();
        for (int i=0;i<sources.size();i++) {
            Source s=sources.get(i);
            try {
                JSONObject data=new JSONObject(get(action(s.api,"ac=list&pg=1")));
                JSONArray cls=data.optJSONArray("class");
                if (cls!=null) for(int j=0;j<cls.length();j++) {
                    JSONObject c=cls.optJSONObject(j); if(c==null) continue;
                    JSONObject n=new JSONObject();
                    String tid=c.optString("type_id",c.optString("type_id_1",""));
                    n.put("type_id",i+"|"+tid);
                    n.put("type_name",s.name+" · "+c.optString("type_name","分类"));
                    classes.put(n);
                }
                appendVideos(list,data.optJSONArray("list"),i,s.name);
            } catch(Exception ignored) { }
        }
        return new JSONObject().put("class",classes).put("list",list).toString();
    }

    @Override public String homeVideoContent() { return new JSONObject().put("list",new JSONArray()).toString(); }

    @Override public String categoryContent(String tid,String pg,boolean filter,HashMap<String,String> extend) {
        try {
            String[] p=tid.split("\\|",2); int idx=Integer.parseInt(p[0]);
            if(idx<0 || idx>=sources.size()) return empty();
            String page=(pg==null || pg.isEmpty())?"1":pg;
            String url=action(sources.get(idx).api,"ac=list&ac=list&t="+enc(p.length>1?p[1]:"")+"&pg="+enc(page));
            JSONObject data=new JSONObject(get(url)); JSONArray out=new JSONArray();
            appendVideos(out,data.optJSONArray("list"),idx,sources.get(idx).name);
            JSONObject result=new JSONObject().put("list",out);
            for(String k:new String[]{"page","pagecount","limit","total"}) if(data.has(k)) result.put(k,data.opt(k));
            return result.toString();
        } catch(Exception e) { return empty(); }
    }

    @Override public String detailContent(List<String> ids) {
        JSONArray out=new JSONArray();
        if(ids==null) return new JSONObject().put("list",out).toString();
        for(String id:ids) try {
            String[] p=id.split("\\|",2); int idx=Integer.parseInt(p[0]);
            if(idx<0 || idx>=sources.size()) continue;
            String url=action(sources.get(idx).api,"ac=detail&ids="+enc(p.length>1?p[1]:""));
            JSONObject data=new JSONObject(get(url)); JSONArray arr=data.optJSONArray("list");
            if(arr!=null) for(int i=0;i<arr.length();i++) out.put(arr.getJSONObject(i));
        } catch(Exception ignored) { }
        return new JSONObject().put("list",out).toString();
    }

    @Override public String searchContent(String key,boolean quick) { return searchContent(key,quick,"1"); }

    @Override public String searchContent(String key,boolean quick,String pg) {
        JSONArray out=new JSONArray();
        for(int i=0;i<sources.size();i++) try {
            Source s=sources.get(i);
            JSONObject data=new JSONObject(get(action(s.api,"ac=detail&wd="+enc(key)+"&pg="+enc(pg==null?"1":pg))));
            appendVideos(out,data.optJSONArray("list"),i,s.name);
        } catch(Exception ignored) { }
        return new JSONObject().put("list",out).toString();
    }

    @Override public String playerContent(String flag,String id,List<String> vipFlags) {
        return new JSONObject().put("parse",0).put("playUrl","").put("url",id).put("header",new JSONObject()).toString();
    }

    private void appendVideos(JSONArray out,JSONArray arr,int idx,String source) throws Exception {
        if(arr==null) return;
        for(int i=0;i<arr.length();i++) {
            JSONObject v=arr.optJSONObject(i); if(v==null) continue;
            JSONObject n=new JSONObject(v.toString());
            String id=v.optString("vod_id","");
            n.put("vod_id",idx+"|"+id);
            if(TextUtils.isEmpty(n.optString("vod_name"))) n.put("vod_name",source);
            out.put(n);
        }
    }
    private String action(String api,String query) {
        String base=api;
        if(base.contains("?")) return base+"&"+query;
        return base+"?"+query;
    }
    private String enc(String s) { try { return URLEncoder.encode(s==null?"":"s","UTF-8").replace("s",""); } catch(Exception e) { return ""; } }
    private String get(String address) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent","Mozilla/5.0");
        c.setRequestProperty("Accept","application/json,text/plain,*/*");
        try {
            InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
            BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));
            StringBuilder b=new StringBuilder(); String line;
            while((line=r.readLine())!=null)b.append(line);
            return b.toString();
        } finally { c.disconnect(); }
    }
    private String empty() { return "{\"list\":[]}"; }
}
