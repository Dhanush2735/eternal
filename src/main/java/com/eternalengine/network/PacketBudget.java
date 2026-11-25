package com.eternalengine.network;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Network: packet sizes and rates.</b> I7.
 *
 * <h2>Why this class exists</h2>
 *
 * I7 says the network needs packet sizes and rates. {@code network/} has the packets; what nothing held
 * was the <b>budget</b>: how big a packet may be, how often it may go, and what happens over budget.
 * Every number here names its source, like {@code PerfBudget}: Minecraft's own custom-payload limit for
 * the size, game defaults for the rates.
 *
 * <h2>Over budget is refused, not slowed</h2>
 *
 * <p>A packet over Minecraft's limit is not a slow packet, it is a packet that never arrives: it is
 * <b>refused</b>, by name and by how much. A window over the rate budget is not refused mid-stream; it
 * is <b>named</b> in words ("the window to Sam sent 40 packets a second against 20"), because the
 * sender is live and the report is what an operator reads.
 */
public final class PacketBudget {

    /** Minecraft refuses a custom payload over this. A limit Minecraft itself imposes. */
    public static final int MAX_BYTES = 32767;

    /** One packet a tick at most: a game default. */
    public static final int MAX_PER_SECOND = 20;

    /** The bytes one player's stream may carry in a second: a game default. */
    public static final long MAX_BYTES_PER_SECOND = 200_000L;

    /** <b>One packet.</b> */
    public record Packet(String what, int bytes) {

        public Packet {
            what = what == null ? "" : what.trim();
        }
    }

    /** <b>The outcome of sending one.</b> */
    public record Sent(Packet packet, String refused) {
        public boolean ok() {
            return packet != null;
        }
    }

    /** <b>One window of sending</b>, and every way it blew the rate budgets. */
    public record Window(String who, double seconds, int packets, long bytes, List<String> over) {

        public Window {
            who = who == null ? "" : who.trim();
            over = over == null ? List.of() : List.copyOf(over);
        }

        public boolean clean() {
            return over.isEmpty();
        }

        public String line() {
            return "the window to " + who + " (" + seconds + " s): " + packets + " packets, " + bytes
                    + " bytes" + (clean() ? ", inside every rate"
                    : "; " + String.join("; ", over));
        }
    }

    private PacketBudget() {
    }

    /**
     * <b>Send one packet.</b> Refused: a packet with no name, a size of nothing or less, and a packet
     * over Minecraft's own limit - naming the limit and by how much, because such a packet is not slow,
     * it never arrives.
     */
    public static Sent send(String what, int bytes) {
        if (what == null || what.trim().isEmpty()) {
            return new Sent(null, "a packet with no name is a packet nobody can account for");
        }
        if (bytes <= 0) {
            return new Sent(null, "a packet of " + bytes + " bytes carries nothing");
        }
        if (bytes > MAX_BYTES) {
            return new Sent(null, "\"" + what.trim() + "\" is " + bytes + " bytes, and Minecraft "
                    + "refuses a custom payload over " + MAX_BYTES + " (" + (bytes - MAX_BYTES)
                    + " over): split it, because this packet is not slow, it never arrives");
        }
        return new Sent(new Packet(what.trim(), bytes), "");
    }

    /**
     * <b>Check a window of sending.</b> The window is named against both rate budgets in words; a clean
     * window says so.
     */
    public static Window window(String who, double seconds, int packets, long bytes) {
        List<String> over = new ArrayList<>();
        if (who == null || who.trim().isEmpty()) {
            over.add("a window with nobody on the other end");
        }
        if (seconds <= 0) {
            over.add("a window of " + seconds + " seconds is no window: say how long it ran");
        } else {
            double perSecond = packets / seconds;
            if (perSecond > MAX_PER_SECOND) {
                over.add("the window to " + who + " sent " + round(perSecond) + " packets a second "
                        + "against " + MAX_PER_SECOND + " (a game default)");
            }
            double bytesPerSecond = bytes / seconds;
            if (bytesPerSecond > MAX_BYTES_PER_SECOND) {
                over.add("the window to " + who + " carried " + round(bytesPerSecond) + " bytes a "
                        + "second against " + MAX_BYTES_PER_SECOND + " (a game default)");
            }
        }
        return new Window(who, seconds, packets, bytes, over);
    }

    public static String ruleLine() {
        return "A packet over Minecraft's limit is refused by name and by how much: it is not slow, it "
                + "never arrives. A window over the rate budgets is named in words, because the sender "
                + "is live and the report is what an operator reads.";
    }

    private static String round(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
