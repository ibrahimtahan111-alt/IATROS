package com.iatros.backend.clinic;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {
}
