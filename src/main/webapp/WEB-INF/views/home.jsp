<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="common/head.jsp">
    <jsp:param name="pageTitle" value="홈"/>
</jsp:include>
<body>
<jsp:include page="common/header.jsp"/>
<div class="layout">
    <jsp:include page="common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">홈</h2>

        <section class="home-panel">
            <h3 class="home-panel__title">시스템 상태</h3>
            <p class="home-panel__item">
                서버 시각 <fmt:formatDate value="${serverTime}" pattern="yyyy-MM-dd HH:mm:ss"/>
            </p>
            <p class="home-panel__item">DB <c:out value="${dbInfo}"/></p>
        </section>

        <section class="home-panel">
            <h3 class="home-panel__title">모듈</h3>
            <c:forEach items="${modules}" var="m">
                <p class="home-panel__item"><c:out value="${m}"/></p>
            </c:forEach>
        </section>

        <%--
          출퇴근 (계획서 4 Task 3). 상태 판정(출근/퇴근 여부)은 HomeController 가
          이미 boolean(checkedIn/checkedOut)으로 끝내 넘긴다 — 여기서는 그 값만
          읽는다. ${todayAttendance.status == 'LATE'} 같은 비교를 JSP 에 두지 않는다.
        --%>
        <section class="home-panel">
            <h3 class="home-panel__title">출퇴근</h3>
            <p class="home-panel__item attendance-status">
                <c:choose>
                    <c:when test="${checkedOut}">
                        출근 ${todayAttendance.checkIn} · 퇴근 ${todayAttendance.checkOut}
                    </c:when>
                    <c:when test="${checkedIn}">
                        출근 ${todayAttendance.checkIn} · 퇴근 미등록
                    </c:when>
                    <c:otherwise>
                        오늘 출근 기록이 없습니다
                    </c:otherwise>
                </c:choose>
            </p>
            <div class="attendance-actions">
                <form method="post" action="${pageContext.request.contextPath}/attendance/check-in">
                    <jsp:include page="common/csrf-input.jsp"/>
                    <button type="submit" class="btn btn--primary" <c:if test="${checkedIn}">disabled</c:if>>출근</button>
                </form>
                <form method="post" action="${pageContext.request.contextPath}/attendance/check-out">
                    <jsp:include page="common/csrf-input.jsp"/>
                    <button type="submit" class="btn btn--primary" <c:if test="${!checkedIn || checkedOut}">disabled</c:if>>퇴근</button>
                </form>
            </div>
        </section>
    </main>
</div>
<jsp:include page="common/footer.jsp"/>
</body>
</html>
