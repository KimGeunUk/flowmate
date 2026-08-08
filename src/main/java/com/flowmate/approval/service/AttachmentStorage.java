package com.flowmate.approval.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.flowmate.common.exception.FlowMateException;

/**
 * 첨부파일 디스크 저장·삭제.
 *
 * 원본 파일명을 디스크에 쓰지 않고 UUID 로 저장하는 이유 세 가지:
 *   1. 경로 조작 — 원본명에 ../ 가 들어오면 임의 위치에 쓸 수 있다
 *   2. 중복 — 같은 이름을 올리면 덮어써진다
 *   3. 한글·특수문자 파일명이 OS·파일시스템마다 다르게 처리된다
 *
 * 원본명은 approval_attachment.file_name 에만 두고 다운로드 시 헤더로 되돌린다.
 */
@Service
public class AttachmentStorage {

    private final Path baseDir;

    public AttachmentStorage(@Value("${flowmate.upload.base-dir}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    /**
     * 파일을 저장하고 baseDir 기준 상대 경로를 돌려준다.
     * DB 에는 상대 경로를 넣는다 — 배포 환경마다 baseDir 이 달라지기 때문이다.
     */
    public String store(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        LocalDate today = LocalDate.now();
        Path dir = baseDir.resolve(String.format("approval/%d/%02d",
                today.getYear(), today.getMonthValue()));
        String stored = UUID.randomUUID() + ext;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(stored));
            }
        } catch (IOException e) {
            throw new FlowMateException("파일을 저장할 수 없습니다: " + original);
        }
        return baseDir.relativize(dir.resolve(stored)).toString().replace('\\', '/');
    }

    /**
     * 상대 경로로 실제 파일 경로를 돌려준다.
     *
     * normalize 후 baseDir 밖을 가리키면 거부한다 — DB 값이 오염되었을 때의 방어다.
     */
    public Path resolve(String relativePath) {
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            throw new FlowMateException("허용되지 않은 경로입니다");
        }
        return target;
    }

    /**
     * 디스크에서 지운다. 이미 없어도 조용히 넘어간다 —
     * DB 행을 먼저 지우고 나서 부르므로, 파일이 이미 지워졌더라도 DB 는 일관된 상태다.
     */
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            throw new FlowMateException("파일을 삭제할 수 없습니다: " + relativePath);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase();
    }
}
