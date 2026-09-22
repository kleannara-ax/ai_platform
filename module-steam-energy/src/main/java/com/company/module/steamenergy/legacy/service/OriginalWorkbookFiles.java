package com.company.module.steamenergy.legacy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 엑셀 서식(원본) 폴더를 찾는 공용 도우미.
 *
 * <p><b>왜 필요한가</b> — {@code Path.of("원본")} 은 <b>JVM 작업 디렉터리</b> 기준이다.
 * {@code gradlew :app:bootRun} 으로 띄우면 작업 디렉터리가 {@code app/} 이라
 * {@code app/원본} 을 찾다가 실패하고, 업로드·다운로드가 500 으로 떨어졌다.
 * jar 로 띄우면 또 다른 자리가 되므로 작업 디렉터리에 기대면 안 된다.
 *
 * <p>그래서 작업 디렉터리에서 <b>위로 올라가며</b> {@code 원본} 폴더를 찾는다.
 * 운영에서 다른 경로에 두려면 {@code -Dsteam.original-dir=/data/aiplatform/원본} 으로
 * 지정할 수 있다(지정하면 그 경로만 쓴다).
 */
public final class OriginalWorkbookFiles {

    /** 운영 배포에서 서식 폴더 위치를 직접 지정할 때 쓰는 시스템 속성. */
    public static final String DIR_PROPERTY = "steam.original-dir";

    private static final String DIR_NAME = "원본";
    private static final int MAX_DEPTH = 6;

    /** 서식 폴더. 못 찾으면 어디를 뒤졌는지 알 수 있게 메시지에 남긴다. */
    public static Path dir() throws IOException {
        String configured = System.getProperty(DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isDirectory(path)) return path;
            throw new IOException(DIR_PROPERTY + " 로 지정한 폴더가 없습니다: " + path.toAbsolutePath());
        }

        Path base = Path.of("").toAbsolutePath();
        StringBuilder tried = new StringBuilder();
        for (int depth = 0; depth < MAX_DEPTH && base != null; depth += 1) {
            Path candidate = base.resolve(DIR_NAME);
            if (Files.isDirectory(candidate)) return candidate;
            tried.append(depth == 0 ? "" : ", ").append(candidate);
            base = base.getParent();
        }
        throw new IOException("'" + DIR_NAME + "' 폴더를 찾지 못했습니다. 찾아본 경로: " + tried
                + " (다른 곳에 두었다면 -D" + DIR_PROPERTY + " 로 지정하세요)");
    }

    /**
     * 서식 폴더에서 조건에 맞는 파일 하나를 고른다.
     *
     * <p>엑셀 임시 파일({@code ~$...})은 항상 제외하고, 후보가 여럿이면 <b>이름 내림차순</b>으로
     * 첫 번째를 쓴다(같은 서식의 최신 연도판을 고르기 위해서다).
     *
     * @param nameMatches 파일 이름 조건
     * @param what        못 찾았을 때 메시지에 넣을 설명 (예: "에너지 회계비용")
     */
    public static Path find(Predicate<String> nameMatches, String what) throws IOException {
        Path dir = dir();
        try (Stream<Path> paths = Files.list(dir)) {
            Optional<Path> found = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.startsWith("~$") && nameMatches.test(name);
                    })
                    .max(Comparator.comparing(path -> path.getFileName().toString()));
            if (found.isEmpty()) {
                throw new IOException("'" + dir + "' 에서 " + what + " 서식 파일을 찾지 못했습니다.");
            }
            return found.get();
        }
    }

    private OriginalWorkbookFiles() {
    }
}
