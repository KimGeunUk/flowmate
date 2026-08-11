<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="내 결재함"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">내 결재함</h2>

        <%--
          탭. 링크가 아니라 hidden tab 을 바꿔 #searchForm 을 다시 보낸다.
          링크로 만들면 현재 검색어·유형이 URL 조립 대상이 되어 pagination.jsp 와
          같은 문제를 반복하게 된다.

          ★ 탭 이름·건수·"할 일 여부"는 전부 서버가 만들어 넘긴다(BoxTab.options).
            예전에는 여기서 c:choose 로 'drafted'→'기안' 을 골랐는데, 그러면 탭을
            하나 추가할 때 화면을 같이 고쳐야 하고 그걸 강제하는 장치가 없다 —
            문서 유형·연차 유형·반려 유형에서 반복해서 겪은 함정이다.

          ★ 배지 숫자는 검색 조건을 따르지 않는다. "내가 처리해야 할 총량"이지
            "지금 검색 결과의 분포"가 아니기 때문이다(ApprovalBoxCounts 주석).
            아래 목록 위의 건수는 반대로 검색을 따라간다 — 두 숫자가 다르게
            움직이는 것이 정상이라, 이름으로 구분해 둔다.
        --%>
        <ul class="box-tabs">
            <c:forEach items="${boxTabs}" var="tab">
                <li class="box-tabs__item">
                    <a class="box-tabs__link ${tab.code eq cond.tab ? 'box-tabs__link--active' : ''} ${tab.todo and tab.count > 0 ? 'box-tabs__link--todo' : ''}"
                       href="#" data-tab="${tab.code}">
                        <c:out value="${tab.label}"/>
                        <span class="box-tabs__count">${tab.count}</span>
                    </a>
                </li>
            </c:forEach>
        </ul>

        <form id="searchForm" class="search-form" method="get"
              action="${pageContext.request.contextPath}/approval/box">
            <input type="hidden" name="page" value="${paging.page}">
            <input type="hidden" name="tab" id="tab" value="${fn:escapeXml(cond.tab)}">
            <div class="form-row">
                <label class="form-label" for="docType">유형</label>
                <select class="form-input" id="docType" name="docType">
                    <option value="">전체</option>
                    <c:forEach items="${docTypes}" var="t">
                        <option value="${t.code}" ${t.code eq cond.docType ? 'selected' : ''}><c:out value="${t.label}"/></option>
                    </c:forEach>
                </select>
                <label class="form-label" for="keyword">검색</label>
                <input class="form-input" type="text" id="keyword" name="keyword"
                       value="${fn:escapeXml(cond.keyword)}" placeholder="제목 또는 문서번호">
                <button class="btn btn--primary" type="submit">검색</button>
            </div>
        </form>

        <%--
          이 숫자는 "현재 탭 + 현재 검색 조건"의 건수다. 예전에는 "전체 N건"이라고
          적혀 있었는데, 기안 탭에서 "전체 6건"을 보면 시스템 전체인지 내 기안인지
          알 수 없었고, 검색어를 넣으면 숫자가 바뀌는데도 이름은 "전체"였다.
          검색 중일 때와 아닐 때를 나눠 이름을 정직하게 붙인다.
        --%>
        <p class="result-count">
            <c:choose>
                <c:when test="${not empty cond.keyword or not empty cond.docType}">
                    검색 결과 <strong>${paging.totalCount}</strong>건
                </c:when>
                <c:otherwise>
                    <c:out value="${boxTabLabel}"/> <strong>${paging.totalCount}</strong>건
                </c:otherwise>
            </c:choose>
        </p>

        <table class="doc-list">
            <thead>
            <tr>
                <th>문서번호</th><th>유형</th><th>제목</th><th>금액</th>
                <th>기안자</th><th>기안일</th><th>상태</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${paging.totalCount == 0}">
                    <tr><td class="doc-list__empty" colspan="7">조회 결과가 없습니다.</td></tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${paging.content}" var="d">
                        <tr>
                            <td>
                                <a class="doc-list__link"
                                   href="${pageContext.request.contextPath}/approval/${d.approvalId}">
                                    <c:out value="${d.docNo}"/>
                                </a>
                            </td>
                            <td><c:out value="${d.docTypeLabel}"/></td>
                            <td><c:out value="${d.title}"/></td>
                            <td class="doc-list__amount">${d.amount}</td>
                            <td><c:out value="${d.drafterName}"/></td>
                            <td>${d.draftedAt}</td>
                            <td>
                                <span class="status status--${fn:toLowerCase(d.status)}">
                                    <c:out value="${d.statusLabel}"/>
                                </span>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>

        <jsp:include page="../common/pagination.jsp"/>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
