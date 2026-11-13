package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import java.util.UUID;

public record DonateProductResponse(String productId, ProductStatus productStatus, UUID donationId,
                                    UUID campaignId, String campaignTitle, String organizationName) {}
