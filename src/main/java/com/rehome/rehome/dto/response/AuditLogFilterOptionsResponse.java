package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuditLogFilterOptionsResponse {
    private final List<ActorOption> actors;
    private final List<String> actionTypes;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ActorOption {
        private final UUID id;
        private final String name;
        private final String email;
        private final Role role;
    }
}
