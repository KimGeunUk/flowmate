<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="조직도"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">조직도</h2>

        <%--
          들여쓰기는 depth 클래스로만 표현하고 CSS 가 여백을 넣는다.
          중첩 ul 을 만들지 않는 이유: JSP 의 jsp:include 재귀는
          c:set scope="request" 로 변수를 덮어써 부모 루프가 에러 없이 조용히 깨진다.
        --%>
        <ul class="dept-tree">
            <c:forEach items="${deptTree}" var="node">
                <li class="dept-tree__item dept-tree__item--depth${node.depth}">
                    <span class="dept-tree__code"><c:out value="${node.deptCode}"/></span>
                    <span class="dept-tree__name"><c:out value="${node.deptName}"/></span>
                    <span class="dept-tree__count">${node.empCount}명</span>
                </li>
            </c:forEach>
        </ul>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
