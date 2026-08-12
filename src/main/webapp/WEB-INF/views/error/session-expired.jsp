<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  세션이 끊긴 채로 폼을 제출했을 때의 화면.

  ★ header/sidebar 를 넣지 않는다. 그 조각들은 로그인한 사용자를 전제로
    이름과 메뉴를 그리는데, 여기 오는 사람은 이미 세션이 없다. 로그인
    화면과 같은 독립 레이아웃을 쓴다.
--%>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="세션 만료"/>
</jsp:include>
<body class="login-page">
<div class="login-box">
    <h1 class="login-box__title">FlowMate</h1>
    <p class="login-box__subtitle">세션이 만료되었습니다</p>

    <p class="alert alert--info">
        로그인 후 시간이 지났거나 서버가 다시 시작되어 연결이 끊어졌습니다.
        <strong>작성 중이던 내용은 저장되지 않았습니다.</strong>
        다시 로그인한 뒤 이어서 작성해 주세요.
    </p>

    <%-- 폭은 .login-box .btn--primary 규칙이 100% 로 잡아 준다(로그인 버튼과 같은 모양) --%>
    <a class="btn btn--primary"
       href="${pageContext.request.contextPath}/login">로그인 화면으로</a>
</div>
</body>
</html>
