package com.flowmate.attendance.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.flowmate.attendance.domain.WorkTimeResult;

/**
 * ★ 커스터마이징 지점 2 — 회사마다 근태 판정 기준(출퇴근 시각 고정 여부)이 다르다.
 *
 * ApprovalLinePolicy(지점 1)와 같은 이유로 순수 로직으로 둔다: 매퍼를 주입받지
 * 않고 이미 조회된 시각만으로 판정하면 DB 없이 단위 테스트할 수 있다.
 */
public interface WorkTimePolicy {

    /**
     * 하루치 출퇴근을 판정한다.
     *
     * @param checkIn  출근 시각. null 이 될 수 없다
     * @param checkOut 퇴근 시각. 아직 퇴근하지 않았으면 null — 이 경우 판정을
     *                 유보한다({@link WorkTimeResult#getStatus()} 가 null)
     * @param date     근무일. 정책에 따라 요일 등을 참조할 수 있어 별도로 받는다
     * @return 근무시간·연장근무·상태
     */
    WorkTimeResult evaluate(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date);
}
