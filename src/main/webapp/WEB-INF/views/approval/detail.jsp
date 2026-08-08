<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="문서 상세"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title"><c:out value="${doc.title}"/></h2>

        <table class="doc-detail">
            <tr>
                <th>문서번호</th><td><c:out value="${doc.docNo}"/></td>
                <th>유형</th><td><c:out value="${doc.docTypeLabel}"/></td>
            </tr>
            <tr>
                <th>기안자</th>
                <td><c:out value="${doc.drafterName}"/> · <c:out value="${doc.drafterPositionName}"/>
                    (<c:out value="${doc.deptName}"/>)</td>
                <th>금액</th><td class="doc-detail__amount">${doc.amount}</td>
            </tr>
            <tr>
                <th>상태</th>
                <td><span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.status}"/></span></td>
                <th>기안일</th><td>${doc.draftedAt}</td>
            </tr>
        </table>

        <section class="doc-content">
            <h3 class="page-title">본문</h3>
            <pre class="doc-content__body"><c:out value="${doc.content}"/></pre>
        </section>

        <section class="attach-section">
            <h3 class="page-title">첨부파일</h3>
            <c:choose>
                <c:when test="${empty attachments}">
                    <p class="alert alert--info">첨부된 파일이 없습니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="attach-list">
                        <c:forEach items="${attachments}" var="file">
                            <li class="attach-list__item">
                                <a class="attach-list__link"
                                   href="${pageContext.request.contextPath}/approval/attach/${file.attachId}">
                                    <c:out value="${file.fileName}"/>
                                </a>
                                <span class="attach-list__size">(<c:out value="${file.fileSizeLabel}"/>)</span>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="approval-line-box">
            <h3 class="page-title">결재선</h3>
            <c:choose>
                <c:when test="${empty lines}">
                    <p class="alert alert--info">결재할 상위자가 없는 문서입니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="approval-line">
                        <c:forEach items="${lines}" var="line">
                            <li class="approval-line__item">
                                <span class="approval-line__step">${line.stepNo}</span>
                                <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                                <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                                <span class="status status--${fn:toLowerCase(line.status)}">
                                    <c:out value="${line.status}"/>
                                </span>
                                <c:if test="${not empty line.comment}">
                                    <span class="approval-line__comment"><c:out value="${line.comment}"/></span>
                                </c:if>
                                <c:if test="${line.processedAt != null}">
                                    <span class="approval-line__at">${line.processedAt}</span>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="doc-actions">
            <%-- 버튼 표시 조건은 Service 가 계산해 넘긴 값만 쓴다. JSP 에 규칙을 흩지 않는다 --%>
            <c:if test="${myTurn}">
                <form class="doc-actions__approve" method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/approve">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <div class="form-row">
                        <label class="form-label" for="comment">의견</label>
                        <input class="form-input" type="text" id="comment" name="comment" maxlength="500">
                        <button class="btn btn--primary" type="submit">승인</button>
                    </div>
                </form>
                <button class="btn btn--danger" type="button" id="rejectOpen">반려</button>
            </c:if>

            <c:if test="${doc.editable}">
                <a class="btn btn--plain"
                   href="${pageContext.request.contextPath}/approval/write?approvalId=${doc.approvalId}">수정</a>
            </c:if>

            <c:if test="${canCancel}">
                <form method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/cancel">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <button class="btn btn--plain" type="submit">회수</button>
                </form>
            </c:if>

            <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">목록</a>
        </section>

        <section class="doc-history">
            <h3 class="page-title">이력</h3>
            <ul class="history-list">
                <c:forEach items="${histories}" var="h">
                    <li class="history-list__item">
                        <span class="history-list__at">${h.createdAt}</span>
                        <span class="history-list__actor"><c:out value="${h.actorName}"/></span>
                        <span class="history-list__action"><c:out value="${h.actionLabel}"/></span>
                        <c:if test="${not empty h.comment}">
                            <span class="history-list__comment"><c:out value="${h.comment}"/></span>
                        </c:if>
                    </li>
                </c:forEach>
            </ul>
        </section>

        <c:if test="${myTurn}">
            <jsp:include page="reject-modal.jsp"/>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
