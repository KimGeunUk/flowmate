<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  <head> 전체를 담당한다. 각 화면은 아래처럼 제목을 넘긴다.
    <jsp:include page="../common/head.jsp">
        <jsp:param name="pageTitle" value="사원 목록"/>
    </jsp:include>

  _csrf meta 두 개는 static/js/common.js 가 AJAX 헤더로 쓴다.
  Security 배선 전에는 값이 비어 있고, 배선 후 자동으로 채워진다.
--%>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="_csrf" content="${_csrf.token}">
    <meta name="_csrf_header" content="${_csrf.headerName}">
    <title><c:out value="${param.pageTitle}"/> · FlowMate</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/style.css">
    <script src="${pageContext.request.contextPath}/static/js/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/static/js/common.js"></script>
</head>
