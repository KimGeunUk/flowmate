package com.flowmate.approval.domain;

/**
 * 결재 문서 입력 값의 상한. **세 곳이 같은 숫자를 봐야 한다** — 화면의 maxlength,
 * DB 컬럼 크기, 그리고 서버 검증.
 *
 * ★ 이 클래스가 생긴 이유: 화면과 DB 는 맞아 있었는데 **서버 검증이 아예 없었다.**
 *   화면을 우회해 201자짜리 제목을 보내면 DB 제약 위반이 그대로 올라와 500 이 됐다.
 *   그 예외는 Spring 의 DataIntegrityViolationException 이라
 *   {@code GlobalExceptionHandler} 가 잡는 IllegalArgumentException 계열도 아니어서,
 *   사용자에게는 원인이 전혀 보이지 않았다.
 *
 *   여기서 던지는 IllegalArgumentException 은 이미 있는 예외 처리 경로를 타므로
 *   "제목은 200자를 넘을 수 없습니다"가 화면에 나온다.
 *
 * ★ CONTENT 만 DB 가 TEXT 라 제약이 없다. 그래도 상한을 두는 이유는 이 값이
 *   AI 프롬프트로 그대로 들어가기 때문이다 — 상한이 없으면 문서 하나가 토큰
 *   비용을 얼마든지 끌어올릴 수 있다. {@code ApprovalLimitsIT} 가 나머지 셋은
 *   실제 DB 컬럼 크기와 대조한다.
 */
public final class ApprovalLimits {

    /** approval_doc.title VARCHAR(200) */
    public static final int TITLE = 200;

    /** approval_doc.content TEXT — DB 제약은 없고 프롬프트 크기를 위해 둔 값 */
    public static final int CONTENT = 10_000;

    /** leave_request.reason VARCHAR(500) */
    public static final int REASON = 500;

    /** approval_line.comment · approval_history.comment VARCHAR(500) */
    public static final int COMMENT = 500;

    private ApprovalLimits() {
    }

    /**
     * 길이를 넘으면 거부한다. null 은 통과시킨다 — 값이 있어야 하는지는
     * 필수 검증이 따로 판단할 일이고, 여기서는 길이만 본다.
     */
    public static void check(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                    fieldName + " 길이가 " + max + "자를 넘습니다 (현재 " + value.length() + "자)");
        }
    }
}
