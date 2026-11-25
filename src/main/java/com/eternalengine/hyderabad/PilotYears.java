package com.eternalengine.hyderabad;

/** Pilot policy; not yet wired into the game. */
public final class PilotYears {
public static final int FIRST = -3000, LAST = 3000;
public enum Status { OUTSIDE, AMBIGUOUS_ZERO, HISTORICAL_OR_BASELINE, FUTURE }
private PilotYears() {}

/** Engine BCE labels; zero requires calendar reconciliation. */
public static Status classify(int year, int lastEvidenceYear) {
 if (lastEvidenceYear < 1 || lastEvidenceYear > LAST) throw new IllegalArgumentException("evidence year");
 if (year < FIRST || year > LAST) return Status.OUTSIDE;
 if (year == 0) return Status.AMBIGUOUS_ZERO;
 return year > lastEvidenceYear ? Status.FUTURE : Status.HISTORICAL_OR_BASELINE;
}
}
