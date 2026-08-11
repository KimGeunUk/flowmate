<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="기안 작성"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">
            <c:choose>
                <c:when test="${form.approvalId != null}">기안 수정</c:when>
                <c:otherwise>기안 작성</c:otherwise>
            </c:choose>
        </h2>

        <c:if test="${doc != null}">
            <p class="result-count">
                문서번호 <strong><c:out value="${doc.docNo}"/></strong>
                · 상태 <span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.status}"/></span>
            </p>
        </c:if>

        <form class="doc-form" method="post" action="${pageContext.request.contextPath}/approval/draft">
            <jsp:include page="../common/csrf-input.jsp"/>
            <c:if test="${form.approvalId != null}">
                <input type="hidden" name="approvalId" value="${form.approvalId}">
            </c:if>

            <div class="form-row">
                <label class="form-label" for="docType">문서 유형</label>
                <select class="form-input" id="docType" name="docType" required>
                    <c:forEach items="${docTypes}" var="t">
                        <option value="${t.code}" ${t.code eq form.docType ? 'selected' : ''}>
                            <c:out value="${t.label}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>

            <div class="form-row">
                <label class="form-label" for="title">제목</label>
                <input class="form-input" type="text" id="title" name="title" maxlength="200" required
                       value="${fn:escapeXml(form.title)}">
            </div>

            <div class="form-row">
                <label class="form-label" for="amount">금액</label>
                <input class="form-input" type="number" id="amount" name="amount" min="0" step="1"
                       value="${form.amount}">
            </div>

            <div class="form-row">
                <label class="form-label" for="content">본문</label>
                <textarea class="form-input" id="content" name="content" rows="10"><c:out value="${form.content}"/></textarea>
            </div>

            <%--
              docType=LEAVE 를 고를 때만 보이는 영역. hidden 속성으로 감춘다 —
              브라우저는 hidden 요소를 렌더링하지 않으므로 별도 CSS 없이도 감춰지고
              (Phase 6 이전이라 이 파일엔 CSS 규칙을 쓰지 않는다), 제약 검증도
              건너뛴다. 실제 검증은 서버(ApprovalService)가 한다 — 화면 쪽은
              안내일 뿐이다 (계획서 4 D4).
            --%>
            <section class="leave-fields" id="leaveFields" hidden>
                <p class="leave-fields__balance">
                    <c:choose>
                        <c:when test="${leaveBalance != null}">
                            연차 잔여 <strong><c:out value="${leaveBalance.remainingDays}"/></strong>일
                            (부여 <c:out value="${leaveBalance.grantedDays}"/> - 사용 <c:out value="${leaveBalance.usedDays}"/>)
                        </c:when>
                        <c:otherwise>연차 잔여 정보가 없습니다.</c:otherwise>
                    </c:choose>
                </p>

                <c:if test="${exceedsBalance}">
                    <p class="alert alert--warning">
                        신청 일수가 잔여 연차를 초과합니다. 이 안내는 참고용이며,
                        실제 승인 가능 여부는 승인 시점에 다시 확인됩니다.
                    </p>
                </c:if>

                <div class="form-row">
                    <label class="form-label" for="leaveType">연차 유형</label>
                    <select class="form-input" id="leaveType" name="leaveType">
                        <c:forEach items="${leaveTypes}" var="t">
                            <option value="${t}" ${t eq form.leaveType ? 'selected' : ''}>
                                <c:out value="${t}"/>
                            </option>
                        </c:forEach>
                    </select>
                </div>

                <div class="form-row">
                    <label class="form-label" for="startDate">시작일</label>
                    <input class="form-input" type="date" id="startDate" name="startDate"
                           value="${form.startDate}">
                    <label class="form-label" for="endDate">종료일</label>
                    <input class="form-input" type="date" id="endDate" name="endDate"
                           value="${form.endDate}">
                </div>

                <c:if test="${leaveRequest != null}">
                    <p class="leave-fields__days">
                        계산된 일수 <strong><c:out value="${leaveRequest.days}"/></strong>일
                        (주말·공휴일 제외 — 서버가 계산합니다)
                    </p>
                </c:if>

                <div class="form-row">
                    <label class="form-label" for="reason">사유</label>
                    <textarea class="form-input" id="reason" name="reason" rows="3"
                              maxlength="500"><c:out value="${form.reason}"/></textarea>
                </div>
            </section>

            <div class="form-row">
                <button class="btn btn--primary" type="submit">임시저장</button>
                <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">내 결재함</a>
            </div>
        </form>

        <c:if test="${not empty lines}">
            <h3 class="page-title">자동 생성된 결재선</h3>
            <ul class="approval-line">
                <c:forEach items="${lines}" var="line">
                    <li class="approval-line__item">
                        <span class="approval-line__step">${line.stepNo}</span>
                        <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                        <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                        <span class="status status--${fn:toLowerCase(line.status)}"><c:out value="${line.status}"/></span>
                    </li>
                </c:forEach>
            </ul>
            <form id="submitForm" class="submit-form" method="post" data-approval-id="${form.approvalId}"
                  action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>

        <c:if test="${form.approvalId != null and empty lines}">
            <p class="alert alert--info">결재할 상위자가 없어 상신하면 즉시 완료됩니다.</p>
            <form id="submitForm" class="submit-form" method="post" data-approval-id="${form.approvalId}"
                  action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>

        <%--
          사전점검 모달(계획서 5 Task 5·6) - 상신 폼이 있을 때만 필요하다.
          aiPreflightEnabled(계획서 5 Task 7, 커스터마이징 지점 5)가 꺼져 있으면
          이 모달의 빈 뼈대조차 렌더링하지 않는다 - 꺼진 기능은 화면에 아무 흔적도
          남기지 않는다(오류가 아니라 부재).
        --%>
        <c:if test="${form.approvalId != null and aiPreflightEnabled}">
            <jsp:include page="preflight-modal.jsp"/>
        </c:if>

        <%-- 첨부는 임시저장 상태에서만 다룬다 (기안자 + DRAFT). doc.editable 이 그 판정이다 --%>
        <c:if test="${doc != null and doc.editable}">
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
                                    <form method="post"
                                          action="${pageContext.request.contextPath}/approval/attach/${file.attachId}/delete">
                                        <jsp:include page="../common/csrf-input.jsp"/>
                                        <button class="btn btn--plain" type="submit">삭제</button>
                                    </form>
                                </li>
                            </c:forEach>
                        </ul>
                    </c:otherwise>
                </c:choose>

                <form class="attach-form" method="post" enctype="multipart/form-data"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/attach">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <div class="form-row">
                        <input class="form-input" type="file" name="file" required>
                        <button class="btn btn--primary" type="submit">첨부</button>
                    </div>
                </form>
            </section>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
<script>
    /*
     * docType=LEAVE 를 고를 때만 leaveFields 를 보여준다.
     * 페이지 진입 시(임시저장 문서를 다시 열었을 때)와 select 변경 시 둘 다 반영한다.
     */
    $(function () {
        function toggleLeaveFields() {
            var isLeave = $('#docType').val() === 'LEAVE';
            $('#leaveFields').prop('hidden', !isLeave);
        }
        toggleLeaveFields();
        $('#docType').on('change', toggleLeaveFields);
    });
</script>

<%--
  상신 전 사전 점검(설계서 §6.4.6, 계획서 5 Task 5·6).

  상신 폼의 기본 제출을 가로채 먼저 /api/ai/approvals/{id}/preflight 를 부른다.
  그 응답에 따라 분기한다:
    - PASS(또는 findings 가 비어 있음) → 모달 없이 바로 상신
    - WARN                              → 모달 표시, 사용자가 [수정하러 가기]
                                           또는 [무시하고 상신]을 고른다

  ★ D8(계획서 5): 이 호출이 실패하는 경우 - 서버가 503 을 주거나, 네트워크
  오류가 나거나, 응답이 오지 않아 타임아웃되거나 - 전부 "모달 없이 바로 상신"과
  같은 경로로 합류한다. 사전 점검은 보조 장치이므로 그것이 죽어도 상신은
  100% 동작해야 한다(설계서 §6.4.3 폴백 원칙).

  flowmateFetch(common.js)를 쓴다 - 이 호출은 fetch() 라서 $.ajaxSetup 이 붙이는
  CSRF 헤더 경로를 타지 않는다. 개별 호출부에서 헤더를 손으로 붙이지 않고 래퍼에
  맡긴다(계획서 5 D5) - 다음 사람이 새 fetch 호출을 추가할 때 또 빠뜨리지 않도록.

  ★ 이 스크립트 블록 전체가 <c:if test="${aiPreflightEnabled}"> 로 감싸여 있다
  (계획서 5 Task 7, 커스터마이징 지점 5) - 꺼져 있으면 상신 폼의 기본 제출을
  아예 가로채지 않으므로 /api/ai/.../preflight 호출 자체가 나가지 않는다.
  D8 의 "실패 시 모달 없이 상신"과 결과는 같지만(둘 다 모달이 안 뜬다), 이
  경로는 네트워크 호출조차 시도하지 않는다는 점이 다르다 - "부재"가 "실패한
  시도"보다 한 단계 더 확실하다.
--%>
<c:if test="${aiPreflightEnabled}">
<script>
    $(function () {
        var $submitForm = $('#submitForm');
        if ($submitForm.length === 0) {
            return;
        }

        var $modal = $('#preflightModal');
        var $findings = $('#preflightFindings');
        var contextPath = '${pageContext.request.contextPath}';
        var PREFLIGHT_TIMEOUT_MS = 15000;

        $submitForm.on('submit', function (event) {
            event.preventDefault();
            // ★ 동기 예외까지 잡아야 D8 이 지켜진다.
            //   runPreflight() 안의 실패(응답 오류·타임아웃·JSON 파싱)는 promise 체인이
            //   전부 submitNow() 로 합류시킨다. 그러나 첫 .then() 에 닿기 전에 나는
            //   동기 예외 - 예를 들어 common.js 가 로드되지 않아 flowmateFetch 가
            //   undefined 인 경우 - 는 그 체인에 들어가지도 못한다.
            //   그러면 preventDefault() 는 이미 실행됐고 submitNow() 에는 도달하지 못해
            //   **상신 버튼이 영구히 먹통이 된다.** 스크립트 하나가 못 떠서 결재를
            //   못 올리는 것은 "AI 실패가 업무 실패가 되어서는 안 된다"의 정반대다.
            try {
                runPreflight();
            } catch (e) {
                submitNow();
            }
        });

        function runPreflight() {
            var approvalId = $submitForm.data('approval-id');
            var hasAbort = ('AbortController' in window);
            var controller = hasAbort ? new AbortController() : null;
            var timeoutId = hasAbort
                ? setTimeout(function () { controller.abort(); }, PREFLIGHT_TIMEOUT_MS)
                : null;

            flowmateFetch(contextPath + '/api/ai/approvals/' + approvalId + '/preflight', {
                method: 'POST',
                signal: controller ? controller.signal : undefined
            }).then(function (response) {
                clearTimeout(timeoutId);
                if (!response.ok) {
                    submitNow();
                    return null;
                }
                return response.json();
            }).then(function (result) {
                if (!result) {
                    return; // 위에서 이미 상신을 진행했다(실패 경로) - 이중 상신 방지
                }
                if (result.verdict !== 'WARN' || !result.findings || result.findings.length === 0) {
                    submitNow();
                    return;
                }
                showModal(result);
            }).catch(function () {
                clearTimeout(timeoutId);
                submitNow();
            });
        }

        function showModal(result) {
            $findings.empty();
            $.each(result.findings, function (i, f) {
                var $item = $('<li>').addClass('preflight-modal__finding');
                $('<span>').addClass('preflight-modal__severity').text(f.severity).appendTo($item);
                $('<span>').addClass('preflight-modal__category').text(f.category).appendTo($item);
                $('<p>').addClass('preflight-modal__message').text(f.message).appendTo($item);
                $('<p>').addClass('preflight-modal__suggestion').text(f.suggestion).appendTo($item);
                $('<p>').addClass('preflight-modal__basis')
                    .text('과거 반려 ' + f.basedOnRejectCount + '건에 근거').appendTo($item);
                $findings.append($item);
            });
            $modal.data('result-id', result.resultId);
            $modal.prop('hidden', false);
        }

        function submitNow() {
            $modal.prop('hidden', true);
            // 네이티브 submit() 은 'submit' 이벤트를 다시 일으키지 않는다(스펙으로
            // 보장된 동작) - 위 핸들러가 다시 가로채 무한 루프가 되는 것을 막는다.
            $submitForm.get(0).submit();
        }

        $(document).on('click', '#preflightFix', function () {
            // 이미 이 화면(작성/수정 화면)에 본문이 있다 - 모달만 닫는다.
            $modal.prop('hidden', true);
        });

        $(document).on('click', '#preflightIgnore', function () {
            var resultId = $modal.data('result-id');
            if (!resultId) {
                submitNow();
                return;
            }
            flowmateFetch(contextPath + '/api/ai/preflight/' + resultId + '/ignore', {
                method: 'POST'
            }).then(function () {
                submitNow();
            }).catch(function () {
                // ignore 기록이 실패해도 상신은 진행한다 - 부가 기록이 상신을 막지 않는다(D8과 같은 정신).
                submitNow();
            });
        });
    });
</script>
</c:if>
</body>
</html>
