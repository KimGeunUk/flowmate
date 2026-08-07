package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalLine;

@Mapper
public interface ApprovalLineMapper {

    /** 결재선 전체를 한 번에 저장한다 */
    void insertAll(@Param("lines") List<ApprovalLine> lines);

    /** 문서의 결재선을 전부 지운다. 임시저장 수정 시 다시 만들기 전에 부른다 */
    void deleteByApprovalId(@Param("approvalId") Long approvalId);

    /** 결재자 이름·직급·부서 조인 포함. step_no 순 */
    List<ApprovalLine> findByApprovalId(@Param("approvalId") Long approvalId);

    /** 특정 단계 하나. 없으면 null */
    ApprovalLine findStep(@Param("approvalId") Long approvalId, @Param("stepNo") int stepNo);

    /** 단계 처리 결과 갱신 (status, comment, processed_at) */
    void updateStep(ApprovalLine line);

    /** WAITING 단계를 CURRENT 로 올린다 */
    void activateStep(@Param("approvalId") Long approvalId, @Param("stepNo") int stepNo);

    /** 반려 시 남은 단계를 전부 SKIPPED 로 만든다 */
    void skipRemaining(@Param("approvalId") Long approvalId, @Param("fromStepNo") int fromStepNo);

    /** 결재선 길이. 상태 전이의 totalStep 으로 쓴다 */
    int countByApprovalId(@Param("approvalId") Long approvalId);
}
