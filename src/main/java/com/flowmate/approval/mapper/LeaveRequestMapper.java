package com.flowmate.approval.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.LeaveRequest;

/**
 * 연차 신청서 확장 테이블. approval_doc 을 PK 로 참조하는 1:1 관계다.
 *
 * upsert 를 쓰는 이유: 임시저장을 반복 수정하면 문서는 있고 확장 행은 없는
 * 상태(최초 저장)와 둘 다 있는 상태(수정)가 섞인다. INSERT/UPDATE 를 분기하는
 * 대신 ON CONFLICT 로 DB 안에서 해결한다 (계획서 4 D6과 같은 방식 — 여기서는
 * 제약 위반을 피하는 용도가 아니라 분기 자체를 없애는 용도다).
 */
@Mapper
public interface LeaveRequestMapper {

    void upsert(LeaveRequest leaveRequest);

    LeaveRequest findByApprovalId(@Param("approvalId") Long approvalId);

    /** docType 이 LEAVE 에서 다른 유형으로 바뀐 임시저장 수정에서 쓴다 */
    void deleteByApprovalId(@Param("approvalId") Long approvalId);
}
