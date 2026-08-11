package com.flowmate.ai.feature;

import com.flowmate.ai.domain.LeaveContext;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.attendance.domain.AttendanceMonthlySummary;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.TeamAvailability;
import com.flowmate.attendance.service.AttendanceQueryService;
import com.flowmate.attendance.service.LeaveInquiryService;
import com.flowmate.config.AiProperties;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 3a — 연차 맥락 표시, LLM 없음.
 *
 * ★ 이 패널 하나로 "결재와 근태 두 모듈이 통합됐다"는 주장이 성립한다(설계
 * §6.4.7). API 키가 없어도 완전히 동작한다 - LlmClient 를 전혀 부르지 않는다.
 *
 * ★ 권한: 새 규칙을 만들지 않고 {@link ApprovalQueryService#findDoc(Long, Long)}
 * 를 그대로 태운다 - 문서를 볼 수 없는 사람에게는 이 메서드가 예외로 끝난다
 * (SummaryService 와 같은 원칙).
 *
 * ★ 교차 모듈 경계: attendance 는 {@link AttendanceQueryService}·
 * {@link LeaveInquiryService} 두 Service 인터페이스로만 본다 - 매퍼를 직접
 * 부르지 않는다. approval 쪽도 마찬가지로 {@link ApprovalQueryService} 하나만
 * 본다.
 */
@Service
public class LeaveContextService {

    private final ApprovalQueryService approvalQueryService;
    private final LeaveInquiryService leaveInquiryService;
    private final AttendanceQueryService attendanceQueryService;
    private final AiProperties aiProperties;

    public LeaveContextService(ApprovalQueryService approvalQueryService,
                               LeaveInquiryService leaveInquiryService,
                               AttendanceQueryService attendanceQueryService,
                               AiProperties aiProperties) {
        this.approvalQueryService = approvalQueryService;
        this.leaveInquiryService = leaveInquiryService;
        this.attendanceQueryService = attendanceQueryService;
        this.aiProperties = aiProperties;
    }

    /**
     * LEAVE 문서가 아니거나 연차 신청 확장 행이 없으면 null - 패널 자체를
     * 감춘다. 없는 데이터를 억지로 그리지 않는다는 점에서 D8("AI 실패가 화면을
     * 깨뜨리지 않는다")과 같은 정신이다 - 다만 여기는 실패가 아니라 애초에
     * "해당 없음"인 경우다.
     *
     * ★ 최근 3개월·팀 가동률의 기준일은 신청서의 {@code startDate} 다 - 실행
     * 시점의 실제 날짜(LocalDate.now())가 아니다. "지금 이 신청을 검토하는
     * 시점에 그 신청자가 최근 어땠는가"를 신청일 기준으로 고정해야 언제
     * 조회하든(승인 직후든 한참 뒤든) 같은 값이 나온다 - 이 기준을
     * 명시하지 않아 내린 결정이다.
     *
     * ★ 기능 플래그(커스터마이징 지점 5): {@code ai.features.leave-context}
     * 가 꺼져 있으면 즉시 null 이다 - "LEAVE 문서가 아니다"·"확장 데이터가 없다"와
     * 같은 취급이다. 이 서비스는 애초에 LlmClient 를 부르지 않으므로(클래스 주석)
     * 이 플래그는 LLM 호출을 막는 것이 아니라 패널의 노출 여부만 결정한다 - 그래도
     * 세 기능이 같은 {@code ai.features.*} 규약으로 켜고 꺼진다는 일관성을 위해
     * 여기 둔다.
     */
    @Transactional(readOnly = true)
    public LeaveContext build(Long approvalId, Long viewerId) {
        if (!aiProperties.getFeatures().isLeaveContext()) {
            return null;
        }
        ApprovalDoc doc = approvalQueryService.findDoc(approvalId, viewerId);
        if (!DocType.LEAVE.equals(doc.getDocType())) {
            return null;
        }
        LeaveRequest leaveRequest = approvalQueryService.findLeaveRequest(approvalId);
        if (leaveRequest == null) {
            return null;
        }

        LocalDate referenceDate = leaveRequest.getStartDate();
        LeaveBalance balance = leaveInquiryService.findBalance(doc.getDrafterId(), referenceDate.getYear());
        TeamAvailability teamAvailability =
                attendanceQueryService.findTeamAvailability(doc.getDeptId(), referenceDate);
        AttendanceMonthlySummary recentSummary = attendanceQueryService.findRecentSummary(
                doc.getDrafterId(), referenceDate.minusMonths(3), referenceDate.minusDays(1));

        LeaveContext context = new LeaveContext();
        context.setLeaveRequest(leaveRequest);
        context.setLeaveBalance(balance);
        context.setTeamAvailability(teamAvailability);
        context.setRecentSummary(recentSummary);
        return context;
    }
}
