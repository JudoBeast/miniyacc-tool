import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Plain Java HTTP server: serves web/ and exposes /api/analyze, /api/parse, /api/generate, /api/health. */
public class Server {
    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                case '\r': break; case '\t': b.append("\\t"); break; default: b.append(c); }
        }
        return b.append('"').toString();
    }
    static String list(Collection<String> c) { StringBuilder b = new StringBuilder("["); int i = 0; for (String s : c) b.append(i++ > 0 ? "," : "").append(q(s)); return b.append("]").toString(); }
    static String setMap(Map<String, Set<String>> m, Collection<String> keys) {
        StringBuilder b = new StringBuilder("{"); int i = 0;
        for (String k : keys) b.append(i++ > 0 ? "," : "").append(q(k)).append(":").append(list(m.get(k)));
        return b.append("}").toString();
    }
    static String analysisJson(SlrEngine e) {
        StringBuilder b = new StringBuilder("{\"ok\":true");
        List<String> ps = new ArrayList<>(); for (SlrEngine.Prod p : e.prods) ps.add(p.toString());
        b.append(",\"productions\":").append(list(ps));
        b.append(",\"first\":").append(setMap(e.first, e.nonTerms)).append(",\"follow\":").append(setMap(e.follow, e.nonTerms));
        b.append(",\"states\":[");
        for (int i = 0; i < e.states.size(); i++) {
            List<String> items = new ArrayList<>(); for (SlrEngine.Item it : e.states.get(i)) items.add(e.itemText(it));
            StringBuilder t = new StringBuilder("{"); int k = 0;
            for (Map.Entry<String, Integer> x : e.trans.get(i).entrySet()) t.append(k++ > 0 ? "," : "").append(q(x.getKey())).append(":").append(x.getValue());
            b.append(i > 0 ? "," : "").append("{\"id\":").append(i).append(",\"items\":").append(list(items)).append(",\"transitions\":").append(t).append("}}");
        }
        b.append("],\"columns\":").append(list(e.columns())).append(",\"gotoColumns\":").append(list(e.gotoColumns())).append(",\"table\":[");
        for (int i = 0; i < e.states.size(); i++) {
            StringBuilder a = new StringBuilder("{"); int k = 0;
            for (String c : e.columns()) { String v = e.action.get(i).get(c); if (v != null) a.append(k++ > 0 ? "," : "").append(q(c)).append(":").append(q(v)); }
            for (String g : e.gotoColumns()) { Integer v = e.gotoT.get(i).get(g); if (v != null) a.append(k++ > 0 ? "," : "").append(q(g)).append(":").append(q(String.valueOf(v))); }
            b.append(i > 0 ? "," : "").append(a).append("}");
        }
        b.append("],\"conflict\":").append(!e.conflicts.isEmpty()).append(",\"conflicts\":").append(list(e.conflicts));
        return b.toString();
    }
    static Map<String, String> form(HttpExchange x) throws IOException {
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) { int i = kv.indexOf('='); if (i > 0) m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8)); }
        return m;
    }
    static void send(HttpExchange x, int code, String type, byte[] body) throws IOException {
        x.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        x.sendResponseHeaders(code, body.length); try (OutputStream o = x.getResponseBody()) { o.write(body); }
    }
    static void json(HttpExchange x, String s) throws IOException { send(x, 200, "application/json", s.getBytes(StandardCharsets.UTF_8)); }
    interface Api { String run(Map<String, String> f) throws Exception; }
    static void api(HttpServer s, String path, Api a) {
        s.createContext(path, x -> {
            try { json(x, a.run(x.getRequestMethod().equals("POST") ? form(x) : new HashMap<>())); }
            catch (IllegalArgumentException ex) { json(x, "{\"ok\":false,\"error\":" + q(ex.getMessage()) + "}"); }
            catch (Exception ex) { json(x, "{\"ok\":false,\"error\":" + q("Server error: " + ex) + "}"); }
        });
    }
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        api(s, "/api/health", f -> "{\"ok\":true,\"status\":\"MiniYACC server is working\"}");
        api(s, "/api/analyze", f -> analysisJson(new SlrEngine(f.getOrDefault("rules", ""), f.get("start"))) + "}");
        api(s, "/api/parse", f -> {
            SlrEngine e = new SlrEngine(f.getOrDefault("rules", ""), f.get("start"));
            boolean[] ok = new boolean[1]; List<SlrEngine.Step> tr = e.parse(f.getOrDefault("input", ""), ok);
            StringBuilder b = new StringBuilder(analysisJson(e)).append(",\"trace\":[");
            for (int i = 0; i < tr.size(); i++) { SlrEngine.Step t = tr.get(i);
                b.append(i > 0 ? "," : "").append("{\"step\":").append(t.n).append(",\"stack\":").append(q(t.stack)).append(",\"input\":").append(q(t.input)).append(",\"action\":").append(q(t.action)).append("}"); }
            return b.append("],\"accepted\":").append(ok[0]).append("}").toString();
        });
        api(s, "/api/generate", f -> {
            SlrEngine e = new SlrEngine(f.getOrDefault("rules", ""), f.get("start"));
            String src = ParserGenerator.generate(e);
            Files.createDirectories(Paths.get("generated")); Files.writeString(Paths.get("generated/GeneratedParser.java"), src);
            return "{\"ok\":true,\"file\":\"generated/GeneratedParser.java\",\"source\":" + q(src) + "}";
        });
        s.createContext("/", x -> {
            String p = x.getRequestURI().getPath(); if (p.equals("/")) p = "/index.html";
            Path f = Paths.get("web").resolve(p.substring(1)).normalize();
            if (!f.startsWith(Paths.get("web").normalize()) || !Files.isRegularFile(f)) { send(x, 404, "text/plain", "Not found".getBytes()); return; }
            String n = f.toString(); String type = n.endsWith(".html") ? "text/html" : n.endsWith(".css") ? "text/css" : n.endsWith(".js") ? "application/javascript" : "application/octet-stream";
            send(x, 200, type, Files.readAllBytes(f));
        });
        s.start(); System.out.println("MiniYACC server running on port " + port);
    }
}
