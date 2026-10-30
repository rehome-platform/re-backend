package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.OperationScope;
import com.rehome.rehome.entity.enums.OrganizationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/** Các lựa chọn cho form đăng ký tổ chức: loại tổ chức và phạm vi hoạt động. */
@Getter
@AllArgsConstructor
public class OrganizationOptionsResponse {

    private final List<Option> orgTypes;
    private final List<Option> operationScopes;

    @Getter
    @AllArgsConstructor
    public static class Option {
        private final String code;
        private final String name;
    }

    public static OrganizationOptionsResponse build() {
        return new OrganizationOptionsResponse(
                Arrays.stream(OrganizationType.values())
                        .map(t -> new Option(t.name(), t.getDisplayName()))
                        .toList(),
                Arrays.stream(OperationScope.values())
                        .map(s -> new Option(s.name(), s.getDisplayName()))
                        .toList());
    }
}
