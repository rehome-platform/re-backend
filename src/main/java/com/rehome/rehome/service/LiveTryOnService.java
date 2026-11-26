package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CompleteLiveTryOnRequest;
import com.rehome.rehome.dto.request.StartLiveTryOnRequest;
import com.rehome.rehome.dto.response.LiveTryOnHistoryItemResponse;
import com.rehome.rehome.dto.response.LiveTryOnQuotaResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionVideoResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface LiveTryOnService {

    LiveTryOnQuotaResponse getQuota(Account account);

    LiveTryOnSessionResponse startSession(Account account, StartLiveTryOnRequest request);

    void confirmFirstFrame(Account account, UUID sessionId);

    void completeSession(Account account, UUID sessionId, CompleteLiveTryOnRequest request);

    LiveTryOnSessionVideoResponse uploadVideo(Account account, UUID sessionId, MultipartFile video);

    PageResponse<LiveTryOnHistoryItemResponse> getHistory(Account account, int page, int size);

    void deleteVideo(Account account, UUID sessionId);
}
