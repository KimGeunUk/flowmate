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
        <%--
          이 화면은 한 번에 끝나지 않는다 - 작성하고, 임시저장해야 결재선이 만들어지고,
          그 결재선을 보고 상신한다. 예전 화면은 그 순서가 보이지 않아서 "왜 상신
          버튼이 없지?"가 되었다(저장 전에는 아예 렌더링되지 않는다). 아래 doc-steps
          가 그 순서를 먼저 보여주고, 화면 구성도 그 순서대로 놓는다:
              작성 폼 → 첨부 → 결재선 → 하단 액션 바
          예전에는 첨부가 상신 버튼 뒤에 있었다 - 상신하고 나면 첨부할 수 없는데도.
        --%>
        <c:set var="saved" value="${form.approvalId != null}"/>

        <h2 class="page-title">
            <c:choose>
                <c:when test="${saved}">기안 수정</c:when>
                <c:otherwise>기안 작성</c:otherwise>
            </c:choose>
        </h2>

        <ol class="doc-steps">
            <li class="doc-steps__item ${saved ? 'doc-steps__item--done' : 'doc-steps__item--current'}">
                <span class="doc-steps__no">1</span>
                <span class="doc-steps__label">내용 작성</span>
            </li>
            <li class="doc-steps__item ${saved ? 'doc-steps__item--done' : ''}">
                <span class="doc-steps__no">2</span>
                <span class="doc-steps__label">임시저장</span>
            </li>
            <li class="doc-steps__item ${saved ? 'doc-steps__item--current' : ''}">
                <span class="doc-steps__no">3</span>
                <span class="doc-steps__label">결재선 확인 후 상신</span>
            </li>
        </ol>

        <c:if test="${doc != null}">
            <p class="doc-meta">
                <span class="doc-meta__item">문서번호
                    <strong class="doc-meta__no"><c:out value="${doc.docNo}"/></strong></span>
                <span class="doc-meta__item">유형
                    <strong><c:out value="${doc.docTypeLabel}"/></strong></span>
                <span class="doc-meta__item">상태
                    <span class="status status--${fn:toLowerCase(doc.status)}">
                        <c:out value="${doc.statusLabel}"/></span></span>
            </p>
        </c:if>

        <%--
          ★ 폼에 id 를 준 이유: 하단 액션 바가 이 폼 바깥(첨부·결재선 아래)에 있다.
            버튼의 form 속성으로 폼과 이어 붙인다 - 첨부 폼은 중첩할 수 없으므로
            폼 하나로 전부 감쌀 수는 없고, 그렇다고 저장 버튼을 화면 중간에 두면
            다시 예전 문제로 돌아간다. form 속성은 문서 순서와 무관하게 id 로
            연결되므로 액션 바가 폼보다 뒤에 있어도 된다.
        --%>
        <form class="doc-form" id="draftForm" method="post"
              action="${pageContext.request.contextPath}/approval/draft">
            <jsp:include page="../common/csrf-input.jsp"/>
            <c:if test="${saved}">
                <input type="hidden" name="approvalId" value="${form.approvalId}">
            </c:if>

            <section class="doc-form__section">
                <h3 class="section-title">기본 정보</h3>

                <div class="form-row">
                    <label class="form-label" for="docType">문서 유형 <span class="form-required">*</span></label>
                    <div class="form-field form-field--fixed">
                        <%--
                          각 항목이 자기 성격(금액 칸을 쓰는가·연차 칸을 쓰는가)을
                          data 속성으로 들고 온다. 스크립트에 EXPENSE 나 LEAVE 같은
                          코드를 적지 않기 위해서다 - 유형을 추가할 때 화면 스크립트를
                          같이 고쳐야 하는 상황을 만들지 않는다(DocType.usesAmount 주석).
                        --%>
                        <select class="form-input form-input--select" id="docType" name="docType" required>
                            <c:forEach items="${docTypes}" var="t">
                                <option value="${t.code}"
                                        data-uses-amount="${t.usesAmount}"
                                        data-uses-leave="${t.usesLeaveFields}"
                                        ${t.code eq form.docType ? 'selected' : ''}><c:out value="${t.label}"/></option>
                            </c:forEach>
                        </select>
                    </div>
                </div>

                <div class="form-row">
                    <label class="form-label" for="title">제목 <span class="form-required">*</span></label>
                    <div class="form-field">
                        <input class="form-input" type="text" id="title" name="title" maxlength="200" required
                               placeholder="결재 요청 내용을 한 줄로 적어 주세요"
                               value="${fn:escapeXml(form.title)}">
                        <span class="form-hint" data-count-for="title"></span>
                    </div>
                </div>

                <%-- 지출결의·구매요청에서만 보인다. 그 판정은 위 option 의 data 속성이 나른다 --%>
                <div class="form-row" id="amountRow" hidden>
                    <label class="form-label" for="amount">금액</label>
                    <div class="form-field form-field--fixed">
                        <input class="form-input form-input--amount" type="number" id="amount" name="amount"
                               min="0" step="1" placeholder="0" value="${form.amount}">
                        <span class="form-unit">원</span>
                        <span class="form-hint form-hint--inline" id="amountHint"></span>
                    </div>
                </div>
            </section>

            <%--
              docType 이 연차신청일 때만 보이는 영역. hidden 속성으로 감춘다 -
              브라우저는 hidden 요소를 렌더링하지 않으므로 제약 검증도 건너뛴다.
              실제 검증은 서버(ApprovalService)가 한다 - 화면 쪽은 안내일 뿐이다
              (계획서 4 D4).
            --%>
            <section class="doc-form__section leave-fields" id="leaveFields" hidden>
                <h3 class="section-title">연차 신청</h3>

                <p class="leave-fields__balance">
                    <c:choose>
                        <c:when test="${leaveBalance != null}">
                            연차 잔여 <strong><c:out value="${leaveBalance.remainingDays}"/></strong>일
                            <span class="leave-fields__breakdown">
                                (부여 <c:out value="${leaveBalance.grantedDays}"/>일
                                 · 사용 <c:out value="${leaveBalance.usedDays}"/>일)</span>
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
                    <div class="form-field form-field--fixed">
                        <select class="form-input form-input--select" id="leaveType" name="leaveType">
                            <c:forEach items="${leaveTypes}" var="t">
                                <option value="${t.code}"
                                        ${t.code eq form.leaveType ? 'selected' : ''}><c:out value="${t.label}"/></option>
                            </c:forEach>
                        </select>
                    </div>
                </div>

                <%--
                  시작·종료를 한 칸으로 묶는다. 예전에는 form-row 하나에 라벨이 둘이라
                  라벨 고정폭(좌측 열) 규칙과 부딪혀 줄이 벌어졌다. 종료일 라벨은
                  화면에서 지우되 aria-label 로 남긴다 - 눈으로는 "~"로 충분하지만
                  화면 낭독기에는 이름이 필요하다.
                --%>
                <div class="form-row">
                    <label class="form-label" for="startDate">기간</label>
                    <div class="form-field form-field--fixed">
                        <input class="form-input form-input--date" type="date" id="startDate" name="startDate"
                               value="${form.startDate}">
                        <span class="form-range-sep">~</span>
                        <input class="form-input form-input--date" type="date" id="endDate" name="endDate"
                               aria-label="종료일" value="${form.endDate}">
                        <c:choose>
                            <c:when test="${leaveRequest != null}">
                                <span class="form-hint">계산된 일수
                                    <strong><c:out value="${leaveRequest.days}"/></strong>일
                                    — 주말·공휴일은 서버가 제외합니다</span>
                            </c:when>
                            <c:otherwise>
                                <span class="form-hint">주말·공휴일은 일수에서 제외됩니다. 반차는 0.5일로 고정됩니다.</span>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>

                <div class="form-row">
                    <label class="form-label" for="reason">사유</label>
                    <div class="form-field">
                        <textarea class="form-input" id="reason" name="reason" rows="3" maxlength="500"
                                  placeholder="예) 가족 여행"><c:out value="${form.reason}"/></textarea>
                        <span class="form-hint" data-count-for="reason"></span>
                    </div>
                </div>
            </section>

            <section class="doc-form__section">
                <h3 class="section-title">본문</h3>
                <div class="form-row form-row--stack">
                    <label class="form-label" for="content">내용</label>
                    <div class="form-field">
                        <textarea class="form-input doc-form__content" id="content" name="content" rows="12"
                                  placeholder="결재자가 판단에 필요한 내용을 적어 주세요.&#10;금액·기간·대상처럼 숫자로 확인되는 항목은 빠뜨리지 않는 편이 좋습니다."><c:out value="${form.content}"/></textarea>
                    </div>
                </div>
            </section>
        </form>

        <%-- 첨부는 임시저장 상태에서만 다룬다 (기안자 + DRAFT). doc.editable 이 그 판정이다 --%>
        <c:if test="${doc != null and doc.editable}">
            <section class="attach-section">
                <h3 class="section-title">첨부파일</h3>
                <c:choose>
                    <c:when test="${empty attachments}">
                        <p class="attach-list__empty">첨부된 파일이 없습니다.</p>
                    </c:when>
                    <c:otherwise>
                        <ul class="attach-list">
                            <c:forEach items="${attachments}" var="file">
                                <li class="attach-list__item">
                                    <a class="attach-list__link"
                                       href="${pageContext.request.contextPath}/approval/attach/${file.attachId}">
                                        <c:out value="${file.fileName}"/>
                                    </a>
                                    <span class="attach-list__size"><c:out value="${file.fileSizeLabel}"/></span>
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
                        <button class="btn" type="submit">첨부</button>
                    </div>
                </form>
            </section>
        </c:if>

        <c:if test="${saved}">
            <section class="approval-line-box">
                <h3 class="section-title">결재선</h3>
                <c:choose>
                    <c:when test="${empty lines}">
                        <p class="alert alert--info">결재할 상위자가 없어 상신하면 즉시 완료됩니다.</p>
                    </c:when>
                    <c:otherwise>
                        <ul class="approval-line">
                            <c:forEach items="${lines}" var="line">
                                <li class="approval-line__item">
                                    <span class="approval-line__step">${line.stepNo}</span>
                                    <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                                    <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                                    <span class="status status--${fn:toLowerCase(line.status)}">
                                        <c:out value="${line.statusLabel}"/></span>
                                </li>
                            </c:forEach>
                        </ul>
                        <p class="form-hint">직급 체계를 따라 자동으로 만들어집니다.</p>
                    </c:otherwise>
                </c:choose>
            </section>

            <%--
              상신 폼은 CSRF 토큰만 담은 빈 폼이다 - 실제 버튼은 아래 액션 바에 있고
              form 속성으로 이 폼을 가리킨다. 사전점검 스크립트가 이 폼의 submit 을
              가로채는 구조는 그대로다(form 속성으로 눌러도 같은 submit 이벤트가 난다).
            --%>
            <form id="submitForm" class="submit-form" method="post" data-approval-id="${form.approvalId}"
                  action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit" hidden>
                <jsp:include page="../common/csrf-input.jsp"/>
            </form>
        </c:if>

        <div class="doc-actions-bar">
            <a class="btn btn--plain doc-actions-bar__back"
               href="${pageContext.request.contextPath}/approval/box">내 결재함</a>
            <button class="btn" type="submit" form="draftForm">임시저장</button>
            <c:if test="${saved}">
                <button class="btn btn--primary" type="submit" form="submitForm" id="submitBtn">상신</button>
            </c:if>
        </div>

        <%--
          사전점검 모달(계획서 5 Task 5·6) - 상신 폼이 있을 때만 필요하다.
          aiPreflightEnabled(계획서 5 Task 7, 커스터마이징 지점 5)가 꺼져 있으면
          이 모달의 빈 뼈대조차 렌더링하지 않는다 - 꺼진 기능은 화면에 아무 흔적도
          남기지 않는다(오류가 아니라 부재).
        --%>
        <c:if test="${saved and aiPreflightEnabled}">
            <jsp:include page="preflight-modal.jsp"/>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
<script>
    $(function () {
        var $docType = $('#docType');

        /*
         * 고른 문서 유형에 따라 입력칸을 바꾼다.
         * 어떤 유형이 무엇을 쓰는지는 각 option 의 data 속성이 들고 온다 -
         * 이 스크립트는 유형 코드를 하나도 모른다(DocType.usesAmount 주석).
         */
        function applyDocType(userChanged) {
            var $selected = $docType.find('option:selected');
            var usesAmount = $selected.data('uses-amount') === true;
            var usesLeave = $selected.data('uses-leave') === true;

            $('#amountRow').prop('hidden', !usesAmount);
            $('#leaveFields').prop('hidden', !usesLeave);

            /*
             * 금액을 쓰지 않는 유형으로 **사용자가 바꿨을 때만** 값을 비운다.
             * 화면 진입 시점에도 비우면, 지출결의 임시저장 문서를 다시 열었을 때
             * 저장돼 있던 금액이 사라진 채로 다시 저장될 수 있다.
             */
            if (userChanged && !usesAmount) {
                $('#amount').val('');
                updateAmountHint();
            }
        }

        /* 입력한 금액을 천 단위로 끊어 다시 보여준다 - 0 이 몇 개인지 세지 않아도 되게 */
        function updateAmountHint() {
            var value = Number($('#amount').val());
            var readable = ($('#amount').val() !== '' && !isNaN(value) && value > 0)
                    ? value.toLocaleString('ko-KR') + '원'
                    : '';
            $('#amountHint').text(readable);
        }

        /* 글자수 카운터(data-count-for)는 common.js 가 처리한다 - 검토 화면도 같이 쓴다 */

        $docType.on('change', function () { applyDocType(true); });
        $('#amount').on('input', updateAmountHint);
        applyDocType(false);
        updateAmountHint();

        /*
         * 저장하지 않은 수정이 남은 채로 상신하면 그 수정은 사라진다 -
         * 상신은 마지막으로 저장된 내용을 올리기 때문이다. 액션 바에서 저장과
         * 상신이 나란히 있게 되면서 잘못 누르기 쉬워졌으므로 한 번 확인한다.
         */
        var $draftForm = $('#draftForm');
        var savedState = $draftForm.serialize();
        $('#submitBtn').on('click', function (event) {
            if ($draftForm.serialize() !== savedState
                    && !window.confirm('저장하지 않은 수정 내용이 있습니다. 저장하지 않고 상신할까요?')) {
                event.preventDefault();
            }
        });
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
