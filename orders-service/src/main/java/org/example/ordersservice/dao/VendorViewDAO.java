package org.example.ordersservice.dao;

import org.example.ordersservice.entity.VendorView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VendorViewDAO extends JpaRepository<VendorView, Long> {}
