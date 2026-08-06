<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  공통 페이징 조각. 사용 조건 두 가지:

    1) request 스코프에 com.flowmate.common.web.Page 객체가 "paging" 이름으로 있어야 한다.
    2) 같은 화면에 <form id="searchForm"> 이 있고 그 안에 <input type="hidden" name="page"> 가 있어야 한다.

  링크 클릭은 static/js/common.js 가 가로채 searchForm 을 다시 submit 한다.
  그래서 이 파일은 검색 조건이 몇 개로 늘어나도 고치지 않는다.

  모델 이름을 "page" 가 아니라 "paging" 으로 쓰는 이유:
  page 는 JSP 스크립팅 암묵 객체 이름과 겹쳐 혼동을 부른다.
--%>
<c:if test="${paging.totalPages > 1}">
    <nav class="pagination">
        <c:if test="${paging.hasPrevBlock}">
            <a class="pagination__link pagination__link--prev" href="#" data-page="${paging.prevBlockPage}">이전</a>
        </c:if>

        <c:forEach begin="${paging.startPage}" end="${paging.endPage}" var="i">
            <c:choose>
                <c:when test="${i eq paging.page}">
                    <strong class="pagination__link pagination__link--current">${i}</strong>
                </c:when>
                <c:otherwise>
                    <a class="pagination__link" href="#" data-page="${i}">${i}</a>
                </c:otherwise>
            </c:choose>
        </c:forEach>

        <c:if test="${paging.hasNextBlock}">
            <a class="pagination__link pagination__link--next" href="#" data-page="${paging.nextBlockPage}">다음</a>
        </c:if>
    </nav>
</c:if>
