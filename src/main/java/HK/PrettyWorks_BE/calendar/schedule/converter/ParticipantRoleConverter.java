package HK.PrettyWorks_BE.calendar.schedule.converter;

import HK.PrettyWorks_BE.calendar.schedule.constant.ParticipantRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * schedule_participants.role 는 DB에서 BOOLEAN(1: WRITER, 0: PARTICIPANT)으로 저장된다.
 * 코드에서는 의미가 분명한 ParticipantRole enum으로 다루고, 이 컨버터가 둘 사이를 자동 변환한다.
 */
@Converter
public class ParticipantRoleConverter implements AttributeConverter<ParticipantRole, Boolean> {

    // 엔티티 → DB : WRITER면 true(1), PARTICIPANT면 false(0)
    @Override
    public Boolean convertToDatabaseColumn(ParticipantRole role) {
        return role == ParticipantRole.WRITER;
    }

    // DB → 엔티티 : true면 WRITER, 그 외(false/null)는 PARTICIPANT
    @Override
    public ParticipantRole convertToEntityAttribute(Boolean dbValue) {
        return Boolean.TRUE.equals(dbValue) ? ParticipantRole.WRITER : ParticipantRole.PARTICIPANT;
    }
}
