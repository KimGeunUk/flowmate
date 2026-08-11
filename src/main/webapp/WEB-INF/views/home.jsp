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

        <%--
          아침에 홈만 열어도 "오늘 내가 뭘 해야 하는지"가 보여야 한다.
          예전 홈에는 출근 버튼은 있는데 결재 대기 건수가 없어서, 결재함에
          들어가 탭을 눌러 봐야만 알 수 있었다 — 정작 그것을 알아야 할 사람이
          결재함에 들어갈 이유를 모르는 상태였다.

          그래서 이 패널을 맨 위에 둔다. 대기와 반려를 합치지 않고 나눠 두는
          이유는 눌러서 갈 곳이 서로 다르기 때문이다.

          boxCounts 는 ApprovalBoxCountsInterceptor 가 싣는다(모든 화면 공통).
        --%>
        <section class="home-panel">
            <h3 class="home-panel__title">결재 할 일</h3>
            <c:choose>
                <c:when test="${boxCounts == null}">
                    <p class="home-panel__item">결재 건수를 불러오지 못했습니다.</p>
                </c:when>
                <c:when test="${not boxCounts.hasTodo}">
                    <p class="home-panel__item">지금 처리할 결재가 없습니다.</p>
                </c:when>
                <c:otherwise>
                    <div class="home-todo">
                        <c:if test="${boxCounts.pending > 0}">
                            <a class="home-todo__item home-todo__item--urgent"
                               href="${pageContext.request.contextPath}/approval/box?tab=pending">
                                <span class="home-todo__count">${boxCounts.pending}</span>
                                <span class="home-todo__label">내 차례</span>
                            </a>
                        </c:if>
                        <c:if test="${boxCounts.rejected > 0}">
                            <a class="home-todo__item"
                               href="${pageContext.request.contextPath}/approval/box?tab=rejected">
                                <span class="home-todo__count">${boxCounts.rejected}</span>
                                <span class="home-todo__label">반려됨</span>
                            </a>
                        </c:if>
                    </div>
                </c:otherwise>
            </c:choose>
        </section>

        <%--
          출퇴근. 상태 판정(출근/퇴근 여부)은 HomeController 가
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

        <%--
          시스템 상태·모듈은 아래로 내렸다. 개발 중에는 이것이 첫 화면이었지만
          쓰는 사람에게는 "오늘 할 일"보다 뒤에 와야 한다.
        --%>
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
    </main>
</div>
<jsp:include page="common/footer.jsp"/>
</body>
</html>
