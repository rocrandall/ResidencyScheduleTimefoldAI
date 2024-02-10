package org.acme.employeescheduling.domain;

import java.time.LocalDateTime;
import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

@Entity
@PlanningEntity(pinningFilter = ShiftPinningFilter.class)
public class Shift {
    @Id
    @PlanningId
    @GeneratedValue
    Long id;

    LocalDateTime start;
    @Column(name = "endDateTime") // "end" clashes with H2 syntax.
    LocalDateTime end;

    String location;
    String requiredSkill;

    @PlanningVariable(nullable=true)
    @ManyToOne
    Employee employee;

    private boolean isOptional;

    public Shift() {
    }

    // Modified constructors
    
    public Shift(LocalDateTime start, LocalDateTime end, String location, String requiredSkill) {
        this(start, end, location, requiredSkill, null, false); // Calling the full constructor
    }
    
    public Shift(LocalDateTime start, LocalDateTime end, String location, String requiredSkill, Employee employee) {
        this(start, end, location, requiredSkill, employee, false); // Calling the full constructor
    }

    // This is the full constructor with all parameters including isOptional
    public Shift(LocalDateTime start, LocalDateTime end, String location, String requiredSkill, Employee employee, boolean isOptional) {
        this.id = null; // id is not passed, so set to null
        this.start = start;
        this.end = end;
        this.location = location;
        this.requiredSkill = requiredSkill;
        this.employee = employee;
        this.isOptional = isOptional;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public int getShiftDurationInMinutes() {
        long minutes = Duration.between(this.start, this.end).toMinutes();
        return (int) minutes; // Safe cast if the value is always within int range
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public void setRequiredSkill(String requiredSkill) {
        this.requiredSkill = requiredSkill;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public void setOptional(boolean isOptional) {
        this.isOptional = isOptional;
    }

    @Override
    public String toString() {
        return location + " " + start + "-" + end;
    }
}
