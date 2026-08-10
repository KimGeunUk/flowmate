package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.RejectHistory;

@Mapper
public interface RejectHistoryMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(RejectHistory h);

    /**
     * ★ Phase 5 사전점검(계획서 5 Task 5)의 집계 원천 - {@code reason_category} 만
     * 읽어 온다. {@code reason_text}(반려 원문 - 사람 이름과 금액이 들어 있다)는 이
     * 쿼리의 SELECT 목록에 아예 없다. "프롬프트에 넣지 않는다"를 코드 리뷰나
     * 프롬프트 작성 규칙으로 지키는 대신, reason_text 가 애플리케이션 메모리에
     * 들어올 방법 자체를 없앤 것이다 - PreflightService 가 아무리 부주의해도
     * 여기서 가져오지 않은 값을 프롬프트에 넣을 수는 없다.
     *
     * 최근 N 건만 보는 이유(설계서 §6.4.6 ②(a)): 오래된 반려 패턴보다 최근 패턴이
     * "지금 이 부서·유형에서 자주 나는 문제"를 더 잘 반영한다.
     */
    List<String> findRecentReasonCategories(@Param("docType") String docType,
                                            @Param("deptId") Long deptId,
                                            @Param("limit") int limit);

    /**
     * 같은 doc_type + dept_id 조합에 반려 이력이 없을 때 전사로 확대한다
     * (설계서 §6.4.6 ②(a) "없으면 전사로 확대"). dept_id 조건만 뺀 같은 모양의 쿼리다.
     */
    List<String> findRecentReasonCategoriesCompanyWide(@Param("docType") String docType,
                                                        @Param("limit") int limit);
}
