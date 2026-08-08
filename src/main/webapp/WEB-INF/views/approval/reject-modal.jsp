<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  반려 유형 선택.

  ★ select 에 required 를 붙이고 빈 option 을 기본값으로 둔다.
    유형 없는 반려를 허용하면 Phase 5 사전점검이 유형별 빈도를 집계할 수 없고,
    집계가 없으면 "과거 반려 3건에 근거함" 같은 숫자를 제시할 수 없다.
    Service 도 같은 검증을 하지만(화면을 우회할 수 있으므로) 화면에서도 막는다.

  필요 모델: doc, rejectReasons
--%>
<div class="reject-modal" id="rejectModal" hidden>
    <div class="reject-modal__panel">
        <h3 class="reject-modal__title">반려</h3>
        <form method="post" action="${pageContext.request.contextPath}/approval/${doc.approvalId}/reject">
            <jsp:include page="../common/csrf-input.jsp"/>

            <div class="form-row">
                <label class="form-label" for="reasonCategory">반려 유형</label>
                <select class="form-input" id="reasonCategory" name="reasonCategory" required>
                    <option value="">선택하세요</option>
                    <c:forEach items="${rejectReasons}" var="r">
                        <option value="${r}"><c:out value="${r}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div class="form-row">
                <label class="form-label" for="reasonText">반려 사유</label>
                <textarea class="form-input" id="reasonText" name="reasonText" rows="4"
                          maxlength="500" placeholder="기안자가 무엇을 고쳐야 하는지 적어 주세요"></textarea>
            </div>

            <div class="form-row">
                <button class="btn btn--danger" type="submit">반려</button>
                <button class="btn btn--plain" type="button" id="rejectCancel">취소</button>
            </div>
        </form>
    </div>
</div>
