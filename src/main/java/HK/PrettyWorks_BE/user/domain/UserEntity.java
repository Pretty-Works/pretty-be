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

}
