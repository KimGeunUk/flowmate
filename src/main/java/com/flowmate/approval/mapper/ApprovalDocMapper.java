package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalBoxCounts;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalSearchCond;

/**
 * ★ 이 매퍼에 캐시(@Cacheable 등)를 붙이지 않는다.
 *   이유는 하나다 — 조회 결과 객체를 여러 요청이 공유하면
 *   한쪽에서 상태를 전이시킨 것이 다른 요청에 보인다.
 */
@Mapper
public interface ApprovalDocMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(ApprovalDoc doc);

    /** 임시저장 문서의 내용 수정 */
    void update(ApprovalDoc doc);

    /** 상태 전이 결과만 갱신 (status, current_step, submitted_at, completed_at) */
    void updateStatus(ApprovalDoc doc);

    /** 기안자·부서 조인 포함. 없으면 null */
    ApprovalDoc findById(@Param("approvalId") Long approvalId);

    /**
     * 동시 결재 방지용 행 잠금 조회.
     * 두 결재자가 같은 문서를 동시에 누르면 둘 다 승인 처리되어 단계가 어긋난다.
     */
    ApprovalDoc findByIdForUpdate(@Param("approvalId") Long approvalId);

    /** 같은 접두사·연도의 최대 일련번호. 없으면 0 */
    int maxDocNoSeq(@Param("prefix") String prefix, @Param("year") int year);

    /**
     * 같은 접두사·연도의 문서번호 채번을 트랜잭션 단위로 직렬화한다.
     *
     * 자문 잠금(advisory lock)은 트랜잭션이 끝날 때 자동으로 풀리므로 해제를 잊을 수 없다.
     * 이 잠금을 잡은 뒤 maxDocNoSeq + insert 를 하면 같은 유형·연도에서
     * 두 트랜잭션이 같은 번호를 만들 수 없다.
     */
    int lockDocNoSeq(@Param("key") String key);

    /** 내 결재함 목록 */
    List<ApprovalDoc> searchBox(ApprovalSearchCond cond);

    /** 내 결재함 건수 */
    long countBox(ApprovalSearchCond cond);

    /** 탭 4종의 건수. 검색 조건은 적용하지 않는다 — ApprovalBoxCounts 주석 참조 */
    ApprovalBoxCounts countBoxTabs(@Param("empId") Long empId);
}
