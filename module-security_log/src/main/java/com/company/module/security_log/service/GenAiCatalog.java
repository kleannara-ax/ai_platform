package com.company.module.security_log.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 생성형 AI 서비스 판정 (DLP 유출 로그 → 보고서 '생성형 AI 사용현황 / 업로드 파일 내역')
 * <ol>
 *   <li>상세 내용·내용·서브 채널에 있는 접속 주소(도메인)가 아래 목록에 있으면 그 서비스</li>
 *   <li>없으면 원본 'AI' 열 값 (빈 값·'기타'·'없음' 제외)을 서비스 이름으로 사용 (목록에 있는 이름이면 표준 이름으로 맞춤)</li>
 * </ol>
 * - 목록: 대화형·검색형·코딩·문서/발표·이미지·영상·음성·번역·회의록·요약 등 주요 생성형 AI (국내 서비스 포함)
 * - 도메인은 하위 도메인까지 일치 (예: chat.openai.com → openai.com 규칙)
 */
public final class GenAiCatalog {

    private GenAiCatalog() {
    }

    /** 도메인(접미사) → 서비스 이름. 긴(구체적인) 도메인을 먼저 검사 */
    private static final Map<String, String> DOMAINS = new LinkedHashMap<>();
    /** 'AI' 열 값 → 표준 이름 (소문자, 공백 제거) */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Pattern HOST = Pattern.compile(
            "(?i)(?:https?://)?((?:[a-z0-9-]+\\.)+[a-z]{2,})(?=[/:?#\\s'\"\\)]|$)");

    private static void add(String name, String... domains) {
        for (String d : domains) {
            DOMAINS.put(d.toLowerCase(Locale.ROOT), name);
        }
        NAMES.put(key(name), name);
    }

    static {
        // ---- 대화형 · 검색형 (구체적인 하위 도메인 먼저)
        add("Gemini", "gemini.google.com", "bard.google.com", "aistudio.google.com", "notebooklm.google.com", "notebooklm.google");
        add("Microsoft Copilot", "copilot.microsoft.com", "copilot.cloud.microsoft", "m365copilot.com", "sydney.bing.com");
        add("GitHub Copilot", "githubcopilot.com", "copilot-proxy.githubusercontent.com");
        add("Naver CLOVA X", "clova-x.naver.com", "clova.ai", "cue.search.naver.com");
        add("CLOVA Note", "clovanote.naver.com");
        add("Papago", "papago.naver.com", "papago.naver.net");
        add("Meta AI", "meta.ai");
        add("Hugging Face Chat", "huggingface.co");
        add("ChatGPT", "chatgpt.com", "chat.openai.com", "openai.com", "oaiusercontent.com", "sora.com");
        add("Claude", "claude.ai", "anthropic.com");
        add("Perplexity", "perplexity.ai", "pplx.ai");
        add("DeepSeek", "deepseek.com", "deepseek.ai");
        add("Grok", "grok.com", "x.ai");
        add("Mistral Le Chat", "mistral.ai");
        add("뤼튼(wrtn)", "wrtn.ai", "wrtn.io");
        add("AskUp", "askup.upstage.ai", "upstage.ai");
        add("Liner", "liner.com", "getliner.com");
        add("Genspark", "genspark.ai");
        add("Manus", "manus.im");
        add("Poe", "poe.com");
        add("You.com", "you.com");
        add("Character.AI", "character.ai");
        add("Pi", "pi.ai", "inflection.ai");
        add("Phind", "phind.com");
        add("Felo", "felo.ai");
        add("Kimi", "kimi.ai", "kimi.com", "moonshot.cn");
        add("Qwen", "qwen.ai", "tongyi.aliyun.com", "qianwen.aliyun.com", "chat.qwen.ai");
        add("ChatGLM", "chatglm.cn", "zhipuai.cn", "z.ai");
        add("Doubao", "doubao.com");
        add("ERNIE Bot", "yiyan.baidu.com");
        add("Blackbox AI", "blackbox.ai");
        add("Cursor", "cursor.com", "cursor.sh");
        add("Replit AI", "replit.com");
        add("v0", "v0.dev", "v0.app");
        add("Lovable", "lovable.dev");
        add("Bolt", "bolt.new");
        add("Tabnine", "tabnine.com");
        add("Codeium", "codeium.com", "windsurf.com");
        // ---- 문서 · 발표 · 글쓰기 · 요약
        add("Gamma", "gamma.app");
        add("Tome", "tome.app");
        add("Napkin AI", "napkin.ai");
        add("Notion AI", "notion.ai");
        add("ChatPDF", "chatpdf.com");
        add("Humata", "humata.ai");
        add("SciSpace", "typeset.io", "scispace.com");
        add("Consensus", "consensus.app");
        add("Elicit", "elicit.com", "elicit.org");
        add("QuillBot", "quillbot.com");
        add("Grammarly", "grammarly.com");
        add("Jasper", "jasper.ai");
        add("Copy.ai", "copy.ai");
        add("Writesonic", "writesonic.com");
        add("Monica", "monica.im");
        add("Sider", "sider.ai");
        add("Merlin", "getmerlin.in");
        add("Tactiq", "tactiq.io");
        add("Otter.ai", "otter.ai");
        add("Fireflies", "fireflies.ai");
        add("tl;dv", "tldv.io");
        add("다글로", "daglo.ai");
        add("DeepL", "deepl.com");
        // ---- 이미지 · 영상 · 음성
        add("Midjourney", "midjourney.com");
        add("Adobe Firefly", "firefly.adobe.com");
        add("Ideogram", "ideogram.ai");
        add("Leonardo AI", "leonardo.ai");
        add("Stable Diffusion", "stability.ai", "dreamstudio.ai", "clipdrop.co");
        add("Krea", "krea.ai");
        add("Runway", "runwayml.com", "runway.ml");
        add("Pika", "pika.art");
        add("Luma AI", "lumalabs.ai");
        add("Kling AI", "klingai.com");
        add("HeyGen", "heygen.com");
        add("Synthesia", "synthesia.io");
        add("D-ID", "d-id.com");
        add("Suno", "suno.com", "suno.ai");
        add("Udio", "udio.com");
        add("ElevenLabs", "elevenlabs.io");
        add("Typecast", "typecast.ai");
        add("Vrew", "vrew.ai", "vrew.voyagerx.com");
        // 'AI' 열 값으로만 들어오는 흔한 표기
        NAMES.put(key("OpenAI"), "ChatGPT");
        NAMES.put(key("Chat GPT"), "ChatGPT");
        NAMES.put(key("Bard"), "Gemini");
        NAMES.put(key("Google Gemini"), "Gemini");
        NAMES.put(key("Copilot"), "Microsoft Copilot");
        NAMES.put(key("Bing Chat"), "Microsoft Copilot");
        NAMES.put(key("ClovaX"), "Naver CLOVA X");
        NAMES.put(key("Clova X"), "Naver CLOVA X");
        NAMES.put(key("뤼튼"), "뤼튼(wrtn)");
        NAMES.put(key("wrtn"), "뤼튼(wrtn)");
    }

    /** 생성형 AI 아님으로 보는 'AI' 열 값 */
    private static final List<String> NOT_AI = List.of("", "기타", "없음", "해당없음", "-", "none", "n/a", "na", "etc", "0");

    /**
     * 생성형 AI 서비스 이름 (아니면 null)
     *
     * @param aiColumn 원본 'AI' 열 값
     * @param texts    상세 내용·내용·서브 채널 등 (접속 주소가 들어 있는 칸)
     */
    public static String detect(String aiColumn, String... texts) {
        for (String t : texts) {
            String s = byUrl(t);
            if (s != null) {
                return s;
            }
        }
        if (aiColumn == null) {
            return null;
        }
        String v = aiColumn.trim();
        if (NOT_AI.contains(v.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String std = NAMES.get(key(v));
        return std != null ? std : (v.length() > 100 ? v.substring(0, 100) : v);
    }

    /** 글 안의 도메인 → 서비스 이름 */
    static String byUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = HOST.matcher(text);
        List<String> hosts = new ArrayList<>();
        while (m.find()) {
            hosts.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        for (String h : hosts) {
            for (Map.Entry<String, String> e : DOMAINS.entrySet()) {
                String d = e.getKey();
                if (h.equals(d) || h.endsWith("." + d)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    /** 목록에 있는 서비스 수 (안내용) */
    public static int size() {
        return (int) DOMAINS.values().stream().distinct().count();
    }

    private static String key(String v) {
        return v.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
