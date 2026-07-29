package HK.PrettyWorks_BE.calendar.leave.domain;

import HK.PrettyWorks_BE.global.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 연차 현황(사용자·연도별 부여일수). 부여일수만 저장하고, 사용/잔여는 저장하지 않고 조회 시 계산한다.
// UNIQUE(user_id, year)는 매년 1/1 부여 배치의 멱등 키 겸용(중복 부여 방지).
@Entity
@Table(name = "leave_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalanceEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 사용자 FK
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 연도. DB는 SMALLINT라 Short로 매핑(ddl-auto=validate 통과 — int면 타입 불일치로 부팅 실패).
    @Column(name = "year", nullable = false)
    private Short year;

    // 그 해 총 부여일수. 사용/잔여는 저장하지 않고 조회 시점에 계산한다.
    @Column(name = "granted_days", nullable = false)
    private int grantedDays;

    @Builder
    public LeaveBalanceEntity(Long userId, Short year, int grantedDays) {
        this.userId = userId;
        this.year = year;
        this.grantedDays = grantedDays;
    }
}
