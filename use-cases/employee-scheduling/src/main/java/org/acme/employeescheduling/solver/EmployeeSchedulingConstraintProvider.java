package org.acme.employeescheduling.solver;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.TreeSet;
import java.util.Comparator;
import java.util.AbstractMap;

import org.acme.employeescheduling.domain.Availability;
import org.acme.employeescheduling.domain.AvailabilityType;
import org.acme.employeescheduling.domain.Shift;
import org.acme.employeescheduling.domain.Employee;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.*;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

        // Block requirements
        private static final int MIN_IR_BLOCKS = 4;

        private static final LocalDate START_DATE = LocalDate.of(2024, 7, 1);
 
        // Weekday and weekend minimum requirements per resident year
        private static final int MIN_WEEKDAY_SHIFTS_R4 = 20;
        private static final int MIN_WEEKDAY_SHIFTS_R3 = 30; // 25
        private static final int MIN_WEEKDAY_SHIFTS_R2 = 50; // 40

        private static final int MAX_WEEKDAY_SHIFTS_R4 = 45; // 45 // 40
        private static final int MAX_WEEKDAY_SHIFTS_R3 = 65; // 60
        private static final int MAX_WEEKDAY_SHIFTS_R2 = 75; // 60 // 65 // 70

        private static final int MIN_WEEKEND_SHIFTS_R4 = 10;
        private static final int MIN_WEEKEND_SHIFTS_R3 = 15; // 12 // 11
        private static final int MIN_WEEKEND_SHIFTS_R2 = 20; // 12 // 13 //15

        private static final int MAX_WEEKEND_SHIFTS_R4 = 14;
        private static final int MAX_WEEKEND_SHIFTS_R3 = 20; // 25
        private static final int MAX_WEEKEND_SHIFTS_R2 = 25; // 27

        // Night float minimum requirements per resident year
        private static final int MIN_NF_SHIFTS_R4 = 6;
        private static final int MIN_NF_SHIFTS_R3 = 7; // 7 // 8
        private static final int MIN_NF_SHIFTS_R2 = 8; 

        // Night float maximum requirements per resident year
        private static final int MAX_NF_SHIFTS_R4 = 7; // 8
        private static final int MAX_NF_SHIFTS_R3 = 9; // 12
        private static final int MAX_NF_SHIFTS_R2 = 12;


        private final List<LocalDate> holidays = List.of(
            // LocalDate.of(2024, Month.JULY, 4),
            LocalDate.of(2024, Month.SEPTEMBER, 2), // Labor Day, first Monday of September
            LocalDate.of(2024, Month.NOVEMBER, 28), // Thanksgiving, fourth Thursday of November
            LocalDate.of(2024, Month.DECEMBER, 25), // Christmas Day
            LocalDate.of(2025, Month.JANUARY, 1), // New Years
            LocalDate.of(2025, Month.MAY, 26) // Memorial Day, last Monday of May
        );


        private static final Set<String> requiredShiftTypes = Set.of("Night Shift", "ED cover", "Peds");

        // Constants for shift start times, as defined in DemoDataGenerator
        static final LocalTime DAY_SHIFT_START_TIME = LocalTime.of(8, 0);  // Assuming this is the start time for day shift   
        static final LocalTime EVENING_SHIFT_START_TIME = LocalTime.of(14, 0);  // Assuming this is the start time for evening shift
        private static final LocalTime NIGHT_SHIFT_START_TIME = LocalTime.of(20, 0);  // Start time for night shift

        private boolean isDayShift(Shift shift) {
                return shift.getStart().toLocalTime().equals(DAY_SHIFT_START_TIME);
        }

        private boolean isEveningShift(Shift shift) {
                return shift.getStart().toLocalTime().equals(EVENING_SHIFT_START_TIME);
        }

        private boolean isNightShift(Shift shift) {
                return shift.getStart().toLocalTime().equals(NIGHT_SHIFT_START_TIME);
        }


        private static int getMinuteOverlap(Shift shift1, Shift shift2) {
                // The overlap of two timeslot occurs in the range common to both timeslots.
                // Both timeslots are active after the higher of their two start times,
                // and before the lower of their two end times.
                LocalDateTime shift1Start = shift1.getStart();
                LocalDateTime shift1End = shift1.getEnd();
                LocalDateTime shift2Start = shift2.getStart();
                LocalDateTime shift2End = shift2.getEnd();
                return (int) Duration.between((shift1Start.compareTo(shift2Start) > 0) ? shift1Start : shift2Start,
                        (shift1End.compareTo(shift2End) < 0) ? shift1End : shift2End).toMinutes();
        }

        private static int getShiftDurationInMinutes(Shift shift) {
                return (int) Duration.between(shift.getStart(), shift.getEnd()).toMinutes();
        }

        private static int getWeekOfYear(LocalDateTime dateTime) {
                WeekFields weekFields = WeekFields.of(Locale.getDefault());
                return dateTime.get(weekFields.weekOfYear());
        }

        @Override
        public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
                return new Constraint[]{

                        // General Constraints
                        noOverlappingShifts(constraintFactory),
                        unavailableEmployee(constraintFactory),
                        unavailableEmployeeForPediatrics(constraintFactory),
                        desiredDayForEmployee(constraintFactory),
                        undesiredDayForEmployee(constraintFactory),

                        // PENALIZE UNASSIGNED MANDATORY SHIFTS
                        penalizeUnassignedShifts(constraintFactory),

                        // ED/STAT COVERAGE CONSTRAINTS
                        preventEDCoverBeforeNightBlock(constraintFactory), 
                        preventEDCoverBeforeSaturdayShift(constraintFactory), 
                        no24HourShiftAfter24HourShift(constraintFactory),
                        noSunday24HourEDCoverShiftAfterNightShiftBlock(constraintFactory),
                        no24HourEDCoverShiftAfterNightShiftBlock(constraintFactory),  
                        noShiftAfter24HourSundayShift(constraintFactory),  
                        penalizeExcessFridayShiftsForR4s(constraintFactory), 
                        penalize24HourShiftsAroundUnavailabilityExcludingNightShifts(constraintFactory), 
                        // penalizeBlockScheduleAfter24HourSundayShift(constraintFactory),   
                        
                        // MIN/MAX RULES
                        minimumWeekendShiftsForResidents(constraintFactory), 
                        minimumWeekdayShiftsForResidents(constraintFactory), 
                        maximumWeekendShiftsForResidents(constraintFactory), 
                        maximumWeekdayShiftsForResidents(constraintFactory), 
                        minimumNFShiftsForResidents(constraintFactory), 
                        maximumNFShiftsForResidents(constraintFactory), 


                        // PEDIATRIC AWAY ROTATION RULES
                        maximumPedsShiftsForResidents(constraintFactory), 
                        pediatricShiftsDuringHolidays(constraintFactory), 
                        noOverlappingPediatricShiftsBetweenDifferentResidents(constraintFactory), 
                        noPediatricsForR4InJune(constraintFactory), 
                        noOverlappingPediatricShiftsForR4aRC(constraintFactory), 
                        // onlyTwoResidentsForOverlappingPediatricShifts(constraintFactory), 

                        // BALANCE RULES
                        fridayEDCoverShiftsBalancing(constraintFactory),      
                        weekendShiftsBalancing(constraintFactory),
                        eveningShiftsBalancing(constraintFactory),
                        nightShiftsBalancing(constraintFactory),
                        weekenddayShiftsBalancing(constraintFactory),
                        rewardConsecutiveNightBlocks(constraintFactory),
                        // dayShiftsBalancing(constraintFactory),

                        // Block constraints to make things work
                        ensureMinimumOneShiftPerRequiredType(constraintFactory),
                        ensureMinimumWeekdayAndWeekendEdCoverShifts(constraintFactory),

                        // General scheduling ACGME rules                         
                        // maximumWorkingHoursInFourWeekPeriod(constraintFactory),     
                        // atLeast10HoursBetweenTwoShifts(constraintFactory),       
                                                       
                };
        }
/*
        Constraint rewardConsecutiveNightBlocks(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    // Ensure we're only considering night shift blocks
                    Joiners.equal(Shift::getEmployee),  // Same employee
                    Joiners.filtering((shift1, shift2) -> isNightShiftBlock(shift1) && isNightShiftBlock(shift2)))
                    .filter((shift1, shift2) -> {
                        // Determine the chronological order of the shifts
                        Shift earlierShift = shift1.getStart().isBefore(shift2.getStart()) ? shift1 : shift2;
                        Shift laterShift = shift1.equals(earlierShift) ? shift2 : shift1;
                        // Calculate the expected start time of the later shift based on the end time of the earlier shift
                        LocalDateTime expectedStartOfLaterShift = earlierShift.getEnd().plusHours(60);  // 2.5 days later
                        // Check if the later shift starts as expected
                        return laterShift.getStart().isEqual(expectedStartOfLaterShift);
                    })
                    // Reward this pattern to encourage scheduling consecutive night blocks with the appropriate weekend break in between
                    .reward(HardMediumSoftScore.ONE_SOFT)
                    .asConstraint("Reward consecutive night blocks with two days off in between");
        }
*/

        Constraint rewardConsecutiveNightBlocks(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .filter(this::isNightShiftBlock) // Filter for night shifts.
                    .groupBy(Shift::getEmployee, toList()) // Group shifts by employee and collect them into a list.
                    .reward(HardMediumSoftScore.ONE_SOFT, (employee, shifts) -> {
                        // Sort shifts by start time to analyze them in sequence.
                        List<Shift> sortedShifts = shifts.stream()
                                                         .sorted(Comparator.comparing(Shift::getStart))
                                                         .collect(Collectors.toList());

                        int totalRewards = 0;
                        List<List<Shift>> blocks = new ArrayList<>();
                        List<Shift> currentBlock = new ArrayList<>();

                        // Logic to form blocks based on the 2.5-day rule between consecutive shifts.
                        for (int i = 0; i < sortedShifts.size(); i++) {
                            Shift currentShift = sortedShifts.get(i);
                            if (!currentBlock.isEmpty()) {
                                Shift lastShiftInBlock = currentBlock.get(currentBlock.size() - 1);
                                long hoursBetween = ChronoUnit.HOURS.between(lastShiftInBlock.getEnd(), currentShift.getStart());
                                if (hoursBetween <= 60) { // Continue the current block.
                                    currentBlock.add(currentShift);
                                } else { // Start a new block.
                                    blocks.add(new ArrayList<>(currentBlock));
                                    currentBlock.clear();
                                    currentBlock.add(currentShift);
                                }
                            } else {
                                currentBlock.add(currentShift);
                            }
                        }
                        if (!currentBlock.isEmpty()) {
                            blocks.add(currentBlock);
                        }

                        // Evaluate each block for rewards, considering the 4-week gap rule for subsequent blocks.
                        LocalDateTime lastBlockEndTime = null;
                        for (List<Shift> block : blocks) {
                            if (lastBlockEndTime == null || ChronoUnit.WEEKS.between(lastBlockEndTime, block.get(0).getStart()) >= 4) {
                                int blockWeeks = block.size();
                                if (blockWeeks >= 2) { // Only reward blocks of 2 or more weeks.
                                    totalRewards += Math.min(blockWeeks - 1, 3); // Cap at 3 points for 4 or more weeks.
                                }
                                lastBlockEndTime = block.get(block.size() - 1).getEnd();
                            }
                        }
                        return totalRewards;
                    })
                    .asConstraint("Reward consecutive night float blocks with sufficient breaks");
        }


        Constraint noPediatricsForR4InJune(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    // Filter for pediatric shifts
                    .filter(shift -> shift.getLocation().equals("Peds"))
                    // Ensure the shift is in June
                    .filter(shift -> shift.getStart().getMonth() == Month.JUNE)
                    // Ensure the employee is an R4 resident
                    .filter(shift -> shift.getEmployee() != null && "R4".equals(shift.getEmployee().getEmployeeType()))
                    .penalize(HardMediumSoftScore.ONE_HARD, 
                              (shift) -> 1)
                    .asConstraint("Pediatric away shifts cannot be assigned to R4 residents during June due to transition to fellowship");
        }

        Constraint noPediatricsForR3InJune(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    // Filter for pediatric shifts
                    .filter(shift -> shift.getLocation().equals("Peds"))
                    // Ensure the shift is in June
                    .filter(shift -> shift.getStart().getMonth() == Month.JUNE)
                    // Ensure the employee is an R4 resident
                    .filter(shift -> shift.getEmployee() != null && "R3".equals(shift.getEmployee().getEmployeeType()))
                    .penalize(HardMediumSoftScore.ONE_HARD, 
                              (shift) -> 10)
                    .asConstraint("Pediatric away shifts cannot be assigned to R3 residents during June because of CORE");
        }        

        Constraint pediatricShiftsDuringHolidays(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .filter(shift -> "Peds".equals(shift.getLocation())) // Assuming "Peds" identifies pediatric shifts
                    .filter(shift -> {
                        // Check if the shift date range includes any of the specified holidays
                        LocalDate shiftStart = shift.getStart().toLocalDate();
                        LocalDate shiftEnd = shift.getEnd().toLocalDate();
                        return holidays.stream().anyMatch(holiday -> 
                                !holiday.isBefore(shiftStart) && !holiday.isAfter(shiftEnd));
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD,
                              shift -> {
                                  // Calculate penalty, for example, 1 hard score per holiday included in the shift
                                  long holidayCount = holidays.stream()
                                          .filter(holiday -> 
                                                  !holiday.isBefore(shift.getStart().toLocalDate()) && 
                                                  !holiday.isAfter(shift.getEnd().toLocalDate()))
                                          .count();
                                  return (int) holidayCount * 50;
                              })
                    .asConstraint("Penalize pediatric shifts that include holidays");
        }

        Constraint ensureMinimumWeekdayAndWeekendEdCoverShifts(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Employee.class)
                    .join(Shift.class, Joiners.equal(Function.identity(), Shift::getEmployee))
                    .groupBy((employee, shift) -> employee, // Group by employee
                             // Collect sets indicating whether ED cover shifts are on weekends or weekdays
                             toSet((employee, shift) -> isWeekend(shift) ? "Weekend" : "Weekday"))
                    .filter((employee, typesOfEdCoverShifts) -> !(typesOfEdCoverShifts.contains("Weekend") && typesOfEdCoverShifts.contains("Weekday")))
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, typesOfEdCoverShifts) -> {
                        int penalty = 0;
                        if (!typesOfEdCoverShifts.contains("Weekend")) penalty += 50; // Adjust penalty for missing weekend shift
                        if (!typesOfEdCoverShifts.contains("Weekday")) penalty += 50; // Adjust penalty for missing weekday shift
                        return penalty;
                    })
                    .asConstraint("Ensure at least one weekend and one weekday ED cover shift for all residents");
        }

        Constraint ensureMinimumOneShiftPerRequiredType(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Employee.class)
                .join(Shift.class, Joiners.equal(Function.identity(), Shift::getEmployee))
                .groupBy((employee, shift) -> employee, // Group by employee to collect all their shifts
                         toSet((employee, shift) -> shift.getLocation())) // Collect unique shift types per employee
                .filter((employee, shiftTypes) -> !requiredShiftTypes.stream().allMatch(shiftTypes::contains)) // Check if all required types are present
                .penalize(HardMediumSoftScore.ONE_HARD, (employee, shiftTypes) -> 
                    100 * (int) requiredShiftTypes.stream().filter(type -> !shiftTypes.contains(type)).count()) // Penalize for each missing type
                .asConstraint("Ensure at least one shift per required type for all residents");
        }

        private boolean isEDDayShift(Shift shift) {
            return shift.getLocation().equals("Day Shift");
        }

        // Custom method to calculate shift duration considering block shifts
        private int calculateCustomShiftDurationInMinutes(Shift shift) {
            if (isBlockLocation(shift) && !isNightShift(shift)) {
                return 30 * 60; // 30 hours in minutes for block shifts
            } else if (isNightShift(shift)) {
                return 60 * 60; // 60 hours in minutes for night shifts
            } else {
                return getShiftDurationInMinutes(shift); // Actual duration for other shifts
            }
        }

        // Method to determine the 4-week period index for a given date with July 1st as the reference start date
        private int getFourWeekPeriodIndex(LocalDate date) {
            LocalDate referenceStartDate = LocalDate.of(date.getYear(), 7, 1);
            long daysSinceReferenceStart = ChronoUnit.DAYS.between(referenceStartDate, date);
            // Check if the date is before July 1st and adjust the year of the reference date
            if (daysSinceReferenceStart < 0) {
                referenceStartDate = LocalDate.of(date.getYear() - 1, 7, 1);
                daysSinceReferenceStart = ChronoUnit.DAYS.between(referenceStartDate, date);
            }
            return (int) (daysSinceReferenceStart / 28); // 4 weeks = 28 days
        }

        // Maximum Working Hours Constraint for 4-week period with July 1st as the reference date
        Constraint maximumWorkingHoursInFourWeekPeriod(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .groupBy(Shift::getEmployee,
                             shift -> getFourWeekPeriodIndex(shift.getStart().toLocalDate()),
                             sum((Shift shift) -> calculateCustomShiftDurationInMinutes(shift)))
                    .filter((employee, fourWeekPeriodIndex, totalMinutes) -> totalMinutes > (320 * 60)) // 320 hours in minutes
                    .penalize(HardMediumSoftScore.ONE_SOFT,
                              (employee, fourWeekPeriodIndex, totalMinutes) -> (totalMinutes - (320 * 60)) / 60)
                    .asConstraint("Maximum working hours in four-week period");
        }
        
        Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
                return constraintFactory.forEachUniquePair(Shift.class, 
                                Joiners.equal(Shift::getEmployee),
                                Joiners.overlapping(Shift::getStart, Shift::getEnd))
                        // Add a filter to exclude pairs where one shift ends exactly when the other starts
                        .filter((shift1, shift2) -> !shift1.getEnd().equals(shift2.getStart()))
                        // Add filter to exclude pairs where on shift is a block location and the other is ED cover
                        .filter((shift1, shift2) -> {
                                boolean isSpecialCase = (isEDCover(shift1) && isBlockLocation(shift2)) 
                                || (isEDCover(shift2) && isBlockLocation(shift1));
                                return !isSpecialCase; // Only penalize if it's not a special case
                        })
                        .penalize(HardMediumSoftScore.ONE_HARD.ofHard(2))
                        .asConstraint("Overlapping shift");
        }

        Constraint noOverlappingPediatricShiftsBetweenDifferentResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                            // This joiner ensures we're looking at shifts with overlapping times
                            Joiners.overlapping(Shift::getStart, Shift::getEnd))
                    // Filter only pediatric shifts
                    .filter((shift1, shift2) -> shift1.getLocation().equals("Peds") && shift2.getLocation().equals("Peds"))
                    // Ensure the shifts belong to different residents
                    .filter((shift1, shift2) -> !shift1.getEmployee().equals(shift2.getEmployee()))
                    .penalize(HardMediumSoftScore.ONE_MEDIUM.ofMedium(1)) // Apply a medium penalty of 1
                    .asConstraint("No overlapping pediatric shifts between different residents");
        }

        Constraint noOverlappingPediatricShiftsForR4aRC(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                            // This joiner ensures we're looking at shifts with overlapping times
                            Joiners.overlapping(Shift::getStart, Shift::getEnd))
                    // Filter only pediatric shifts
                    .filter((shift1, shift2) -> shift1.getLocation().equals("Peds") && shift2.getLocation().equals("Peds"))
                    // Ensure the shifts belong to different residents
                    .filter((shift1, shift2) -> shift1.getEmployee().getName().equals("R4a RC") ||
                                        shift2.getEmployee().getName().equals("R4a RC"))
                    .penalize(HardMediumSoftScore.ONE_HARD.ofHard(1)) // Apply a medium penalty of 1
                    .asConstraint("No overlapping pediatric shifts of RC");
        }


        Constraint onlyTwoResidentsForOverlappingPediatricShifts(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    // Filter for pediatric shifts
                    .filter(shift -> shift.getLocation().equals("Peds"))
                    // Create a composite group key consisting of the start and end times to identify overlapping shifts
                    .groupBy(shift -> new AbstractMap.SimpleEntry<>(shift.getStart(), shift.getEnd()), 
                             countDistinct(Shift::getEmployee))
                    // Apply a filter to identify groups where more than two distinct employees have overlapping shifts
                    .filter((timeFrame, distinctEmployeeCount) -> distinctEmployeeCount > 2)
                    // Penalize each group of overlapping shifts that violates the constraint
                    .penalize(HardMediumSoftScore.ONE_HARD, 
                              (timeFrame, distinctEmployeeCount) -> (distinctEmployeeCount - 2) * 10)
                    .asConstraint("Only two residents can overlap for pediatric shifts at a time");
        }        

        Constraint preventEDCoverBeforeSaturdayShift(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    // Check if shift1 is an ED cover on Friday and shift2 is any shift on Saturday
                    boolean shift1IsEDCoverOnFriday = isEDCover(shift1) && shift1.getStart().getDayOfWeek() == DayOfWeek.FRIDAY;
                    boolean shift2IsOnSaturday = shift2.getStart().getDayOfWeek() == DayOfWeek.SATURDAY;
                    // Ensure shift2 starts within the same week of shift1's start to prevent penalizing across different weeks
                    boolean withinSameWeek = shift1.getStart().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .isEqual(shift2.getStart().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));

                    return shift1IsEDCoverOnFriday && shift2IsOnSaturday && withinSameWeek;
                })
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Prevent ED cover shifts on Friday with a proceeding shift on Saturday");
        }        

        Constraint preventEDCoverBeforeNightBlock(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    boolean shift1IsEDCoverEndingBeforeNight = isEDCover(shift1) && isNightShift(shift2) && shift1.getEnd().equals(shift2.getStart());
                    boolean shift2IsEDCoverEndingBeforeNight = isEDCover(shift2) && isNightShift(shift1) && shift2.getEnd().equals(shift1.getStart());
                    return shift1IsEDCoverEndingBeforeNight || shift2IsEDCoverEndingBeforeNight;
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Prevent ED cover shifts ending right before night block shifts");
        }

        private boolean isEDCover(Shift shift) {
                return shift.getLocation().equals("ED cover"); // Check if the shift is an ED call shift
        }

        private boolean isBlockLocation(Shift shift) {
                // Add your logic to determine if a shift is a block location shift
                // For example, it could be a check against a set of block location names
                return shift.getLocation().equals("IR"); // || shift.getLocation().equals("Peds"); // || shift.getLocation().equals("Day Shift");
        }

        Constraint atLeast10HoursBetweenTwoShifts(ConstraintFactory constraintFactory) {
                return constraintFactory.forEachUniquePair(Shift.class,
                                Joiners.equal(Shift::getEmployee),
                                Joiners.lessThanOrEqual(Shift::getEnd, Shift::getStart))
                        .filter((firstShift, secondShift) -> 
                                !(isBlockLocation(firstShift) && isEveningShift(secondShift)) &&
                                Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours() < 10)
                        .penalize(HardMediumSoftScore.ONE_MEDIUM,
                                (firstShift, secondShift) -> {
                                        long hoursShortOfBreak = 10 - Duration.between(firstShift.getEnd(), secondShift.getStart()).toHours();
                                        return hoursShortOfBreak > 0 ? (int) hoursShortOfBreak : 0;
                                })
                        .asConstraint("At least 10 hours between 2 shifts");
        }

        Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .join(Availability.class,
                          Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                    .filter((shift, availability) -> 
                        availability.getAvailabilityType() == AvailabilityType.UNAVAILABLE &&
                        !shift.getLocation().equals("Peds") && // Exclude "Peds" shifts from this constraint
                        (shift.getStart().toLocalDate().equals(availability.getDate()) ||
                         shift.getEnd().toLocalDate().equals(availability.getDate()) ||
                         (shift.getStart().toLocalDate().isBefore(availability.getDate()) &&
                          shift.getEnd().toLocalDate().isAfter(availability.getDate()))))
                    .penalize(HardMediumSoftScore.ONE_HARD, (shift, availability) -> 100)
                    .asConstraint("Unavailable employee including shift span, excluding Pediatrics");
        }

        Constraint unavailableEmployeeForPediatrics(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .filter(shift -> shift.getLocation().equals("Peds")) // Focus on "Peds" shifts
                    .join(Availability.class,
                          Joiners.equal(Shift::getEmployee, Availability::getEmployee),
                          Joiners.filtering((shift, availability) -> 
                              availability.getAvailabilityType() == AvailabilityType.UNAVAILABLE &&
                              (shift.getStart().toLocalDate().isBefore(availability.getDate()) &&
                               shift.getEnd().toLocalDate().isAfter(availability.getDate()) ||
                               shift.getStart().toLocalDate().isEqual(availability.getDate()) ||
                               shift.getEnd().toLocalDate().isEqual(availability.getDate()))))
                    .penalize(HardMediumSoftScore.ONE_HARD,
                              (shift, availability) -> calculateOverlapDays(shift, availability))
                    .asConstraint("Penalize Pediatrics shifts starting or ending on an unavailable employee day");
        }

        private int calculateOverlapDays(Shift shift, Availability availability) {
            LocalDate shiftStart = shift.getStart().toLocalDate();
            LocalDate shiftEnd = shift.getEnd().toLocalDate();
            LocalDate unavailableDate = availability.getDate();
            int overlapDays = 0;

            // Check if the unavailable date is within the shift period
            if ((shiftStart.isBefore(unavailableDate) || shiftStart.isEqual(unavailableDate)) &&
                (shiftEnd.isAfter(unavailableDate) || shiftEnd.isEqual(unavailableDate))) {
                // Calculate overlap considering the shift might span multiple days
                if (shiftStart.isBefore(unavailableDate)) {
                    overlapDays += (int) ChronoUnit.DAYS.between(unavailableDate, shiftEnd.plusDays(1)); // +1 to include end date
                } else if (shiftEnd.isAfter(unavailableDate)) {
                    overlapDays += (int) ChronoUnit.DAYS.between(shiftStart, unavailableDate.plusDays(1)); // +1 for inclusive end
                } else {
                    // Shift starts and ends on the unavailable day
                    overlapDays = 1;
                }
            }

            return overlapDays * 100;
        }        

        Constraint penalize24HourShiftsAroundUnavailabilityExcludingNightShifts(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                // Join shifts with unavailability to find overlaps
                .join(Availability.class, Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.UNAVAILABLE)
                // Use isWeekendEDCoverShift to identify the 24-hour shifts (weekend ED cover shifts)
                .filter((shift, availability) -> isWeekendEDCoverShift(shift))
                // Exclude night shift blocks from this penalty
                .filter((shift, availability) -> !isNightShiftBlock(shift))
                .filter((shift, availability) -> {
                    LocalDate shiftDate = shift.getStart().toLocalDate();
                    LocalDate availabilityDate = availability.getDate();
                    // Check if the shift is within 2 days before or after the unavailability date
                    boolean isDayBeforeOrAfter = shiftDate.plusDays(1).equals(availabilityDate) ||
                                                  shiftDate.minusDays(1).equals(availabilityDate) ||
                                                  shiftDate.equals(availabilityDate);
                    return isDayBeforeOrAfter;
                })
                .penalize(HardMediumSoftScore.ONE_HARD, (shift, availability) -> 1)
                .asConstraint("Penalize any 24-hour shifts 48 hours before or after unavailability for the same employee, excluding night shifts");
        }

        Constraint noShiftAfter24HourSundayShift(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    // Check if shift1 is a night shift block ending on Saturday morning and shift2 is a 24-hour ED cover shift starting immediately after
                    boolean shift1Monday = shift1.getStart().getDayOfWeek() == DayOfWeek.MONDAY;

                    boolean shift2SundayShift = isWeekendEDCoverShift(shift2) &&
                                                                    shift2.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift2.getEnd().toLocalDate().isEqual(shift1.getStart().toLocalDate()); // Ensure shift1 starts on the same day shift2 ends

                    // Reverse roles 

                    boolean shift2Monday = shift2.getStart().getDayOfWeek() == DayOfWeek.MONDAY;

                    boolean shift1SundayShift = isWeekendEDCoverShift(shift1) &&
                                                                    shift1.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift1.getEnd().toLocalDate().isEqual(shift2.getStart().toLocalDate()); // Ensure shift1 starts on the same day shift2 ends

                    return (shift1Monday && shift2SundayShift) ||
                           (shift2Monday && shift1SundayShift);
                })
                .penalize(HardMediumSoftScore.ONE_HARD, (shift1, shift2) -> 10)
                .asConstraint("No shift after 24-hour sunday weekend shift");
        }    

        Constraint no24HourShiftAfter24HourShift(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    // Check if shift1 is a night shift block ending on Saturday morning and shift2 is a 24-hour ED cover shift starting immediately after
                    boolean shift1SaturdayShiftEndingSunday = isWeekendEDCoverShift(shift1) && 
                                                               shift1.getStart().getDayOfWeek() == DayOfWeek.SATURDAY; 

                    boolean shift2SundayShift = isWeekendEDCoverShift(shift2) &&
                                                                    shift2.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift2.getStart().equals(shift1.getEnd()); // Ensuring shifts are sequential

                    // Reverse roles if shift2 is the night shift block and shift1 is the 24-hour ED cover
                    boolean shift2SaturdayShiftEndingSunday = isWeekendEDCoverShift(shift2) && 
                                                              shift2.getStart().getDayOfWeek() == DayOfWeek.SATURDAY; 

                    boolean shift1SundayShift = isWeekendEDCoverShift(shift1) &&
                                                                    shift1.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift1.getStart().equals(shift2.getEnd()); // Ensuring shifts are sequential

                    return (shift1SaturdayShiftEndingSunday && shift2SundayShift) ||
                           (shift2SaturdayShiftEndingSunday && shift1SundayShift);
                })
                .penalize(HardMediumSoftScore.ONE_HARD, (shift1, shift2) -> 10)
                .asConstraint("No 24 shift after 24-hour weekend shift");
        }    

        Constraint noSunday24HourEDCoverShiftAfterNightShiftBlock(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    // Check if shift1 is a night shift block ending on Saturday morning and shift2 is a 24-hour ED cover shift on Sunday
                    boolean shift1IsNightShiftEndingSaturday = isNightShiftBlock(shift1); // Assuming 8 AM as the end time

                    boolean shift2Is24HourEDCoverStartingSunday= isWeekendEDCoverShift(shift2) &&
                                                                    shift2.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift2.getStart().equals(shift1.getEnd().plusDays(1)); // Ensuring shifts are sequential skipping the saturday shift

                    // Reverse roles if shift2 is the night shift block and shift1 is the 24-hour ED cover
                    boolean shift2IsNightShiftEndingSaturday = isNightShiftBlock(shift2);

                    boolean shift1Is24HourEDCoverStartingSunday = isWeekendEDCoverShift(shift1) &&
                                                                    shift1.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                                                                    shift1.getStart().equals(shift2.getEnd().plusDays(1));

                    return (shift1IsNightShiftEndingSaturday && shift2Is24HourEDCoverStartingSunday) ||
                           (shift2IsNightShiftEndingSaturday && shift1Is24HourEDCoverStartingSunday);
                })
                .penalize(HardMediumSoftScore.ONE_MEDIUM, (shift1, shift2) -> 1)
                .asConstraint("Penalize 24-hour ED cover shift on Sunday directly after a night shift block");
        }        


        Constraint no24HourEDCoverShiftAfterNightShiftBlock(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee))
                .filter((shift1, shift2) -> {
                    // Check if shift1 is a night shift block ending on Saturday morning and shift2 is a 24-hour ED cover shift starting immediately after
                    boolean shift1IsNightShiftEndingSaturday = isNightShiftBlock(shift1) && 
                                                               shift1.getEnd().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                                               shift1.getEnd().toLocalTime().equals(LocalTime.of(8, 0)); // Assuming 8 AM as the end time

                    boolean shift2Is24HourEDCoverStartingSaturday = isWeekendEDCoverShift(shift2) &&
                                                                    shift2.getStart().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                                                    shift2.getStart().equals(shift1.getEnd()); // Ensuring shifts are sequential

                    // Reverse roles if shift2 is the night shift block and shift1 is the 24-hour ED cover
                    boolean shift2IsNightShiftEndingSaturday = isNightShiftBlock(shift2) && 
                                                               shift2.getEnd().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                                               shift2.getEnd().toLocalTime().equals(LocalTime.of(8, 0));

                    boolean shift1Is24HourEDCoverStartingSaturday = isWeekendEDCoverShift(shift1) &&
                                                                    shift1.getStart().getDayOfWeek() == DayOfWeek.SATURDAY &&
                                                                    shift1.getStart().equals(shift2.getEnd());

                    return (shift1IsNightShiftEndingSaturday && shift2Is24HourEDCoverStartingSaturday) ||
                           (shift2IsNightShiftEndingSaturday && shift1Is24HourEDCoverStartingSaturday);
                })
                .penalize(HardMediumSoftScore.ONE_HARD, (shift1, shift2) -> 10)
                .asConstraint("No 24-hour ED cover shift on Saturday directly after a night shift block");
        }        

        Constraint penalizeBlockScheduleAfter24HourSundayShift(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachUniquePair(Shift.class,
                    Joiners.equal(Shift::getEmployee),
                    Joiners.lessThanOrEqual(Shift::getEnd, Shift::getStart))
                .filter((firstShift, secondShift) -> 
                    firstShift.getStart().getDayOfWeek() == DayOfWeek.SUNDAY &&
                    firstShift.getEnd().getDayOfWeek() == DayOfWeek.MONDAY &&
                    Duration.between(firstShift.getStart(), firstShift.getEnd()).equals(Duration.ofHours(24)) &&
                    secondShift.getStart().getDayOfWeek() == DayOfWeek.MONDAY &&
                    isBlockLocation(secondShift))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("Penalize block schedule after 24-hour Sunday shift");
        }


        Constraint minimumNFShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    // Ensure the shift is a Night Shift
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("Night Shift"))
                    // Group by employee
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        // Skip null employees since the focus is on ensuring minimum shifts for existing R2-R4 employees
                        if (employee == null) return false;
                        // Determine the minimum shift count based on employee type
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_NF_SHIFTS_R2;
                            case "R3" -> MIN_NF_SHIFTS_R3;
                            case "R4" -> MIN_NF_SHIFTS_R4;
                            default -> 0; // This default case could potentially be removed if all employee types are covered
                        };
                        return count < minimumRequiredShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, count) -> {
                        // Calculate the shortfall and penalty
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_NF_SHIFTS_R2;
                            case "R3" -> MIN_NF_SHIFTS_R3;
                            case "R4" -> MIN_NF_SHIFTS_R4;
                            default -> 0; // Again, this might not be necessary if you're sure of your employee types
                        };
                        int shortfall = (minimumRequiredShifts - count) * 10;
                        return Math.max(0, shortfall); // Ensure non-negative penalty
                    })
                    .asConstraint("Minimum night float shifts for R2, R3, R4 residents");
        }

        Constraint maximumNFShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    // Ensure the shift is a Night Shift
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("Night Shift"))
                    // Group by employee
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        // Skip null employees since the focus is on enforcing maximum shifts for existing R2-R4 employees
                        if (employee == null) return false;
                        // Determine the maximum shift count based on employee type
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_NF_SHIFTS_R2;
                            case "R3" -> MAX_NF_SHIFTS_R3;
                            case "R4" -> MAX_NF_SHIFTS_R4;
                            default -> Integer.MAX_VALUE; // Default to a very high number if the type is not covered
                        };
                        return count > maximumAllowedShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, count) -> {
                        // Calculate the excess and penalty
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_NF_SHIFTS_R2;
                            case "R3" -> MAX_NF_SHIFTS_R3;
                            case "R4" -> MAX_NF_SHIFTS_R4;
                            default -> Integer.MAX_VALUE;
                        };
                        int excess = count - maximumAllowedShifts;
                        return Math.max(0, excess); // Ensure non-negative penalty
                    })
                    .asConstraint("Maximum night float shifts for R2, R3, R4 residents");
        }        

        Constraint maximumPedsShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    // Ensure the shift is a Pediatric Shift
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("Peds"))
                    // Group by employee
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        // Skip null employees and check if the count of Peds shifts is greater than 1
                        return employee != null && count > 1;
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, count) -> {
                        // Calculate the excess and penalty
                        int excess = 10 * (count - 1); // Only 1 Peds shift is allowed
                        return Math.max(0, excess); // Ensure non-negative penalty
                    })
                    .asConstraint("Maximum pediatric shifts per resident");
        }

        Constraint minimumWeekendShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("ED cover"))
                    .filter(shift -> isWeekend(shift)) // Only consider weekend shifts
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        if (employee == null) return false;
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_WEEKEND_SHIFTS_R2;
                            case "R3" -> MIN_WEEKEND_SHIFTS_R3;
                            case "R4" -> MIN_WEEKEND_SHIFTS_R4;
                            default -> 0;
                        };
                        return count < minimumRequiredShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, count) -> { // Example of a higher penalty level for weekend shifts
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_WEEKEND_SHIFTS_R2;
                            case "R3" -> MIN_WEEKEND_SHIFTS_R3;
                            case "R4" -> MIN_WEEKEND_SHIFTS_R4;
                            default -> 0;
                        };
                        return (minimumRequiredShifts - count) * 5;
                    })
                    .asConstraint("Minimum weekend shifts for R2, R3, R4 residents");
        }

        Constraint maximumWeekendShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("ED cover"))
                    .filter(shift -> isWeekend(shift)) // Only consider weekend shifts
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        if (employee == null) return false;
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_WEEKEND_SHIFTS_R2;
                            case "R3" -> MAX_WEEKEND_SHIFTS_R3;
                            case "R4" -> MAX_WEEKEND_SHIFTS_R4;
                            default -> Integer.MAX_VALUE;
                        };
                        return count > maximumAllowedShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_HARD, (employee, count) -> { // Example of a penalty level for exceeding weekend shifts
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_WEEKEND_SHIFTS_R2;
                            case "R3" -> MAX_WEEKEND_SHIFTS_R3;
                            case "R4" -> MAX_WEEKEND_SHIFTS_R4;
                            default -> Integer.MAX_VALUE;
                        };
                        return count - maximumAllowedShifts;
                    })
                    .asConstraint("Maximum weekend shifts for R2, R3, R4 residents");
        }        

        Constraint minimumWeekdayShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("ED cover"))
                    .filter(shift -> !isWeekend(shift)) // Only consider weekday shifts
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        if (employee == null) return false;
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_WEEKDAY_SHIFTS_R2;
                            case "R3" -> MIN_WEEKDAY_SHIFTS_R3;
                            case "R4" -> MIN_WEEKDAY_SHIFTS_R4;
                            default -> 0;
                        };
                        return count < minimumRequiredShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_MEDIUM, (employee, count) -> { // Example of a lower penalty level for weekday shifts
                        int minimumRequiredShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MIN_WEEKDAY_SHIFTS_R2;
                            case "R3" -> MIN_WEEKDAY_SHIFTS_R3;
                            case "R4" -> MIN_WEEKDAY_SHIFTS_R4;
                            default -> 0;
                        };
                        return minimumRequiredShifts - count;
                    })
                    .asConstraint("Minimum weekday shifts for R2, R3, R4 residents");
        }

        Constraint maximumWeekdayShiftsForResidents(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getLocation() != null && shift.getLocation().equals("ED cover"))
                    .filter(shift -> !isWeekend(shift)) // Only consider weekday shifts
                    .groupBy(Shift::getEmployee, count())
                    .filter((employee, count) -> {
                        if (employee == null) return false;
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_WEEKDAY_SHIFTS_R2;
                            case "R3" -> MAX_WEEKDAY_SHIFTS_R3;
                            case "R4" -> MAX_WEEKDAY_SHIFTS_R4;
                            default -> Integer.MAX_VALUE;
                        };
                        return count > maximumAllowedShifts;
                    })
                    .penalize(HardMediumSoftScore.ONE_MEDIUM, (employee, count) -> { // Penalty level for exceeding weekday shifts
                        int maximumAllowedShifts = switch (employee.getEmployeeType()) {
                            case "R2" -> MAX_WEEKDAY_SHIFTS_R2;
                            case "R3" -> MAX_WEEKDAY_SHIFTS_R3;
                            case "R4" -> MAX_WEEKDAY_SHIFTS_R4;
                            default -> Integer.MAX_VALUE;
                        };
                        return count - maximumAllowedShifts;
                    })
                    .asConstraint("Maximum weekday shifts for R2, R3, R4 residents");
        }

        Constraint penalizeExcessFridayShiftsForR4s(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getStart().getDayOfWeek() == DayOfWeek.FRIDAY)
                    .filter(shift -> "ED cover".equals(shift.getLocation()))
                    // Focus on R4 residents
                    .filter(shift -> shift.getEmployee() != null && "R4".equals(shift.getEmployee().getEmployeeType()))
                    // Group by employee to count Friday shifts
                    .groupBy(Shift::getEmployee, count())
                    // Apply the penalty when the count of Friday shifts exceeds 5
                    .filter((employee, count) -> count > 5)
                    .penalize(HardMediumSoftScore.ONE_MEDIUM, (employee, count) -> {
                        // Penalty calculation: for each shift above the threshold of 5, apply a penalty
                        return (count - 5) * 1; // Example: if penaltyMultiplier is set to 1, then each shift above 5 incurs a penalty of 1 point.
                    })
                    .asConstraint("Penalize excess Friday shifts for R4 residents");
        }

        // BALANCING SHIFT CONSTRAINTS

        Constraint weekenddayShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                    .filter(shift -> shift.getStart().getDayOfWeek() == DayOfWeek.SATURDAY || shift.getStart().getDayOfWeek() == DayOfWeek.SUNDAY)
                    .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                    .penalize(HardMediumSoftScore.ONE_HARD,
                        (employeeType, shiftList) -> calculateSatSunShiftBalancePenalty(shiftList))
                    .asConstraint("Balance Saturday and Sunday shifts for each employee type");
        }

        private int calculateSatSunShiftBalancePenalty(List<Shift> shifts) {
            // Count Saturday and Sunday shifts separately
            long saturdayCount = shifts.stream().filter(shift -> shift.getStart().getDayOfWeek() == DayOfWeek.SATURDAY).count();
            long sundayCount = shifts.stream().filter(shift -> shift.getStart().getDayOfWeek() == DayOfWeek.SUNDAY).count();

            // Calculate difference and apply penalty based on the imbalance
            long difference = Math.abs(saturdayCount - sundayCount);
            int maxDifference = 2;
            int penalty = 0;
            if (difference > maxDifference) {
                // Apply an incremental penalty for each unit of difference beyond the maxDifference
                penalty = (int) Math.pow(difference - maxDifference, 2); // Quadratic penalty for greater imbalance
            }
            return penalty;
        }

        Constraint weekendShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                .filter(this::isWeekend)
                .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                .penalize(HardMediumSoftScore.ONE_HARD,
                    (employeeType, shiftList) -> calculateWKShiftBalancePenalty(shiftList)) // 5
                .asConstraint("WeekendShiftsBalancing");
        }

        Constraint nightShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                .filter(this::isNightShift) // Only include shifts that are identified as night shifts
                .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                .penalize(HardMediumSoftScore.ONE_HARD, // ONE_MEDIUM
                    (employeeType, shiftList) -> 2 * calculateNFShiftBalancePenalty(shiftList))
                .asConstraint("NightShiftsBalancing");
        }      

        Constraint eveningShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                .filter(this::isEveningShift)
                .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                    (employeeType, shiftList) -> calculateShiftBalancePenalty(shiftList))
                .asConstraint("EveningShiftsBalancing");
        }

        Constraint dayShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                .filter(this::isDayShift) // Only include shifts that are identified as day shifts
                .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                .penalize(HardMediumSoftScore.ONE_HARD,
                    (employeeType, shiftList) -> 2 * calculateShiftBalancePenalty(shiftList)) // Double the penalty for day shifts
                .asConstraint("DayShiftsBalancing");
        }

        Constraint fridayEDCoverShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
                .filter(shift -> isEDCover(shift) && shift.getStart().getDayOfWeek() == DayOfWeek.FRIDAY)
                .groupBy(shift -> shift.getEmployee().getEmployeeType(), toList())
                .penalize(HardMediumSoftScore.ONE_SOFT,
                    (employeeType, shiftList) -> calculateEDFShiftBalancePenalty(shiftList)) // Double the penalty for day shifts
                .asConstraint("fridayEDCoverShiftsBalancing");
        }

 
         private int calculateEDFShiftBalancePenalty(List<Shift> shifts) {
            // Create a map to count shifts per employee
            Map<String, Integer> shiftCounts = new HashMap<>();
            for (Shift shift : shifts) {
                String employeeName = shift.getEmployee().getName();
                shiftCounts.merge(employeeName, 1, Integer::sum);
            }

            // Calculate the penalty based on the deviation from the balancing criterion
            int maxDifference = 1;
            int penalty = 0;
            for (String employee : shiftCounts.keySet()) {
                for (String otherEmployee : shiftCounts.keySet()) {
                    if (!employee.equals(otherEmployee)) {
                        int difference = Math.abs(shiftCounts.get(employee) - shiftCounts.get(otherEmployee));
                        if (difference > maxDifference) {
                            penalty += difference - maxDifference;
                        }
                    }
                }
            }
            return penalty;
        } 

        private int calculateWKShiftBalancePenalty(List<Shift> shifts) {
            // Create a map to count shifts per employee
            Map<String, Integer> shiftCounts = new HashMap<>();
            for (Shift shift : shifts) {
                String employeeName = shift.getEmployee().getName();
                shiftCounts.merge(employeeName, 1, Integer::sum);
            }

            // Define the maximum allowed difference in the number of weekend shifts between any two employees
            int maxDifference = 1;
            // Initialize the penalty
            int penalty = 0;
            // Iterate over each pair of employees to calculate the difference in their weekend shift counts
            for (String employee : shiftCounts.keySet()) {
                for (String otherEmployee : shiftCounts.keySet()) {
                    if (!employee.equals(otherEmployee)) {
                        int difference = Math.abs(shiftCounts.get(employee) - shiftCounts.get(otherEmployee));
                        // Apply penalty if the difference exceeds the maximum allowed
                        if (difference > maxDifference) {
                            // Incrementally increase the penalty for differences exceeding the maxDifference
                            // For example, the penalty could grow quadratically with the difference
                            int incrementalPenalty = (difference - maxDifference) * (difference - maxDifference);
                            penalty += incrementalPenalty;
                        }
                    }
                }
            }
            return penalty;
        }

        private int calculateNFShiftBalancePenalty(List<Shift> shifts) {
            // Create a map to count shifts per employee
            Map<String, Integer> shiftCounts = new HashMap<>();
            for (Shift shift : shifts) {
                String employeeName = shift.getEmployee().getName();
                shiftCounts.merge(employeeName, 1, Integer::sum);
            }

            // Calculate the penalty based on the deviation from the balancing criterion
            int maxDifference = 1;
            int penalty = 0;
            for (String employee : shiftCounts.keySet()) {
                for (String otherEmployee : shiftCounts.keySet()) {
                    if (!employee.equals(otherEmployee)) {
                        int difference = Math.abs(shiftCounts.get(employee) - shiftCounts.get(otherEmployee));
                        if (difference > maxDifference) {
                            penalty += difference - maxDifference;
                        }
                    }
                }
            }
            return penalty;
        }

        private int calculateShiftBalancePenalty(List<Shift> shifts) {
            // Create a map to count shifts per employee
            Map<String, Integer> shiftCounts = new HashMap<>();
            for (Shift shift : shifts) {
                String employeeName = shift.getEmployee().getName();
                shiftCounts.merge(employeeName, 1, Integer::sum);
            }

            // Calculate the penalty based on the deviation from the balancing criterion
            int maxDifference = 5;
            int penalty = 0;
            for (String employee : shiftCounts.keySet()) {
                for (String otherEmployee : shiftCounts.keySet()) {
                    if (!employee.equals(otherEmployee)) {
                        int difference = Math.abs(shiftCounts.get(employee) - shiftCounts.get(otherEmployee));
                        if (difference > maxDifference) {
                            penalty += difference - maxDifference;
                        }
                    }
                }
            }
            return penalty;
        }
/*
        Constraint fridayEDCoverShiftsBalancing(ConstraintFactory constraintFactory) {
            return constraintFactory.forEach(Shift.class)
            .filter(shift -> isEDCover(shift) && shift.getStart().getDayOfWeek() == DayOfWeek.FRIDAY)
            .groupBy(Shift::getEmployee, count())
            .penalize(HardMediumSoftScore.ONE_SOFT,
                      (employee, shiftCount) -> calculateEDCoverShiftBalancePenalty(employee, shiftCount, constraintFactory))
            .asConstraint("FridayEDCoverShiftsBalancing");
        }

        private int calculateEDCoverShiftBalancePenalty(Employee employee, long shiftCount, ConstraintFactory constraintFactory) {
            // Fetch all shifts to get a global view for balance calculation
            List<Shift> allFridayEDCoverShifts = constraintFactory.getConstraintStream().ofType(Shift.class)
                    .filter(shift -> isEDCover(shift) && shift.getStart().getDayOfWeek() == DayOfWeek.FRIDAY)
                    .toList();

            // Count shifts per employee
            Map<Employee, Long> shiftCounts = allFridayEDCoverShifts.stream()
                    .collect(Collectors.groupingBy(Shift::getEmployee, Collectors.counting()));

            long averageShifts = shiftCounts.values().stream().mapToLong(Long::longValue).sum() / shiftCounts.size();
            long penalty = Math.abs(shiftCount - averageShifts);

            // This penalty encourages even distribution of Friday ED cover shifts among all employees
            return (int) penalty;
        }
*/
        Constraint penalizeUnassignedShifts(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getEmployee() == null && !shift.isOptional())
                    .penalize("Unassigned mandatory shifts", HardMediumSoftScore.ONE_HARD, (shift) -> {
                        if (isWeekendEDCoverShift(shift)) {
                            return 2; // Double penalty for unassigned weekend ED cover shifts
                        } else if (isNightShiftBlock(shift)) {
                            return 5; // Triple penalty for unassigned night shifts // 3
                        } else if (isWeekdayEDCoverShift(shift)) {
                            return 1; // Single penalty for weekday ED cover shifts
                        } else {
                            return 1; // Default single penalty for all other shifts
                        }
                    });
        }

        private boolean isWeekendEDCoverShift(Shift shift) {
            DayOfWeek dayOfWeek = shift.getStart().getDayOfWeek();
            return (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) && "ED cover".equals(shift.getLocation());
        }

        private boolean isNightShiftBlock(Shift shift) {
            // Assuming "Night Shift" is used to denote night shifts
            return "Night Shift".equals(shift.getLocation());
        }

        private boolean isWeekdayEDCoverShift(Shift shift) {
            DayOfWeek dayOfWeek = shift.getStart().getDayOfWeek();
            return (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) && "ED cover".equals(shift.getLocation());
        }

/*
        Constraint penalizeUnassignedShifts(ConstraintFactory constraintFactory) {
            return constraintFactory.forEachIncludingNullVars(Shift.class)
                    .filter(shift -> shift.getEmployee() == null && !shift.isOptional())
                    .penalize(HardMediumSoftScore.ONE_HARD, (shift) -> 1) 
                    .asConstraint("Unassigned mandatory shifts");
        }
*/

        private boolean isWeekend(Shift shift) {
                DayOfWeek day = shift.getStart().getDayOfWeek();
                return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        }

        Constraint desiredDayForEmployee(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(Shift.class)
                        .join(Availability.class, Joiners.equal((Shift shift) -> shift.getStart().toLocalDate(), Availability::getDate),
                                Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                        .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.DESIRED)
                        .reward(HardMediumSoftScore.ONE_SOFT,
                                (shift, availability) -> getShiftDurationInMinutes(shift))
                        .asConstraint("Desired day for employee");
        }

        Constraint undesiredDayForEmployee(ConstraintFactory constraintFactory) {
                return constraintFactory.forEach(Shift.class)
                        .join(Availability.class, Joiners.equal((Shift shift) -> shift.getStart().toLocalDate(), Availability::getDate),
                                Joiners.equal(Shift::getEmployee, Availability::getEmployee))
                        .filter((shift, availability) -> availability.getAvailabilityType() == AvailabilityType.UNDESIRED)
                        .penalize(HardMediumSoftScore.ONE_SOFT,
                                (shift, availability) -> getShiftDurationInMinutes(shift))
                        .asConstraint("Undesired day for employee");
        }    

}
