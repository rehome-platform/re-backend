package com.rehome.rehome.generator;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.EnumSet;

/** Sinh mã nhân viên Moderator dạng MOD000001 từ sequence moderator_code_seq (giống MemberCodeGenerator). */
public class ModeratorCodeGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
                           Object currentValue, EventType eventType) {
        return session.doReturningWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT nextval('moderator_code_seq')");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return String.format("MOD%06d", rs.getLong(1));
            }
        });
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
