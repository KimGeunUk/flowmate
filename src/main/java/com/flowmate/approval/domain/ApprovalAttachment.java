package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 첨부파일 한 건.
 *
 * fileName 은 사용자가 올린 원본 파일명, filePath 는 AttachmentStorage.store() 가 돌려준
 * baseDir 기준 상대 경로다 — 디스크에는 UUID 로 저장되므로 둘은 다르다.
 */
public class ApprovalAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long attachId;
    private Long approvalId;
    private String fileName;
    private String filePath;
    private long fileSize;
    private LocalDateTime uploadedAt;

    /**
     * 화면 표시용 크기.
     *
     * JSP 가 ${file.fileSizeLabel} 로 읽는다. 단위 변환을 EL 안에 두면 나눗셈이
     * 부동소수로 떨어져 표시가 어색해지므로 도메인 객체에 파생 getter 로 둔다.
     */
    public String getFileSizeLabel() {
        return (this.fileSize / 1024) + " KB";
    }

    public Long getAttachId() {
        return attachId;
    }

    public void setAttachId(Long attachId) {
        this.attachId = attachId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
