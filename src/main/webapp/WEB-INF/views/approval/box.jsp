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
        --%>
        <ul class="box-tabs">
            <c:forEach items="${['drafted','pending','done','rejected']}" var="t">
                <li class="box-tabs__item">
                    <a class="box-tabs__link ${t eq cond.tab ? 'box-tabs__link--active' : ''}"
                       href="#" data-tab="${t}">
                        <c:choose>
                            <c:when test="${t eq 'drafted'}">기안</c:when>
                            <c:when test="${t eq 'pending'}">대기</c:when>
                            <c:when test="${t eq 'done'}">완료</c:when>
                            <c:otherwise>반려</c:otherwise>
                        </c:choose>
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

        <p class="result-count">전체 <strong>${paging.totalCount}</strong>건</p>

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
                                    <c:out value="${d.status}"/>
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
