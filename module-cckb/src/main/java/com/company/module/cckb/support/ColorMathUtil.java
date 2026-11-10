package com.company.module.cckb.support;

import java.awt.image.BufferedImage;

/**
 * CCKB 색상 판정 핵심 수학 로직 — 원본 "CCKB 후면 색상 검사 시스템_V4.html" 의 JS 구현을
 * 1:1로 Java 로 포팅한 것 (값/상수/연산 순서 모두 동일하게 유지하여 결과가 바뀌지 않도록 함).
 *
 * <p>포팅 대상 JS 함수 매핑:
 * <ul>
 *   <li>{@code rgbToLab/rgbToXyz/rgbPivot/xyzPivot} → {@link #rgbToLab(int,int,int)}</li>
 *   <li>{@code averageLabFromImage} → {@link #averageLab(BufferedImage)}</li>
 *   <li>{@code deltaE2000/hp/deltaHp/avgHue/deg2rad/rad2deg} → {@link #deltaE2000(double[], double[])}</li>
 *   <li>{@code classifyResult} → {@link #classify(double, double, double, double, Thresholds)}</li>
 * </ul>
 */
public final class ColorMathUtil {

    private ColorMathUtil() {
    }

    // ────────────────────────────────────────────────────────────
    // RGB → Lab (D65, sRGB) — 원본 rgbToXyz/xyzPivot/rgbPivot/rgbToLab
    // ────────────────────────────────────────────────────────────

    private static final double XN = 95.047;
    private static final double YN = 100.0;
    private static final double ZN = 108.883;

    private static double rgbPivot(double v) {
        v = v / 255.0;
        return v > 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }

    private static double xyzPivot(double v) {
        return v > 0.008856 ? Math.cbrt(v) : (7.787 * v) + (16.0 / 116.0);
    }

    /** 반환: {L, a, b} */
    public static double[] rgbToLab(int r, int g, int b) {
        double rl = rgbPivot(r);
        double gl = rgbPivot(g);
        double bl = rgbPivot(b);

        double x = rl * 0.4124564 + gl * 0.3575761 + bl * 0.1804375;
        double y = rl * 0.2126729 + gl * 0.7151522 + bl * 0.0721750;
        double z = rl * 0.0193339 + gl * 0.1191920 + bl * 0.9503041;

        x = (x * 100.0) / XN;
        y = (y * 100.0) / YN;
        z = (z * 100.0) / ZN;

        double fx = xyzPivot(x);
        double fy = xyzPivot(y);
        double fz = xyzPivot(z);

        double L = (116.0 * fy) - 16.0;
        double a = 500.0 * (fx - fy);
        double bVal = 200.0 * (fy - fz);

        return new double[]{L, a, bVal};
    }

    /**
     * 이미지 전체 픽셀의 평균 Lab 값을 구한다.
     * 원본 JS averageLabFromImage()의 적응형 스텝 샘플링(step = max(1, floor(sqrt(totalPixels/60000))))
     * 로직을 그대로 포팅 — 이미지가 클수록 더 듬성듬성 샘플링해 속도를 확보한다.
     * 알파가 0인 픽셀(투명)은 건너뛴다.
     */
    public static double[] averageLab(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long totalPixels = (long) width * height;
        int step = (int) Math.max(1, Math.floor(Math.sqrt(totalPixels / 60000.0)));

        double sumL = 0, sumA = 0, sumB = 0;
        long count = 0;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double[] lab = rgbToLab(r, g, b);
                sumL += lab[0];
                sumA += lab[1];
                sumB += lab[2];
                count++;
            }
        }

        if (count == 0) {
            return new double[]{0, 0, 0};
        }
        return new double[]{sumL / count, sumA / count, sumB / count};
    }

    // ────────────────────────────────────────────────────────────
    // CIEDE2000 — 원본 deltaE2000/hp/deltaHp/avgHue/deg2rad/rad2deg
    // ────────────────────────────────────────────────────────────

    private static double deg2rad(double deg) {
        return deg * (Math.PI / 180.0);
    }

    private static double rad2deg(double rad) {
        return rad * (180.0 / Math.PI);
    }

    private static double hp(double ap, double b) {
        if (ap == 0 && b == 0) {
            return 0;
        }
        double hue = rad2deg(Math.atan2(b, ap));
        return hue >= 0 ? hue : hue + 360;
    }

    private static double deltaHp(double c1p, double c2p, double h1p, double h2p) {
        if (c1p * c2p == 0) {
            return 0;
        }
        if (Math.abs(h2p - h1p) <= 180) {
            return h2p - h1p;
        }
        return (h2p <= h1p) ? (h2p - h1p + 360) : (h2p - h1p - 360);
    }

    private static double avgHue(double c1p, double c2p, double h1p, double h2p) {
        if (c1p * c2p == 0) {
            return h1p + h2p;
        }
        if (Math.abs(h1p - h2p) <= 180) {
            return (h1p + h2p) / 2.0;
        }
        return (h1p + h2p < 360) ? (h1p + h2p + 360) / 2.0 : (h1p + h2p - 360) / 2.0;
    }

    /**
     * CIEDE2000 색차(ΔE00) 계산. lab1/lab2 = {L, a, b}.
     * kL=kC=kH=1 고정 (원본과 동일).
     */
    public static double deltaE2000(double[] lab1, double[] lab2) {
        double l1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double l2 = lab2[0], a2 = lab2[1], b2 = lab2[2];

        double c1 = Math.sqrt(a1 * a1 + b1 * b1);
        double c2 = Math.sqrt(a2 * a2 + b2 * b2);
        double avgC = (c1 + c2) / 2.0;

        double g = 0.5 * (1 - Math.sqrt(Math.pow(avgC, 7) / (Math.pow(avgC, 7) + Math.pow(25, 7))));
        double a1p = (1 + g) * a1;
        double a2p = (1 + g) * a2;

        double c1p = Math.sqrt(a1p * a1p + b1 * b1);
        double c2p = Math.sqrt(a2p * a2p + b2 * b2);
        double avgCp = (c1p + c2p) / 2.0;

        double h1p = hp(a1p, b1);
        double h2p = hp(a2p, b2);

        double dLp = l2 - l1;
        double dCp = c2p - c1p;

        double dhp = deltaHp(c1p, c2p, h1p, h2p);
        double dHp = 2 * Math.sqrt(c1p * c2p) * Math.sin(deg2rad(dhp / 2.0));

        double avgHp = avgHue(c1p, c2p, h1p, h2p);
        double avgLp = (l1 + l2) / 2.0;

        double t = 1 - 0.17 * Math.cos(deg2rad(avgHp - 30))
                + 0.24 * Math.cos(deg2rad(2 * avgHp))
                + 0.32 * Math.cos(deg2rad(3 * avgHp + 6))
                - 0.20 * Math.cos(deg2rad(4 * avgHp - 63));

        double dTheta = 30 * Math.exp(-Math.pow((avgHp - 275) / 25.0, 2));
        double rc = 2 * Math.sqrt(Math.pow(avgCp, 7) / (Math.pow(avgCp, 7) + Math.pow(25, 7)));
        double sl = 1 + (0.015 * Math.pow(avgLp - 50, 2)) / Math.sqrt(20 + Math.pow(avgLp - 50, 2));
        double sc = 1 + 0.045 * avgCp;
        double sh = 1 + 0.015 * avgCp * t;
        double rt = -Math.sin(deg2rad(2 * dTheta)) * rc;

        double kl = 1, kc = 1, kh = 1;

        return Math.sqrt(
                Math.pow(dLp / (kl * sl), 2)
                        + Math.pow(dCp / (kc * sc), 2)
                        + Math.pow(dHp / (kh * sh), 2)
                        + rt * (dCp / (kc * sc)) * (dHp / (kh * sh))
        );
    }

    // ────────────────────────────────────────────────────────────
    // 판정 분류 — 원본 classifyResult()
    // ────────────────────────────────────────────────────────────

    /** 판정 임계값 묶음 (InspectionStandard 엔티티에서 변환해 사용) */
    public record Thresholds(double normalDe, double normalL, double normalA, double normalB,
                              double defectDe, double defectL, double defectA, double defectB) {
    }

    /** 판정 결과 — code: NORMAL/BOUNDARY/DEFECT, reason: 원본 message 문구 */
    public record JudgmentResult(String code, String reason) {
    }

    /**
     * 원본 classifyResult(dE00, dL, dA, dB, t) 그대로 포팅.
     * <pre>
     *   normalFailures: dE00 >= normalDE, |dL|>normalL, |dA|>normalA, |dB|>normalB
     *   defectFailures: dE00 >  defectDE, |dL|>defectL, |dA|>defectA, |dB|>defectB
     *   normalFailures 없음           → NORMAL
     *   defectFailures 1개 이상       → DEFECT
     *   그 외(normal 위반 but defect 미달) → BOUNDARY
     * </pre>
     */
    public static JudgmentResult classify(double dE00, double dL, double dA, double dB, Thresholds t) {
        double absL = Math.abs(dL), absA = Math.abs(dA), absB = Math.abs(dB);

        java.util.List<String> normalFailures = new java.util.ArrayList<>();
        java.util.List<String> defectFailures = new java.util.ArrayList<>();

        if (dE00 >= t.normalDe()) normalFailures.add("ΔE00");
        if (absL > t.normalL()) normalFailures.add("|ΔL*|");
        if (absA > t.normalA()) normalFailures.add("|Δa*|");
        if (absB > t.normalB()) normalFailures.add("|Δb*|");

        if (dE00 > t.defectDe()) defectFailures.add("ΔE00");
        if (absL > t.defectL()) defectFailures.add("|ΔL*|");
        if (absA > t.defectA()) defectFailures.add("|Δa*|");
        if (absB > t.defectB()) defectFailures.add("|Δb*|");

        if (normalFailures.isEmpty()) {
            return new JudgmentResult("NORMAL", "모든 값이 정상 기준 이내입니다.");
        }
        if (!defectFailures.isEmpty()) {
            return new JudgmentResult("DEFECT", String.join(", ", defectFailures) + "이 기준을 벗어나 불량입니다.");
        }
        return new JudgmentResult("BOUNDARY", String.join(", ", normalFailures) + "이 정상 기준을 벗어나 경계입니다.");
    }
}
