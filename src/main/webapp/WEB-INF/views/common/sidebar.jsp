<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<nav class="lnb">
    <ul class="lnb__group">
        <li class="lnb__group-title">조직</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/employees">사원 목록</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/dept-tree">조직도</a></li>
    </ul>
    <ul class="lnb__group">
        <li class="lnb__group-title">전자결재</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/approval/write">기안 작성</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/approval/box">내 결재함</a></li>
    </ul>
    <ul class="lnb__group">
        <li class="lnb__group-title">근태관리</li>
        <%-- 출퇴근 등록은 홈의 버튼으로 처리한다(Task 3) — 별도 화면 링크가 없다 --%>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/attendance/my">내 근태</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/attendance/dept">부서 근태 현황</a></li>
    </ul>
</nav>
