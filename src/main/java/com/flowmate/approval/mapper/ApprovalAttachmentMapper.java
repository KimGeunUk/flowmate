package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalAttachment;

@Mapper
public interface ApprovalAttachmentMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(ApprovalAttachment a);

    /** 문서 하나의 첨부 목록. 업로드 순 */
    List<ApprovalAttachment> findByApprovalId(@Param("approvalId") Long approvalId);

    ApprovalAttachment findById(@Param("attachId") Long attachId);

    void delete(@Param("attachId") Long attachId);
}
