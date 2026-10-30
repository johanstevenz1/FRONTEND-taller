package com.monitoring.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.model.CallStatus;
import com.monitoring.model.LogEntry;
import com.monitoring.model.ServiceSummary;
import com.monitoring.store.LogStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {
    private static final List<String> SERVICES = List.of("inventory", "orders", "payments");
    private static final List<String> COLORS = List.of("#3157d5", "#0b9a75", "#d97706");
    private final MetricsController metricsController;
    private final LogStore logStore;
    private final ObjectMapper objectMapper;

    public DashboardController(MetricsController metricsController, LogStore logStore, ObjectMapper objectMapper) {
        this.metricsController = metricsController;
        this.logStore = logStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public org.springframework.web.servlet.ModelAndView dashboard(
            @RequestParam(required = false, defaultValue = "") String service,
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(required = false, defaultValue = "") String from,
            @RequestParam(required = false, defaultValue = "") String to,
            @RequestParam(required = false, defaultValue = "") String notice,
            @RequestParam(defaultValue = "0") int page) {
        String safeService = SERVICES.contains(service) ? service : "";
        String safeStatus = Arrays.asList("SUCCESS", "ERROR").contains(status) ? status : "";
        Instant fromInstant = parseDate(from);
        Instant toInstant = parseDate(to);
        int safePage = Math.max(0, page);
        var logs = logStore.findAll(safeService, safeStatus.isEmpty() ? null : CallStatus.valueOf(safeStatus),
                fromInstant, toInstant, safePage, 20);

        StringBuilder html = new StringBuilder(16000);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<meta http-equiv=\"refresh\" content=\"3;url=").append(escape(refreshUrl(safeService, safeStatus, from, to, safePage))).append("\">")
                .append("<title>Microservices Monitoring Dashboard</title><style>").append(styles()).append("</style></head><body><main>")
                .append("<header><div><p class=\"eyebrow\">OBSERVABILITY</p><h1>Microservices Monitoring Dashboard</h1>")
                .append("<p class=\"muted\">In-memory service health and request history. Refreshes every 3 seconds.</p></div>")
                .append("<form method=\"post\" action=\"/dashboard/simulate-load\"><button class=\"primary\" type=\"submit\">Simulate load</button></form></header>");
        if (!notice.isBlank()) html.append("<div class=\"notice\">").append(escape(notice)).append("</div>");
        appendSummary(html, metricsController.summary());
        appendChart(html);
        appendFilters(html, safeService, safeStatus, from, to);
        appendLogs(html, logs.content(), logs.page(), logs.totalPages(), safeService, safeStatus, from, to);
        html.append("</main></body></html>");
        return new org.springframework.web.servlet.ModelAndView(htmlView(html.toString()));
    }

    private View htmlView(String html) {
        return new AbstractView() {
            @Override
            protected void renderMergedOutputModel(Map<String, Object> model,
                    jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response) throws Exception {
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(html);
            }
        };
    }

    @PostMapping("/dashboard/simulate-load")
    public RedirectView simulateLoad() {
        Map<String, Integer> result = metricsController.simulateLoad();
        String message = "50 calls executed: " + result.get("success") + " success, " + result.get("errors") + " errors";
        return new RedirectView("/?notice=" + encode(message), true);
    }

    private void appendSummary(StringBuilder html, List<ServiceSummary> summaries) {
        html.append("<section class=\"cards\" aria-label=\"Service status\">");
        for (ServiceSummary summary : summaries) {
            boolean unhealthy = summary.errorRate() > 15;
            html.append("<article class=\"card ").append(unhealthy ? "unhealthy" : "healthy").append("\"><div class=\"card-top\"><h2>")
                    .append(escape(summary.serviceId())).append("</h2><span class=\"badge\">")
                    .append(unhealthy ? "Unhealthy" : "Healthy").append("</span></div><dl>")
                    .append(metric("Total calls", String.valueOf(summary.totalCalls())))
                    .append(metric("Success rate", summary.successRate() + "%"))
                    .append(metric("Avg response", summary.avgResponseTimeMs() + " ms"))
                    .append("</dl></article>");
        }
        html.append("</section>");
    }

    private String metric(String label, String value) {
        return "<div><dt>" + label + "</dt><dd>" + escape(value) + "</dd></div>";
    }

    private void appendChart(StringBuilder html) {
        List<LogEntry> all = logStore.all();
        html.append("<section class=\"panel\"><div class=\"section-heading\"><div><p class=\"eyebrow\">LATENCY</p><h2>Recent response times</h2></div><span class=\"muted\">Last 20 calls per service · milliseconds</span></div>")
                .append("<div class=\"chart-wrap\"><svg viewBox=\"0 0 900 260\" role=\"img\" aria-label=\"Response time chart\"><line x1=\"55\" y1=\"220\" x2=\"870\" y2=\"220\" class=\"axis\"/><line x1=\"55\" y1=\"20\" x2=\"55\" y2=\"220\" class=\"axis\"/><text x=\"10\" y=\"24\" class=\"axis-label\">ms</text>");
        long max = all.stream().mapToLong(LogEntry::durationMs).max().orElse(1);
        max = Math.max(1, max);
        html.append("<text x=\"8\" y=\"220\" class=\"axis-label\">0</text><text x=\"8\" y=\"40\" class=\"axis-label\">").append(max).append("</text>");
        for (int serviceIndex = 0; serviceIndex < SERVICES.size(); serviceIndex++) {
            String service = SERVICES.get(serviceIndex);
            List<LogEntry> points = all.stream().filter(log -> log.serviceId().equals(service)).sorted(java.util.Comparator.comparing(LogEntry::timestamp)).skip(Math.max(0, all.stream().filter(log -> log.serviceId().equals(service)).count() - 20)).toList();
            if (!points.isEmpty()) {
                List<String> coordinates = new ArrayList<>();
                StringBuilder markers = new StringBuilder();
                for (int i = 0; i < points.size(); i++) {
                    int x = 60 + (int) Math.round(i * 800.0 / 19.0);
                    int y = 215 - (int) Math.round(points.get(i).durationMs() * 185.0 / max);
                    coordinates.add(x + "," + y);
                    markers.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"4\" fill=\"").append(COLORS.get(serviceIndex)).append("\"><title>Call ")
                            .append(i + 1).append(": ").append(points.get(i).durationMs()).append(" ms</title></circle>");
                }
                html.append("<polyline class=\"line\" stroke=\"").append(COLORS.get(serviceIndex)).append("\" points=\"")
                        .append(String.join(" ", coordinates)).append("\"/>").append(markers);
            }
        }
        html.append("<text x=\"55\" y=\"245\" class=\"axis-label\">1</text><text x=\"830\" y=\"245\" class=\"axis-label\">20 calls</text></svg></div><div class=\"legend\">");
        for (int i = 0; i < SERVICES.size(); i++) html.append("<span><i style=\"background:").append(COLORS.get(i)).append("\"></i>").append(SERVICES.get(i)).append("</span>");
        html.append("</div></section>");
    }

    private void appendFilters(StringBuilder html, String service, String status, String from, String to) {
        html.append("<section class=\"panel\"><div class=\"section-heading\"><div><p class=\"eyebrow\">REQUESTS</p><h2>Recent logs</h2></div></div>")
                .append("<form method=\"get\" action=\"/\" class=\"filters\"><label>Service<select name=\"service\"><option value=\"\">All</option>");
        for (String serviceId : SERVICES) html.append("<option value=\"").append(serviceId).append("\"").append(service.equals(serviceId) ? " selected" : "").append(">").append(serviceId).append("</option>");
        html.append("</select></label><label>Status<select name=\"status\"><option value=\"\">All</option>");
        for (String value : List.of("SUCCESS", "ERROR")) html.append("<option value=\"").append(value).append("\"").append(status.equals(value) ? " selected" : "").append(">").append(value).append("</option>");
        html.append("</select></label><label>From<input name=\"from\" type=\"datetime-local\" value=\"").append(escape(toLocalInput(from))).append("\"></label>")
                .append("<label>To<input name=\"to\" type=\"datetime-local\" value=\"").append(escape(toLocalInput(to))).append("\"></label>")
                .append("<button type=\"submit\" class=\"secondary\">Apply filters</button><a class=\"clear\" href=\"/\">Clear filters</a></form></section>");
    }

    private void appendLogs(StringBuilder html, List<LogEntry> logs, int page, int totalPages,
                            String service, String status, String from, String to) {
        if (logs.isEmpty()) html.append("<p class=\"empty\">No logs yet. Click 'Simulate load'.</p>");
        else {
            html.append("<div class=\"table-wrap\"><table><thead><tr><th>Request ID</th><th>Service</th><th>Operation</th><th>Duration</th><th>Status</th><th>Timestamp</th><th>Details</th></tr></thead><tbody>");
            for (LogEntry log : logs) {
                html.append("<tr><td class=\"mono\">").append(escape(log.requestId())).append("</td><td>").append(escape(log.serviceId()))
                        .append("</td><td>").append(escape(log.operation())).append("</td><td>").append(log.durationMs()).append(" ms</td><td><span class=\"state ")
                        .append(log.status() == CallStatus.SUCCESS ? "success" : "error").append("\">").append(log.status()).append("</span></td><td>")
                        .append(escape(log.timestamp().toString())).append("</td><td><details><summary>View</summary><div class=\"detail-grid\"><div><b>Input parameters</b><pre>")
                        .append(escape(json(log.params()))).append("</pre></div><div><b>Response</b><pre>").append(escape(json(log.response()))).append("</pre></div>");
                if (log.status() == CallStatus.ERROR) html.append("<div><b>Error</b><pre>").append(escape(log.errorMessage())).append("</pre></div><div><b>Stack trace summary</b><pre>").append(escape(log.stackTraceSummary())).append("</pre></div>");
                html.append("</div></details></td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        html.append("<nav class=\"pagination\"><span>Page ").append(page + 1).append(" of ").append(Math.max(1, totalPages)).append("</span>");
        if (page > 0) html.append("<a href=\"").append(escape(pageUrl(service, status, from, to, page - 1))).append("\">Previous</a>");
        if (page + 1 < totalPages) html.append("<a href=\"").append(escape(pageUrl(service, status, from, to, page + 1))).append("\">Next</a>");
        html.append("</nav>");
    }

    private String json(Object value) {
        try { return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (JsonProcessingException exception) { return String.valueOf(value); }
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException ignored) { return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant(); }
    }

    private String toLocalInput(String value) {
        if (value == null || value.isBlank()) return "";
        try { return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); }
        catch (RuntimeException ignored) { return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); }
    }

    private String refreshUrl(String service, String status, String from, String to, int page) {
        return pageUrl(service, status, from, to, page);
    }

    private String pageUrl(String service, String status, String from, String to, int page) {
        List<String> query = new ArrayList<>();
        if (!service.isBlank()) query.add("service=" + encode(service));
        if (!status.isBlank()) query.add("status=" + encode(status));
        if (!from.isBlank()) query.add("from=" + encode(from));
        if (!to.isBlank()) query.add("to=" + encode(to));
        query.add("page=" + page);
        return "/?" + String.join("&", query);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String escape(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }

    private String styles() {
        return "*{box-sizing:border-box}body{margin:0;background:#f4f6fa;color:#172033;font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}main{max-width:1200px;margin:auto;padding:36px 24px 64px}header,.section-heading,.card-top,.pagination{display:flex;align-items:center;justify-content:space-between;gap:16px}header{margin-bottom:26px}h1{font-size:clamp(26px,4vw,38px);margin:0}h2{margin:0;font-size:19px;text-transform:capitalize}.eyebrow{font-size:11px;letter-spacing:.15em;color:#66718a;font-weight:800;margin:0 0 6px}.muted{color:#69758c}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:20px}.card,.panel{background:#fff;border:1px solid #e2e7f0;border-radius:16px;box-shadow:0 5px 18px #1c2b4510}.card{padding:20px;border-top:4px solid #15966f}.card.unhealthy{border:1px solid #f0b7b7;border-top:4px solid #d83737;background:#fff7f7}.badge{padding:4px 10px;border-radius:99px;background:#e8f7f0;color:#08764e;font-size:12px;font-weight:700}.unhealthy .badge{background:#ffe5e5;color:#b42323}dl{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:24px 0 0}dt{font-size:12px;color:#758097}dd{font-size:19px;font-weight:750;margin:2px 0 0}.panel{padding:22px;margin-bottom:18px}.section-heading{margin-bottom:18px}.chart-wrap{width:100%;overflow:auto}.chart-wrap svg{display:block;width:100%;min-width:600px;height:auto}.axis{stroke:#dce2ec;stroke-width:1}.axis-label{fill:#6b768c;font-size:12px}.line{fill:none;stroke-width:3;stroke-linejoin:round;stroke-linecap:round}.legend{display:flex;justify-content:center;gap:22px;padding-top:8px}.legend span{display:flex;align-items:center;gap:7px;text-transform:capitalize}.legend i{width:10px;height:10px;border-radius:50%}.filters{display:flex;align-items:end;flex-wrap:wrap;gap:12px}.filters label{display:grid;gap:5px;font-weight:650;font-size:12px;color:#59657c}.filters select,.filters input{height:38px;border:1px solid #cfd6e2;border-radius:8px;background:#fff;padding:0 10px;color:#172033}.primary,.secondary{border:0;border-radius:9px;padding:11px 16px;font-weight:750;cursor:pointer}.primary{background:#3157d5;color:#fff}.secondary{background:#e9eefb;color:#2547b4}.clear{padding:8px;color:#4058a2}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;min-width:860px}th,td{text-align:left;padding:12px 10px;border-bottom:1px solid #edf0f5;vertical-align:top}th{font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:#738098}td{font-size:13px}.mono{font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.state{font-size:11px;font-weight:800;padding:4px 8px;border-radius:20px}.state.success{background:#e6f6ee;color:#08764e}.state.error{background:#ffe7e7;color:#b42323}details summary{cursor:pointer;color:#3157d5;font-weight:700}.detail-grid{min-width:330px;display:grid;grid-template-columns:1fr 1fr;gap:12px;padding:12px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f4f6fa;border-radius:7px;padding:10px;font-size:11px;max-height:220px;overflow:auto}.pagination{justify-content:flex-end;margin-top:16px;color:#66718a}.pagination a{color:#3157d5;text-decoration:none;font-weight:700}.empty,.notice{padding:16px;border-radius:10px;background:#fff}.notice{margin-bottom:16px;background:#e9f6ef;color:#08764e;font-weight:700}@media(max-width:800px){main{padding:22px 14px}.cards{grid-template-columns:1fr}header{align-items:flex-start}.card dl{margin-top:16px}}";
    }
}
