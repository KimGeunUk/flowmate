package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalBoxCounts;
import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.BoxTab;
import com.flowmate.approval.domain.DocType;
import com.flowmate.common.web.Page;

/**
 * 내 결재함의 탭 4종.
 *
 * ★ 이 테스트는 시드 문서를 쓰지 않는다. 사원 세 명과 문서 여섯 건을 자기가
 *   만들어 쓴다.
 *
 *   원래는 시드의 문서 1~6 을 그대로 단정했다("곽수빈의 기안 6건", "신동혁의
 *   대기함에는 2번 하나"). 그런데 이 DB 는 데모 앱과 공유하는 것이라,
 *   **누군가 화면에서 결재를 한 번 누르면** 그 문서의 결재선 상태가 바뀌어
 *   테스트가 깨진다. 실제로 두 번 깨졌다 — 한 번은 새로 기안된 문서 때문에,
 *   한 번은 시드 문서 2번을 승인해서.
 *
 *   앞선 수정은 "시드 밖 문서를 지운다"였는데 그것으로는 부족했다. 추가된
 *   문서는 치웠지만 **바뀐 시드 문서는 되돌리지 못했기** 때문이다. 빌려 쓰는
 *   한 같은 일이 반복된다.
 *
 *   그래서 아예 빌리지 않는다. 여기서 만드는 사원은 이 트랜잭션 밖에 존재한
 *   적이 없으므로, 데모에서 무엇을 하든 이 테스트의 숫자에 닿을 수 없다.
 *   덤으로 테스트가 무엇을 전제하는지가 파일 안에 다 적히게 됐다 — 예전에는
 *   그 전제가 seed SQL 에 있었고 주석으로만 옮겨 적혀 있었다.
 *
 *   (이 프로젝트에서 "테스트가 환경을 관찰하지 말고 조건을 통제하라"가
 *    다섯 번째다. LlmConfigTest 는 환경변수, AttendanceServiceIT 는 오늘 근태,
 *    AttendanceQueryServiceIT·LeaveContextServiceIT 는 시드 기간이었다.)
 *
 * 만드는 문서 6건 — 시드가 그렸던 모양을 그대로 옮겼다:
 *   1 DRAFT / 2 PENDING(1단계가 결재자A 차례) / 3 PENDING(2단계가 결재자B 차례)
 *   4 APPROVED(둘 다 승인) / 5 REJECTED(A 가 반려) / 6 CANCELED
 */
@SpringBootTest
@Transactional
class ApprovalQueryServiceIT {

    /** 이 테스트가 만드는 사원 — 트랜잭션이 끝나면 사라진다 */
    private Long drafter;
    private Long approverA;
    private Long approverB;

    /** 만든 문서 6건의 id */
    private Long draftDoc;
    private Long pendingAtA;
    private Long pendingAtB;
    private Long approvedDoc;
    private Long rejectedDoc;
    private Long canceledDoc;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void buildOwnFixture() {
        drafter = insertEmployee('D', "테스트기안자");
        approverA = insertEmployee('A', "테스트결재자갑");
        approverB = insertEmployee('B', "테스트결재자을");

        draftDoc = insertDoc(DocType.EXPENSE, "출장비 정산", 540000, ApprovalStatus.DRAFT, 0);

        pendingAtA = insertDoc(DocType.EXPENSE, "미팅 식대", 120000, ApprovalStatus.PENDING, 1);
        insertLine(pendingAtA, 1, approverA, "CURRENT");
        insertLine(pendingAtA, 2, approverB, "WAITING");

        pendingAtB = insertDoc(DocType.PURCHASE, "모니터 4대 구매", 1600000, ApprovalStatus.PENDING, 2);
        insertLine(pendingAtB, 1, approverA, "APPROVED");
        insertLine(pendingAtB, 2, approverB, "CURRENT");

        approvedDoc = insertDoc(DocType.EXPENSE, "교통비 정산", 88000, ApprovalStatus.APPROVED, 2);
        insertLine(approvedDoc, 1, approverA, "APPROVED");
        insertLine(approvedDoc, 2, approverB, "APPROVED");

        rejectedDoc = insertDoc(DocType.PURCHASE, "사무용 의자 교체", 900000, ApprovalStatus.REJECTED, 1);
        insertLine(rejectedDoc, 1, approverA, "REJECTED");
        insertLine(rejectedDoc, 2, approverB, "SKIPPED");

        canceledDoc = insertDoc(DocType.GENERAL, "워크숍 계획 공유", 0, ApprovalStatus.CANCELED, 0);
    }

    @Test
    @DisplayName("기안 탭은 내가 기안한 문서 전부를 보여준다")
    void draftedTabShowsAllMyDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond(BoxTab.DRAFTED, drafter));

        assertThat(page.getTotalCount()).isEqualTo(6);
        assertThat(page.getContent()).allSatisfy(d -> assertThat(d.getDrafterId()).isEqualTo(drafter));
    }

    @Test
    @DisplayName("대기 탭은 지금 내 차례인 문서만 보여준다")
    void pendingTabShowsOnlyMyTurn() {
        assertThat(queryService.searchBox(cond(BoxTab.PENDING, approverA)).getContent())
                .extracting(ApprovalDoc::getApprovalId).containsExactly(pendingAtA);

        assertThat(queryService.searchBox(cond(BoxTab.PENDING, approverB)).getContent())
                .extracting(ApprovalDoc::getApprovalId).containsExactly(pendingAtB);

        // 기안자는 대기 탭에 아무것도 없다
        assertThat(queryService.searchBox(cond(BoxTab.PENDING, drafter)).getTotalCount()).isZero();
    }

    @Test
    @DisplayName("완료 탭은 내가 처리를 끝낸 문서를 보여준다")
    void doneTabShowsWhatIProcessed() {
        // 결재자갑: 3번 승인, 4번 승인, 5번 반려. 2번은 아직 자기 차례라 완료가 아니다.
        Page<ApprovalDoc> page = queryService.searchBox(cond(BoxTab.DONE, approverA));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId)
                .containsExactlyInAnyOrder(pendingAtB, approvedDoc, rejectedDoc);
    }

    @Test
    @DisplayName("반려 탭은 내가 기안했고 반려된 문서만 보여준다")
    void rejectedTabShowsMyRejectedDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond(BoxTab.REJECTED, drafter));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(rejectedDoc);
        assertThat(page.getContent()).allSatisfy(
                d -> assertThat(d.getStatus()).isEqualTo(ApprovalStatus.REJECTED));
    }

    @Test
    @DisplayName("문서 유형과 검색어로 좁힐 수 있다")
    void narrowsByDocTypeAndKeyword() {
        ApprovalSearchCond byType = cond(BoxTab.DRAFTED, drafter);
        byType.setDocType(DocType.PURCHASE);
        assertThat(queryService.searchBox(byType).getTotalCount()).isEqualTo(2);

        ApprovalSearchCond byKeyword = cond(BoxTab.DRAFTED, drafter);
        byKeyword.setKeyword("출장비");
        assertThat(queryService.searchBox(byKeyword).getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 페이지를 넘는 페이지를 요청하면 마지막 페이지로 보정한다")
    void clampsPageBeyondLast() {
        ApprovalSearchCond cond = cond(BoxTab.DRAFTED, drafter);
        cond.setSize(2);
        cond.setPage(99);

        Page<ApprovalDoc> page = queryService.searchBox(cond);

        assertThat(page.getTotalPages()).isEqualTo(3);   // 6건 / 2건씩
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getStartPage()).isLessThanOrEqualTo(page.getEndPage());
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("★ 탭 배지 건수가 각 탭 목록의 건수와 정확히 일치한다 — 두 쿼리가 갈라지면 여기서 걸린다")
    void tabCountsAgreeWithEachTabListing() {
        // countBoxTabs 는 네 탭을 한 번에 세려고 boxWhere 와 같은 조건을 따로
        // 적어 두었다(하나의 tab 만 고르는 구조라 재사용할 수 없다). 그러면
        // 한쪽만 고쳐 두 숫자가 조용히 갈라질 수 있다 — 화면에는 "대기 3"이라고
        // 떠 있는데 눌러 보면 2건인 상태가 된다. 그 어긋남을 여기서 잡는다.
        //
        // 세 사람 모두로 확인한다. 한 사람만 보면 0 끼리 우연히 맞을 수 있다.
        for (Long empId : new Long[] { drafter, approverA, approverB }) {
            ApprovalBoxCounts counts = queryService.countBoxTabs(empId);

            for (String tab : BoxTab.ALL) {
                long fromListing = queryService.searchBox(cond(tab, empId)).getTotalCount();
                assertThat(counts.countOf(tab))
                        .as("empId=%d, tab=%s", empId, tab)
                        .isEqualTo(fromListing);
            }
        }
    }

    @Test
    @DisplayName("★ 배지 건수는 검색 조건을 따르지 않는다 — '몇 건 남았나'는 검색과 무관한 질문이다")
    void tabCountsIgnoreSearchFilters() {
        ApprovalBoxCounts before = queryService.countBoxTabs(drafter);
        assertThat(before.getDrafted()).isEqualTo(6);

        // 아무것도 걸리지 않는 검색을 해도 배지는 그대로여야 한다.
        ApprovalSearchCond narrowed = cond(BoxTab.DRAFTED, drafter);
        narrowed.setKeyword("존재하지 않는 문서 제목");
        assertThat(queryService.searchBox(narrowed).getTotalCount()).isZero();

        ApprovalBoxCounts after = queryService.countBoxTabs(drafter);
        assertThat(after.getDrafted()).isEqualTo(6);
        assertThat(after.getPending()).isEqualTo(before.getPending());
        assertThat(after.getDone()).isEqualTo(before.getDone());
        assertThat(after.getRejected()).isEqualTo(before.getRejected());
    }

    @Test
    @DisplayName("할 일 판정: 결재자는 대기가 있고, 기안자는 반려가 있다")
    void hasTodoReflectsWhoHasSomethingToDo() {
        assertThat(queryService.countBoxTabs(approverA).isHasTodo()).isTrue();   // 대기 1건
        assertThat(queryService.countBoxTabs(drafter).isHasTodo()).isTrue();     // 반려 1건
        assertThat(queryService.countBoxTabs(drafter).getPending()).isZero();
    }

    @Test
    @DisplayName("LIKE 와일드카드를 입력해도 리터럴로 검색된다")
    void treatsWildcardAsLiteral() {
        ApprovalSearchCond cond = cond(BoxTab.DRAFTED, drafter);
        cond.setKeyword("%");

        assertThat(queryService.searchBox(cond).getTotalCount()).isZero();
    }

    // ── 픽스처 ──────────────────────────────────────────────────────

    /**
     * 사원 하나. emp_no 는 VARCHAR(20) 이므로 짧게 만든다 — "IT" + 역할 한 글자 +
     * 나노초 하위 9자리(12자). 시드의 사원번호(2016004 같은 7자리 숫자)와 형태가
     * 달라 겹칠 수 없고, 혹시 겹치면 UNIQUE 위반으로 곧바로 깨지므로 조용히
     * 잘못될 여지는 없다.
     *
     * 부서·직급은 시드의 개발팀(7)·사원(6)을 빌린다 — FK 를 만족시키는 용도일
     * 뿐이고, 이 테스트의 어떤 단정도 부서·직급을 보지 않는다.
     * 비밀번호는 실제 해시가 아니다. 이 사원으로 로그인할 일이 없다.
     */
    private Long insertEmployee(char role, String name) {
        String empNo = "IT" + role + (System.nanoTime() % 1_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO employee (emp_no, emp_name, dept_id, position_id, hire_date, password_hash) "
                        + "VALUES (?, ?, 7, 6, DATE '2020-01-01', 'not-a-real-hash')",
                empNo, name);
        return jdbcTemplate.queryForObject(
                "SELECT emp_id FROM employee WHERE emp_no = ?", Long.class, empNo);
    }

    private Long insertDoc(String docType, String title, long amount, String status, int currentStep) {
        String docNo = "IT-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO approval_doc (doc_no, doc_type, title, content, drafter_id, dept_id, "
                        + "amount, status, current_step, drafted_at) "
                        + "VALUES (?, ?, ?, '통합 테스트 본문', ?, 7, ?, ?, ?, TIMESTAMP '2026-03-01 09:00:00')",
                docNo, docType, title, drafter, amount, status, currentStep);
        return jdbcTemplate.queryForObject(
                "SELECT approval_id FROM approval_doc WHERE doc_no = ?", Long.class, docNo);
    }

    private void insertLine(Long approvalId, int stepNo, Long approverId, String status) {
        jdbcTemplate.update(
                "INSERT INTO approval_line (approval_id, step_no, approver_id, line_type, status) "
                        + "VALUES (?, ?, ?, 'APPROVAL', ?)",
                approvalId, stepNo, approverId, status);
    }

    private ApprovalSearchCond cond(String tab, Long empId) {
        ApprovalSearchCond c = new ApprovalSearchCond();
        c.setTab(tab);
        c.setEmpId(empId);
        return c;
    }
}
