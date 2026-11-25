package com.eternalengine.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Step 26 (systems A3, A7, K4): the list of every data file the mod carries, with its size, SHA-256, rows, role,
 * sources, licences, recorded/estimated mark, builder script and pack ({@code data/eternalengine/eternal/manifest.json},
 * written by {@code worldfill/build_manifest.py}).
 *
 * <p>{@code /ee check all} proves with it that the data in the game is exactly the data that was built and checked:
 * every shipped file is there with the same size and hash, every file names a licence, none forbids commercial use
 * except the one named exception ({@code history/prices.json.gz}, printed on every check run), and the credits file
 * ({@code DATA_CREDITS.md}, written from the same list) travels with the mod.
 */
public final class DataManifest {

    public static final String ROOT = "/data/eternalengine/eternal/";
    public static final String RESOURCE = ROOT + "manifest.json";
    public static final String CREDITS = "/DATA_CREDITS.md";
    private static final Pattern NON_COMMERCIAL = Pattern.compile("(?i)\\bNC\\b|non-?commercial");

    public record Entry(String path, long bytes, String sha256, boolean shipped, long rows, String role,
                        List<String> sources, List<String> licences, String mark, String markNote, String builder,
                        String pack) {}

    public record Source(String id, String name, String licence, String duty) {}

    /** A shipped file that is missing or differs from what was built. */
    public record Problem(String path, String what) {}

    private final List<Entry> files;
    private final Map<String, Source> sources;
    private final String problem;

    private DataManifest(List<Entry> files, Map<String, Source> sources, String problem) {
        this.files = files;
        this.sources = sources;
        this.problem = problem;
    }

    public static DataManifest load() {
        try (InputStream in = DataManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new DataManifest(List.of(), Map.of(), "resource " + RESOURCE + " absent");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Source> src = new LinkedHashMap<>();
            if (root.has("sources")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sources").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    src.put(e.getKey(), new Source(e.getKey(), str(o, "name"), str(o, "licence"), str(o, "duty")));
                }
            }
            List<Entry> out = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("files")) {
                JsonObject o = el.getAsJsonObject();
                out.add(new Entry(str(o, "path"), o.get("bytes").getAsLong(), str(o, "sha256"),
                        o.get("shipped").getAsBoolean(),
                        o.has("rows") && !o.get("rows").isJsonNull() ? o.get("rows").getAsLong() : -1L,
                        str(o, "role"), list(o, "sources"), list(o, "licences"), str(o, "mark"), str(o, "markNote"),
                        str(o, "builder"), str(o, "pack")));
            }
            return new DataManifest(List.copyOf(out), Map.copyOf(src), null);
        } catch (Exception e) {
            return new DataManifest(List.of(), Map.of(), "manifest unreadable: " + e);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static List<String> list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (JsonElement e : a) {
                out.add(e.getAsString());
            }
        }
        return List.copyOf(out);
    }

    /** Null when the manifest loaded, else why not. */
    public String problem() {
        return problem;
    }

    public List<Entry> files() {
        return files;
    }

    public Map<String, Source> sources() {
        return sources;
    }

    public List<Entry> shipped() {
        return files.stream().filter(Entry::shipped).toList();
    }

    public long shippedBytes() {
        return shipped().stream().mapToLong(Entry::bytes).sum();
    }

    /** Every shipped file read through: present, the same size, the same SHA-256. */
    public List<Problem> verify() {
        List<Problem> out = new ArrayList<>();
        byte[] buf = new byte[1 << 16];
        for (Entry e : shipped()) {
            try (InputStream in = DataManifest.class.getResourceAsStream(ROOT + e.path())) {
                if (in == null) {
                    out.add(new Problem(e.path(), "missing"));
                    continue;
                }
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long n = 0;
                for (int k; (k = in.read(buf)) > 0; ) {
                    md.update(buf, 0, k);
                    n += k;
                }
                String hex = java.util.HexFormat.of().formatHex(md.digest());
                if (n != e.bytes()) {
                    out.add(new Problem(e.path(), n + " bytes, the manifest says " + e.bytes()));
                } else if (!hex.equals(e.sha256())) {
                    out.add(new Problem(e.path(), "different content (SHA-256 " + hex.substring(0, 12) + "...)"));
                }
            } catch (Exception ex) {
                out.add(new Problem(e.path(), "unreadable: " + ex.getMessage()));
            }
        }
        return out;
    }

    /** Masters (shipped=false) that are in the mod anyway. */
    public List<String> mastersPresent() {
        List<String> out = new ArrayList<>();
        for (Entry e : files) {
            if (!e.shipped() && DataManifest.class.getResource(ROOT + e.path()) != null) {
                out.add(e.path());
            }
        }
        return out;
    }

    public List<String> withoutLicence() {
        return files.stream().filter(e -> e.licences().isEmpty() || e.sources().isEmpty()).map(Entry::path).toList();
    }

    public List<String> nonCommercial() {
        return files.stream().filter(e -> e.licences().stream().anyMatch(l -> NON_COMMERCIAL.matcher(l).find()))
                .map(Entry::path).toList();
    }

    /** Sources the credits file does not name (it is written from the manifest, so none). */
    public List<String> missingFromCredits() {
        String text;
        try (InputStream in = DataManifest.class.getResourceAsStream(CREDITS)) {
            if (in == null) {
                return List.of("(no " + CREDITS + " in the mod)");
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return List.of("(" + CREDITS + " unreadable)");
        }
        List<String> out = new ArrayList<>();
        for (Source s : sources.values()) {
            if (!text.contains(s.name())) {
                out.add(s.id());
            }
        }
        return out;
    }

    /** Files of each mark: recorded, estimated, mixed. */
    public Map<String, Integer> marks() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Entry e : files) {
            out.merge(e.mark().isBlank() ? "unmarked" : e.mark(), 1, Integer::sum);
        }
        return out;
    }
}
