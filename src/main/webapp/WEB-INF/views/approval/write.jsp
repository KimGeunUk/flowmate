<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="기안 작성"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">
            <c:choose>
                <c:when test="${form.approvalId != null}">기안 수정</c:when>
                <c:otherwise>기안 작성</c:otherwise>
            </c:choose>
        </h2>

        <c:if test="${doc != null}">
            <p class="result-count">
                문서번호 <strong><c:out value="${doc.docNo}"/></strong>
                · 상태 <span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.status}"/></span>
            </p>
        </c:if>

        <form class="doc-form" method="post" action="${pageContext.request.contextPath}/approval/draft">
            <jsp:include page="../common/csrf-input.jsp"/>
            <c:if test="${form.approvalId != null}">
                <input type="hidden" name="approvalId" value="${form.approvalId}">
            </c:if>

            <div class="form-row">
                <label class="form-label" for="docType">문서 유형</label>
                <select class="form-input" id="docType" name="docType" required>
                    <c:forEach items="${docTypes}" var="t">
                        <option value="${t}" ${t eq form.docType ? 'selected' : ''}>
                            <c:out value="${t}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>

            <div class="form-row">
                <label class="form-label" for="title">제목</label>
                <input class="form-input" type="text" id="title" name="title" maxlength="200" required
                       value="${fn:escapeXml(form.title)}">
            </div>

            <div class="form-row">
                <label class="form-label" for="amount">금액</label>
                <input class="form-input" type="number" id="amount" name="amount" min="0" step="1"
                       value="${form.amount}">
            </div>

            <div class="form-row">
                <label class="form-label" for="content">본문</label>
                <textarea class="form-input" id="content" name="content" rows="10"><c:out value="${form.content}"/></textarea>
            </div>

            <div class="form-row">
                <button class="btn btn--primary" type="submit">임시저장</button>
                <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">내 결재함</a>
            </div>
        </form>

        <c:if test="${not empty lines}">
            <h3 class="page-title">자동 생성된 결재선</h3>
            <ul class="approval-line">
                <c:forEach items="${lines}" var="line">
                    <li class="approval-line__item">
                        <span class="approval-line__step">${line.stepNo}</span>
                        <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                        <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                        <span class="status status--${fn:toLowerCase(line.status)}"><c:out value="${line.status}"/></span>
                    </li>
                </c:forEach>
            </ul>
            <form method="post" action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>

        <c:if test="${form.approvalId != null and empty lines}">
            <p class="alert alert--info">결재할 상위자가 없어 상신하면 즉시 완료됩니다.</p>
            <form method="post" action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>

        <%-- 첨부는 임시저장 상태에서만 다룬다 (기안자 + DRAFT). doc.editable 이 그 판정이다 --%>
        <c:if test="${doc != null and doc.editable}">
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
                                    <form method="post"
                                          action="${pageContext.request.contextPath}/approval/attach/${file.attachId}/delete">
                                        <jsp:include page="../common/csrf-input.jsp"/>
                                        <button class="btn btn--plain" type="submit">삭제</button>
                                    </form>
                                </li>
                            </c:forEach>
                        </ul>
                    </c:otherwise>
                </c:choose>

                <form class="attach-form" method="post" enctype="multipart/form-data"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/attach">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <div class="form-row">
                        <input class="form-input" type="file" name="file" required>
                        <button class="btn btn--primary" type="submit">첨부</button>
                    </div>
                </form>
            </section>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
