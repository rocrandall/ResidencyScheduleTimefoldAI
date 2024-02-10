package org.acme.employeescheduling.persistence;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.acme.employeescheduling.domain.Rotation;

@ApplicationScoped
public class RotationRepository implements PanacheRepository<Rotation> {
    // The implementation of the PanacheRepository interface provides 
    // basic CRUD operations. Additional custom methods can be defined here if needed.
}