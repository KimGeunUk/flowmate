<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="오류"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title"><c:out value="${errorTitle}"/></h2>
        <p class="alert alert--error"><c:out value="${errorMessage}"/></p>
        <%-- 홈으로 고정한다 — 이 화면은 approval 뿐 아니라 attendance 의 업무
             예외도 같이 받는다(GlobalExceptionHandler).
             모듈마다 다른 대상으로 보내려면 errorTitle 처럼 모델 값을 하나
             더 늘려야 하는데, 지금은 그 구분이 필요할 만큼 화면이 갈리지 않는다 --%>
        <a class="btn btn--plain" href="${pageContext.request.contextPath}/">홈으로</a>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
