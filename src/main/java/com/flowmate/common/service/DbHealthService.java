package com.flowmate.common.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.common.mapper.DbHealthMapper;

/**
 * DB 연결 상태 조회.
 *
 * 하는 일이 위임 한 줄뿐이지만 Service 계층을 두는 이유는 계층 규칙 때문이다.
 * Controller 가 Mapper 를 직접 부르지 않는다는 규칙에 예외를 만들지 않는다.
 */
@Service
public class DbHealthService {

    private final DbHealthMapper dbHealthMapper;

    public DbHealthService(DbHealthMapper dbHealthMapper) {
        this.dbHealthMapper = dbHealthMapper;
    }

    @Transactional(readOnly = true)
    public String findDbInfo() {
        return dbHealthMapper.selectDbInfo();
    }
}
