package com.flowmate.approval.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.service.DepartmentService;

/**
 * 결재 매퍼와 부서장 체인 조회. Task 1 의 시드(문서 6건)를 전제로 한다.
 */
@SpringBootTest
@Transactional
class ApprovalDocMapperIT {

    @Autowired
    private ApprovalDocMapper approvalDocMapper;

    @Autowired
    private ApprovalLineMapper approvalLineMapper;

    @Autowired
    private DepartmentService departmentService;

    @Test
    @DisplayName("문서를 저장하면 생성된 PK 가 객체에 채워진다")
    void insertPopulatesGeneratedKey() {
        ApprovalDoc doc = newDraft();

        approvalDocMapper.insert(doc);

        assertThat(doc.getApprovalId()).isNotNull().isGreaterThan(6L);
    }

    @Test
    @DisplayName("문서를 조회하면 기안자명과 부서명이 함께 온다")
    void findByIdJoinsDrafterAndDept() {
        ApprovalDoc doc = approvalDocMapper.findById(1L);

        assertThat(doc).isNotNull();
        assertThat(doc.getDocNo()).isEqualTo("EXP-2026-0001");
        assertThat(doc.getDrafterName()).isEqualTo("곽수빈");
        assertThat(doc.getDeptName()).isEqualTo("개발팀");
        assertThat(doc.getDrafterPositionName()).isEqualTo("사원");
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
    }

    @Test
    @DisplayName("없는 문서를 조회하면 null 이다")
    void findByIdReturnsNullWhenAbsent() {
        assertThat(approvalDocMapper.findById(99999L)).isNull();
    }

    @Test
    @DisplayName("문서번호 일련번호는 같은 유형·같은 연도에서 이어진다")
    void docNoSequenceContinuesPerTypeAndYear() {
        // 절대값을 단정하지 않는다. 화면 검증이 실제 DB 에 문서를 남기면 시드 기준 숫자가
        // 달라지는데, 그때 이 테스트가 깨지는 것은 문서번호 로직의 문제가 아니다.
        int expBefore = approvalDocMapper.maxDocNoSeq("EXP", 2026);
        assertThat(expBefore).isGreaterThanOrEqualTo(3);   // 시드에 EXP-2026-0001~0003
        assertThat(approvalDocMapper.maxDocNoSeq("PUR", 2026)).isGreaterThanOrEqualTo(2);

        // 쓰이지 않은 접두사는 0 — COALESCE 가 NULL 을 0 으로 바꾸는지의 검증이다
        assertThat(approvalDocMapper.maxDocNoSeq("CON", 2026)).isZero();

        // 새 문서를 넣으면 그 유형의 최대 일련번호가 정확히 1 늘어난다
        ApprovalDoc doc = newDraft();
        doc.setDocNo(String.format("EXP-2026-%04d", expBefore + 1));
        approvalDocMapper.insert(doc);
        assertThat(approvalDocMapper.maxDocNoSeq("EXP", 2026)).isEqualTo(expBefore + 1);
    }

    @Test
    @DisplayName("채번 잠금을 잡아도 같은 트랜잭션에서 조회와 저장이 이어진다")
    void lockDocNoSeqDoesNotBreakTransaction() {
        assertThat(approvalDocMapper.lockDocNoSeq("EXP-2026")).isEqualTo(1);

        // 잠금 이후에도 트랜잭션이 정상이어야 한다
        int seq = approvalDocMapper.maxDocNoSeq("EXP", 2026);
        assertThat(seq).isGreaterThanOrEqualTo(3);

        ApprovalDoc doc = newDraft();
        doc.setDocNo(String.format("EXP-2026-%04d", seq + 1));
        approvalDocMapper.insert(doc);
        assertThat(doc.getApprovalId()).isNotNull();

        // 같은 키를 다시 잡아도 (같은 트랜잭션이므로) 막히지 않는다
        assertThat(approvalDocMapper.lockDocNoSeq("EXP-2026")).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 전이 결과만 갱신한다")
    void updateStatusPersistsTransition() {
        ApprovalDoc doc = approvalDocMapper.findById(1L);
        doc.submit(2);

        approvalDocMapper.updateStatus(doc);

        ApprovalDoc reloaded = approvalDocMapper.findById(1L);
        assertThat(reloaded.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(reloaded.getCurrentStep()).isEqualTo(1);
        assertThat(reloaded.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("결재선을 한 번에 저장하고 결재자 정보와 함께 조회한다")
    void insertAllAndFindLines() {
        ApprovalDoc doc = newDraft();
        approvalDocMapper.insert(doc);

        ApprovalLine first = line(doc.getApprovalId(), 1, 14L);
        ApprovalLine second = line(doc.getApprovalId(), 2, 3L);
        approvalLineMapper.insertAll(List.of(first, second));

        List<ApprovalLine> lines = approvalLineMapper.findByApprovalId(doc.getApprovalId());

        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2);
        assertThat(lines).extracting(ApprovalLine::getApproverName).containsExactly("신동혁", "박현주");
        assertThat(lines).extracting(ApprovalLine::getApproverPositionName).containsExactly("과장", "부장");
    }

    @Test
    @DisplayName("★ 부서장 체인은 기안자 부서에서 루트까지 가까운 순서로 온다")
    void deptHeadChainWalksUpwardNearestFirst() {
        // 개발팀(7) → 사업본부(3) → 대표이사실(1)
        List<Employee> chain = departmentService.findDeptHeadChain(7L);

        assertThat(chain).extracting(Employee::getEmpName)
                .containsExactly("신동혁", "박현주", "정도현");
        assertThat(chain).extracting(Employee::getPositionLevel)
                .containsExactly(3, 5, 6);
        assertThat(chain).extracting(Employee::getDeptName)
                .containsExactly("개발팀", "사업본부", "대표이사실");
    }

    @Test
    @DisplayName("다른 본부의 체인도 같은 모양이다")
    void deptHeadChainForAnotherDivision() {
        // 인사팀(4) → 경영지원본부(2) → 대표이사실(1)
        List<Employee> chain = departmentService.findDeptHeadChain(4L);

        assertThat(chain).extracting(Employee::getEmpName)
                .containsExactly("최민석", "김성일", "정도현");
    }

    @Test
    @DisplayName("루트 부서의 체인은 자기 자신 하나뿐이다")
    void deptHeadChainForRootHasOnlyItself() {
        List<Employee> chain = departmentService.findDeptHeadChain(1L);

        assertThat(chain).extracting(Employee::getEmpName).containsExactly("정도현");
    }

    @Test
    @DisplayName("부서마다 최고 직급 1명씩만 나온다 - 동급이 있어도 중복되지 않는다")
    void deptHeadChainReturnsExactlyOnePerDepartment() {
        List<Employee> chain = departmentService.findDeptHeadChain(7L);

        assertThat(chain).extracting(Employee::getDeptId).doesNotHaveDuplicates();
    }

    private ApprovalDoc newDraft() {
        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocNo("EXP-2026-9001");
        doc.setDocType(DocType.EXPENSE);
        doc.setTitle("통합 테스트 문서");
        doc.setContent("본문");
        doc.setDrafterId(18L);
        doc.setDeptId(7L);
        doc.setAmount(new BigDecimal("100000"));
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.now());
        return doc;
    }

    private ApprovalLine line(Long approvalId, int stepNo, Long approverId) {
        ApprovalLine line = new ApprovalLine();
        line.setApprovalId(approvalId);
        line.setStepNo(stepNo);
        line.setApproverId(approverId);
        line.setLineType(LineType.APPROVAL);
        line.setStatus(LineStatus.WAITING);
        return line;
    }
}
