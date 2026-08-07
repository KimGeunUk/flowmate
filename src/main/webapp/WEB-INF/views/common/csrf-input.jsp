<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  POST 폼에 넣을 CSRF hidden input.

  Spring Security 6 은 일반 <form> 에 토큰을 자동 주입하지 않는다.
  이 줄을 빠뜨린 폼은 컴파일·템플릿 단계에서 아무 신호가 없다가 제출 시점에 403 이 된다.
  파일마다 복붙하면 언젠가 한 곳을 빠뜨리므로 조각으로 만들어 include 한다.

  사용법:
    <form method="post" action="...">
        <jsp:include page="../common/csrf-input.jsp"/>
        ...
    </form>
--%>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
