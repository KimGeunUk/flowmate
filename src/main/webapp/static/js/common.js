/*
 * FlowMate 공용 스크립트.
 * 화면별 스크립트는 각 JSP 하단에 두고, 여기에는 모든 화면에 적용되는 것만 둔다.
 */
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
});
