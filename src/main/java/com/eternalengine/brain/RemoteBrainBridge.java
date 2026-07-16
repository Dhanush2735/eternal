package com.eternalengine.brain;

import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.anomaly.AnomalyRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Server-side remote brain bridge for multiplayer servers.
 *
 * Eternal Engine intentionally does not require or start a local LLM process.
 * The Minecraft server remains authoritative. This bridge sends bounded server
 * facts / diagnostic text to configured HTTPS gateway(s) and receives advisory
 * text or proposals. Every proposal still goes through LlmProposalValidator and
 * ServerDecisionQueue before any subsystem can act.
 *
 * Gateway priority is fixed by design:
 * 1. LiteLLM/OpenAI-compatible gateway as the primary source.
 * 2. Portkey/OpenAI-compatible gateway as the secondary fallback source.
 */
public final class RemoteBrainBridge implements LlmBrainBridge {

    private static final int MAX_CHAT_MESSAGES_PER_SESSION = 18;
    private static final int MAX_ATTACHMENT_CHARS = 24_000;

    private final HttpClient client;
    private final List<GatewayTarget> targets;
    private final int timeoutMs;
    private final boolean configured;
    private final ConcurrentHashMap<String, List<ChatTurn>> chatSessions = new ConcurrentHashMap<>();

    public RemoteBrainBridge(String liteLlmEndpoint, String liteLlmModel, String liteLlmToken,
                             String liteLlmExtraHeaders, String portkeyEndpoint, String portkeyModel,
                             String portkeyProviderToken, String portkeyExtraHeaders, int timeoutMs) {
        this.timeoutMs = Math.max(500, Math.min(60_000, timeoutMs));
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.timeoutMs))
            .build();

        List<GatewayTarget> list = new ArrayList<>(2);
        GatewayTarget lite = GatewayTarget.create("LiteLLM", liteLlmEndpoint, liteLlmModel,
            liteLlmToken, liteLlmExtraHeaders);
        if (lite.configured()) {
            list.add(lite);
        }
        GatewayTarget portkey = GatewayTarget.create("Portkey", portkeyEndpoint, portkeyModel,
            portkeyProviderToken, portkeyExtraHeaders);
        if (portkey.configured()) {
            list.add(portkey);
        }
        this.targets = List.copyOf(list);
        this.configured = !this.targets.isEmpty();
    }

    @Override
    public CompletableFuture<List<LlmProposal>> proposeFromAffairs(CurrentAffairsBriefing briefing) {
        if (briefing == null || !isAvailable()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String prompt = "You are Eternal Engine's remote advisory brain. Read only these server facts and propose at most one safe action. "
            + "Allowed actions: REQUEST_CURRENT_AFFAIRS, REQUEST_ANOMALY_EXPLANATION, DECLARE_WAR, OFFER_PEACE, MOVE_ARMY, "
            + "OPEN_TRADE_ROUTE, CLOSE_TRADE_ROUTE, QUARANTINE_REGION, PROPOSE_BUILDING, PROPOSE_INCIDENT_RESPONSE, PROPOSE_VEHICLE_ROUTE. "
            + "Return plain text containing ACTION=<one allowed action>. Facts: " + briefing.toChatSummary();
        return requestText("proposal-affairs", prompt, List.of())
            .thenApply(text -> parseProposalText(text, "remote_affairs"))
            .exceptionally(error -> List.of());
    }

    @Override
    public CompletableFuture<List<LlmProposal>> proposeFromAnomalies(List<AnomalyRecord> anomalies) {
        if (anomalies == null || anomalies.isEmpty() || !isAvailable()) {
            return CompletableFuture.completedFuture(List.of());
        }
        StringBuilder prompt = new StringBuilder("You are Eternal Engine's anomaly advisor. Propose only safe diagnostic actions. ");
        prompt.append("Return ACTION=REQUEST_ANOMALY_EXPLANATION unless another allowed read-only action is clearly better. Anomalies: ");
        anomalies.stream().limit(5).forEach(a -> prompt.append(a.toSummaryString()).append(" | "));
        return requestText("proposal-anomaly", prompt.toString(), List.of())
            .thenApply(text -> parseProposalText(text, "remote_anomaly"))
            .exceptionally(error -> List.of());
    }

    @Override
    public CompletableFuture<String> chat(String sessionId, String userMessage, List<BrainChatAttachment> attachments) {
        if (!configured) {
            return CompletableFuture.completedFuture("Remote brain gateway is not configured. Configure LiteLLM as primary and/or Portkey as secondary in serverconfig/eternalengine-server.toml [brainGateway], then run /ee brain remote-start.");
        }
        String safeSession = sanitizeSessionId(sessionId);
        String prompt = buildChatUserMessage(userMessage, attachments);
        return requestText(safeSession, prompt, getHistory(safeSession))
            .thenApply(answer -> {
                List<ChatTurn> history = chatSessions.computeIfAbsent(safeSession,
                    ignored -> java.util.Collections.synchronizedList(new ArrayList<>()));
                appendChatTurn(history, "user", prompt);
                appendChatTurn(history, "assistant", answer);
                return answer;
            })
            .exceptionally(error -> "Remote brain gateway failed: " + safeError(error.getMessage())
                + ". Check LiteLLM/Portkey endpoint, token, model, and extra headers on the Minecraft server.");
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    public void shutdown() {
        chatSessions.clear();
    }

    public String configuredTargetsSummary() {
        if (targets.isEmpty()) {
            return "none";
        }
        return String.join(" -> ", targets.stream().map(GatewayTarget::name).toList());
    }

    private CompletableFuture<String> requestText(String sessionId, String prompt, List<ChatTurn> history) {
        return CompletableFuture.supplyAsync(() -> requestTextBlocking(sessionId, prompt, history));
    }

    private String requestTextBlocking(String sessionId, String prompt, List<ChatTurn> history) {
        String lastFailure = "no configured targets";
        for (GatewayTarget target : targets) {
            try {
                String result = requestTarget(target, sessionId, prompt, history);
                if (result != null && !result.isBlank()) {
                    return result;
                }
                lastFailure = target.name() + " returned an empty response";
            } catch (Exception e) {
                lastFailure = target.name() + " failed: " + safeError(e.getMessage());
            }
        }
        return "Remote brain gateways failed. Last failure: " + lastFailure;
    }

    private String requestTarget(GatewayTarget target, String sessionId, String prompt, List<ChatTurn> history) throws Exception {
        JsonObject body = target.openAiCompatible()
            ? buildOpenAiRequest(target, prompt, history)
            : buildGatewayRequest(target, sessionId, prompt, history);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target.endpoint())
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!target.bearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + target.bearerToken());
        }
        for (Map.Entry<String, String> header : target.extraHeaders().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        HttpResponse<String> response = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .join();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + safeError(response.body()));
        }
        return parseTextResponse(response.body());
    }

    private JsonObject buildOpenAiRequest(GatewayTarget target, String prompt, List<ChatTurn> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", target.model());
        body.addProperty("stream", false);
        body.addProperty("temperature", 0.2d);
        body.add("messages", buildMessages(prompt, history));
        return body;
    }

    private JsonObject buildGatewayRequest(GatewayTarget target, String sessionId, String prompt, List<ChatTurn> history) {
        JsonObject body = new JsonObject();
        body.addProperty("sessionId", sessionId);
        body.addProperty("model", target.model());
        body.addProperty("stream", false);
        body.add("messages", buildMessages(prompt, history));
        return body;
    }

    private JsonArray buildMessages(String prompt, List<ChatTurn> history) {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "You are Eternal Engine's multiplayer-safe advisory brain. "
            + "The Minecraft server is authoritative. You may explain bugs, summarize files, and propose safe actions, "
            + "but you must not claim to mutate the world. Do not invent server facts that were not supplied.");
        messages.add(system);
        for (ChatTurn turn : history) {
            JsonObject message = new JsonObject();
            message.addProperty("role", turn.role());
            message.addProperty("content", turn.content());
            messages.add(message);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", prompt);
        messages.add(user);
        return messages;
    }

    private String parseTextResponse(String body) {
        String text = body == null ? "" : body;
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("reply")) {
                return json.get("reply").getAsString();
            }
            if (json.has("response")) {
                return json.get("response").getAsString();
            }
            if (json.has("message")) {
                JsonObject message = json.getAsJsonObject("message");
                if (message != null && message.has("content")) {
                    return message.get("content").getAsString();
                }
            }
            if (json.has("choices")) {
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (choice.has("message")) {
                        JsonObject message = choice.getAsJsonObject("message");
                        if (message != null && message.has("content")) {
                            return message.get("content").getAsString();
                        }
                    }
                    if (choice.has("text")) {
                        return choice.get("text").getAsString();
                    }
                }
            }
            if (json.has("error")) {
                return "Remote brain returned an error: " + json.get("error").toString();
            }
        } catch (Exception ignored) {
            // Accept plain text responses too.
        }
        return text.isBlank() ? "Remote brain returned an empty response." : text;
    }

    private List<LlmProposal> parseProposalText(String text, String source) {
        String upper = (text == null ? "" : text).toUpperCase(Locale.ROOT);
        LlmAction selected = LlmAction.REQUEST_CURRENT_AFFAIRS;
        for (LlmAction action : LlmAction.values()) {
            if (action != LlmAction.UNKNOWN && upper.contains(action.name())) {
                selected = action;
                break;
            }
        }
        return List.of(new LlmProposal("remote_" + source + "_" + System.currentTimeMillis(),
            "remote_brain_gateway", selected.name(), java.util.Map.of("source", source),
            "Remote brain proposal from server facts", 0.70d));
    }

    private List<ChatTurn> getHistory(String safeSession) {
        List<ChatTurn> history = chatSessions.computeIfAbsent(safeSession,
            ignored -> java.util.Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            int start = Math.max(0, history.size() - MAX_CHAT_MESSAGES_PER_SESSION);
            return List.copyOf(history.subList(start, history.size()));
        }
    }

    private void appendChatTurn(List<ChatTurn> history, String role, String content) {
        synchronized (history) {
            history.add(new ChatTurn(role, content));
            while (history.size() > MAX_CHAT_MESSAGES_PER_SESSION * 2) {
                history.remove(0);
            }
        }
    }

    private String buildChatUserMessage(String userMessage, List<BrainChatAttachment> attachments) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(userMessage == null ? "" : userMessage.trim());
        if (attachments != null && !attachments.isEmpty()) {
            prompt.append("\n\nAttached context files/repo snippets:");
            attachments.stream().limit(12).forEach(attachment -> {
                String content = attachment.content() == null ? "" : attachment.content();
                if (content.length() > MAX_ATTACHMENT_CHARS) {
                    content = content.substring(0, MAX_ATTACHMENT_CHARS) + "\n...[truncated by Eternal Engine web console]";
                }
                prompt.append("\n\n--- FILE: ").append(attachment.name()).append(" ---\n").append(content);
            });
        }
        return prompt.toString();
    }

    private static boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        return endpoint.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    private static Map<String, String> parseExtraHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return headers;
        }
        String[] pairs = raw.split(";");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String name = pair.substring(0, idx).trim();
            String value = pair.substring(idx + 1).trim();
            if (isSafeHeaderName(name) && isSafeHeaderValue(value)) {
                headers.put(name, value);
            }
        }
        return Map.copyOf(headers);
    }

    private static boolean isSafeHeaderName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("authorization") || lower.equals("content-type")
            || lower.equals("content-length") || lower.equals("host")) {
            return false;
        }
        return name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,80}");
    }

    private static boolean isSafeHeaderValue(String value) {
        return value != null && value.length() <= 2000
            && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private String sanitizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "minecraft";
        }
        String safe = sessionId.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private String safeError(String value) {
        if (value == null || value.isBlank()) {
            return "unknown error";
        }
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() > 300 ? clean.substring(0, 300) : clean;
    }

    private record GatewayTarget(String name, URI endpoint, String model, String bearerToken,
                                 Map<String, String> extraHeaders, boolean openAiCompatible,
                                 boolean configured) {
        static GatewayTarget create(String name, String endpoint, String model, String bearerToken, String extraHeaders) {
            String safeEndpoint = endpoint == null ? "" : endpoint.trim();
            boolean configured = isAllowedEndpoint(safeEndpoint);
            URI uri = configured ? URI.create(safeEndpoint) : URI.create("https://invalid.eternalengine.local/");
            String safeModel = model == null || model.isBlank() ? "default" : model.trim();
            String safeToken = bearerToken == null ? "" : bearerToken.trim();
            return new GatewayTarget(name, uri, safeModel, safeToken, parseExtraHeaders(extraHeaders),
                safeEndpoint.contains("/v1/chat/completions"), configured);
        }
    }

    private record ChatTurn(String role, String content) {
    }
}
