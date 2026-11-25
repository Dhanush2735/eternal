package com.eternalengine.hyderabad;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Read-only present-day OSM snapshot. Not a historical reconstruction or world writer. */
public final class PilotOsmSnapshot {
    public static final class Point {
        public final double latitude, longitude;
        Point(double lat, double lon) { latitude = lat; longitude = lon; }
    }
    public static final class Way {
        public final long osmId;
        public final List<Point> geometry;
        public final Map<String, String> tags;
        Way(long id, List<Point> points, Map<String, String> tags) {
            this.osmId = id;
            this.geometry = Collections.unmodifiableList(new ArrayList<>(points));
            this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        }
    }
    private PilotOsmSnapshot() {}
    private static final String RESOURCE = "/data/eternalengine/eternal/hyderabad/charminar_osm_2026-11-03.xml.gz";
    private static final String SHA256 = "546427cdb4478ae04b144b48e051cca023872e46ed4ded0496da4e8486a8ce90";

    /** Read the bundled, hash-verified snapshot; it is not automatically applied to a world. */
    public static List<Way> readBundledCharminar2026(int requestedYear) throws IOException {
        if (requestedYear != 2026) throw new IOException("Charminar snapshot is present-day 2026 only");
        try (InputStream in = PilotOsmSnapshot.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("Bundled Charminar snapshot not found");
            byte[] compressed = in.readAllBytes();
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(compressed);
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (!SHA256.contentEquals(hex)) throw new IOException("Charminar snapshot hash mismatch");
            } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
            return readCharminar2026(new java.io.ByteArrayInputStream(compressed), requestedYear);
        }
    }

    /** Read the Charminar 2026 snapshot only for its collection year; no past or future extrapolation. */
    public static List<Way> readCharminar2026(InputStream compressedXml, int requestedYear) throws IOException {
        if (compressedXml == null) throw new IOException("Missing OSM snapshot");
        if (requestedYear != 2026) throw new IOException("Charminar snapshot is present-day 2026 only");
        try (GZIPInputStream in = new GZIPInputStream(compressedXml)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document doc = builder.parse(in);
            if (!"osm".equals(doc.getDocumentElement().getTagName())) throw new IOException("Not an OSM document");
            Map<Long, Point> nodes = new HashMap<>();
            NodeList xmlNodes = doc.getElementsByTagName("node");
            for (int i=0; i<xmlNodes.getLength(); i++) {
                Element e = (Element) xmlNodes.item(i);
                long id = Long.parseLong(e.getAttribute("id"));
                double lat = Double.parseDouble(e.getAttribute("lat")), lon = Double.parseDouble(e.getAttribute("lon"));
                if (id <= 0 || !Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180 || nodes.put(id, new Point(lat, lon)) != null)
                    throw new IOException("Invalid or duplicate node " + id);
            }
            List<Way> ways = new ArrayList<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            NodeList xmlWays = doc.getElementsByTagName("way");
            for (int i=0; i<xmlWays.getLength(); i++) {
                Element e = (Element) xmlWays.item(i);
                long id = Long.parseLong(e.getAttribute("id"));
                if (id <= 0 || !seen.add(id)) throw new IOException("Invalid or duplicate way " + id);
                Map<String, String> tags = new LinkedHashMap<>();
                NodeList tagNodes = e.getElementsByTagName("tag");
                for (int j=0; j<tagNodes.getLength(); j++) {
                    Element t = (Element) tagNodes.item(j);
                    tags.put(t.getAttribute("k"), t.getAttribute("v"));
                }
                boolean building = tags.containsKey("building") && !"no".equals(tags.get("building"));
                boolean part = tags.containsKey("building:part") && !"no".equals(tags.get("building:part"));
                if (!building && !part && !tags.containsKey("highway")) continue;
                NodeList refs = e.getElementsByTagName("nd");
                List<Point> points = new ArrayList<>();
                List<Long> ids = new ArrayList<>();
                for (int j=0; j<refs.getLength(); j++) {
                    long ref = Long.parseLong(((Element)refs.item(j)).getAttribute("ref"));
                    Point p = nodes.get(ref);
                    if (p == null) throw new IOException("Way " + id + " references missing node " + ref);
                    ids.add(ref); points.add(p);
                }
                if (points.size() < 2) throw new IOException("Way " + id + " has insufficient points");
                if ((building || part) && (ids.size()<4 || !ids.get(0).equals(ids.get(ids.size()-1))))
                    throw new IOException("Building way " + id + " is not closed");
                ways.add(new Way(id, points, tags));
            }
            return Collections.unmodifiableList(ways);
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid or unsafe OSM snapshot", e); }
    }
}
