package com.scarlxrd.datacollector.model.repository;

import com.scarlxrd.datacollector.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
