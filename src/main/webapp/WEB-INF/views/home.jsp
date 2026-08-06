<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>FlowMate</title>
</head>
<body>
<h1>FlowMate</h1>
<p>서버 시각: <fmt:formatDate value="${serverTime}" pattern="yyyy-MM-dd HH:mm:ss"/></p>
<ul>
    <c:forEach items="${modules}" var="m">
        <li><c:out value="${m}"/></li>
    </c:forEach>
</ul>
</body>
</html>
