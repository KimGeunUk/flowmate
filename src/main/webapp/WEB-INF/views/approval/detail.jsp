<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="문서 상세"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title"><c:out value="${doc.title}"/></h2>

        <table class="doc-detail">
            <tr>
                <th>문서번호</th><td><c:out value="${doc.docNo}"/></td>
                <th>유형</th><td><c:out value="${doc.docTypeLabel}"/></td>
            </tr>
            <tr>
                <th>기안자</th>
                <td><c:out value="${doc.drafterName}"/> · <c:out value="${doc.drafterPositionName}"/>
                    (<c:out value="${doc.deptName}"/>)</td>
                <th>금액</th><td class="doc-detail__amount">${doc.amount}</td>
            </tr>
            <tr>
                <th>상태</th>
                <td><span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.statusLabel}"/></span></td>
                <th>기안일</th><td>${doc.draftedAt}</td>
            </tr>
        </table>

        <%--
          연차 맥락 패널 - LEAVE 문서에만,
          문서를 볼 수 있는 사람에게만 보인다(leaveContext 자체가 그 권한 검사를
          통과한 뒤에만 채워진다 - ApprovalBoxController 참고). LLM 을 쓰지
          않으므로 API 키 없이도 항상 값이 채워진다. leaveContext 가 null 이면
          (LEAVE 가 아니거나 확장 데이터가 없으면) 패널 자체가 나타나지 않는다 -
          JSP 는 그 판정을 하지 않고 null 여부만 본다.
        --%>
        <c:if test="${leaveContext != null}">
            <section class="leave-context">
                <h3 class="page-title">연차 신청 검토 정보</h3>
                <dl class="leave-context__list">
                    <dt class="leave-context__term">신청일</dt>
                    <dd class="leave-context__value">
                        <c:out value="${leaveContext.leaveRequest.startDate}"/> ~
                        <c:out value="${leaveContext.leaveRequest.endDate}"/>
                        · <c:out value="${leaveContext.leaveRequest.days}"/>일
                    </dd>

                    <dt class="leave-context__term">연차 현황</dt>
                    <dd class="leave-context__value">
                        <c:choose>
                            <c:when test="${leaveContext.leaveBalance != null}">
                                부여 <c:out value="${leaveContext.leaveBalance.grantedDays}"/>
                                · 사용 <c:out value="${leaveContext.leaveBalance.usedDays}"/>
                                · 잔여 <c:out value="${leaveContext.leaveBalance.remainingDays}"/>
                                · 소진율 <c:out value="${leaveContext.leaveBalance.usedPercent}"/>%
                            </c:when>
                            <c:otherwise>연차 현황 정보가 없습니다.</c:otherwise>
                        </c:choose>
                    </dd>

                    <dt class="leave-context__term">해당일 팀 현황</dt>
                    <dd class="leave-context__value">
                        팀 부재 <c:out value="${leaveContext.teamAvailability.absentCount}"/>명
                        · 팀 가동률 <c:out value="${leaveContext.teamAvailability.availabilityPercent}"/>%
                        (팀 인원 <c:out value="${leaveContext.teamAvailability.teamSize}"/>명)
                    </dd>

                    <dt class="leave-context__term">최근 3개월</dt>
                    <dd class="leave-context__value">
                        지각 <c:out value="${leaveContext.recentSummary.lateCount}"/>회
                        · 연장 <c:out value="${leaveContext.recentSummary.overtimeHours}"/>시간
                        · 결근 <c:out value="${leaveContext.recentSummary.absentCount}"/>회
                    </dd>
                </dl>
            </section>
        </c:if>

        <section class="doc-content">
            <h3 class="page-title">본문</h3>
            <pre class="doc-content__body"><c:out value="${doc.content}"/></pre>
        </section>

        <%--
          AI 요약. 본문과 분리된 영역이라 요약이 실패해도
          본문 표시에는 영향이 없다 - 실패 시 이 영역 안에서만 안내 문구로
          바뀐다. data-approval-id 는 아래 스크립트가 AJAX 요청을 만드는 데 쓴다.

          ★ aiSummaryEnabled(커스터마이징 지점 5) - ai.features.summary
          가 꺼지면 이 <section> 자체를 렌더링하지 않는다. "AI 요약을 일시적으로
          사용할 수 없습니다"(실제 실패 시 문구)와 다르다 - 꺼진 기능은 오류가
          아니라 부재이므로 영역 자체가 없어야 한다.
        --%>
        <c:if test="${aiSummaryEnabled}">
            <section class="ai-summary-box">
                <h3 class="page-title">AI 요약</h3>
                <div id="aiSummaryArea" class="ai-summary" data-approval-id="${doc.approvalId}">
                    <p class="ai-summary__loading">요약을 불러오는 중입니다...</p>
                </div>
            </section>
        </c:if>

        <section class="attach-section">
            <h3 class="page-title">첨부파일</h3>
            <c:choose>
                <c:when test="${empty attachments}">
                    <p class="alert alert--info">첨부된 파일이 없습니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="attach-list">
                        <c:forEach items="${attachments}" var="file">
                            <li class="attach-list__item">
                                <a class="attach-list__link"
                                   href="${pageContext.request.contextPath}/approval/attach/${file.attachId}">
                                    <c:out value="${file.fileName}"/>
                                </a>
                                <span class="attach-list__size">(<c:out value="${file.fileSizeLabel}"/>)</span>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="approval-line-box">
            <h3 class="page-title">결재선</h3>
            <c:choose>
                <c:when test="${empty lines}">
                    <p class="alert alert--info">결재할 상위자가 없는 문서입니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="approval-line">
                        <c:forEach items="${lines}" var="line">
                            <li class="approval-line__item">
                                <span class="approval-line__step">${line.stepNo}</span>
                                <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                                <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                                <span class="status status--${fn:toLowerCase(line.status)}">
                                    <c:out value="${line.statusLabel}"/>
                                </span>
                                <c:if test="${not empty line.comment}">
                                    <span class="approval-line__comment"><c:out value="${line.comment}"/></span>
                                </c:if>
                                <c:if test="${line.processedAt != null}">
                                    <span class="approval-line__at">${line.processedAt}</span>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </section>

        <%--
          검토 영역. 의견 칸은 **하나**다.

          ★ 예전에는 의견 칸이 둘이었다 — 승인 폼의 "의견"과 반려 모달의
            "반려 사유". 그런데 둘은 같은 컬럼(approval_line.comment,
            approval_history.comment)에 저장된다. 이름만 다른 같은 칸이었다.

            더 나쁜 것은 승인 폼 안에 있던 의견 칸이 반려에는 딸려가지 않는
            것이었다. 결재자가 "견적서가 없습니다"라고 적고 [반려]를 누르면
            모달이 뜨면서 그 문장이 사라졌다 — 의견을 가장 길게 쓰는 경우가
            반려인데 하필 그때 버려졌다.

            지금은 화면이 데이터 모델과 같은 모양이다:
              의견(comment)          → 승인·반려 공통이므로 칸도 하나
              반려 유형(reason_category) → 반려 전용이므로 반려 모달에만
            모달의 의견 칸은 이 칸의 값을 이어받는다(common.js).

          버튼 표시 조건은 Service 가 계산해 넘긴 값만 쓴다. JSP 에 규칙을 흩지 않는다.
        --%>
        <c:if test="${myTurn}">
            <section class="doc-review">
                <h3 class="section-title">검토</h3>
                <form id="approveForm" method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/approve">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <div class="form-row form-row--stack">
                        <label class="form-label" for="comment">의견</label>
                        <div class="form-field">
                            <textarea class="form-input" id="comment" name="comment" rows="3" maxlength="500"
                                      placeholder="승인이든 반려든 여기에 적습니다. 반려할 때는 유형만 더 고르면 됩니다."></textarea>
                            <span class="form-hint" data-count-for="comment"></span>
                        </div>
                    </div>
                    <div class="doc-review__actions">
                        <button class="btn btn--primary" type="submit">승인</button>
                        <button class="btn btn--danger" type="button" id="rejectOpen">반려</button>
                    </div>
                </form>
            </section>
        </c:if>

        <section class="doc-actions">
            <c:if test="${doc.editable}">
                <a class="btn btn--plain"
                   href="${pageContext.request.contextPath}/approval/write?approvalId=${doc.approvalId}">수정</a>
            </c:if>

            <c:if test="${canCancel}">
                <form method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/cancel">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <button class="btn btn--plain" type="submit">회수</button>
                </form>
            </c:if>

            <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">목록</a>
        </section>

        <section class="doc-history">
            <h3 class="page-title">이력</h3>
            <ul class="history-list">
                <c:forEach items="${histories}" var="h">
                    <li class="history-list__item">
                        <span class="history-list__at">${h.createdAt}</span>
                        <span class="history-list__actor"><c:out value="${h.actorName}"/></span>
                        <span class="history-list__action"><c:out value="${h.actionLabel}"/></span>
                        <c:if test="${not empty h.comment}">
                            <span class="history-list__comment"><c:out value="${h.comment}"/></span>
                        </c:if>
                    </li>
                </c:forEach>
            </ul>
        </section>

        <c:if test="${myTurn}">
            <jsp:include page="reject-modal.jsp"/>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
<%--
  ai.features.summary 가 꺼져 있으면 이 스크립트 자체를 렌더링하지 않는다 -
  위 <section class="ai-summary-box"> 도 없으므로(같은 플래그로 감쌌다) #aiSummaryArea
  가 애초에 존재하지 않는다. 스크립트를 무조건 내보내고 그 안에서 방어적으로
  #aiSummaryArea 유무를 검사하는 방법도 있지만, "AJAX 요청 자체를 만들지 않는다"를
  화면 코드로도 눈에 보이게 하려고 <c:if> 로 스크립트 블록째로 걸러낸다
  (커스터마이징 지점 5 - 꺼진 기능은 API 를 두드리지 않는다).
--%>
<c:if test="${aiSummaryEnabled}">
<script>
    /*
     * AI 요약을 비동기로 불러온다. jQuery $.ajax 를 쓴다 -
     * common.js 의 $.ajaxSetup 이 이미 모든 jQuery AJAX 요청에 CSRF 헤더를
     * 붙이므로 이 화면에서 따로 배선할 것이 없다(는 fetch 전용
     * 래퍼를 다루는데, 사전점검 모달처럼 fetch 가 필요한 화면이 생기기 전까지는
     * 아직 없다).
     *
     * 실패(네트워크 오류·403·404·503 등 무엇이든)는 전부 같은 안내 문구로
     * 처리한다 - 문서 본문은 이미 서버 렌더링으로 표시돼 있으므로 이 영역만
     * 바뀌고 나머지 화면은 전혀 영향받지 않는다.
     */
    $(function () {
        var $area = $('#aiSummaryArea');
        var approvalId = $area.data('approval-id');

        $.ajax({
            url: '${pageContext.request.contextPath}/api/ai/approvals/' + approvalId + '/summary',
            method: 'POST',
            dataType: 'json'
        }).done(function (result) {
            renderSummary(result);
        }).fail(function () {
            $area.html('<p class="ai-summary__unavailable">AI 요약을 일시적으로 사용할 수 없습니다.</p>');
        });

        function renderSummary(result) {
            var html = '';
            if (result.summary && result.summary.length > 0) {
                html += '<ul class="ai-summary__lines">';
                $.each(result.summary, function (i, line) {
                    html += '<li class="ai-summary__line">' + escapeHtml(line) + '</li>';
                });
                html += '</ul>';
            }
            if (result.keyFacts) {
                var factHtml = '';
                $.each(result.keyFacts, function (key, value) {
                    if (!value) {
                        return;
                    }
                    factHtml += '<dt class="ai-summary__fact-key">' + escapeHtml(key) + '</dt>';
                    factHtml += '<dd class="ai-summary__fact-value">' + escapeHtml(value) + '</dd>';
                });
                if (factHtml) {
                    html += '<dl class="ai-summary__facts">' + factHtml + '</dl>';
                }
            }
            if (!html) {
                html = '<p class="ai-summary__unavailable">AI 요약을 일시적으로 사용할 수 없습니다.</p>';
            }
            $area.html(html);
        }

        function escapeHtml(text) {
            return $('<div>').text(text).html();
        }
    });
</script>
</c:if>
</body>
</html>
