<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="common/head.jsp">
    <jsp:param name="pageTitle" value="로그인"/>
</jsp:include>
<body class="login-page">
<div class="login-box">
    <h1 class="login-box__title">FlowMate</h1>
    <p class="login-box__subtitle">AI 사전점검 그룹웨어</p>

    <c:if test="${param.error != null}">
        <p class="alert alert--error">사원번호 또는 비밀번호가 올바르지 않습니다.</p>
    </c:if>
    <c:if test="${param.logout != null}">
        <p class="alert alert--info">로그아웃되었습니다.</p>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/login">
        <%--
          Spring Security 6 은 일반 form 에 CSRF 토큰을 자동 주입하지 않는다.
          이 hidden input 이 없으면 로그인 POST 가 403 으로 막힌다.
        --%>
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>

        <div class="form-row">
            <label class="form-label" for="empNo">사원번호</label>
            <input class="form-input" type="text" id="empNo" name="empNo" autofocus required>
        </div>
        <div class="form-row">
            <label class="form-label" for="password">비밀번호</label>
            <input class="form-input" type="password" id="password" name="password" required>
        </div>
        <button class="btn btn--primary" type="submit">로그인</button>
    </form>
</div>
</body>
</html>
