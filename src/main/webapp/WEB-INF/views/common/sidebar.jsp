<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  boxCounts 는 ApprovalBoxCountsInterceptor 가 모든 화면 모델에 싣는다 —
  각 컨트롤러가 따로 넣지 않는다. 집계에 실패하면 아예 실리지 않으므로
  아래 조건은 자연히 거짓이 되고 배지만 사라진다(화면은 그대로 그려진다).
--%>
<nav class="lnb">
    <ul class="lnb__group">
        <li class="lnb__group-title">조직</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/employees">사원 목록</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/org/dept-tree">조직도</a></li>
    </ul>
    <ul class="lnb__group">
        <li class="lnb__group-title">전자결재</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/approval/write">기안 작성</a></li>
        <%--
          배지에는 "대기"만 센다. 반려도 내 할 일이지만 성격이 다르다 —
          대기는 **남이 나를 기다리는** 건수라 미루면 다른 사람의 일이 멈춘다.
          그리고 숫자 하나에 둘을 합치면 눌러서 갈 곳이 한 군데로 정해지지
          않는다. 반려는 홈의 "결재 할 일" 패널에서 따로 보여준다.
        --%>
        <li>
            <a class="lnb__link" href="${pageContext.request.contextPath}/approval/box">내 결재함
                <c:if test="${boxCounts.pending > 0}">
                    <span class="lnb__badge" title="결재 대기 ${boxCounts.pending}건">${boxCounts.pending}</span>
                </c:if>
            </a>
        </li>
    </ul>
    <ul class="lnb__group">
        <li class="lnb__group-title">근태관리</li>
        <%-- 출퇴근 등록은 홈의 버튼으로 처리한다(Task 3) — 별도 화면 링크가 없다 --%>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/attendance/my">내 근태</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/attendance/dept">부서 근태 현황</a></li>
    </ul>
</nav>
