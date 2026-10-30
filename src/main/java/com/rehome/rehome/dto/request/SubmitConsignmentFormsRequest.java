package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitConsignmentFormsRequest {

    @NotNull(message = "warehouseId không được để trống")
    private UUID warehouseId;

    @NotEmpty(message = "formIds không được để trống")
    @Size(max = 20, message = "formIds tối đa 20 phần tử")
    private List<@NotNull(message = "formIds chứa giá trị rỗng") UUID> formIds;
}
