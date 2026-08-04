package HK.PrettyWorks_BE.user.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import HK.PrettyWorks_BE.user.constant.DepartmentType;
import HK.PrettyWorks_BE.user.constant.GenderType;
import HK.PrettyWorks_BE.user.constant.PositionType;
import HK.PrettyWorks_BE.user.constant.StatusType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_no", nullable = false, length = 20, unique = true)
    private String employeeNo;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private GenderType gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 30)
    private DepartmentType department;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 30)
    private PositionType position;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusType status;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    // 마지막으로 알림함(종 아이콘)을 연 시점의 최신 알림 id. 이보다 큰 알림이 있으면 뱃지가 켜진다.
    // 알림 행마다 "봤음"을 남기지 않는 이유: 뱃지는 사용자당 한 값이면 충분한데, 행마다 기록하면
    // 드롭다운을 열 때마다 쌓인 건수만큼 UPDATE가 나간다. 한 번도 연 적이 없으면 null이다.
    @Column(name = "last_seen_notification_id")
    private Long lastSeenNotificationId;

    @Builder
    public UserEntity(String employeeNo, String passwordHash, String name, String email,
                      String phoneNumber, LocalDate birthDate, GenderType gender,
                      DepartmentType department, PositionType position, StatusType status, LocalDate hireDate) {
        this.employeeNo = employeeNo;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.department = department;
        this.position = position;
        this.status = status;
        this.hireDate = hireDate;
    }

    // 알림함을 연 시점 갱신. 뒤로 물러나지 않게 큰 값일 때만 올린다 —
    // 두 탭에서 거의 동시에 열면 나중 요청이 오래된 id를 들고 올 수 있고, 그러면 이미 끈 뱃지가 다시 켜진다.
    public void markNotificationsSeen(Long latestNotificationId) {
        if (latestNotificationId == null) {
            return;
        }
        if (lastSeenNotificationId == null || latestNotificationId > lastSeenNotificationId) {
            this.lastSeenNotificationId = latestNotificationId;
        }
    }

}
