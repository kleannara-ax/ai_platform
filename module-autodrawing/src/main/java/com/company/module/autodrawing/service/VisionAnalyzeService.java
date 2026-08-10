package com.company.module.autodrawing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Vision AI 이미지 분석 서비스 — 기존 Express POST /api/analyze 로직 변환
 */
@Slf4j
@Service
public class VisionAnalyzeService {

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Value("${openai.api-key:#{null}}")
    private String apiKey;

    @Value("${openai.base-url:#{null}}")
    private String baseUrl;

    private static final String VISION_PROMPT = """
            You are a mechanical drawing analyzer specialized in reading hand-drawn shaft drawings.
            
            Analyze this hand-drawn mechanical shaft drawing and extract ALL geometric information.
            
            Return a JSON object with EXACTLY this structure (no markdown, no explanation, ONLY valid JSON):
            
            {
              "totalLength": <number or null>,
              "sections": [
                {
                  "position": "S1",
                  "diameter": <number or null>,
                  "length": <number or null>,
                  "diameterConfidence": "confirmed" | "estimated" | "uncertain",
                  "lengthConfidence": "confirmed" | "estimated" | "uncertain"
                }
              ],
              "hiddenFeatures": [
                {
                  "id": "HF1",
                  "section": "S1",
                  "type": "tap-bore" | "keyway",
                  "side": "left" | "right",
                  "diameter": <number or null>,
                  "depth": <number or null>,
                  "keywayWidth": <number or null>,
                  "keywayHeight": <number or null>,
                  "keywayDepth": <number or null>,
                  "spec": "<string like 'M10 TAP depth30' or null>"
                }
              ],
              "auxiliaryViews": [
                {
                  "id": "AUX1",
                  "relatedSection": "S1",
                  "shape": "obround" | "circle" | "rectangle",
                  "width": <number>,
                  "height": <number>
                }
              ],
              "chamfers": [
                { "side": "left" | "right", "spec": "<string or null>" }
              ],
              "centerHoles": [
                { "side": "left" | "right", "diameter": <number or null> }
              ],
              "material": "<string or null>",
              "surfaceFinish": "<string or null>",
              "notes": "<string or null>"
            }
            
            CRITICAL RULES:
            1. Count EVERY distinct diameter section from left to right.
            2. Sections are numbered S1 (leftmost) to SN (rightmost).
            3. Read ALL numbers exactly as written - never invent values.
            4. If a value is unreadable, use null.
            5. Total length should equal sum of all section lengths.
            6. For TAP holes, include diameter and depth.
            7. For keyways, include width, height, and depth values if visible.
            8. Auxiliary views are typically shown above the main drawing.
            9. Look for Korean annotations.
            10. Assess confidence for each dimension.
            
            Return ONLY the JSON object, nothing else.""";

    public VisionAnalyzeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String getEffectiveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        // Fallback: YAML config file
        try {
            String home = System.getProperty("user.home");
            java.io.File configFile = new java.io.File(home, ".genspark_llm.yaml");
            if (configFile.exists()) {
                var yamlMapper = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
                JsonNode cfg = yamlMapper.readTree(configFile);
                JsonNode key = cfg.path("openai").path("api_key");
                if (!key.isMissingNode()) return key.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to read YAML config: {}", e.getMessage());
        }
        return null;
    }

    public String getEffectiveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) return baseUrl;
        try {
            String home = System.getProperty("user.home");
            java.io.File configFile = new java.io.File(home, ".genspark_llm.yaml");
            if (configFile.exists()) {
                var yamlMapper = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
                JsonNode cfg = yamlMapper.readTree(configFile);
                JsonNode url = cfg.path("openai").path("base_url");
                if (!url.isMissingNode()) return url.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to read YAML config: {}", e.getMessage());
        }
        return "https://api.openai.com/v1";
    }

    /**
     * 이미지 분석 — base64 인코딩된 이미지를 Vision API로 전송
     */
    public JsonNode analyze(byte[] imageBytes, String mimeType, String originalFilename) throws IOException {
        log.info("[API] Analyzing image: {} ({} bytes, {})", originalFilename, imageBytes.length, mimeType);

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;

        String effectiveKey = getEffectiveApiKey();
        String effectiveUrl = getEffectiveBaseUrl();

        if (effectiveKey == null) {
            throw new IOException("OpenAI API key not configured");
        }

        log.info("[API] Calling Vision API... (baseURL: {})", effectiveUrl);

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "gpt-5");
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.1);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");

        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", VISION_PROMPT);

        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", dataUrl);
        imageUrl.put("detail", "high");

        String url = effectiveUrl.endsWith("/")
                ? effectiveUrl + "chat/completions"
                : effectiveUrl + "/chat/completions";

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + effectiveKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("[API] Vision API error: {} {}", response.code(), responseBody);
                throw new IOException("Vision API error: " + response.code());
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            String contentStr = responseJson.path("choices").path(0).path("message").path("content").asText();

            log.info("[API] Raw Vision response received ({} chars)", contentStr.length());

            if (contentStr.isEmpty()) {
                throw new IOException("Empty response from Vision API");
            }

            // JSON 파싱 (마크다운 코드블록 제거)
            String jsonStr = contentStr.trim();
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceFirst("^```(?:json)?\\s*\\n?", "").replaceFirst("\\n?```\\s*$", "");
            }

            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                // Try to extract JSON
                int start = contentStr.indexOf('{');
                int end = contentStr.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    parsed = objectMapper.readTree(contentStr.substring(start, end + 1));
                } else {
                    throw new IOException("Failed to parse Vision API response: " + e.getMessage());
                }
            }

            if (!parsed.has("sections") || !parsed.get("sections").isArray()) {
                throw new IOException("Invalid response structure: missing sections array");
            }

            int sectionCount = parsed.get("sections").size();
            log.info("[API] Extracted {} sections, totalLength={}",
                    sectionCount, parsed.path("totalLength").asText("null"));

            // signals 변환 + 결과 조합
            JsonNode signals = convertToSignals(parsed);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("success", true);
            result.set("raw", parsed);
            result.set("signals", signals);
            result.put("sectionCount", sectionCount);
            result.set("totalLength", parsed.get("totalLength"));
            return result;
        }
    }

    /**
     * Vision API 응답 → signals 형식 변환 (기존 convertToSignals 그대로)
     */
    private JsonNode convertToSignals(JsonNode parsed) {
        ObjectNode signals = objectMapper.createObjectNode();

        // hasHorizontalCenterline
        ObjectNode centerline = signals.putObject("hasHorizontalCenterline");
        centerline.put("value", true);
        centerline.put("confidence", "confirmed");

        // shaftLikelihood
        ObjectNode shaft = signals.putObject("shaftLikelihood");
        shaft.put("value", 0.95);
        shaft.put("confidence", "confirmed");

        // totalLength
        if (parsed.has("totalLength") && !parsed.get("totalLength").isNull()) {
            ObjectNode tl = signals.putObject("totalLength");
            tl.put("value", parsed.get("totalLength").asDouble());
            tl.put("confidence", "confirmed");
        } else {
            signals.putNull("totalLength");
        }

        // segmentLengths
        ArrayNode segLengths = signals.putArray("segmentLengths");
        JsonNode sections = parsed.path("sections");
        for (int i = 0; i < sections.size(); i++) {
            JsonNode sec = sections.get(i);
            ObjectNode sl = segLengths.addObject();
            sl.set("value", sec.get("length"));
            sl.put("confidence", mapConfidence(sec.path("lengthConfidence").asText("uncertain")));
            sl.put("position", sec.path("position").asText("S" + (i + 1)));
        }

        // diameters (grouped)
        ArrayNode diameters = signals.putArray("diameters");
        java.util.Map<Double, ObjectNode> diamGroups = new java.util.LinkedHashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            JsonNode sec = sections.get(i);
            if (sec.has("diameter") && !sec.get("diameter").isNull()) {
                double d = sec.get("diameter").asDouble();
                diamGroups.computeIfAbsent(d, k -> {
                    ObjectNode group = objectMapper.createObjectNode();
                    group.put("value", d);
                    group.put("confidence", mapConfidence(sec.path("diameterConfidence").asText("uncertain")));
                    group.putArray("segments");
                    return group;
                });
                ((ArrayNode) diamGroups.get(d).get("segments"))
                        .add(sec.path("position").asText("S" + (i + 1)));
            }
        }
        diamGroups.values().forEach(diameters::add);

        // hiddenFeatures
        ArrayNode hfArray = signals.putArray("hiddenFeatures");
        JsonNode hfs = parsed.path("hiddenFeatures");
        for (int i = 0; i < hfs.size(); i++) {
            JsonNode hf = hfs.get(i);
            ObjectNode hfNode = hfArray.addObject();
            hfNode.put("id", hf.path("id").asText("HF" + (i + 1)));
            hfNode.put("section", hf.path("section").asText());
            hfNode.put("type", hf.path("type").asText());
            hfNode.put("side", hf.path("side").asText());
            hfNode.put("confidence", "confirmed");
            if ("tap-bore".equals(hf.path("type").asText())) {
                hfNode.set("diameter", hf.get("diameter"));
                hfNode.set("depth", hf.get("depth"));
            } else if ("keyway".equals(hf.path("type").asText())) {
                hfNode.set("keywayWidth", hf.get("keywayWidth"));
                hfNode.set("keywayHeight", hf.get("keywayHeight"));
                hfNode.set("keywayDepth", hf.get("keywayDepth"));
            }
        }

        // tapSpecs
        ArrayNode tapSpecs = signals.putArray("tapSpecs");
        for (int i = 0; i < hfs.size(); i++) {
            JsonNode hf = hfs.get(i);
            if ("tap-bore".equals(hf.path("type").asText())) {
                ObjectNode ts = tapSpecs.addObject();
                ts.put("holeId", hf.path("id").asText("HF" + (i + 1)));
                ts.put("section", hf.path("section").asText());
                String spec = hf.has("spec") && !hf.get("spec").isNull() ? hf.get("spec").asText()
                        : (hf.has("diameter") ? "M" + hf.get("diameter").asInt() + " TAP" : null);
                if (spec != null) ts.put("spec", spec); else ts.putNull("spec");
                ts.put("specConf", "confirmed");
            }
        }

        // auxiliaryViews
        ArrayNode auxArray = signals.putArray("auxiliaryViews");
        JsonNode auxs = parsed.path("auxiliaryViews");
        for (int i = 0; i < auxs.size(); i++) {
            JsonNode aux = auxs.get(i);
            ObjectNode auxNode = auxArray.addObject();
            auxNode.put("id", aux.path("id").asText("AUX" + (i + 1)));
            auxNode.put("position", i == 0 ? "top-left" : "top-" + i);
            auxNode.put("label", "");

            ObjectNode shape = auxNode.putObject("shape");
            shape.put("type", aux.path("shape").asText("obround"));
            shape.put("width", aux.path("width").asDouble());
            shape.put("height", aux.path("height").asDouble());
            shape.put("confidence", "confirmed");

            ArrayNode dims = auxNode.putArray("dimensions");
            ObjectNode hDim = dims.addObject();
            hDim.put("axis", "horizontal");
            hDim.put("value", aux.path("width").asDouble());
            hDim.put("confidence", "confirmed");
            ObjectNode vDim = dims.addObject();
            vDim.put("axis", "vertical");
            vDim.put("value", aux.path("height").asDouble());
            vDim.put("confidence", "confirmed");

            auxNode.put("relatedSection", aux.path("relatedSection").asText());
            auxNode.put("projectionLines", true);
        }

        // chamfers
        ArrayNode chamfers = signals.putArray("chamfers");
        JsonNode chfs = parsed.path("chamfers");
        if (chfs.size() > 0) {
            for (int i = 0; i < chfs.size(); i++) {
                ObjectNode ch = chamfers.addObject();
                ch.put("side", chfs.get(i).path("side").asText());
                ch.set("spec", chfs.get(i).get("spec"));
                ch.put("confidence", chfs.get(i).has("spec") && !chfs.get(i).get("spec").isNull() ? "confirmed" : "uncertain");
            }
        } else {
            ObjectNode lch = chamfers.addObject(); lch.put("side", "left"); lch.putNull("spec"); lch.put("confidence", "uncertain");
            ObjectNode rch = chamfers.addObject(); rch.put("side", "right"); rch.putNull("spec"); rch.put("confidence", "uncertain");
        }

        // centerHoles
        ArrayNode centerHoles = signals.putArray("centerHoles");
        JsonNode chs = parsed.path("centerHoles");
        if (chs.size() > 0) {
            for (int i = 0; i < chs.size(); i++) {
                ObjectNode ch = centerHoles.addObject();
                ch.put("side", chs.get(i).path("side").asText());
                ch.set("diameter", chs.get(i).get("diameter"));
                ch.put("confidence", chs.get(i).has("diameter") && !chs.get(i).get("diameter").isNull() ? "confirmed" : "uncertain");
            }
        } else {
            ObjectNode lch = centerHoles.addObject(); lch.put("side", "left"); lch.putNull("diameter"); lch.put("confidence", "uncertain");
            ObjectNode rch = centerHoles.addObject(); rch.put("side", "right"); rch.putNull("diameter"); rch.put("confidence", "uncertain");
        }

        // material, surfaceFinish
        ObjectNode mat = signals.putObject("material");
        mat.set("value", parsed.has("material") ? parsed.get("material") : objectMapper.nullNode());
        mat.put("confidence", parsed.has("material") && !parsed.get("material").isNull() ? "confirmed" : "uncertain");

        ObjectNode sf = signals.putObject("surfaceFinish");
        sf.set("value", parsed.has("surfaceFinish") ? parsed.get("surfaceFinish") : objectMapper.nullNode());
        sf.put("confidence", parsed.has("surfaceFinish") && !parsed.get("surfaceFinish").isNull() ? "confirmed" : "uncertain");

        signals.putArray("holes");
        signals.putArray("slots");
        signals.putArray("keyways");
        signals.putArray("uncertainSignals");

        return signals;
    }

    private String mapConfidence(String conf) {
        return switch (conf) {
            case "confirmed" -> "confirmed";
            case "estimated" -> "estimated";
            default -> "uncertain";
        };
    }
}
