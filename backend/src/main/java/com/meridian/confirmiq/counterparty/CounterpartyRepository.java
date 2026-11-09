package com.meridian.confirmiq.counterparty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Optional<Counterparty> findByShortCodeIgnoreCase(String shortCode);

    Optional<Counterparty> findByLei(String lei);

    boolean existsByShortCodeIgnoreCase(String shortCode);

    boolean existsByLei(String lei);
}
