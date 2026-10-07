package com.smartprocure.service;

import com.smartprocure.dto.response.SearchResponse;
import com.smartprocure.security.user.UserPrincipal;

public interface SearchService {
    SearchResponse search(String query, UserPrincipal currentUser);
}
