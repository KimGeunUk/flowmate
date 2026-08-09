package com.flowmate.attendance.mapper;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.attendance.domain.Attendance;

/**
 * 일별 근태. Task 6(연차 반영)은 upsertForLeave 만 쓴다 — 출퇴근 등록(Task 3)의
 * 메서드는 이 매퍼가 아니라 이후 Task에서 추가한다.
 */
@Mapper
public interface AttendanceMapper {

    /**
     * 연차 반영 UPSERT (계획서 4 D6). 그날 행이 이미 있을 수 있다 — 오전에
     * 출근을 찍고 오후에 반차를 낸 경우. ON CONFLICT 로 status/note 만
     * 덮어쓰고 check_in/check_out/work_minutes 는 건드리지 않는다.
     *
     * 여기서도 제약 위반을 잡지 않는다(D2 와 같은 이유) — ON CONFLICT 는
     * DB 안에서 해결되어 예외를 만들지 않으므로 트랜잭션이 중단되지 않는다.
     */
    void upsertForLeave(Attendance attendance);

    /** 테스트 검증·화면 조회용 */
    Attendance findByEmpIdAndWorkDate(@Param("empId") Long empId, @Param("workDate") LocalDate workDate);
}
