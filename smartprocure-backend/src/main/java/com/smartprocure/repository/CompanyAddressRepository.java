package com.smartprocure.repository;

import com.smartprocure.entity.CompanyAddress;
import com.smartprocure.entity.CompanyAddress.AddressType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyAddressRepository extends JpaRepository<CompanyAddress, Long> {

    List<CompanyAddress> findByAddressType(AddressType addressType);

    Optional<CompanyAddress> findByAddressTypeAndIsDefaultTrue(AddressType addressType);
}
