package org.acme.employeescheduling.rest;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.acme.employeescheduling.bootstrap.DemoDataGenerator;
import org.acme.employeescheduling.domain.EmployeeSchedule;
import org.acme.employeescheduling.domain.ScheduleState;
import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;
import org.acme.employeescheduling.domain.ShiftCountDto;
import org.acme.employeescheduling.domain.Rotation;


import org.acme.employeescheduling.persistence.AvailabilityRepository;
import org.acme.employeescheduling.persistence.EmployeeRepository;
import org.acme.employeescheduling.persistence.ScheduleStateRepository;
import org.acme.employeescheduling.persistence.ShiftRepository;
import org.acme.employeescheduling.persistence.RotationRepository;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;

import io.quarkus.panache.common.Sort;

@Path("/schedule")
public class EmployeeScheduleResource {

    public static final Long SINGLETON_SCHEDULE_ID = 1L;
    
    static final LocalTime EVENING_SHIFT_START_TIME = LocalTime.of(14, 0); 

    @Inject
    AvailabilityRepository availabilityRepository;
    @Inject
    EmployeeRepository employeeRepository;
    @Inject
    ShiftRepository shiftRepository;
    @Inject
    ScheduleStateRepository scheduleStateRepository;
    @Inject
    RotationRepository rotationRepository;

    @Inject
    DemoDataGenerator dataGenerator;

    @Inject
    SolverManager<EmployeeSchedule, Long> solverManager;
    @Inject
    SolutionManager<EmployeeSchedule, HardMediumSoftScore> solutionManager;

    // To try, open http://localhost:8080/schedule

    @GET
    public EmployeeSchedule getSchedule() {
        SolverStatus solverStatus = getSolverStatus();
        EmployeeSchedule solution = findById(SINGLETON_SCHEDULE_ID);
        solutionManager.update(solution); // Sets the score
        solution.setSolverStatus(solverStatus);
        
        // Calculate shift counts
        Map<String, ShiftCountDto> shiftCounts = calculateShiftCounts(solution.getShiftList());
        solution.setShiftCounts(shiftCounts);

        return solution;
    }

    @GET
    @Path("/rotations")
    public List<Rotation> getAllRotations() {
        return rotationRepository.listAll();
    }

    private Map<String, ShiftCountDto> calculateShiftCounts(List<Shift> shifts) {
        Map<String, ShiftCountDto> shiftCounts = new HashMap<>();
        for (Shift shift : shifts) {
            Employee employee = shift.getEmployee();
            if (employee != null) {
                // Existing logic when employee is not null
                String employeeName = employee.getName();
                String employeeType = employee.getEmployeeType();
                ShiftCountDto countDto = shiftCounts.computeIfAbsent(employeeName, k -> new ShiftCountDto(employeeType));

                countDto.incrementShiftsByLocation(shift.getLocation());

                if ("Night Shift".equals(shift.getLocation())) {
                    countDto.incrementNightShifts();
                }

                if ("Day Shift".equals(shift.getLocation())) {
                    countDto.incrementDayShifts();
                }

                if ("IR".equals(shift.getLocation())) {
                    countDto.incrementIRShifts();
                }

                if ("Peds".equals(shift.getLocation())) {
                    countDto.incrementPedsShifts();
                }

                if ("ED cover".equals(shift.getLocation()) && isSaturday(shift)) {
                    countDto.incrementSaturdayShifts();
                }

                if ("ED cover".equals(shift.getLocation()) && isSunday(shift)) {
                    countDto.incrementSundayShifts();
                }

                if ("ED cover".equals(shift.getLocation()) && isFriday(shift)) {
                    countDto.incrementFridayShifts();
                }

                if (isEveningShift(shift)) {
                    countDto.incrementTotalShifts();
                    countDto.incrementEveningShifts();
                }

                if (isWeekend(shift)) {
                    countDto.incrementTotalShifts();
                    countDto.incrementWeekendShifts();
                }
            } else {
                // Handling for shifts where the employee is null
                // You can choose to ignore these shifts, count them separately, or handle as needed
            }
        }
        return shiftCounts;
    }


    private boolean isEveningShift(Shift shift) {
            return shift.getStart().toLocalTime().equals(EVENING_SHIFT_START_TIME);
    }

    private boolean isWeekend(Shift shift) {
        DayOfWeek day = shift.getStart().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isSaturday(Shift shift) {
        DayOfWeek day = shift.getStart().getDayOfWeek();
        return day == DayOfWeek.SATURDAY;
    }

    private boolean isSunday(Shift shift) {
        DayOfWeek day = shift.getStart().getDayOfWeek();
        return day == DayOfWeek.SUNDAY;
    }

    private boolean isFriday(Shift shift) {
        DayOfWeek day = shift.getStart().getDayOfWeek();
        return day == DayOfWeek.FRIDAY;
    }

    public SolverStatus getSolverStatus() {
        return solverManager.getSolverStatus(SINGLETON_SCHEDULE_ID);
    }

    @POST
    @Path("solve")
    public void solve() {
        solverManager.solveAndListen(SINGLETON_SCHEDULE_ID,
                this::findById,
                solution -> {
                    // After solving, save the solution
                    save(solution);
                    // Then, explain the solution


                    System.out.println("-------------------------------NEW SOLUTION---------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("Score:");
                    System.out.println(solutionManager.explain(solution).getScore());
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");

                    solutionManager.explain(solution).getConstraintMatchTotalMap().forEach((constraintId, constraintMatchTotal) -> {

                        System.out.println("Constraint: " + constraintId);
                        System.out.println("  Score impact: " + constraintMatchTotal.getScore());
                        constraintMatchTotal.getConstraintMatchSet().forEach(constraintMatch -> {
                            // System.out.println("    Match: " + constraintMatch);
                            System.out.println("    Justification: " + constraintMatch.getJustificationList());
                            System.out.println("    Score: " + constraintMatch.getScore());
                        });
                    });
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("----------------------------------------------------------------------");
                });
    }

    @POST
    @Transactional
    @Path("publish")
    public void publish() {
        if (!getSolverStatus().equals(SolverStatus.NOT_SOLVING)) {
            throw new IllegalStateException("Cannot publish a schedule while solving is in progress.");
        }
        ScheduleState scheduleState = scheduleStateRepository.findById(SINGLETON_SCHEDULE_ID);
        LocalDate newHistoricDate = scheduleState.getFirstDraftDate();
        LocalDate newDraftDate = scheduleState.getFirstDraftDate().plusDays(scheduleState.getPublishLength());

        scheduleState.setLastHistoricDate(newHistoricDate);
        scheduleState.setFirstDraftDate(newDraftDate);

        dataGenerator.generateDraftShifts(scheduleState);
    }

    @POST
    @Path("stopSolving")
    public void stopSolving() {
        solverManager.terminateEarly(SINGLETON_SCHEDULE_ID);
    }

    @Transactional
    protected EmployeeSchedule findById(Long id) {
        if (!SINGLETON_SCHEDULE_ID.equals(id)) {
            throw new IllegalStateException("There is no schedule with id (" + id + ").");
        }
        return new EmployeeSchedule(
                scheduleStateRepository.findById(SINGLETON_SCHEDULE_ID),
                availabilityRepository.listAll(Sort.by("date").and("id")),
                employeeRepository.listAll(Sort.by("name")),
                shiftRepository.listAll(Sort.by("location").and("start").and("id")));
    }

    @Transactional
    protected void save(EmployeeSchedule schedule) {
        for (Shift shift : schedule.getShiftList()) {
            // TODO this is awfully naive: optimistic locking causes issues if called by the SolverManager
            Shift attachedShift = shiftRepository.findById(shift.getId());
            attachedShift.setEmployee(shift.getEmployee());
        }
    }
}
