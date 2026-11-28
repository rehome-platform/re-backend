package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.LegalFileDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LegalFileKind;
import java.util.UUID;

public interface OrganizationLegalFileService {

    LegalFileDownload download(Account caller, UUID organizationId, LegalFileKind kind, UUID documentId);
}
