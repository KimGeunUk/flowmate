package com.flowmate.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * DB 연결 상태 확인용. 화면에 DB 이름과 버전을 노출해
 * "애플리케이션이 떴다"와 "DB까지 연결됐다"를 구분한다.
 */
@Mapper
public interface DbHealthMapper {

    @Select("SELECT current_database() || ' / ' || split_part(version(), ' ', 2)")
    String selectDbInfo();
}
