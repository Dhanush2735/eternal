package com.eternalengine.knowledge;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The person register (the approved prototype's "Who is he?" screen): the masthead, the
 * place-ruler-date line, what is in his mind, the five pages (who he is, his life, his day,
 * his memory, events and town), and the tally — every line carries its mark (recorded,
 * estimated, believed, not recorded, not yet), and the page counts them.
 */
public final class RegisterView {

    /** One line of a page: its label, its words, and its mark (rec, est, bel, non, yet). */
    public record Row(String label, String value, String tag) {
    }

    /** A page section under a heading (the page's own first section has no heading). */
    public record Section(String title, List<Row> rows, String note) {
    }

    /** One of the five pages. */
    public record Tab(String title, List<Section> sections) {
    }

    public int entityId;
    public String name = "";
    public String date = "";
    /** The strip: "Person register · … · your copy of …" */
    public String strip = "";
    /** The seal, top and bottom (the place's first letters, the century). */
    public String sealTop = "";
    public String sealBottom = "";
    /** The place · ruler · date line under the masthead. */
    public String dateRow = "";
    /** What is in his mind, and the small that says how it is known. */
    public String mind = "";
    public String mindSmall = "";
    public final List<Tab> tabs = new ArrayList<>();
    /** The tally: the lines, and each mark's count. */
    public int lines;
    public int rec;
    public int est;
    public int bel;
    public int non;
    public int yet;

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(cut(name, 64), 64);
        buf.writeUtf(cut(date, 96), 96);
        buf.writeUtf(cut(strip, 160), 160);
        buf.writeUtf(cut(sealTop, 16), 16);
        buf.writeUtf(cut(sealBottom, 16), 16);
        buf.writeUtf(cut(dateRow, 160), 160);
        buf.writeUtf(cut(mind, 300), 300);
        buf.writeUtf(cut(mindSmall, 120), 120);
        buf.writeVarInt(Math.min(5, tabs.size()));
        for (int ti = 0; ti < Math.min(5, tabs.size()); ti++) {
            Tab t = tabs.get(ti);
            buf.writeUtf(cut(t.title(), 48), 48);
            int ns = Math.min(8, t.sections().size());
            buf.writeVarInt(ns);
            for (int si = 0; si < ns; si++) {
                Section s = t.sections().get(si);
                buf.writeUtf(cut(s.title(), 96), 96);
                int nr = Math.min(32, s.rows().size());
                buf.writeVarInt(nr);
                for (int ri = 0; ri < nr; ri++) {
                    Row r = s.rows().get(ri);
                    buf.writeUtf(cut(r.label(), 64), 64);
                    buf.writeUtf(cut(r.value(), 300), 300);
                    buf.writeUtf(cut(r.tag(), 8), 8);
                }
                buf.writeUtf(cut(s.note(), 300), 300);
            }
        }
        buf.writeVarInt(lines);
        buf.writeVarInt(rec);
        buf.writeVarInt(est);
        buf.writeVarInt(bel);
        buf.writeVarInt(non);
        buf.writeVarInt(yet);
    }

    public static RegisterView decode(FriendlyByteBuf buf) {
        RegisterView v = new RegisterView();
        v.entityId = buf.readVarInt();
        v.name = buf.readUtf(64);
        v.date = buf.readUtf(96);
        v.strip = buf.readUtf(160);
        v.sealTop = buf.readUtf(16);
        v.sealBottom = buf.readUtf(16);
        v.dateRow = buf.readUtf(160);
        v.mind = buf.readUtf(300);
        v.mindSmall = buf.readUtf(120);
        int tabCount = readCount(buf, 5, "tabs");
        for (int ti = 0; ti < tabCount; ti++) {
            String title = buf.readUtf(48);
            List<Section> sections = new ArrayList<>();
            int sectionCount = readCount(buf, 8, "sections");
            for (int si = 0; si < sectionCount; si++) {
                String st = buf.readUtf(96);
                List<Row> rows = new ArrayList<>();
                int rowCount = readCount(buf, 32, "rows");
                for (int ri = 0; ri < rowCount; ri++) {
                    rows.add(new Row(buf.readUtf(64), buf.readUtf(300), buf.readUtf(8)));
                }
                sections.add(new Section(st, rows, buf.readUtf(300)));
            }
            v.tabs.add(new Tab(title, sections));
        }
        v.lines = buf.readVarInt();
        v.rec = buf.readVarInt();
        v.est = buf.readVarInt();
        v.bel = buf.readVarInt();
        v.non = buf.readVarInt();
        v.yet = buf.readVarInt();
        return v;
    }
    private static int readCount(FriendlyByteBuf buf, int max, String field) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        return count;
    }

}
