<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<nav class="lnb">
    <ul class="lnb__group">
        <li class="lnb__group-title">조직</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/employees">사원 목록</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/dept-tree">조직도</a></li>
    </ul>
    <%--
      이후 '전자결재' 그룹(기안 작성 · 내 결재함)과 '근태관리' 그룹(출퇴근 · 부서 현황)을 여기에 추가한다.
      존재하지 않는 화면 링크를 미리 두지 않는다 — 404 가 데모에서 그대로 보인다.
    --%>
</nav>
