<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="사원 목록"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">사원 목록</h2>

        <%--
          id="searchForm" 과 hidden page 는 common/pagination.jsp 의 사용 조건이다.
          이름을 바꾸면 페이징 링크가 에러 없이 조용히 동작하지 않는다.
        --%>
        <form id="searchForm" class="search-form" method="get"
              action="${pageContext.request.contextPath}/org/employees">
            <input type="hidden" name="page" value="${paging.page}">
            <div class="form-row">
                <label class="form-label" for="deptId">부서</label>
                <select class="form-input" id="deptId" name="deptId">
                    <option value="">전체</option>
                    <c:forEach items="${deptOptions}" var="d">
                        <option value="${d.deptId}" ${d.deptId eq cond.deptId ? 'selected' : ''}>
                            <c:out value="${d.deptName}"/>
                        </option>
                    </c:forEach>
                </select>

                <label class="form-label" for="keyword">검색</label>
                <input class="form-input" type="text" id="keyword" name="keyword"
                       value="${fn:escapeXml(cond.keyword)}" placeholder="이름 또는 사원번호">

                <button class="btn btn--primary" type="submit">검색</button>
            </div>
        </form>

        <p class="result-count">전체 <strong>${paging.totalCount}</strong>명</p>

        <table class="emp-list">
            <thead>
            <tr>
                <th>사원번호</th>
                <th>이름</th>
                <th>부서</th>
                <th>직급</th>
                <th>입사일</th>
                <th>이메일</th>
            </tr>
            </thead>
            <tbody>
            <%--
              "결과 없음" 판정은 paging.empty 가 아니라 totalCount 로 한다.
              Service 가 페이지를 보정하므로 지금은 둘이 같은 뜻이지만,
              totalCount 가 "검색 조건에 맞는 행이 정말 없다" 를 직접 말하는 값이다.
            --%>
            <c:choose>
                <c:when test="${paging.totalCount == 0}">
                    <tr>
                        <td class="emp-list__empty" colspan="6">조회 결과가 없습니다.</td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${paging.content}" var="emp">
                        <tr>
                            <td><c:out value="${emp.empNo}"/></td>
                            <td><c:out value="${emp.empName}"/></td>
                            <td><c:out value="${emp.deptName}"/></td>
                            <td><c:out value="${emp.positionName}"/></td>
                            <%-- LocalDate.toString() 이 이미 yyyy-MM-dd 이므로 fmt 태그가 필요 없다 --%>
                            <td>${emp.hireDate}</td>
                            <td><c:out value="${emp.email}"/></td>
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
