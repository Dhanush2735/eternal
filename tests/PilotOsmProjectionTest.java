import com.eternalengine.hyderabad.PilotOsmSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;

/** Headless probe using the existing copied BTE projection, not a live Minecraft world. */
public class PilotOsmProjectionTest {
    public static void main(String[] args) throws Exception {
        int count=0;
        double worst=0;
        for (var way : PilotOsmSnapshot.readCharminar2026(Files.newInputStream(Path.of(args[0])),2026)) {
            for (var p : way.geometry) {
                double[] xz=Probe.fromGeo(p.latitude,p.longitude);
                double[] back=Probe.toGeo(xz[0],xz[1]);
                double error=Probe.hav(p.latitude,p.longitude,back[0],back[1]);
                if (!Double.isFinite(error) || error>0.5) throw new AssertionError("Projection round trip " + error + "m");
                worst=Math.max(worst,error); count++;
            }
        }
        if (count<100) throw new AssertionError("Too few geometry points: " + count);
        System.out.printf(java.util.Locale.ROOT,"BTE headless projection: %d way vertices; worst round trip %.6f m%n",count,worst);
    }
}
