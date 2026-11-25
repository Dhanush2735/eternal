import com.eternalengine.hyderabad.PilotYears;
import static com.eternalengine.hyderabad.PilotYears.Status.*;
public class PilotYearsTest {
static void eq(Object a,Object b){if(!a.equals(b))throw new AssertionError();}
public static void main(String[] args) {
 eq(PilotYears.classify(-3001, 2026), OUTSIDE);
 eq(PilotYears.classify(-3000, 2026), HISTORICAL_OR_BASELINE);
 eq(PilotYears.classify(-1, 2026), HISTORICAL_OR_BASELINE);
 eq(PilotYears.classify(0, 2026), AMBIGUOUS_ZERO);
 eq(PilotYears.classify(1, 2026), HISTORICAL_OR_BASELINE);
 eq(PilotYears.classify(2026, 2026), HISTORICAL_OR_BASELINE);
 eq(PilotYears.classify(2027, 2026), FUTURE);
 eq(PilotYears.classify(3000, 2026), FUTURE);
 eq(PilotYears.classify(3001, 2026), OUTSIDE);
 try { PilotYears.classify(1, 0); throw new AssertionError(); } catch (IllegalArgumentException expected) { }
 
}
}
