package HK.PrettyWorks_BE.agent.shared.attachment;

/**
 * 첨부 파일을 FastAPI로 실어 보낼 때의 표현 방식입니다.
 *
 * <p>BE는 파일을 해석하지 않고 통과시키는 게이트라, 바이트를 그대로 옮길 방법만 정하면 됩니다.
 * 텍스트 계열은 JSON 문자열에 그대로 담는 편이 LLM이 바로 읽을 수 있어 유리하고,
 * 그렇지 않은 형식은 바이트를 잃지 않게 Base64로 감쌉니다.</p>
 *
 * <p>지금 허용된 형식(txt)은 전부 {@link #TEXT}지만, 설정에서 다른 형식을 열면
 * {@link #BASE64}가 함께 나가기 시작합니다. FastAPI는 이 값만 보고 분기하면 되고,
 * 허용 형식이 늘어도 스키마는 바뀌지 않습니다.</p>
 */
public enum AgentFileEncoding {

    // content 가 파일 원문(UTF-8 디코딩 결과)입니다.
    TEXT,

    // content 가 원본 바이트의 Base64 문자열입니다.
    BASE64
}
