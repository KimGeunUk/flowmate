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
    </main>
</div>
<jsp:include page="common/footer.jsp"/>
</body>
</html>
