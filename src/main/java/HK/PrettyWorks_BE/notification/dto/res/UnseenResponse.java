package HK.PrettyWorks_BE.notification.dto.res;

// 종 아이콘의 빨간 점 유무. 30초마다 호출된다.
// 개수를 주지 않는 이유: 화면이 점만 찍으므로 셀 필요가 없고, 숫자를 내려주면
// 뱃지에 그대로 찍혀 "드롭다운을 열면 꺼진다"는 규칙과 어긋나는 화면이 만들어진다.
public record UnseenResponse(boolean hasUnseen) {
}
