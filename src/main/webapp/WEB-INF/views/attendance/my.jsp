<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="내 근태"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">내 근태</h2>

        <%-- 월 이동은 ?ym=2026-08 링크다. 검색 조건이 하나뿐이라 approval/box 처럼
             hidden form 을 다시 제출하는 방식 대신 단순 링크로 충분하다 --%>
        <nav class="attendance-month-nav">
            <a class="btn btn--plain" href="${pageContext.request.contextPath}/attendance/my?ym=${prevYm}">이전 달</a>
            <strong class="attendance-month-nav__current"><c:out value="${yearMonth}"/></strong>
            <a class="btn btn--plain" href="${pageContext.request.contextPath}/attendance/my?ym=${nextYm}">다음 달</a>
        </nav>

        <%-- 합계 4종(설계서 §6.3): 근무일수·지각·연장·연차사용. 전부
             AttendanceMonthlySummary(Service 계층)가 계산한 값이고 JSP 는 읽기만 한다 --%>
        <section class="attendance-summary">
            <p class="attendance-summary__item">근무일수 <strong><c:out value="${summary.workingDays}"/></strong>일</p>
            <p class="attendance-summary__item">지각 <strong><c:out value="${summary.lateCount}"/></strong>회</p>
            <p class="attendance-summary__item">연장근무 <strong><c:out value="${summary.overtimeMinutes}"/></strong>분</p>
            <p class="attendance-summary__item">연차사용 <strong><c:out value="${summary.leaveUsedDays}"/></strong>일</p>
        </section>

        <table class="attendance-list">
            <thead>
            <tr>
                <th>날짜</th><th>출근</th><th>퇴근</th><th>근무(분)</th><th>연장(분)</th><th>상태</th><th>비고</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${empty summary.rows}">
                    <tr><td class="attendance-list__empty" colspan="7">이 달의 근태 기록이 없습니다.</td></tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${summary.rows}" var="row">
                        <tr>
                            <td>${row.workDate}</td>
                            <td>${row.checkIn}</td>
                            <td>${row.checkOut}</td>
                            <td><c:out value="${row.workMinutes}"/></td>
                            <td><c:out value="${row.overtimeMinutes}"/></td>
                            <td>
                                <span class="status status--${fn:toLowerCase(row.status)}">
                                    <c:out value="${row.statusLabel}"/>
                                </span>
                            </td>
                            <td><c:out value="${row.note}"/></td>
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
