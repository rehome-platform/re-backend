package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationProfileRepository organizationProfileRepository;
    private final OrganizationProfileMapper organizationProfileMapper;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public RegisterOrganizationResponse registerOrganization(UUID accountId, RegisterOrganizationRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        if (account.getRole() != Role.ORGANIZATION) {
            throw new AppException(ErrorCode.NOT_ORGANIZATION_ACCOUNT);
        }

        String email = request.getContactEmail().trim().toLowerCase();

        if (organizationProfileRepository.existsByAccountId(accountId)) {
            throw new AppException(ErrorCode.ORGANIZATION_PROFILE_ALREADY_EXISTS);
        }
        if (organizationProfileRepository.existsByContactEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }

        if (organizationProfileRepository.existsByContactEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }
        if (organizationProfileRepository.existsByTaxCode(request.getTaxCode())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        OrganizationProfile organization = organizationProfileMapper.toEntity(request);
        organization.setContactEmail(email);              // lưu bản đã chuẩn hóa
        organization.setAccount(account);                 // account giờ là managed
        organization.setVerificationStatus(VerificationStatus.PENDING);

        organizationProfileRepository.save(organization);
        return organizationProfileMapper.toResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResubmitResponse resubmit(Account account, RegisterOrganizationRequest request) {
        OrganizationProfile profile = organizationProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        if (profile.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw new AppException(ErrorCode.ORGANIZATION_NOT_REJECTED);
        }

        String email = request.getContactEmail().trim().toLowerCase();
        if (organizationProfileRepository.existsByContactEmailIgnoreCaseAndAccountIdNot(email, account.getId())) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }
        if (organizationProfileRepository.existsByTaxCodeAndAccountIdNot(request.getTaxCode(), account.getId())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        organizationProfileMapper.updateEntity(request, profile);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        organizationProfileRepository.save(profile);

        return new OrganizationResubmitResponse(profile.getVerificationStatus());
    }

}
