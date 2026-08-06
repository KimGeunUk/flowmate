<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  loginEmployee 는 이후 Security 작업에서 @ControllerAdvice 가 모든 화면 모델에 넣어준다.
  Security 배선 전에는 비어 있어 사용자 영역이 렌더링되지 않는다.
  이 파일을 그때 다시 열지 않기 위해 미리 완성해 둔다.
--%>
<header class="gnb">
    <a class="gnb__brand" href="${pageContext.request.contextPath}/">FlowMate</a>
    <c:if test="${not empty loginEmployee}">
        <div class="gnb__user">
            <span class="gnb__user-name"><c:out value="${loginEmployee.empName}"/></span>
            <span class="gnb__user-org">
                <c:out value="${loginEmployee.deptName}"/> · <c:out value="${loginEmployee.positionName}"/>
            </span>
            <form class="gnb__logout" method="post" action="${pageContext.request.contextPath}/logout">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <button class="btn btn--plain" type="submit">로그아웃</button>
            </form>
        </div>
    </c:if>
</header>
