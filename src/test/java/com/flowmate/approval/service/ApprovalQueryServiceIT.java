package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.common.web.Page;

/**
 * 내 결재함의 탭 4종. Task 1 의 시드 6건을 전제로 한다.
 *
 * 시드: DRAFT 1건(id 1), PENDING 2건(id 2 는 1단계 CURRENT=신동혁, id 3 은 2단계 CURRENT=박현주),
 *       APPROVED 1건(id 4), REJECTED 1건(id 5), CANCELED 1건(id 6)
 */
@SpringBootTest
@Transactional
class ApprovalQueryServiceIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;

    /**
     * 시드가 쓰는 approval_id 의 마지막 값. 그 위는 전부 앱이 만든 것이다.
     *
     * 21-seed-approval.sql 이 1~6 을, 50-seed-demo.sql 이 10001~10200 을 명시적
     * id 로 넣고 마지막에 시퀀스를 MAX 로 맞춘다. 따라서 실행 중인 앱이 만드는
     * 문서는 반드시 이 값보다 크다.
     */
    private static final long SEED_MAX_APPROVAL_ID = 10200L;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ "시드 말고는 아무 문서도 없다"를 테스트가 직접 만든다.
     *
     *   이 클래스는 "곽수빈의 기안 6건", "신동혁의 대기함에는 2번 문서 하나",
     *   "전체 3페이지" 처럼 **정확한 수**를 단정한다. 그래서 누군가 화면에서
     *   기안을 하나 올리면 그 즉시 세 건이 깨진다. 실제로 깨졌다 — 데모를
     *   확인하다 만든 문서 하나(EXP-2026-9201) 때문에.
     *
     *   이 프로젝트에서 같은 함정이 네 번째다(LlmConfigTest 는 환경변수,
     *   AttendanceServiceIT 는 오늘 근태, AttendanceQueryServiceIT·
     *   LeaveContextServiceIT 는 시드 기간). 매번 결론이 같다 —
     *   **테스트가 환경을 관찰하면 안 되고 조건을 통제해야 한다.**
     *   앱을 써 봤다는 이유로 빌드가 깨지면, 다음 사람은 데모를 만지기를
     *   망설이게 된다.
     *
     *   클래스가 @Transactional 이라 이 삭제도 함께 롤백된다 — 개발용 DB 의
     *   진짜 데이터는 그대로다.
     */
    @BeforeEach
    void keepOnlySeedDocuments() {
        // 자식 테이블부터. FK 가 전부 NO ACTION 이라 순서를 지켜야 한다.
        for (String table : new String[] {
                "ai_preflight_result", "approval_attachment", "approval_history",
                "approval_line", "approval_reject_history", "leave_usage", "leave_request" }) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE approval_id > ?", SEED_MAX_APPROVAL_ID);
        }
        jdbcTemplate.update("DELETE FROM approval_doc WHERE approval_id > ?", SEED_MAX_APPROVAL_ID);
    }

    @Test
    @DisplayName("기안 탭은 내가 기안한 문서 전부를 보여준다")
    void draftedTabShowsAllMyDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond("drafted", KWAK));

        assertThat(page.getTotalCount()).isEqualTo(6);
        assertThat(page.getContent()).allSatisfy(d -> assertThat(d.getDrafterId()).isEqualTo(KWAK));
    }

    @Test
    @DisplayName("대기 탭은 지금 내 차례인 문서만 보여준다")
    void pendingTabShowsOnlyMyTurn() {
        // 신동혁은 id 2 의 1단계가 CURRENT
        Page<ApprovalDoc> shinBox = queryService.searchBox(cond("pending", SHIN));
        assertThat(shinBox.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(2L);

        // 박현주는 id 3 의 2단계가 CURRENT
        Page<ApprovalDoc> parkBox = queryService.searchBox(cond("pending", PARK));
        assertThat(parkBox.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(3L);

        // 기안자는 대기 탭에 아무것도 없다
        assertThat(queryService.searchBox(cond("pending", KWAK)).getTotalCount()).isZero();
    }

    @Test
    @DisplayName("완료 탭은 내가 처리를 끝낸 문서를 보여준다")
    void doneTabShowsWhatIProcessed() {
        // 신동혁: id 3 승인, id 4 승인, id 5 반려
        Page<ApprovalDoc> page = queryService.searchBox(cond("done", SHIN));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId)
                .containsExactlyInAnyOrder(3L, 4L, 5L);
    }

    @Test
    @DisplayName("반려 탭은 내가 기안했고 반려된 문서만 보여준다")
    void rejectedTabShowsMyRejectedDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond("rejected", KWAK));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(5L);
        assertThat(page.getContent()).allSatisfy(
                d -> assertThat(d.getStatus()).isEqualTo(ApprovalStatus.REJECTED));
    }

    @Test
    @DisplayName("문서 유형과 검색어로 좁힐 수 있다")
    void narrowsByDocTypeAndKeyword() {
        ApprovalSearchCond byType = cond("drafted", KWAK);
        byType.setDocType(DocType.PURCHASE);
        assertThat(byType).isNotNull();
        assertThat(queryService.searchBox(byType).getTotalCount()).isEqualTo(2);

        ApprovalSearchCond byKeyword = cond("drafted", KWAK);
        byKeyword.setKeyword("출장비");
        assertThat(queryService.searchBox(byKeyword).getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 페이지를 넘는 페이지를 요청하면 마지막 페이지로 보정한다")
    void clampsPageBeyondLast() {
        ApprovalSearchCond cond = cond("drafted", KWAK);
        cond.setSize(2);
        cond.setPage(99);

        Page<ApprovalDoc> page = queryService.searchBox(cond);

        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getStartPage()).isLessThanOrEqualTo(page.getEndPage());
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("LIKE 와일드카드를 입력해도 리터럴로 검색된다")
    void treatsWildcardAsLiteral() {
        ApprovalSearchCond cond = cond("drafted", KWAK);
        cond.setKeyword("%");

        assertThat(queryService.searchBox(cond).getTotalCount()).isZero();
    }

    private ApprovalSearchCond cond(String tab, Long empId) {
        ApprovalSearchCond c = new ApprovalSearchCond();
        c.setTab(tab);
        c.setEmpId(empId);
        return c;
    }
}
