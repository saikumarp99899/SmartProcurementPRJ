package com.smartprocure.repository;

import com.smartprocure.entity.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {
    Optional<CostCenter> findByCode(String code);
    boolean existsByCode(String code);
    List<CostCenter> findByDepartmentId(Long departmentId);
}
