package org.acme.employeescheduling.domain;

import java.util.List;
import java.util.Map; 

import org.acme.employeescheduling.domain.ShiftCountDto; 

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolverStatus;

@PlanningSolution
public class EmployeeSchedule {
    @ProblemFactCollectionProperty
    List<Availability> availabilityList;

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    List<Employee> employeeList;

    @PlanningEntityCollectionProperty
    List<Shift> shiftList;

    @PlanningScore
    HardMediumSoftScore score;

    ScheduleState scheduleState;

    SolverStatus solverStatus;

    private Map<String, ShiftCountDto> shiftCounts; // New field to store shift counts

    // No-arg constructor required for Timefold
    public EmployeeSchedule() {}

    public EmployeeSchedule(ScheduleState scheduleState, List<Availability> availabilityList, List<Employee> employeeList, List<Shift> shiftList) {
        this.scheduleState = scheduleState;
        this.availabilityList = availabilityList;
        this.employeeList = employeeList;
        this.shiftList = shiftList;
    }

    public ScheduleState getScheduleState() {
        return scheduleState;
    }

    public void setScheduleState(ScheduleState scheduleState) {
        this.scheduleState = scheduleState;
    }

    public List<Availability> getAvailabilityList() {
        return availabilityList;
    }

    public void setAvailabilityList(List<Availability> availabilityList) {
        this.availabilityList = availabilityList;
    }

    public List<Employee> getEmployeeList() {
        return employeeList;
    }

    public void setEmployeeList(List<Employee> employeeList) {
        this.employeeList = employeeList;
    }

    public List<Shift> getShiftList() {
        return shiftList;
    }

    public void setShiftList(List<Shift> shiftList) {
        this.shiftList = shiftList;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }

    public SolverStatus getSolverStatus() {
        return solverStatus;
    }

    public void setSolverStatus(SolverStatus solverStatus) {
        this.solverStatus = solverStatus;
    }

    // New setter for the shiftCounts field
    public void setShiftCounts(Map<String, ShiftCountDto> shiftCounts) {
        this.shiftCounts = shiftCounts;
    }

    // New getter for the shiftCounts field (if needed)
    public Map<String, ShiftCountDto> getShiftCounts() {
        return this.shiftCounts;
    }

}
