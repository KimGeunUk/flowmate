package com.flowmate.approval.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.flowmate.approval.domain.ApprovalAttachment;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.mapper.ApprovalAttachmentMapper;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.approval.service.AttachmentStorage;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.FlowMateException;
import com.flowmate.org.security.LoginEmployee;

/**
 * 첨부파일 업로드·다운로드·삭제.
 *
 * 업로드·삭제는 기안자 본인 + 임시저장 상태로 제한한다 — ApprovalDoc.isEditable() 이
 * 그 판정이다. 다운로드는 문서 조회 권한을 그대로 재사용한다: ApprovalQueryService.findDoc
 * 이 기안자이거나 결재선에 있는 사람만 통과시키므로, 첨부파일 id 만 알아도 문서를 볼 권한이
 * 없는 사람에게는 내려주지 않는다.
 */
@Controller
@RequestMapping("/approval")
public class AttachmentController {

    private final ApprovalAttachmentMapper attachmentMapper;
    private final ApprovalQueryService queryService;
    private final AttachmentStorage storage;

    public AttachmentController(ApprovalAttachmentMapper attachmentMapper,
                                ApprovalQueryService queryService,
                                AttachmentStorage storage) {
        this.attachmentMapper = attachmentMapper;
        this.queryService = queryService;
        this.storage = storage;
    }

    @PostMapping("/{approvalId}/attach")
    public String upload(@PathVariable Long approvalId,
                         @RequestParam("file") MultipartFile file,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        requireEditableByDrafter(approvalId, loginEmployee.getEmpId());

        if (!file.isEmpty()) {
            String relativePath = storage.store(file);

            ApprovalAttachment attachment = new ApprovalAttachment();
            attachment.setApprovalId(approvalId);
            attachment.setFileName(file.getOriginalFilename());
            attachment.setFilePath(relativePath);
            attachment.setFileSize(file.getSize());
            attachment.setUploadedAt(LocalDateTime.now());
            attachmentMapper.insert(attachment);
        }
        return "redirect:/approval/write?approvalId=" + approvalId;
    }

    /** 문서 조회 권한 재사용 — 첨부파일만 따로 열 수 있으면 안 된다 */
    @GetMapping("/attach/{attachId}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long attachId,
                                                        @AuthenticationPrincipal LoginEmployee loginEmployee) {
        ApprovalAttachment attachment = requireAttachment(attachId);
        queryService.findDoc(attachment.getApprovalId(), loginEmployee.getEmpId());

        // filename*=UTF-8'' 형식을 쓰는 이유: 구형 filename="..." 는 한글이 브라우저마다 깨진다.
        // +를 %20으로 바꾸는 것은 URLEncoder 가 공백을 +로 만들지만 HTTP 헤더에서는 %20이어야 하기 때문이다.
        String encoded = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        // Content-Type 을 명시하지 않으면 협상 결과가 첨부 종류에 따라 들쭉날쭉해진다.
        // 어차피 Content-Disposition: attachment 가 저장 대화상자를 강제하므로
        // 항상 application/octet-stream 으로 내려 브라우저의 콘텐츠 스니핑을 배제한다.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentLength(attachment.getFileSize())
                .body(new FileSystemResource(storage.resolve(attachment.getFilePath())));
    }

    @PostMapping("/attach/{attachId}/delete")
    public String delete(@PathVariable Long attachId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        ApprovalAttachment attachment = requireAttachment(attachId);
        requireEditableByDrafter(attachment.getApprovalId(), loginEmployee.getEmpId());

        // DB 행을 먼저 지운다. 파일 삭제가 실패해도 다운로드 링크가 죽은 DB 행을
        // 가리키는 것보다는, 디스크에 쓸모없는 파일 하나가 남는 쪽이 덜 해롭다.
        attachmentMapper.delete(attachId);
        storage.delete(attachment.getFilePath());
        return "redirect:/approval/write?approvalId=" + attachment.getApprovalId();
    }

    private ApprovalDoc requireEditableByDrafter(Long approvalId, Long actorId) {
        ApprovalDoc doc = queryService.findDoc(approvalId, actorId);
        if (!Objects.equals(doc.getDrafterId(), actorId)) {
            throw new ApprovalAccessDeniedException("기안자만 첨부파일을 처리할 수 있습니다");
        }
        if (!doc.isEditable()) {
            throw new ApprovalAccessDeniedException(
                    "임시저장 상태에서만 첨부파일을 처리할 수 있습니다: " + doc.getStatus());
        }
        return doc;
    }

    private ApprovalAttachment requireAttachment(Long attachId) {
        ApprovalAttachment attachment = attachmentMapper.findById(attachId);
        if (attachment == null) {
            throw new FlowMateException("첨부파일을 찾을 수 없습니다: " + attachId);
        }
        return attachment;
    }
}
