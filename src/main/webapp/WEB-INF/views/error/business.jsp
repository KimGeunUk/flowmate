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
        <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">내 결재함으로</a>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
