package io.github.gseobi.commerce.orchestration.order.repository;

import io.github.gseobi.commerce.orchestration.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
