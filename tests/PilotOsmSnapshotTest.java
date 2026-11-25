import com.eternalengine.hyderabad.PilotOsmSnapshot;
import com.eternalengine.hyderabad.PilotOsmSnapshot.Way;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class PilotOsmSnapshotTest {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static byte[] zip(String s) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(out)) { z.write(s.getBytes(StandardCharsets.UTF_8)); }
        return out.toByteArray();
    }
    static void rejects(String xml) throws Exception {
        try { PilotOsmSnapshot.readCharminar2026(new ByteArrayInputStream(zip(xml)), 2026); throw new AssertionError("Expected rejection"); }
        catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        List<Way> ways = PilotOsmSnapshot.readCharminar2026(Files.newInputStream(source), 2026);
        List<Way> bundled = PilotOsmSnapshot.readBundledCharminar2026(2026);
        check(bundled.size() == ways.size(), "bundled resource");
        try { PilotOsmSnapshot.readBundledCharminar2026(1591); throw new AssertionError("bundled historical use"); }
        catch (IOException expected) { }
        check(ways.size()==22, "sample ways");
        check(ways.stream().filter(w -> w.tags.containsKey("highway")).count()==12, "roads");
        check(ways.stream().filter(w -> w.tags.containsKey("building")).count()==4, "buildings");
        check(ways.stream().filter(w -> w.tags.containsKey("building:part")).count()==7, "parts");
        Way c=ways.stream().filter(w -> w.osmId==238186115L).findFirst().orElseThrow();
        check("Charminar".equals(c.tags.get("name")) && c.geometry.size()==39, "landmark polygon");
        check(c.geometry.get(0)==c.geometry.get(c.geometry.size()-1), "closed polygon");
        check(c.geometry.get(0).latitude>17 && c.geometry.get(0).longitude>78, "coordinate order");
        try { ways.clear(); throw new AssertionError("list mutable"); } catch (UnsupportedOperationException expected) { }
        try { c.tags.put("name", "test"); throw new AssertionError("tags mutable"); } catch (UnsupportedOperationException expected) { }
        try { c.geometry.clear(); throw new AssertionError("geometry mutable"); } catch (UnsupportedOperationException expected) { }
        for (int year : new int[]{-3000, 1591, 2025, 2027, 3000}) {
            try { PilotOsmSnapshot.readCharminar2026(new ByteArrayInputStream(new byte[0]), year); throw new AssertionError("Accepted year " + year); }
            catch (IOException expected) { }
        }
        rejects("<osm><node id='1' lat='17' lon='78'/><way id='4'><nd ref='1'/><nd ref='2'/><tag k='highway' v='primary'/></way></osm>");
        rejects("<osm><node id='1' lat='17' lon='78'/><node id='1' lat='17' lon='78'/></osm>");
        rejects("<osm><node id='1' lat='17' lon='78'/><node id='2' lat='17' lon='78'/><way id='4'><nd ref='1'/><nd ref='2'/><tag k='building' v='yes'/></way></osm>");
        rejects("<!DOCTYPE osm [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><osm>&secret;</osm>");
        rejects("<not_osm/>");
        System.out.println("Pilot OSM snapshot checks passed");
    }
}
