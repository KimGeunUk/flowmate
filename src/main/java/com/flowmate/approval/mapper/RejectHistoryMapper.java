package com.flowmate.approval.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.flowmate.approval.domain.RejectHistory;

/**
 * 유형별 빈도 집계는 Phase 5 에서 추가한다 — 지금 만들면 쓰는 곳이 없다.
 */
@Mapper
public interface RejectHistoryMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(RejectHistory h);
}
