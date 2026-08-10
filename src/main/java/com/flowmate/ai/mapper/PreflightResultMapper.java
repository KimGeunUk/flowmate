package com.flowmate.ai.mapper;

import com.flowmate.ai.domain.PreflightRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code ai_preflight_result} 행 (설계서 §5.5, 계획서 5 Task 5).
 */
@Mapper
public interface PreflightResultMapper {

    /** WARN 판정만 저장한다(PreflightRecord 클래스 주석 참고). 생성된 PK 를 인자 객체에 채운다 */
    void insert(PreflightRecord record);

    /** '무시하고 상신'을 눌렀음을 기록한다 */
    void markIgnored(@Param("resultId") Long resultId);

    /** ignore 요청의 권한 검사에 쓴다 - approvalId 를 얻어야 문서 열람 권한을 다시 태울 수 있다 */
    PreflightRecord findById(@Param("resultId") Long resultId);
}
