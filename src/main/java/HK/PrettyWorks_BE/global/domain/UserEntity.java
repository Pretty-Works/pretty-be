package HK.PrettyWorks_BE.global.domain;

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

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "department", nullable = false, length = 30)
    private String department;

    @Column(name = "position", nullable = false, length = 30)
    private String position;

    //@Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Builder
    public UserEntity(String employeeNo, String name, String email, String phone,
                      String passwordHash, String department, String position,
                      String status, LocalDate hireDate) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.department = department;
        this.position = position;
        this.status = status;
        this.hireDate = hireDate;
    }

}
