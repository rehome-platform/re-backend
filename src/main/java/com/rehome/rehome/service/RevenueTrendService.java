package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.RevenueTrendResponse;

public interface RevenueTrendService {

    RevenueTrendResponse trend(String from, String to, String granularity);
}
