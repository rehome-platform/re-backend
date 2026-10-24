package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.service.CounterService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/counter")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
public class CounterController {

    private final CounterService counterService;

    // API 25
    @PostMapping("/lookup")
    public ApiResponse<List<MemberLookupItemResponse>> lookup(@RequestBody CounterLookupRequest request) {
        return ApiResponse.success(counterService.lookup(request));
    }

    // API 27
    @GetMapping("/members/{accountId}/tasks")
    public ApiResponse<MemberTasksResponse> tasks(@PathVariable UUID accountId) {
        return ApiResponse.success(counterService.getTasks(accountId));
    }
}