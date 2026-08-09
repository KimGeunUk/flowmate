<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="부서 근태 현황"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">부서 근태 현황</h2>

        <%-- 본인 부서와 그 하위 부서까지만 나온다 — AttendanceController 가
             deptId 를 요청 파라미터가 아니라 로그인 주체에서만 가져온다 --%>
        <nav class="attendance-month-nav">
            <a class="btn btn--plain" href="${pageContext.request.contextPath}/attendance/dept?ym=${prevYm}">이전 달</a>
            <strong class="attendance-month-nav__current"><c:out value="${yearMonth}"/></strong>
            <a class="btn btn--plain" href="${pageContext.request.contextPath}/attendance/dept?ym=${nextYm}">다음 달</a>
        </nav>

        <table class="attendance-list">
            <thead>
            <tr>
                <th>부서</th><th>이름</th><th>근무일수</th><th>지각</th><th>연장(분)</th><th>연차사용</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${empty rows}">
                    <tr><td class="attendance-list__empty" colspan="6">조회 대상 사원이 없습니다.</td></tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${rows}" var="r">
                        <tr>
                            <td><c:out value="${r.deptName}"/></td>
                            <td><c:out value="${r.empName}"/></td>
                            <td><c:out value="${r.workingDays}"/></td>
                            <td><c:out value="${r.lateCount}"/></td>
                            <td><c:out value="${r.overtimeMinutes}"/></td>
                            <td><c:out value="${r.leaveUsedDays}"/></td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
