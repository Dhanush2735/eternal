package com.eternalengine.commands;

import com.eternalengine.civilization.HistoricalPresence;
import com.eternalengine.knowledge.KnowledgeDate;
import com.eternalengine.money.MarketService;
import com.eternalengine.money.ProductAtlas;
import com.eternalengine.money.PurseService;
import com.eternalengine.money.ShopService;
import com.eternalengine.money.TradeView;
import com.eternalengine.news.MapService;
import com.eternalengine.news.MapView;
import com.eternalengine.news.NewsEras;
import com.eternalengine.news.NewsService;
import com.eternalengine.news.PaperView;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Steps 19c to 19e, admin and test commands: {@code /ee shop}, {@code /ee paper} and {@code /ee map}. They show, as
 * text, what a seller sells, what today's paper prints and what a map holds at a place; with {@code buy} or
 * {@code give} they do it for the player who runs them. Product atlas, part B: {@code /ee products}.
 */
final class NewsCommands {

    private NewsCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> shop() {
        return Commands.literal("shop").requires(src -> src.hasPermission(2))
                .executes(ctx -> shop(ctx.getSource(), ""))
                .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> shop(ctx.getSource(), StringArgumentType.getString(ctx, "what"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> paper() {
        return Commands.literal("paper").requires(src -> src.hasPermission(2))
                .executes(ctx -> paper(ctx.getSource(), ""))
                .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> paper(ctx.getSource(), StringArgumentType.getString(ctx, "what"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> map() {
        return Commands.literal("map").requires(src -> src.hasPermission(2))
                .executes(ctx -> map(ctx.getSource(), ""))
                .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> map(ctx.getSource(), StringArgumentType.getString(ctx, "what"))));
    }

    static String formIds() {
        StringBuilder sb = new StringBuilder();
        for (com.eternalengine.news.BookForms.Form f : com.eternalengine.news.BookForms.Form.values()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(f.id);
        }
        return sb.toString();
    }

    /**
     * Step 22: {@code /ee book forms [lat lon year]}: the forms of the history book at a place and year (default: where
     * the player stands, today), who sells which, and the price in the money of the place.
     */
    static int bookForms(CommandSourceStack source, String[] w) {
        double lat;
        double lon;
        int year;
        try {
            if (w.length >= 4) {
                lat = Double.parseDouble(w[1]);
                lon = Double.parseDouble(w[2]);
                year = Integer.parseInt(w[3]);
            } else {
                ServerPlayer p = source.getPlayer();
                double[] at = p == null ? null : com.eternalengine.money.PurseService.placeOf(p);
                if (at == null) {
                    source.sendFailure(Component.literal("Usage: /ee book forms <lat> <lon> <year> (or stand somewhere)"));
                    return 0;
                }
                lat = at[0];
                lon = at[1];
                year = com.eternalengine.knowledge.KnowledgeDate.year(com.eternalengine.knowledge.KnowledgeDate.today());
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Usage: /ee book forms <lat> <lon> <year>"));
            return 0;
        }
        var here = com.eternalengine.money.MoneyTable.get().at(lat, lon, year);
        java.util.List<com.eternalengine.news.BookForms.Form> forms = com.eternalengine.news.BookForms.here(lat, lon, year);
        StringBuilder sb = new StringBuilder();
        for (com.eternalengine.news.BookForms.Form f : forms) {
            long price = com.eternalengine.money.ShopService.bookPrice(f, here);
            sb.append(sb.length() == 0 ? "" : ", ").append(f.title.toLowerCase(Locale.ROOT)).append(" (")
                    .append(com.eternalengine.money.MoneyFormat.amount(here.money(), price, year)).append(")");
        }
        boolean writes = forms.get(0).writing;
        say(source, String.format(Locale.ROOT, "[EE book] At %.4f, %.4f in %s %s: %s.", lat, lon,
                com.eternalengine.money.ProductAtlas.yearText(year), writes ? "people keep notes in"
                        : "nobody writes; people remember with", sb));
        String[] roles = {com.eternalengine.money.MarketService.MERCHANT, com.eternalengine.money.MarketService.TRADER,
                com.eternalengine.money.MarketService.PEDLAR, com.eternalengine.news.NewsEras.PAPER_SELLER,
                com.eternalengine.news.NewsEras.NEWS_WRITER};
        StringBuilder who = new StringBuilder();
        for (String r : roles) {
            java.util.List<com.eternalengine.news.BookForms.Form> sold = com.eternalengine.news.BookForms.soldBy(r, lat, lon, year);
            StringBuilder names = new StringBuilder();
            for (com.eternalengine.news.BookForms.Form f : sold) {
                names.append(names.length() == 0 ? "" : ", ").append(f.title.toLowerCase(Locale.ROOT));
            }
            who.append(who.length() == 0 ? "" : "; ").append(r.replace('_', ' ')).append(": ").append(names);
        }
        String aid = com.eternalengine.news.BookForms.memoryAid(lat, lon, year).title.toLowerCase(Locale.ROOT);
        say(source, "[EE book] Sellers: " + who + ". Anybody else, with no seller near, gives "
                + (aid.endsWith("s") ? "" : "a ") + aid + ".");
        return forms.size();
    }

    static LiteralArgumentBuilder<CommandSourceStack> products() {
        return Commands.literal("products").requires(src -> src.hasPermission(2))
                .executes(ctx -> products(ctx.getSource(), ""))
                .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> products(ctx.getSource(), StringArgumentType.getString(ctx, "what"))));
    }

    /** Step 20: {@code /ee book [card <id> | journal | tree <id> | track <id> | untrack | give]}. */
    static LiteralArgumentBuilder<CommandSourceStack> book() {
        return Commands.literal("book")
                .executes(ctx -> book(ctx.getSource(), ""))
                .then(Commands.argument("what", StringArgumentType.greedyString())
                        .executes(ctx -> book(ctx.getSource(), StringArgumentType.getString(ctx, "what"))));
    }

    /** The player's history book: opens it, and tells it as text (for tests and for reading in the log). */
    static int book(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        String action = w.length > 0 ? w[0].toLowerCase(Locale.ROOT) : "";
        String arg = w.length > 1 ? w[1] : "";
        if (action.equals("forms")) {
            return bookForms(source, w); // step 22: works from the console too
        }
        ServerPlayer p = source.getPlayer();
        if (p == null) {
            source.sendFailure(Component.literal("Only a player has a history book."));
            return 0;
        }
        if (!action.equals("give") && !com.eternalengine.knowledge.BookService.mayRead(p)) {
            source.sendFailure(Component.literal(com.eternalengine.knowledge.BookService.noBook(p)));
            return 0;
        }
        com.eternalengine.knowledge.BookView v;
        switch (action) {
            case "" -> v = com.eternalengine.knowledge.BookService.act(p, "card", "", "notes");
            case "card" -> v = com.eternalengine.knowledge.BookService.act(p, "card", arg, "notes");
            case "journal" -> v = com.eternalengine.knowledge.BookService.act(p, "tab", arg, "journal");
            case "tree" -> v = com.eternalengine.knowledge.BookService.act(p, "tab", arg, "tree");
            case "track" -> v = com.eternalengine.knowledge.BookService.act(p, "track", arg, "notes");
            case "untrack" -> v = com.eternalengine.knowledge.BookService.act(p, "untrack", arg, "notes");
            case "give" -> {
                if (!source.hasPermission(2)) {
                    source.sendFailure(Component.literal("Only an operator can give books."));
                    return 0;
                }
                com.eternalengine.news.BookForms.Form f = arg.isBlank() ? com.eternalengine.news.BookForms.Form.NOTEBOOK
                        : com.eternalengine.news.BookForms.Form.byId(arg);
                if (f == null) {
                    source.sendFailure(Component.literal("No such form: " + arg + ". Forms: " + formIds()));
                    return 0;
                }
                com.eternalengine.knowledge.BookService.give(p, f);
                say(source, "[EE book] A history book (" + f.title.toLowerCase(Locale.ROOT) + ") is in your inventory.");
                return 1;
            }
            default -> {
                source.sendFailure(Component.literal("Usage: /ee book [card <id> | journal | tree <id> | track <id> | untrack"
                        + " | give [form] | forms [lat lon year]]"));
                return 0;
            }
        }
        say(source, "[EE book] History book, " + v.subtitle + ": " + v.heard + (v.heard == 1 ? " name" : " names")
                + (v.trackingLabel.isBlank() ? "" : "; " + v.trackingLabel) + ".");
        Map<String, List<String>> groups = new java.util.LinkedHashMap<>();
        for (com.eternalengine.knowledge.BookView.Entry e : v.entries) {
            groups.computeIfAbsent(com.eternalengine.knowledge.BookView.groupName(e.kind()), k -> new ArrayList<>())
                    .add(e.title() + " (" + (e.notes() > 0 ? e.notes() + (e.notes() == 1 ? " note" : " notes") : "only the name")
                            + ")");
        }
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            say(source, "  " + g.getKey() + ": " + String.join("; ", g.getValue()) + ".");
        }
        if (v.tab.equals("journal")) {
            say(source, "  Journal (" + v.journal.size() + "):");
            for (int i = 0; i < Math.min(12, v.journal.size()); i++) {
                var d = v.journal.get(i);
                say(source, "    " + d.date() + "  " + d.text() + (d.card().isBlank() ? "" : " [" + d.card() + "]"));
            }
        } else if (!v.card.isBlank()) {
            say(source, "  Open: " + v.title + " (" + v.kind + ", " + v.card + ")" + (v.tab.equals("tree") ? ", family tree" : ""));
            if (v.tab.equals("tree")) {
                say(source, "    " + v.treeNote);
                for (var n : v.tree) {
                    say(source, "    · " + n.label() + (n.id().isBlank() ? "" : " [" + n.id() + "]"));
                }
            } else {
                for (var n : v.notes) {
                    say(source, "    " + n.date() + " · " + n.by() + ": \u201c" + com.eternalengine.knowledge.TalkView.plain(n.text())
                            + "\u201d");
                }
                if (!v.nothing.isBlank()) {
                    say(source, "    " + v.nothing);
                }
                if (v.canTrack) {
                    say(source, "    " + (v.tracking ? "Tracking it" : "Can be tracked on the compass")
                            + (v.where.isBlank() ? "" : ": " + v.where) + ".");
                }
                if (!v.links.isEmpty() || v.unheardLinks > 0) {
                    List<String> ls = new ArrayList<>();
                    for (var l : v.links) {
                        ls.add(l.label() + " [" + l.id() + "]");
                    }
                    say(source, "    Linked names: " + (ls.isEmpty() ? "" : String.join(", ", ls)) + (v.unheardLinks > 0
                            ? (ls.isEmpty() ? "" : "; ") + v.unheardLinks + " not heard yet" : "") + (v.familyTree
                            ? "; family tree: /ee book tree " + v.card : "") + ".");
                }
                for (String l : v.later) {
                    say(source, "    Later (time traveller view): " + l);
                }
            }
        }
        if (!v.hint.isBlank()) {
            say(source, "  " + v.hint);
        }
        return 1;
    }

    /** Place and date from "at <lat> <lon> [yyyy-mm-dd | year]", else the player's place and today. */
    private record Where(double lat, double lon, int date, List<String> rest) {
    }

    private static Where where(CommandSourceStack source, String args) {
        String[] w = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");
        double lat = Double.NaN, lon = Double.NaN;
        int date = KnowledgeDate.today();
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < w.length; i++) {
            if (w[i].equalsIgnoreCase("at") && i + 2 < w.length) {
                lat = Double.parseDouble(w[i + 1]);
                lon = Double.parseDouble(w[i + 2]);
                i += 2;
                if (i + 1 < w.length && w[i + 1].matches("-?\\d{1,4}(-\\d{2}-\\d{2})?")) {
                    String d = w[++i];
                    date = d.contains("-") && d.length() >= 10 ? KnowledgeDate.parse(d)
                            : KnowledgeDate.key(Integer.parseInt(d), KnowledgeDate.month(date), KnowledgeDate.day(date));
                }
            } else {
                rest.add(w[i].toLowerCase(Locale.ROOT));
            }
        }
        if (Double.isNaN(lat) && source.getPlayer() != null) {
            double[] p = PurseService.placeOf(source.getPlayer());
            if (p != null) {
                lat = p[0];
                lon = p[1];
            }
        }
        return new Where(lat, lon, date, rest);
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    /**
     * Product atlas, part B: {@code /ee products [here | at <lat> <lon> [year]]}. What is made here, brought from far,
     * not here yet (and when it comes) and gone, with the food, cloth, fuel and light people here have.
     */
    static int products(CommandSourceStack source, String args) {
        Where at;
        try {
            at = where(source, args);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Usage: /ee products [here | at <lat> <lon> [year]]"));
            return 0;
        }
        if (Double.isNaN(at.lat()) || Double.isNaN(at.lon())) {
            source.sendFailure(Component.literal("No place: stand in the world, or use /ee products at <lat> <lon> [year]"));
            return 0;
        }
        int year = KnowledgeDate.year(at.date());
        ProductAtlas atlas = ProductAtlas.get();
        ProductAtlas.Place place = atlas.place(at.lat(), at.lon(), year);
        say(source, String.format(Locale.ROOT, "[EE products] %.4f, %.4f: %s.", at.lat(), at.lon(), atlas.summaryAt(place)));
        if (place.reading() != null) {
            String how = place.reading().sentence();
            say(source, (how.isBlank() ? "" : how + " ") + "Needs met here: " + (place.farm() ? "farming" : "no farming") + ", "
                    + (place.herd() ? "herding" : "no herding") + ", " + (place.town() ? "towns" : "no towns") + ".");
        } else {
            say(source, "Every need is met here (farming, herding, towns).");
        }
        say(source, "Food, cloth, fuel and light: " + atlas.staplesAt(place) + ".");
        List<ProductAtlas.Answer> all = atlas.all(place);
        for (ProductAtlas.Status st : ProductAtlas.Status.values()) {
            List<ProductAtlas.Answer> these = new ArrayList<>();
            for (ProductAtlas.Answer a : all) {
                if (a.status() == st) {
                    these.add(a);
                }
            }
            if (these.isEmpty()) {
                continue;
            }
            if (st == ProductAtlas.Status.NOT_YET) {
                these.sort(java.util.Comparator.comparingInt(ProductAtlas.Answer::since));
            }
            List<String> names = new ArrayList<>();
            int never = 0;
            for (ProductAtlas.Answer a : these) {
                String n = a.product().name().toLowerCase(Locale.ROOT);
                switch (st) {
                    case MADE_HERE -> names.add(n);
                    case BROUGHT -> names.add(n + (a.why().isBlank() ? " (from " + ProductAtlas.yearText(a.since()) + ")"
                            : " (" + a.why() + ")"));
                    case GONE -> names.add(n + " (" + ProductAtlas.yearText(a.since()) + ")");
                    case NOT_YET -> {
                        if (a.since() == ProductAtlas.NEVER) {
                            never++;
                        } else if (names.size() < 15) {
                            names.add(n + " (" + ProductAtlas.yearText(a.since()) + ")");
                        }
                    }
                }
            }
            String head = switch (st) {
                case MADE_HERE -> "Made here";
                case BROUGHT -> "Brought from far";
                case NOT_YET -> "Not here yet";
                case GONE -> "Gone";
            };
            String tail = st == ProductAtlas.Status.NOT_YET ? (names.isEmpty() ? "" : "coming next: " + String.join(", ", names))
                    + (never > 0 ? (names.isEmpty() ? "" : "; ") + never + " never come here" : "") : String.join(", ", names);
            say(source, head + " (" + these.size() + "): " + tail + ".");
        }
        return 1;
    }

    /** {@code /ee shop [merchant|trader|pedlar|paper_seller|news_writer] [at <lat> <lon> [date]] [buy <offer>]}. */
    static int shop(CommandSourceStack source, String args) {
        Where at;
        try {
            at = where(source, args);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Usage: /ee shop [merchant|trader|pedlar|paper_seller|news_writer] [at <lat> <lon> "
                    + "[yyyy-mm-dd]] [buy <offer id>]"));
            return 0;
        }
        List<String> roles = new ArrayList<>();
        String buy = null;
        for (int i = 0; i < at.rest().size(); i++) {
            String r = at.rest().get(i);
            if (r.equals("buy") && i + 1 < at.rest().size()) {
                buy = at.rest().get(++i);
            } else if (ShopService.sells(r)) {
                roles.add(r);
            }
        }
        if (roles.isEmpty()) {
            roles = List.of(MarketService.MERCHANT, NewsEras.PAPER_SELLER, NewsEras.NEWS_WRITER);
        }
        ServerPlayer player = source.getPlayer();
        int year = KnowledgeDate.year(at.date());
        say(source, String.format(Locale.ROOT, "[EE shop] %.4f, %.4f on %s", at.lat(), at.lon(), KnowledgeDate.label(at.date())));
        for (String role : roles) {
            UUID id = UUID.nameUUIDFromBytes(("ee-shop:" + role + ":" + Math.round(at.lat() * 100) + ":"
                    + Math.round(at.lon() * 100)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ShopService.Seller s = new ShopService.Seller(role, "the " + role.replace('_', ' '), -1, id, "market",
                    at.lat(), at.lon(), at.date());
            if (buy != null && player != null) {
                MarketService.Done d = ShopService.buy(player, s, buy, priceOf(s, player, buy));
                say(source, (d.ok() ? "Bought: " : "Not bought: ") + d.text());
            }
            TradeView v = ShopService.view(s, player, null, null, false);
            if (v.offers.isEmpty()) {
                say(source, "A " + role.replace('_', ' ') + " here sells nothing.");
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (TradeView.Offer o : v.offers) {
                parts.add(o.id() + " = " + o.name() + ": " + o.priceText() + (o.left() >= 0 ? " (" + o.left() + " left today)" : "")
                        + (o.owned() ? " [you have it]" : ""));
            }
            say(source, "A " + role.replace('_', ' ') + " sells: " + String.join("; ", parts) + ".");
            if (!v.purse.isBlank()) {
                say(source, v.purse + ".");
            }
        }
        HistoricalPresence.Result r = null;
        try {
            r = com.eternalengine.core.WorldQuery.presence(null, com.eternalengine.core.WorldKey.at(at.lat(), at.lon(), year));
        } catch (Throwable ignored) {
            // No data here.
        }
        if (r != null) {
            NewsEras.Kind k = NewsEras.kind(r.rank(), r.ruler().isPresent(), at.lat(), at.lon(), year);
            say(source, "The news here: " + (k == NewsEras.Kind.NONE ? "nobody brings it" : k.role().replace('_', ' '))
                    + " (" + r.rank().name().toLowerCase(Locale.ROOT) + " in " + NewsEras.regionName(at.lat(), at.lon())
                    + "; printed papers in its cities from " + NewsEras.printYear(com.eternalengine.civilization.SettlementRank.CITY,
                    at.lat(), at.lon()) + (NewsEras.estimate(at.lat(), at.lon()) ? ", an estimate" : "") + ").");
        }
        return 1;
    }

    private static long priceOf(ShopService.Seller s, ServerPlayer p, String id) {
        TradeView v = ShopService.view(s, p, null, null, false);
        TradeView.Offer o = v.offer(id);
        return o == null ? -1L : o.price();
    }

    /** {@code /ee paper [hand] [at <lat> <lon> [date]] [give]}: today's paper as text, or the paper itself. */
    static int paper(CommandSourceStack source, String args) {
        Where at;
        try {
            at = where(source, args);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Usage: /ee paper [hand] [at <lat> <lon> [yyyy-mm-dd]] [give]"));
            return 0;
        }
        boolean hand = at.rest().contains("hand");
        PaperView v = NewsService.paper(at.lat(), at.lon(), at.date(), hand);
        say(source, "[EE paper] " + v.name + " | " + v.line);
        if (!v.fork.isBlank()) {
            say(source, "  ! " + v.fork);
        }
        for (PaperView.Story s : v.stories) {
            List<String> places = new ArrayList<>();
            for (String p : s.places()) {
                places.add(NewsService.placeName(p) + (NewsService.placeAt(p) == null ? " (no place)" : ""));
            }
            say(source, "  * " + s.headline() + ": " + com.eternalengine.knowledge.TalkView.plain(s.text())
                    + (places.isEmpty() ? "" : " [places: " + String.join(", ", places) + "]"));
        }
        if (!v.prices.isEmpty()) {
            say(source, "  " + v.pricesTitle + ": " + String.join("; ", v.prices));
        }
        if (!v.ad.isBlank()) {
            say(source, "  Ad: " + v.ad);
        }
        if (at.rest().contains("give") && source.getPlayer() != null) {
            ItemStack stack = NewsService.item(v);
            if (!source.getPlayer().getInventory().add(stack)) {
                source.getPlayer().drop(stack, false);
            }
            say(source, "Gave you the paper. Right-click it to read.");
        }
        if (at.rest().contains("read") && source.getPlayer() != null) {
            ItemStack stack = source.getPlayer().getMainHandItem();
            if (NewsService.of(stack) != null) {
                NewsService.read(source.getPlayer(), stack);
                say(source, "Read the paper in your hand.");
            } else {
                say(source, "There is no paper in your hand.");
            }
        }
        return 1;
    }

    /** {@code /ee map [at <lat> <lon> [date]] [give]}: what a map bought here shows, or the map itself. */
    static int map(CommandSourceStack source, String args) {
        Where at;
        try {
            at = where(source, args);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal("Usage: /ee map [at <lat> <lon> [yyyy-mm-dd]] [give]"));
            return 0;
        }
        ItemStack stack = MapService.item(at.lat(), at.lon(), at.date());
        if (at.rest().contains("held") && source.getPlayer() != null
                && source.getPlayer().getMainHandItem().getItem() instanceof com.eternalengine.news.MapItem) {
            stack = source.getPlayer().getMainHandItem();
        }
        var tag = stack.getTagElement(com.eternalengine.news.MapItem.TAG);
        MapView v = MapService.view(tag, new double[]{at.lat(), at.lon()});
        List<String> towns = new ArrayList<>();
        for (MapView.Town t : v.towns) {
            towns.add(t.name() + (t.major() ? "*" : ""));
        }
        int points = 0;
        for (double[] r : v.roads) {
            points += r.length / 2;
        }
        say(source, "[EE map] " + v.title + " (" + v.subtitle + "): " + v.towns.size() + " towns, " + v.roads.size()
                + " roads (" + points + " points), " + v.marks.size() + " news marks"
                + String.format(Locale.ROOT, "; box %.2f..%.2f N, %.2f..%.2f E", v.minLat, v.maxLat, v.minLon, v.maxLon));
        say(source, "  Towns (* big): " + String.join(", ", towns.subList(0, Math.min(40, towns.size()))));
        if (!v.marks.isEmpty()) {
            List<String> marks = new ArrayList<>();
            for (MapView.Mark k : v.marks) {
                marks.add(k.name() + " " + KnowledgeDate.shortLabel(k.date()));
            }
            say(source, "  Marks: " + String.join(", ", marks));
        }
        if (at.rest().contains("give") && source.getPlayer() != null) {
            if (!source.getPlayer().getInventory().add(stack)) {
                source.getPlayer().drop(stack, false);
            }
            say(source, "Gave you the map. Right-click it to open.");
        }
        return 1;
    }
}
