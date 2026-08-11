/*
 * FlowMate 공용 스크립트.
 * 화면별 스크립트는 각 JSP 하단에 두고, 여기에는 모든 화면에 적용되는 것만 둔다.
 */

/*
 * ★ flowmateFetch(url, options) — CSRF 헤더를 붙이는 fetch 래퍼 (
 * Phase 1부터 이월된 항목).
 *
 * 왜 필요한가: 아래 $.ajaxSetup 은 jQuery 의 AJAX 경로에만 적용된다. jQuery 는 매
 * 요청 전에 beforeSend 훅을 부르므로 헤더를 자동으로 붙일 수 있지만, 전역 fetch()
 * 는 jQuery 를 전혀 거치지 않는 별개의 브라우저 API 라서 이 훅을 타지 않는다.
 * 그 결과 fetch 로 보낸 POST 는 CSRF 헤더가 없어 Spring Security 가 조용히 403 을
 * 돌려준다 - 에러 메시지가 "CSRF" 라고 말해 주지 않으므로 원인을 알아내기 어렵다.
 *
 * 사전점검 모달(preflight-modal.jsp)처럼 서버 응답을 기다렸다가 그 결과로 분기해야
 * 하는 흐름은 <form> 제출보다 fetch 가 자연스러운데, 그 fetch 호출마다 헤더를
 * 손으로 붙이면 다음 사람이 새 호출부를 추가할 때 반드시 빠뜨린다. 그래서 개별
 * 호출부가 아니라 이 래퍼 하나에 배선한다 - fetch 를 쓰는 곳은 전부 이것을 거친다.
 *
 * 토큰은 $.ajaxSetup 과 같은 출처(head.jsp 의 meta 태그)에서 읽는다 - 값이 어긋날
 * 여지가 없다.
 */
function flowmateFetch(url, options) {
    var opts = options || {};
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');

    var headers = {};
    $.extend(headers, opts.headers || {});
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    opts.headers = headers;

    return fetch(url, opts);
}

$(function () {

    /*
     * CSRF: Spring Security 는 POST/PUT/DELETE 에 토큰을 요구한다.
     * <form> 은 각 JSP 가 hidden input 으로 직접 넣지만, AJAX 는 헤더로 보내야 한다.
     * head.jsp 의 meta 태그에서 값을 읽어 모든 AJAX 요청에 자동으로 붙인다.
     * (AI 호출이 전부 AJAX 이므로 여기서 한 번 배선해 둔다.)
     */
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');
    if (csrfToken && csrfHeader) {
        $.ajaxSetup({
            beforeSend: function (xhr) {
                xhr.setRequestHeader(csrfHeader, csrfToken);
            }
        });
    }

    /*
     * 페이징: common/pagination.jsp 가 그린 링크를 가로채
     * 같은 화면의 #searchForm 을 다시 submit 한다.
     *
     * 링크에 검색 조건을 직접 붙이지 않는 이유:
     * 조건이 늘어날 때마다 URL 조립과 인코딩을 손봐야 하고, 그 작업이 화면마다 반복된다.
     * 폼을 다시 보내면 조건이 몇 개든 pagination.jsp 를 고치지 않는다.
     */
    $(document).on('click', '.pagination__link[data-page]', function (event) {
        event.preventDefault();
        var targetPage = $(this).data('page');
        var $form = $('#searchForm');
        if ($form.length === 0) {
            return;
        }
        $form.find('input[name="page"]').val(targetPage);
        $form.trigger('submit');
    });

    /*
     * 내 결재함 탭. 페이징과 같은 이유로 링크가 아니라 폼 재전송이다.
     * 탭을 바꾸면 페이지는 1로 되돌린다 — 3페이지에서 탭을 옮기면
     * 그 탭에 3페이지가 없어 빈 화면이 된다.
     */
    $(document).on('click', '.box-tabs__link[data-tab]', function (event) {
        event.preventDefault();
        var $form = $('#searchForm');
        if ($form.length === 0) {
            return;
        }
        $form.find('input[name="tab"]').val($(this).data('tab'));
        $form.find('input[name="page"]').val(1);
        $form.trigger('submit');
    });

    /*
     * 글자수 카운터. maxlength 가 있는 칸 옆에 <span data-count-for="칸id"> 를
     * 두면 여기서 채운다 — 기안 작성의 제목·사유, 결재 검토의 의견이 쓴다.
     *
     * 화면마다 같은 코드를 다시 쓰지 않으려고 공용으로 올렸다. 규약은 하나뿐이다:
     * data-count-for 가 가리키는 칸에 maxlength 가 있어야 한다.
     */
    $('[data-count-for]').each(function () {
        var $hint = $(this);
        var $field = $('#' + $hint.attr('data-count-for'));
        var max = $field.attr('maxlength');
        if ($field.length === 0 || !max) {
            return;
        }
        function update() {
            $hint.text($field.val().length + ' / ' + max + '자');
        }
        $field.on('input', update);
        update();
    });

    /*
     * 반려 모달.
     *
     * ★ 검토란의 의견(#comment)과 모달의 의견(#reasonText)은 **같은 칸**이다 —
     *   승인이든 반려든 approval_line.comment / approval_history.comment 라는
     *   같은 자리에 저장된다. 그래서 열고 닫을 때 값을 서로 옮겨 하나처럼
     *   움직이게 한다.
     *
     *   예전에는 옮기지 않았다. 결재자가 검토란에 사유를 적고 [반려]를 누르면
     *   모달이 빈 칸으로 떠서 처음부터 다시 타이핑해야 했다.
     */
    $(document).on('click', '#rejectOpen', function () {
        $('#reasonText').val($('#comment').val());
        $('#rejectModal').prop('hidden', false);
        $('#reasonCategory').trigger('focus');
    });
    $(document).on('click', '#rejectCancel', function () {
        /*
         * 되돌려 담는다. [취소]가 취소하는 것은 **반려라는 행위**이지 방금
         * 친 글이 아니다 — 모달에서 다듬은 문장이 사라지면 한 칸이라는
         * 약속이 깨진다. 승인으로 마음을 바꾼 결재자는 그 문장을 그대로
         * 승인 의견으로 쓸 수 있어야 한다.
         */
        $('#comment').val($('#reasonText').val()).trigger('input');
        $('#rejectModal').prop('hidden', true);
    });
});
