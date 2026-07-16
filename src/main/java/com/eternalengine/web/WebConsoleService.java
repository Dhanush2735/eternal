package com.eternalengine.web;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.brain.BrainChatAttachment;
import com.eternalengine.config.EternalEngineConfig;
import com.eternalengine.core.SimulationEngine;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Localhost-only web chat for development diagnostics.
 *
 * This deliberately shares SimulationEngine's LlmBrainBridge instance instead of
 * creating another LLM client. The web page is advisory/read-only: it can send
 * user text and selected file contents to the same server-side brain session,
 * but it cannot execute Minecraft commands, mutate world state, edit saves, or
 * enqueue physical changes.
 */
public final class WebConsoleService {

    private static final Logger LOGGER = EternalEngineMod.getLogger();
    private static final int MAX_REQUEST_BYTES = 2_000_000;
    private static final int MAX_FILE_CHARS = 80_000;
    private static final int MAX_ATTACHMENTS = 16;

    private final SecureRandom random = new SecureRandom();
    private volatile HttpServer httpServer;
    private volatile ExecutorService executor;
    private volatile MinecraftServer minecraftServer;
    private volatile String token;
    private volatile int boundPort;

    public synchronized boolean start(MinecraftServer server) {
        if (httpServer != null) {
            return true;
        }
        if (server == null) {
            return false;
        }
        this.minecraftServer = server;
        this.token = configuredOrGeneratedToken();
        int port = EternalEngineConfig.SERVER.webConsolePort.get();
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            created.createContext("/", this::handleIndex);
            created.createContext("/api/status", this::handleStatus);
            created.createContext("/api/chat", this::handleChat);
            ExecutorService createdExecutor = Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "EternalEngine-WebConsole");
                thread.setDaemon(true);
                return thread;
            });
            created.setExecutor(createdExecutor);
            created.start();
            this.executor = createdExecutor;
            this.httpServer = created;
            this.boundPort = port;
            LOGGER.info("[EE WebConsole] Started on http://127.0.0.1:{} (token available via /ee web url)", port());
            return true;
        } catch (IOException e) {
            LOGGER.error("[EE WebConsole] Failed to start on 127.0.0.1:{}", port, e);
            this.httpServer = null;
            this.executor = null;
            this.boundPort = 0;
            return false;
        }
    }

    public synchronized void stop() {
        HttpServer running = this.httpServer;
        this.httpServer = null;
        if (running != null) {
            running.stop(1);
        }
        ExecutorService runningExecutor = this.executor;
        this.executor = null;
        if (runningExecutor != null) {
            runningExecutor.shutdownNow();
        }
        this.minecraftServer = null;
        this.boundPort = 0;
        LOGGER.info("[EE WebConsole] Stopped");
    }

    public boolean isRunning() {
        return httpServer != null;
    }

    public int port() {
        return boundPort > 0 ? boundPort : EternalEngineConfig.SERVER.webConsolePort.get();
    }

    public String accessUrl() {
        String safeToken = token == null ? "" : token;
        return "http://127.0.0.1:" + port() + "/?token=" + urlEncodeToken(safeToken);
    }

    public String tokenForDisplay() {
        return token == null ? "" : token;
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }
        sendBytes(exchange, 200, "text/html; charset=utf-8", html().getBytes(StandardCharsets.UTF_8));
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            sendJson(exchange, 403, jsonError("Unauthorized. Use the token from /ee web url."));
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("running", isRunning());
        json.addProperty("brainAvailable", SimulationEngine.getInstance().getBrainBridge().isAvailable());
        json.addProperty("session", "minecraft");
        json.addProperty("status", SimulationEngine.getInstance().getStatusSummary());
        sendJson(exchange, 200, json);
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method not allowed"));
            return;
        }
        if (!authorized(exchange)) {
            sendJson(exchange, 403, jsonError("Unauthorized. Use the token from /ee web url."));
            return;
        }

        String requestBody = readRequestBody(exchange);
        JsonObject request;
        try {
            request = JsonParser.parseString(requestBody).getAsJsonObject();
        } catch (Exception e) {
            sendJson(exchange, 400, jsonError("Invalid JSON: " + safe(e.getMessage())));
            return;
        }

        String sessionId = stringField(request, "sessionId", "minecraft");
        String message = stringField(request, "message", "");
        if (message.isBlank()) {
            sendJson(exchange, 400, jsonError("Message is empty."));
            return;
        }
        List<BrainChatAttachment> attachments = parseAttachments(request);
        String prompt = buildDiagnosticPrompt(message, attachments);
        CompletableFuture<String> future = SimulationEngine.getInstance().getBrainBridge()
            .chat(sessionId, prompt, List.of());
        try {
            String reply = future.join();
            JsonObject response = new JsonObject();
            response.addProperty("reply", reply);
            response.addProperty("sessionId", sessionId);
            response.addProperty("brainAvailable", SimulationEngine.getInstance().getBrainBridge().isAvailable());
            response.addProperty("attachmentsAccepted", attachments.size());
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendJson(exchange, 500, jsonError("Brain chat failed: " + safe(e.getMessage())));
        }
    }

    private String buildDiagnosticPrompt(String message, List<BrainChatAttachment> attachments) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User problem / question:\n").append(message.trim()).append("\n\n");
        prompt.append("Current Eternal Engine server facts from the running Minecraft server:\n");
        try {
            prompt.append(SimulationEngine.getInstance().getStatusSummary());
        } catch (Exception e) {
            prompt.append("Status unavailable: ").append(safe(e.getMessage())).append('\n');
        }
        try {
            List<String> audit = SimulationEngine.getInstance().getBrainAuditLog().recent(8);
            if (!audit.isEmpty()) {
                prompt.append("\nRecent brain audit entries:\n");
                audit.forEach(line -> prompt.append("- ").append(line).append('\n'));
            }
        } catch (Exception ignored) {
            // Diagnostic context should never break chat.
        }
        if (!attachments.isEmpty()) {
            prompt.append("\nFiles/repo snippets selected by the user in the browser:\n");
            for (BrainChatAttachment attachment : attachments) {
                String content = attachment.content();
                if (content.length() > MAX_FILE_CHARS) {
                    content = content.substring(0, MAX_FILE_CHARS) + "\n...[truncated by Eternal Engine web console]";
                }
                prompt.append("\n--- FILE: ").append(attachment.name()).append(" ---\n").append(content).append('\n');
            }
        }
        prompt.append("\nAnswer as a practical mod-debugging assistant. If you identify likely causes, name exact methods/classes when possible. ")
            .append("Do not invent repo contents that were not supplied.");
        return prompt.toString();
    }

    private List<BrainChatAttachment> parseAttachments(JsonObject request) {
        List<BrainChatAttachment> attachments = new ArrayList<>();
        JsonElement element = request.get("attachments");
        if (element == null || !element.isJsonArray()) {
            return attachments;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (attachments.size() >= MAX_ATTACHMENTS) {
                break;
            }
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            String name = stringField(object, "name", "attachment.txt");
            String content = stringField(object, "content", "");
            if (content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS) + "\n...[truncated by Eternal Engine web console]";
            }
            if (!content.isBlank()) {
                attachments.add(new BrainChatAttachment(name, content));
            }
        }
        return attachments;
    }

    private boolean authorized(HttpExchange exchange) {
        String expected = token;
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String provided = queryParams(exchange.getRequestURI()).get("token");
        if (provided == null || provided.isBlank()) {
            provided = exchange.getRequestHeaders().getFirst("X-Eternal-Token");
        }
        return expected.equals(provided);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new IOException("Request too large; max " + MAX_REQUEST_BYTES + " bytes");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Map<String, String> queryParams(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            String key = idx >= 0 ? part.substring(0, idx) : part;
            String value = idx >= 0 ? part.substring(idx + 1) : "";
            map.put(urlDecode(key), urlDecode(value));
        }
        return map;
    }

    private String configuredOrGeneratedToken() {
        String configured = EternalEngineConfig.SERVER.webConsoleToken.get();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private JsonObject jsonError(String message) {
        JsonObject object = new JsonObject();
        object.addProperty("error", message);
        return object;
    }

    private String stringField(JsonObject object, String field, String fallback) {
        try {
            JsonElement element = object.get(field);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private void sendJson(HttpExchange exchange, int status, JsonObject object) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8", object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        sendBytes(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String urlEncodeToken(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String safe(@Nullable String value) {
        if (value == null) {
            return "unknown";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    private String html() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Eternal Engine Brain Console</title>
              <style>
                :root { color-scheme: dark; --bg:#08111f; --panel:#111d31; --text:#e7edf7; --muted:#98a7bd; --accent:#70d6ff; --bad:#ff8a8a; --ok:#8dffb0; }
                * { box-sizing: border-box; }
                body { margin:0; font-family: system-ui, Segoe UI, Roboto, Arial, sans-serif; background: radial-gradient(circle at top left,#17345d,#08111f 42%); color:var(--text); }
                header { padding:20px 24px; border-bottom:1px solid #26364f; background:rgba(8,17,31,.82); position:sticky; top:0; backdrop-filter: blur(8px); }
                h1 { margin:0 0 6px; font-size:22px; }
                .muted { color:var(--muted); font-size:13px; }
                main { display:grid; grid-template-columns: 1fr 330px; gap:16px; padding:16px; max-width:1300px; margin:0 auto; }
                .panel { background:rgba(17,29,49,.92); border:1px solid #26364f; border-radius:14px; box-shadow:0 14px 40px rgba(0,0,0,.25); }
                #chat { min-height:58vh; max-height:62vh; overflow:auto; padding:16px; display:flex; flex-direction:column; gap:12px; }
                .msg { padding:12px 14px; border-radius:12px; white-space:pre-wrap; line-height:1.38; }
                .user { background:#173d62; align-self:flex-end; max-width:88%; }
                .bot { background:#17243a; border:1px solid #2a4265; align-self:flex-start; max-width:94%; }
                .err { color:var(--bad); }
                form { padding:12px; border-top:1px solid #26364f; display:grid; gap:10px; }
                textarea, input { width:100%; background:#08111f; color:var(--text); border:1px solid #314762; border-radius:10px; padding:10px; }
                textarea { min-height:105px; resize:vertical; }
                button { background:linear-gradient(135deg,#43c6ff,#7b61ff); color:white; border:0; padding:10px 14px; border-radius:10px; font-weight:700; cursor:pointer; }
                button:disabled { opacity:.55; cursor:wait; }
                aside { padding:14px; display:flex; flex-direction:column; gap:12px; }
                code { background:#07101d; padding:2px 5px; border-radius:5px; }
                .fileList { max-height:220px; overflow:auto; font-size:12px; color:var(--muted); }
                .ok { color:var(--ok); } .accent { color:var(--accent); }
                @media (max-width: 900px) { main { grid-template-columns:1fr; } #chat { max-height:52vh; } }
              </style>
            </head>
            <body>
              <header>
                <h1>Eternal Engine Brain Console</h1>
                <div class="muted">Localhost-only developer chat using the same server-side brain bridge as the Minecraft mod. The brain may be a remote gateway configured by the server admin. Advisory/read-only: no raw commands, no direct world edits.</div>
              </header>
              <main>
                <section class="panel">
                  <div id="chat"></div>
                  <form id="form">
                    <textarea id="message" placeholder="Explain the bug. Example: building plan-test spawned 15 blocks above terrain after Terra++ teleport to Hyderabad..."></textarea>
                    <button id="send" type="submit">Send to server-side brain</button>
                  </form>
                </section>
                <aside class="panel">
                  <div>
                    <b>Session</b>
                    <input id="session" value="minecraft" />
                    <div class="muted">Use the same session name to keep memory.</div>
                  </div>
                  <div>
                    <b>Attach files or repo folder</b>
                    <input id="files" type="file" multiple />
                    <input id="folder" type="file" multiple webkitdirectory />
                    <div class="muted">The browser reads selected text/code files and sends snippets. Nothing is read from disk unless you select it.</div>
                    <div id="fileList" class="fileList"></div>
                  </div>
                  <div>
                    <b>Status</b>
                    <pre id="status" class="muted">Loading...</pre>
                  </div>
                  <div class="muted">Tip: use <code>/ee web url</code> in Minecraft to reopen this page with the token.</div>
                </aside>
              </main>
              <script>
                const params = new URLSearchParams(location.search);
                const token = params.get('token') || '';
                const chat = document.getElementById('chat');
                const form = document.getElementById('form');
                const send = document.getElementById('send');
                const files = document.getElementById('files');
                const folder = document.getElementById('folder');
                const fileList = document.getElementById('fileList');
                const statusBox = document.getElementById('status');
                let attachments = [];
                function add(role, text, err=false) {
                  const div = document.createElement('div');
                  div.className = 'msg ' + (role === 'user' ? 'user' : 'bot') + (err ? ' err' : '');
                  div.textContent = text;
                  chat.appendChild(div);
                  chat.scrollTop = chat.scrollHeight;
                }
                async function refreshStatus() {
                  try {
                    const r = await fetch('/api/status?token=' + encodeURIComponent(token));
                    const j = await r.json();
                    statusBox.textContent = 'Brain available: ' + j.brainAvailable + '\\n' + (j.status || '').split('\\n').slice(0,12).join('\\n');
                  } catch (e) { statusBox.textContent = 'Status failed: ' + e; }
                }
                async function readFiles(list) {
                  attachments = [];
                  const accepted = Array.from(list).filter(f => f.size <= 120000 && /[.](java|gradle|json|toml|md|txt|log|properties|xml|yml|yaml|js|ts|py)$/i.test(f.name)).slice(0, 16);
                  for (const f of accepted) {
                    const text = await f.text();
                    attachments.push({ name: f.webkitRelativePath || f.name, content: text.slice(0, 80000) });
                  }
                  fileList.textContent = attachments.length ? attachments.map(a => '• ' + a.name).join('\\n') : 'No text/code files selected.';
                }
                files.addEventListener('change', e => readFiles(e.target.files));
                folder.addEventListener('change', e => readFiles(e.target.files));
                form.addEventListener('submit', async e => {
                  e.preventDefault();
                  const message = document.getElementById('message').value.trim();
                  if (!message) return;
                  add('user', message + (attachments.length ? '\\n\\n[attached files: ' + attachments.length + ']' : ''));
                  document.getElementById('message').value = '';
                  send.disabled = true;
                  try {
                    const r = await fetch('/api/chat?token=' + encodeURIComponent(token), {
                      method:'POST', headers:{'Content-Type':'application/json'},
                      body: JSON.stringify({ sessionId: document.getElementById('session').value || 'minecraft', message, attachments })
                    });
                    const j = await r.json();
                    if (!r.ok) add('bot', j.error || 'Request failed', true); else add('bot', j.reply || '(empty)');
                    refreshStatus();
                  } catch (err) { add('bot', 'Request failed: ' + err, true); }
                  finally { send.disabled = false; }
                });
                add('bot', 'Connected. Explain the problem and optionally select source files or a repo folder. I will answer from supplied files plus live Eternal Engine server facts.');
                refreshStatus();
              </script>
            </body>
            </html>
            """;
    }
}
