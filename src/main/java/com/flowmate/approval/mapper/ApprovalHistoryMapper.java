package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalHistory;

@Mapper
public interface ApprovalHistoryMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(ApprovalHistory h);

    /** 행위자 이름·직급 조인 포함. 시간순 */
    List<ApprovalHistory> findByApprovalId(@Param("approvalId") Long approvalId);
}
