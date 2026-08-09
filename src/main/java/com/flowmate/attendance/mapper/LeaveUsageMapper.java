package com.flowmate.attendance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.attendance.domain.LeaveUsage;

/**
 * 연차 반영 이력. approval_id 가 UNIQUE 다 (계획서 4 D2 — 중복 반영 방지).
 *
 * ★ existsByApprovalId 를 먼저 불러 분기한다. UNIQUE 제약 위반을 잡아서
 * 판단하지 않는다 — PostgreSQL 은 제약 위반 시 트랜잭션 전체를 중단 상태로
 * 만들어 이후 모든 쿼리가 25P02 로 죽는다. 이 매퍼는 ApprovalService.approve()
 * 의 트랜잭션 안에서 쓰이므로, 여기서 제약 위반을 잡으면 승인까지 같이
 * 죽는다. UNIQUE 제약은 그래도 남긴다 — 최후 방어선이다.
 */
@Mapper
public interface LeaveUsageMapper {

    boolean existsByApprovalId(@Param("approvalId") Long approvalId);

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(LeaveUsage usage);

    /** 테스트 검증용. 화면 경로에서는 쓰지 않는다 */
    LeaveUsage findByApprovalId(@Param("approvalId") Long approvalId);
}
