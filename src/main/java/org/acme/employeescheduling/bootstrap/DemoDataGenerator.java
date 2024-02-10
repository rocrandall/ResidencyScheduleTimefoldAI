package org.acme.employeescheduling.bootstrap;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit; // Add this import
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.employeescheduling.domain.Availability;
import org.acme.employeescheduling.domain.AvailabilityType;
import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.ScheduleState;
import org.acme.employeescheduling.domain.Shift;
import org.acme.employeescheduling.domain.Rotation;

import org.acme.employeescheduling.persistence.AvailabilityRepository;
import org.acme.employeescheduling.persistence.EmployeeRepository;
import org.acme.employeescheduling.persistence.ScheduleStateRepository;
import org.acme.employeescheduling.persistence.ShiftRepository;
import org.acme.employeescheduling.persistence.RotationRepository;

import org.acme.employeescheduling.rest.EmployeeScheduleResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class DemoDataGenerator {

    @ConfigProperty(name = "schedule.demoData", defaultValue = "SMALL")
    DemoData demoData;

    public enum DemoData {
        NONE,
        SMALL,
        LARGE
    }

    // Define a list of predefined employees with their required skills
    List<Employee> predefinedEmployeeList = List.of(
    //    new Employee("R1a E", Set.of("RESIDENT"), "R1"),
    //    new Employee("R1b V", Set.of("RESIDENT"), "R1"),
        new Employee("R2a A", Set.of("RESIDENT"), "R2"),
        new Employee("R2b K", Set.of("RESIDENT"), "R2"),
        new Employee("R3a P", Set.of("RESIDENT"), "R3"),
        new Employee("R3b M", Set.of("RESIDENT"), "R3"),
        new Employee("R4a RC", Set.of("RESIDENT"), "R4"),
        new Employee("R4b RS", Set.of("RESIDENT"), "R4")      
        // ... other predefined employees ...
    );

    Map<String, List<LocalDate>> employeeUnavailableDatesMap = new HashMap<>();

    static final String[] LOCATIONS = { "ED cover"};

    static final Duration SHIFT_LENGTH = Duration.ofHours(8);
    static final Duration DAY_SHIFT_LENGTH = Duration.ofHours(6);
    static final Duration EVENING_SHIFT_LENGTH = Duration.ofHours(6);
    static final Duration NIGHT_SHIFT_LENGTH = Duration.ofHours(12);
    static final Duration WEEKEND_SHIFT_LENGTH = Duration.ofHours(24);

    static final LocalTime DAY_SHIFT_START_TIME = LocalTime.of(8, 0);   
    static final LocalTime EVENING_SHIFT_START_TIME = LocalTime.of(14, 0);  // Updated to 2 PM
    static final LocalTime NIGHT_SHIFT_START_TIME = LocalTime.of(20, 0);  // 8 PM remains the same
    static final LocalTime WEEKEND_SHIFT_START_TIME = LocalTime.of(8, 0); // Start time for 24-hour weekend shift

    static final LocalDate START_DATE = LocalDate.of(2024, 7, 1);

    private static final int INITIAL_ROSTER_LENGTH_IN_DAYS = 365; // Total length of the roster in days

    // introduce a set to track residents who have received an ED cover shift
    private Set<String> assignedEdCoverResidents = new HashSet<>();

    // tracking sets for weekend and weekday ED cover shifts
    private Set<String> assignedWeekendEdCoverResidents = new HashSet<>();
    private Set<String> assignedWeekdayEdCoverResidents = new HashSet<>();

    // Initialize sets to track assigned shifts for each rotation type
    private Set<String> assignedIrShifts = new HashSet<>();
    private Set<String> assignedNightShifts = new HashSet<>();
    private Set<String> assignedDayShifts = new HashSet<>();
    private Set<String> assignedPedsShifts = new HashSet<>();

    private boolean assignedEdCoverToR4 = false;

    static final Boolean randomemployee = false;
 
    Map<String,List<LocalTime>> locationToShiftStartTimeListMap = new HashMap<>();

    @Inject
    EmployeeRepository employeeRepository;
    @Inject
    AvailabilityRepository availabilityRepository;
    @Inject
    ShiftRepository shiftRepository;
    @Inject
    ScheduleStateRepository scheduleStateRepository;
    @Inject
    RotationRepository rotationRepository;

    @Transactional
    public void generateDemoData(@Observes StartupEvent startupEvent) {

        initializeUnavailableDatesMap();

        ScheduleState scheduleState = new ScheduleState();
        scheduleState.setFirstDraftDate(START_DATE);
        scheduleState.setDraftLength(INITIAL_ROSTER_LENGTH_IN_DAYS);
        scheduleState.setPublishLength(365);
        scheduleState.setLastHistoricDate(START_DATE.minusDays(365));
        scheduleState.setTenantId(EmployeeScheduleResource.SINGLETON_SCHEDULE_ID);

        scheduleStateRepository.persist(scheduleState);


        if (demoData == DemoData.NONE) {
            return;
        }

        // Persist predefined employees with their skills
        for (Employee employee : predefinedEmployeeList) {
            employeeRepository.persist(employee);
        }

        // Generate and persist only the unavailable dates for each predefined employee
        for (Employee employee : predefinedEmployeeList) {
            List<LocalDate> unavailableDates = employeeUnavailableDatesMap.get(employee.getName());
            if (unavailableDates != null) {
                for (LocalDate unavailableDate : unavailableDates) {
                    availabilityRepository.persist(new Availability(employee, unavailableDate, AvailabilityType.UNAVAILABLE));
                }
            }
        }

        // Update shift start times for "ED" location
        locationToShiftStartTimeListMap.put("ED cover", new ArrayList<>());
        for (LocalDate date = START_DATE; date.isBefore(START_DATE.plusDays(INITIAL_ROSTER_LENGTH_IN_DAYS)); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                locationToShiftStartTimeListMap.get("ED cover").add(WEEKEND_SHIFT_START_TIME); // 24-hour shift for weekends
            } else {
                locationToShiftStartTimeListMap.get("ED cover").clear(); // Clear previous entries
                locationToShiftStartTimeListMap.get("ED cover").add(EVENING_SHIFT_START_TIME); // Evening shift
            }
        }

        // Generate shifts for ED 

        for (LocalDate date = START_DATE; date.isBefore(START_DATE.plusDays(INITIAL_ROSTER_LENGTH_IN_DAYS - 1)); date = date.plusDays(1)) {
            generateShiftsForDay(date, "ED cover");
        }

        // Generate Rotations and there block shifts

        generateRotationData();

    }

    private void initializeUnavailableDatesMap() {
        // Populate the map with unavailable dates for each resident

        employeeUnavailableDatesMap.put("R2a A", new ArrayList<>());
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 8, 24), LocalDate.of(2024, 9, 1)));
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 12, 28), LocalDate.of(2025, 1, 5)));
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 9)));
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2025, 5, 3), LocalDate.of(2025, 5, 11)));
        // 2 Holidays
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 26))); // working Christmas holiday, with post call
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2025, 5, 26), LocalDate.of(2025, 5, 27))); // working Memorial Day holiday, with post call

        // Holidays Off
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 11, 28), LocalDate.of(2024, 11, 28))); // Thanksgiving holiday
 /*
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 4))); // July 4th holiday
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 2))); // Labor day holiday, with post call
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25))); // Christmas holiday, with post call
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))); // New Years holiday        
        employeeUnavailableDatesMap.get("R2a A").addAll(createDateRange(LocalDate.of(2025, 5, 26), LocalDate.of(2025, 5, 26))); // Memorial Day holiday, with post call
*/
        employeeUnavailableDatesMap.put("R2b K", new ArrayList<>());
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2025, 1, 4), LocalDate.of(2025, 1, 19)));
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2025, 4, 12), LocalDate.of(2025, 4, 20)));
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2025, 5, 17), LocalDate.of(2025, 5, 25)));
        // 2 Holidays
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 5))); // working July 4th holiday, with post call
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 3))); // working Labor day holiday, with post call

        // Holidays Off
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))); // New Years holiday     
/*             
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 11, 28), LocalDate.of(2024, 11, 28))); // Thanksgiving holiday
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 4))); // July 4th holiday
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 2))); // Labor day holiday, with post call
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25))); // Christmas holiday, with post call      
        employeeUnavailableDatesMap.get("R2b K").addAll(createDateRange(LocalDate.of(2025, 5, 26), LocalDate.of(2025, 5, 26))); // Memorial Day holiday, with post call
*/
        employeeUnavailableDatesMap.put("R3a P", new ArrayList<>());
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 7, 13), LocalDate.of(2024, 8, 4)));
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 9, 27), LocalDate.of(2024, 9, 29))); // wedding
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2025, 1, 25), LocalDate.of(2025, 2, 2)));
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2025, 2, 3), LocalDate.of(2025, 6, 7))); // study period
        // 1 Holidays
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 11, 28), LocalDate.of(2024, 11, 29))); // working Thanksgiving holiday, with post call
/*
        // Holidays Off
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 11, 28), LocalDate.of(2024, 11, 28))); // Thanksgiving holiday
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 4))); // July 4th holiday
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 2))); // Labor day holiday, with post call
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25))); // Christmas holiday, with post call
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))); // New Years holiday        
        employeeUnavailableDatesMap.get("R3a P").addAll(createDateRange(LocalDate.of(2025, 5, 26), LocalDate.of(2025, 5, 26))); // Memorial Day holiday, with post call
*/
        employeeUnavailableDatesMap.put("R3b M", new ArrayList<>());
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 12, 14), LocalDate.of(2024, 12, 22)));
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2025, 1, 18), LocalDate.of(2025, 1, 26)));
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 10, 26), LocalDate.of(2024, 11, 3)));
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 8, 24), LocalDate.of(2024, 9, 1)));
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2025, 2, 3), LocalDate.of(2025, 6, 7))); // study period
        // 1 Holidays
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2))); // working New Years holiday, with post call

        // Holidays Off
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 4))); // July 4th holiday
/*      employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 9, 2), LocalDate.of(2024, 9, 2))); // Labor day holiday, with post call
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 11, 28), LocalDate.of(2024, 11, 28))); // Thanksgiving holiday        
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25))); // Christmas holiday, with post call
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))); // New Years holiday        
        employeeUnavailableDatesMap.get("R3b M").addAll(createDateRange(LocalDate.of(2025, 5, 26), LocalDate.of(2025, 5, 26))); // Memorial Day holiday, with post call
*/
        employeeUnavailableDatesMap.put("R4a RC", new ArrayList<>());
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 7, 12), LocalDate.of(2024, 7, 16))); // wedding
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 9, 13), LocalDate.of(2024, 9, 15))); // wedding
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 10, 26), LocalDate.of(2024, 11, 3)));
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 11, 23), LocalDate.of(2024, 12, 1)));
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 12, 21), LocalDate.of(2024, 12, 29)));
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2025, 1, 25), LocalDate.of(2025, 2, 2)));
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 30))); // fellowship transition 

        // Holidays Off
        employeeUnavailableDatesMap.get("R4a RC").addAll(createDateRange(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 4))); // July 4th holiday

        employeeUnavailableDatesMap.put("R4b RS", new ArrayList<>());
        employeeUnavailableDatesMap.get("R4b RS").addAll(createDateRange(LocalDate.of(2024, 8, 17), LocalDate.of(2024, 9, 1)));
        employeeUnavailableDatesMap.get("R4b RS").addAll(createDateRange(LocalDate.of(2024, 11, 30), LocalDate.of(2024, 12, 8)));
        employeeUnavailableDatesMap.get("R4b RS").addAll(createDateRange(LocalDate.of(2025, 4, 19), LocalDate.of(2025, 4, 27)));
        employeeUnavailableDatesMap.get("R4b RS").addAll(createDateRange(LocalDate.of(2025, 6, 15), LocalDate.of(2025, 6, 30))); // fellowship transition  
    }


    // Define holidays
    private final List<LocalDate> holidays = List.of(
        LocalDate.of(2024, Month.JULY, 4),
        LocalDate.of(2024, Month.SEPTEMBER, 2), // Labor Day, first Monday of September
        LocalDate.of(2024, Month.NOVEMBER, 28), // Thanksgiving, fourth Thursday of November
        LocalDate.of(2024, Month.DECEMBER, 25), // Christmas Day
        LocalDate.of(2025, Month.JANUARY, 1), // New Years
        LocalDate.of(2025, Month.MAY, 26) // Memorial Day, last Monday of May
    );

    // BLOCK SCHEDULING GENERATION

    public void generateRotationData() {
        // Define rotation requirements for each resident type, type, weeks
        Map<String, Integer> IRRequirements = Map.of("IR", 4); 
        Map<String, Integer> nightShiftRequirements = Map.of("Night Shift", 8); 
        Map<String, Integer> erDayShiftRequirements = Map.of("Day Shift", 5); 
        Map<String, Integer> pedsRequirements = Map.of("Peds", 2); 


        // Create and persist rotation instances
        Rotation rotationIR = new Rotation("IRShiftType", IRRequirements);
        Rotation nightShiftRotation = new Rotation("NightShiftType", nightShiftRequirements);
        Rotation erDayShiftRotation = new Rotation("ERDayShiftType", erDayShiftRequirements);
        Rotation pedsRotation = new Rotation("PedsType", pedsRequirements);

        // ... other rotations ...

        // ... persist other rotations ...
        rotationRepository.persist(rotationIR);
        rotationRepository.persist(nightShiftRotation);
        rotationRepository.persist(erDayShiftRotation);
        rotationRepository.persist(pedsRotation);

       // generateBlockShifts("IR");
        generateBlockShifts("Night Shift");
       // generateBlockShifts("Day Shift");
        generateBlockShifts("Peds");

    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }


    private void generateBlockShifts(String rotationName) {
            LocalDate rosterEndDate = START_DATE.plusDays(INITIAL_ROSTER_LENGTH_IN_DAYS);

            Set<String> assignedShiftsForRotation = switch (rotationName) {
                case "IR" -> assignedIrShifts;
                case "Night Shift" -> assignedNightShifts;
                case "Day Shift" -> assignedDayShifts;
                case "Peds" -> assignedPedsShifts;
                default -> throw new IllegalArgumentException("Unknown rotation: " + rotationName);
            };

            if ("Peds".equals(rotationName)) {
                LocalDate firstPedsShiftStartDate = LocalDate.of(START_DATE.getYear(), 6, 30); // June 30th
                LocalDate lastPedsShiftStartDate = LocalDate.of(rosterEndDate.getYear(), 6, 28); // Two weeks before the end of June
                for (LocalDate date = firstPedsShiftStartDate; date.isBefore(lastPedsShiftStartDate); date = date.plusWeeks(2)) {
                    LocalDateTime startDateTime = date.atTime(10, 0); // Assuming start time for Peds
                    LocalDateTime endDateTime = date.plusDays(13).atTime(20, 0); // Two weeks later on Saturday evening

                    List<Employee> eligibleEmployees = predefinedEmployeeList.stream()
                        .filter(emp -> !assignedShiftsForRotation.contains(emp.getName()))
                        .collect(Collectors.toList());

                    createShift(startDateTime, endDateTime, rotationName, null, true);
                    createShift(startDateTime, endDateTime, rotationName, null, true);
                }
                for (LocalDate date = firstPedsShiftStartDate.plusWeeks(1); date.isBefore(lastPedsShiftStartDate.minusWeeks(1)); date = date.plusWeeks(2)) {
                    LocalDateTime startDateTime = date.atTime(10, 0); // Assuming start time for Peds
                    LocalDateTime endDateTime = date.plusDays(13).atTime(20, 0); // Two weeks later on Saturday evening

                    List<Employee> eligibleEmployees = predefinedEmployeeList.stream()
                        .filter(emp -> !assignedShiftsForRotation.contains(emp.getName()))
                        .collect(Collectors.toList());

                    if (!eligibleEmployees.isEmpty()) {
                        Employee selectedEmployee = pickRandomEmployee(eligibleEmployees);
                        assignedShiftsForRotation.add(selectedEmployee.getName());
                        createShift(startDateTime, endDateTime, rotationName, selectedEmployee, true); // Peds shifts are optional
                        createShift(startDateTime, endDateTime, rotationName, selectedEmployee, true); // Peds shifts are optional
                    } else {
                        createShift(startDateTime, endDateTime, rotationName, null, true);
                        createShift(startDateTime, endDateTime, rotationName, null, true);
                    }
                }                
            } else if ("Night Shift".equals(rotationName)) {
                LocalDate lastNightShiftStartDate = LocalDate.of(rosterEndDate.getYear(), 6, 29); // Last week of June
                for (LocalDate date = START_DATE; date.isBefore(lastNightShiftStartDate); date = date.plusDays(7)) {
                    LocalDateTime startDateTime = date.atTime(20, 0); // Start time for Night Shift
                    LocalDateTime endDateTime = date.plusDays(5).atTime(8, 0); // Five days later in the morning

                    List<Employee> eligibleEmployees = predefinedEmployeeList.stream()
                        .filter(emp -> !assignedShiftsForRotation.contains(emp.getName()))
                        .collect(Collectors.toList());

                    if (!eligibleEmployees.isEmpty()) {
                        Employee selectedEmployee = pickRandomEmployee(eligibleEmployees);
                        assignedShiftsForRotation.add(selectedEmployee.getName());
                        createShift(startDateTime, endDateTime, rotationName, selectedEmployee, false); // Night Shifts are not optional
                    } else {
                        createShift(startDateTime, endDateTime, rotationName, null, false);
                    }
                }
            } else {
                // Logic for other rotations...
    
                for (LocalDate date = START_DATE; date.isBefore(rosterEndDate); date = date.plusDays(7)) { // Weekly rotations
                    LocalDateTime startDateTime = date.atTime(8, 0);
                    LocalDateTime endDateTime = date.plusDays(4).atTime(14, 0);

                    List<Employee> eEmployees = predefinedEmployeeList.stream()
                            .filter(emp -> !assignedShiftsForRotation.contains(emp.getName()))
                            .filter(emp -> {
                                // Exclude R1 residents from Day and Night shifts
                                /*
                                if (("Day Shift".equals(rotationName) || "Night Shift".equals(rotationName)) && "R1".equals(emp.getEmployeeType())) {
                                    return false;
                                }*/
                                return true;
                            })
                            .collect(Collectors.toList());

                    if (!eEmployees.isEmpty()) {
                        Employee sEmployee = pickRandomEmployee(eEmployees);
                        assignedShiftsForRotation.add(sEmployee.getName());
                        createShift(startDateTime, endDateTime, rotationName, sEmployee, rotationName.equals("IR")); // Assuming IR shifts are optional
                    } else {
                        // If no eligible employees left, continue creating shifts without assigning an employee
                        createShift(startDateTime, endDateTime, rotationName, null, false);
                    }
                }
            }

        }


    private void createShift(LocalDateTime start, LocalDateTime end, String rotationName, Employee assignedEmployee, boolean isOptional) {

        // String requiredSkill = rotationName.equals("Night Shift") ? "RESIDENT" : null; // NIGHT shift requires type resident
        String requiredSkill = "RESIDENT";

        isOptional = rotationName.equals("IR"); // Set IR shifts as optional    
        isOptional = rotationName.equals("Peds"); // Set Peds shifts as optional    

        Shift blockShift; 

        blockShift = new Shift(start, end, rotationName, requiredSkill, assignedEmployee);

        blockShift.setOptional(isOptional);
        shiftRepository.persist(blockShift);
    }
    
    private List<LocalDate> createDateRange(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        while (!start.isAfter(end)) {
            dates.add(start);
            start = start.plusDays(1);
        }
        return dates;
    }

    private void generateShiftsForDay(LocalDate date, String location) {
        if (!location.equals("ED cover") || isHoliday(date)) {
            return; // Skip or define alternative logic for other locations
        }

        List<Employee> eligibleEmployees = predefinedEmployeeList.stream()
            .filter(emp -> emp.getSkillSet().contains("RESIDENT"))
            .collect(Collectors.toList());

        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            // Generate a single 24-hour shift for the ED location on weekends
            LocalDateTime shiftStartDateTime = date.atTime(WEEKEND_SHIFT_START_TIME);
            LocalDateTime shiftEndDateTime = shiftStartDateTime.plusHours(WEEKEND_SHIFT_LENGTH.toHours());
            Employee assignedEmployee = pickRandomEmployee(eligibleEmployees);
            generateShiftForTimeslot(shiftStartDateTime, shiftEndDateTime, location, assignedEmployee);
        } else {
            // Generate updated evening shift for ED location
            LocalDateTime eveningShiftStartDateTime = date.atTime(EVENING_SHIFT_START_TIME); // 2 PM start time
            LocalDateTime eveningShiftEndDateTime = eveningShiftStartDateTime.plusHours(EVENING_SHIFT_LENGTH.toHours()); // 6-hour duration
            Employee assignedEmployee = pickRandomEmployee(eligibleEmployees);

            generateShiftForTimeslot(eveningShiftStartDateTime, eveningShiftEndDateTime, location, assignedEmployee);
        }
    }

    private boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    private Employee pickRandomEmployee(List<Employee> employees) {
        return employees.get(new Random().nextInt(employees.size()));
    }

    private void generateShiftForTimeslot(LocalDateTime start, LocalDateTime end, String location, Employee employee) {
        String requiredSkill = "RESIDENT";
        Shift newShift; // Declare the variable outside the if...else block

        if (location.equals("ED cover")) {
            boolean isWeekendShift = isWeekend(start.toLocalDate());
            List<Employee> eligibleEmployees = predefinedEmployeeList.stream()
                // .filter(emp -> !emp.getEmployeeType().equals("R1")) // Exclude R1 residents
                // Check if the employee has not been assigned the current shift type (weekday or weekend)
                .filter(emp -> (isWeekendShift && !assignedWeekendEdCoverResidents.contains(emp.getName())) || 
                               (!isWeekendShift && !assignedWeekdayEdCoverResidents.contains(emp.getName())))
                .collect(Collectors.toList());

            if (!eligibleEmployees.isEmpty()) {
                // Pick a random eligible employee who hasn't received the specific type of ED cover shift
                Employee selectedEmployee = pickRandomEmployee(eligibleEmployees);
                // Update tracking based on shift type
                if (isWeekendShift) {
                    assignedWeekendEdCoverResidents.add(selectedEmployee.getName());
                } else {
                    assignedWeekdayEdCoverResidents.add(selectedEmployee.getName());
                }
                newShift = new Shift(start, end, location, requiredSkill, selectedEmployee);
            } else {
                // If all eligible residents have been assigned or no specific logic is needed, create shift without assigning
                newShift = new Shift(start, end, location, requiredSkill, null);
            }
        } else {
            // Logic for non-ED cover shifts remains unchanged
            newShift = new Shift(start, end, location, requiredSkill, employee);
        }

        shiftRepository.persist(newShift);
    }    


    private <T> T pickRandom(T[] source, Random random) {
        return source[random.nextInt(source.length)];
    }

    private <T> Set<T> pickSubset(List<T> sourceSet, Random random, int... distribution) {
        int probabilitySum = 0;
        for (int probability : distribution) {
            probabilitySum += probability;
        }
        int choice = random.nextInt(probabilitySum);
        int numOfItems = 0;
        while (choice >= distribution[numOfItems]) {
            choice -= distribution[numOfItems];
            numOfItems++;
        }
        List<T> items = new ArrayList<>(sourceSet);
        Collections.shuffle(items, random);
        return new HashSet<>(items.subList(0, numOfItems + 1));
    }

    private List<String> joinAllCombinations(String[]... partArrays) {
        int size = 1;
        for (String[] partArray : partArrays) {
            size *= partArray.length;
        }
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StringBuilder item = new StringBuilder();
            int sizePerIncrement = 1;
            for (String[] partArray : partArrays) {
                item.append(' ');
                item.append(partArray[(i / sizePerIncrement) % partArray.length]);
                sizePerIncrement *= partArray.length;
            }
            item.delete(0,1);
            out.add(item.toString());
        }
        return out;
    }

    @Transactional
    public void generateDraftShifts(ScheduleState scheduleState) {
        // Use predefinedEmployeeList instead of fetching from the repository
        for (int i = 0; i < scheduleState.getPublishLength(); i++) {
            LocalDate date = scheduleState.getFirstDraftDate().plusDays(i);

            for (Employee employee : predefinedEmployeeList) {
                List<LocalDate> unavailableDates = employeeUnavailableDatesMap.get(employee.getName());
                if (unavailableDates != null && unavailableDates.contains(date)) {
                    availabilityRepository.persist(new Availability(employee, date, AvailabilityType.UNAVAILABLE));
                }
            }
        }
    }

}
