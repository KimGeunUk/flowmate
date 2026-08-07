package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.common.exception.ApprovalAccessDeniedException;

/**
 * 결재 처리의 트랜잭션 경계. 시드(문서 6건)와 조직 시드(사원 20)를 전제로 한다.
 *
 * 사원번호: 곽수빈 18(개발팀 사원), 신동혁 14(개발팀 과장), 박현주 3(사업본부 부장), 정도현 1(이사)
 */
@SpringBootTest
@Transactional
class ApprovalServiceIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;
    private static final Long JEONG = 1L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private ApprovalLineMapper lineMapper;

    @Test
    @DisplayName("임시저장하면 문서번호가 부여되고 결재선이 자동 생성된다")
    void saveDraftGeneratesDocNoAndLines() {
        Long id = approvalService.saveDraft(newForm("소액 지출", "1000000"), KWAK);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getDocNo()).matches("EXP-\\d{4}-\\d{4}");
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(doc.getCurrentStep()).isZero();

        List<ApprovalLine> lines = lineMapper.findByApprovalId(id);
        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(SHIN, PARK);
        assertThat(lines).allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(LineStatus.WAITING));
    }

    @Test
    @DisplayName("금액이 크면 결재선에 이사가 추가된다 - 정책이 실제로 적용된다")
    void largeAmountAddsExecutiveToLine() {
        Long id = approvalService.saveDraft(newForm("고액 구매", "5000000"), KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    @Test
    @DisplayName("이사가 기안하면 결재선이 비어 있다")
    void executiveDraftHasNoLine() {
        Long id = approvalService.saveDraft(newForm("이사 기안", "1000000"), JEONG);

        assertThat(lineMapper.findByApprovalId(id)).isEmpty();
    }

    @Test
    @DisplayName("문서번호는 같은 유형·연도에서 이어진다")
    void docNoIncrementsPerTypeAndYear() {
        Long first = approvalService.saveDraft(newForm("첫 번째", "10000"), KWAK);
        Long second = approvalService.saveDraft(newForm("두 번째", "10000"), KWAK);

        String firstNo = queryService.findDoc(first, KWAK).getDocNo();
        String secondNo = queryService.findDoc(second, KWAK).getDocNo();

        int firstSeq = Integer.parseInt(firstNo.substring(firstNo.length() - 4));
        int secondSeq = Integer.parseInt(secondNo.substring(secondNo.length() - 4));
        assertThat(secondSeq).isEqualTo(firstSeq + 1);
    }

    @Test
    @DisplayName("임시저장 문서는 기안자만 수정할 수 있다")
    void onlyDrafterCanEditDraft() {
        Long id = approvalService.saveDraft(newForm("원본", "10000"), KWAK);

        ApprovalForm changed = newForm("수정본", "20000");
        changed.setApprovalId(id);

        assertThatThrownBy(() -> approvalService.saveDraft(changed, SHIN))
                .isInstanceOf(ApprovalAccessDeniedException.class);

        approvalService.saveDraft(changed, KWAK);
        assertThat(queryService.findDoc(id, KWAK).getTitle()).isEqualTo("수정본");
    }

    @Test
    @DisplayName("수정할 때 금액이 바뀌면 결재선을 다시 만든다")
    void editingAmountRebuildsLine() {
        Long id = approvalService.saveDraft(newForm("소액", "1000000"), KWAK);
        assertThat(lineMapper.findByApprovalId(id)).hasSize(2);

        ApprovalForm changed = newForm("고액으로 수정", "5000000");
        changed.setApprovalId(id);
        approvalService.saveDraft(changed, KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    private ApprovalForm newForm(String title, String amount) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle(title);
        form.setContent("본문 내용");
        form.setAmount(new BigDecimal(amount));
        return form;
    }
}
