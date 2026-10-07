package com.smartprocure.repository;

import com.smartprocure.entity.Vendor;
import com.smartprocure.entity.Vendor.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    boolean existsByVendorCode(String vendorCode);

    boolean existsByEmail(String email);

    Optional<Vendor> findByVendorCode(String vendorCode);

    /**
     * Paginated search: searches vendorName or email containing the keyword, optionally filtered by status.
     * JPQL query using LIKE for case-insensitive partial match.
     */
    @Query("SELECT v FROM Vendor v WHERE " +
           "(:status IS NULL OR v.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(v.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(v.vendorCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Vendor> searchVendors(@Param("keyword") String keyword,
                               @Param("status") VendorStatus status,
                               Pageable pageable);

    long countByStatus(VendorStatus status);

    /**
     * Search vendors by name or code (case-insensitive LIKE), limited results.
     */
    @Query("SELECT v FROM Vendor v WHERE " +
           "LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(v.vendorCode) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Vendor> searchByNameOrCode(@Param("query") String query, Pageable pageable);
}
