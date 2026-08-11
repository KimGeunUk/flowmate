<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  상신 전 사전 점검 모달.

  reject-modal.jsp 와 달리 이 모달의 내용(findings 목록)은 서버 렌더링이 아니라
  write.jsp 하단 스크립트가 상신 클릭 시점에 /api/ai/approvals/{id}/preflight 를
  불러 채운다 - 점검은 "지금 이 순간의 본문"에 대해 매번 새로 돌아야 하므로
  (사전점검을 캐시하지 않는 이유와 같다) 화면 진입 시점에 미리 만들어 둘 수 없다.
  그래서 이 JSP 는 빈 뼈대만 그린다.

  버튼 2개:
    수정하러 가기 - 모달을 닫는다. 이미 이 화면(작성/수정 화면)에 본문이 있으므로
                    별도 이동이 필요 없다 - 사용자가 textarea 를 고친 뒤 상신을
                    다시 누르면 된다.
    무시하고 상신 - ai_preflight_result.ignored_yn = 'Y' 로 기록한 뒤 상신한다.

  findings 각 항목은 basedOnRejectCount 를 반드시 함께 보여준다 - "과거 N건에
  근거함"이라는 숫자가 이 기능 전체의 존재 이유다.
--%>
<div class="preflight-modal" id="preflightModal" hidden>
    <div class="preflight-modal__panel">
        <h3 class="preflight-modal__title">상신 전 확인</h3>
        <p class="preflight-modal__intro">
            과거 반려 이력에 근거해 아래 사항을 확인해 주세요. 무시하고 상신할 수도 있습니다.
        </p>
        <ul class="preflight-modal__findings" id="preflightFindings"></ul>
        <div class="form-row">
            <button class="btn btn--plain" type="button" id="preflightFix">수정하러 가기</button>
            <button class="btn btn--danger" type="button" id="preflightIgnore">무시하고 상신</button>
        </div>
    </div>
</div>
