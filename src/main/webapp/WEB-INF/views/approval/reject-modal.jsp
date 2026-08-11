<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  반려 모달.

  ★ 이 모달이 문는 것은 **반려 유형 하나**다.
    의견은 검토 영역(detail.jsp)의 칸에서 이어받는다 — 두 칸은 같은 컬럼에
    저장되므로 화면에서도 하나여야 한다. 여기 있는 textarea 는 그 값을
    그대로 받아 다듬는 자리이지 새로 쓰는 자리가 아니다(common.js 가 옮긴다).

  ★ select 에 required 를 붙이고 빈 option 을 기본값으로 둔다.
    유형 없는 반려를 허용하면 Phase 5 사전점검이 유형별 빈도를 집계할 수 없고,
    집계가 없으면 "과거 반려 3건에 근거함" 같은 숫자를 제시할 수 없다.
    Service 도 같은 검증을 하지만(화면을 우회할 수 있으므로) 화면에서도 막는다.
    유형은 의견과 달리 자유 텍스트로 대체할 수 없다 — 이 모달이 남아 있는
    이유가 그것 하나다.

  필요 모델: doc, rejectReasons(RejectReason.options())
--%>
<div class="reject-modal" id="rejectModal" hidden>
    <div class="reject-modal__panel">
        <h3 class="reject-modal__title">반려</h3>
        <p class="reject-modal__intro">
            반려 유형은 이후 사전 점검이 "과거 반려 N건에 근거함"을 계산하는 데 쓰입니다.
        </p>
        <form method="post" action="${pageContext.request.contextPath}/approval/${doc.approvalId}/reject">
            <jsp:include page="../common/csrf-input.jsp"/>

            <div class="form-row form-row--stack">
                <label class="form-label" for="reasonCategory">반려 유형 <span class="form-required">*</span></label>
                <div class="form-field">
                    <select class="form-input" id="reasonCategory" name="reasonCategory" required>
                        <option value="">선택하세요</option>
                        <c:forEach items="${rejectReasons}" var="r">
                            <option value="${r.code}"><c:out value="${r.label}"/></option>
                        </c:forEach>
                    </select>
                </div>
            </div>

            <div class="form-row form-row--stack">
                <label class="form-label" for="reasonText">의견</label>
                <div class="form-field">
                    <textarea class="form-input" id="reasonText" name="reasonText" rows="4" maxlength="500"
                              placeholder="기안자가 무엇을 고쳐야 하는지 적어 주세요"></textarea>
                    <span class="form-hint">검토란에 적은 의견이 그대로 넘어옵니다. 여기서 더 다듬어도 됩니다.</span>
                </div>
            </div>

            <div class="reject-modal__actions">
                <button class="btn btn--plain" type="button" id="rejectCancel">취소</button>
                <button class="btn btn--danger" type="submit">반려</button>
            </div>
        </form>
    </div>
</div>
