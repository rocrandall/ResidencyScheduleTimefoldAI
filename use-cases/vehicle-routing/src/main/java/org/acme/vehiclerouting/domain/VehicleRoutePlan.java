package org.acme.vehiclerouting.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.solver.SolverStatus;

import org.acme.vehiclerouting.domain.geo.DrivingTimeCalculator;
import org.acme.vehiclerouting.domain.geo.HaversineDrivingTimeCalculator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The plan for routing vehicles to customers, including:
 * <ul>
 * <li>capacity - each vehicle has a capacity for customers demand,</li>
 * <li>time windows - each customer accepts the vehicle only in specified time window.</li>
 * </ul>
 *
 * The planning solution is optimized according to the driving time (as opposed to the travel distance, for example)
 * because it is easy to determine if the vehicle arrival time fits into the customer time window.
 * In addition, optimizing travel time optimizes the distance too, as a side effect - in case there is a faster route,
 * the travel time takes precedence (highway vs. local road).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@PlanningSolution
public class VehicleRoutePlan {

    private String name;

    private Location southWestCorner;
    private Location northEastCorner;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    @ProblemFactCollectionProperty
    private List<Depot> depots;

    @PlanningEntityCollectionProperty
    private List<Vehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Customer> customers;

    @PlanningScore
    private HardSoftLongScore score;

    private SolverStatus solverStatus;

    private String scoreExplanation;

    public VehicleRoutePlan() {
    }

    public VehicleRoutePlan(String name, HardSoftLongScore score, SolverStatus solverStatus) {
        this.name = name;
        this.score = score;
        this.solverStatus = solverStatus;
    }

    @JsonCreator
    public VehicleRoutePlan(@JsonProperty("name") String name,
            @JsonProperty("southWestCorner") Location southWestCorner,
            @JsonProperty("northEastCorner") Location northEastCorner,
            @JsonProperty("startDateTime") LocalDateTime startDateTime,
            @JsonProperty("endDateTime") LocalDateTime endDateTime,
            @JsonProperty("depots") List<Depot> depots,
            @JsonProperty("vehicles") List<Vehicle> vehicles,
            @JsonProperty("customers") List<Customer> customers) {
        this.name = name;
        this.southWestCorner = southWestCorner;
        this.northEastCorner = northEastCorner;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.depots = depots;
        this.vehicles = vehicles;
        this.customers = customers;
        List<Location> locations = Stream.concat(
                depots.stream().map(Depot::getLocation),
                customers.stream().map(Customer::getLocation)).toList();

        DrivingTimeCalculator drivingTimeCalculator = HaversineDrivingTimeCalculator.getInstance();
        drivingTimeCalculator.initDrivingTimeMaps(locations);
    }

    public String getName() {
        return name;
    }

    public Location getSouthWestCorner() {
        return southWestCorner;
    }

    public Location getNorthEastCorner() {
        return northEastCorner;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public List<Depot> getDepots() {
        return depots;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public HardSoftLongScore getScore() {
        return score;
    }

    public void setScore(HardSoftLongScore score) {
        this.score = score;
    }

    // ************************************************************************
    // Complex methods
    // ************************************************************************

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public long getTotalDrivingTimeSeconds() {
        return vehicles == null ? 0 : vehicles.stream().mapToLong(Vehicle::getTotalDrivingTimeSeconds).sum();
    }

    public SolverStatus getSolverStatus() {
        return solverStatus;
    }

    public void setSolverStatus(SolverStatus solverStatus) {
        this.solverStatus = solverStatus;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getScoreExplanation() {
        return scoreExplanation;
    }

    public void setScoreExplanation(String scoreExplanation) {
        this.scoreExplanation = scoreExplanation;
    }
}
