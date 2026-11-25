import com.eternalengine.hyderabad.PilotOsmSnapshot;
import com.eternalengine.hyderabad.PilotProjectedPreview;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class PilotProjectedPreviewTest {
    static void check(boolean b, String s) { if (!b) throw new AssertionError(s); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked f) throws Exception { try { f.run(); throw new AssertionError("expected rejection"); } catch (IOException expected) {} }
    public static void main(String[] args) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PilotProjectedPreview p = PilotProjectedPreview.bundled(2026, (lat,lon) -> { calls.incrementAndGet(); return new double[] {-lat*1000.0,lon*1000.0}; });
        check(p.features.size()==22 && p.vertices==212 && calls.get()==212, "project every feature vertex");
        check(p.features.stream().filter(f -> f.road).count()==12, "roads");
        check(p.features.stream().filter(f -> f.building).count()==4, "buildings");
        check(p.features.stream().filter(f -> f.buildingPart).count()==7, "parts");
        var original=PilotOsmSnapshot.readBundledCharminar2026(2026).get(0).geometry.get(0);
        var first=p.features.get(0).vertices.get(0);
        check(first.x==(int)Math.floor(-original.latitude*1000.0) && first.z==(int)Math.floor(original.longitude*1000.0), "coordinate order and negative floor");
        check(first.x>=p.minX && first.x<=p.maxX && first.z>=p.minZ && first.z<=p.maxZ,"bounds");
        try {p.features.clear();throw new AssertionError("mutable features");} catch(UnsupportedOperationException expected){}
        try {p.features.get(0).vertices.clear();throw new AssertionError("mutable vertices");} catch(UnsupportedOperationException expected){}
        for (int y: new int[]{-3000,0,1591,2025,2027,3000}) rejects(() -> PilotProjectedPreview.bundled(y,(a,b)->new double[]{1,2}));
        rejects(() -> PilotProjectedPreview.project(Collections.emptyList(),(a,b)->new double[]{1,2}));
        rejects(() -> PilotProjectedPreview.bundled(2026,null));
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->null));
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->new double[]{1}));
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->new double[]{Double.NaN,2}));
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->new double[]{2147483648.0,2}));
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->{throw new IllegalStateException("failed");}));
        AtomicInteger partial=new AtomicInteger();
        rejects(() -> PilotProjectedPreview.bundled(2026,(a,b)->partial.incrementAndGet()==42?null:new double[]{1,2}));
        check(partial.get()==42,"fails on the first bad vertex");
        System.out.println("Projected Hyderabad sample checks passed (including all-or-nothing failures)");
    }
}
