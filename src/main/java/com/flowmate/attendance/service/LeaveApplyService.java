package com.flowmate.attendance.service;

import com.flowmate.attendance.domain.LeaveApplyCommand;

/**
 * ★ approval 이 승인 반영에서 보는 유일한 지점 (이 Phase의 척추).
 *
 * approvalId 만 받지 않는다 — 값(LeaveApplyCommand)을 받는다. attendance 가
 * approvalId 로 사원·기간·일수를 알아내려면 approval 의 leave_request 를
 * 읽어야 하고, 그러면 attendance → approval 호출이 생겨 의존이 양방향(순환)이
 * 된다. approval 이 자기 테이블을 읽어 값을 조립해 넘기고, attendance 는
 * 받은 값만 쓴다 — 그래야 attendance 를 단위 테스트할 때 approval 을 전혀
 * 몰라도 된다.
 *
 * ApprovalService.approve() 의 트랜잭션 안에서 직접 호출된다. Spring 이벤트가
 * 아니다 — 어느 한쪽이 실패하면 전부 롤백되어야 "승인은 됐는데 연차가 안
 * 깎였다" 같은 부분 실패를 막을 수 있기 때문이다.
 */
public interface LeaveApplyService {

    /**
     * 연차 신청서 승인을 근태에 반영한다.
     *
     * 이미 반영된 approvalId 면 조용히 통과한다 (D2 — 중복 반영 방지, 제약
     * 위반을 잡지 않고 먼저 조회해서 분기한다). 잔여가 부족하면
     * {@link IllegalStateException} 을 던져 호출자(ApprovalService.approve())의
     * 트랜잭션을 롤백시킨다 (D4).
     */
    void apply(LeaveApplyCommand command);
}
