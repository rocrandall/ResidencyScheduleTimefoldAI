package org.acme.employeescheduling.domain;

import java.util.HashMap;
import java.util.Map;

public class ShiftCountDto {
    private int totalShifts;
    private int eveningShifts;
    private int weekendShifts;
    private int pedsShifts;
    private int nightShifts;
    private int dayShifts;
    private int irShifts;
    private int saturdayShifts;
    private int sundayShifts;
    private int fridayShifts;    
    private Map<String, Integer> shiftsByLocation;
    private String employeeType;

    public ShiftCountDto(String employeeType) {
        this.shiftsByLocation = new HashMap<>();
        this.employeeType = employeeType;
    }

    public void incrementTotalShifts() {
        this.totalShifts++;
    }

    public void incrementEveningShifts() {
        this.eveningShifts++;
    }

    public void incrementWeekendShifts() {
        this.weekendShifts++;
    }

    public void incrementPedsShifts() {
        this.pedsShifts++;
    }

    public void incrementNightShifts() {
        this.nightShifts++;
    }

    public void incrementDayShifts() {
        this.dayShifts++;
    }

    public void incrementIRShifts() {
        this.irShifts++;
    }

    public void incrementSundayShifts() {
        this.sundayShifts++;
    }

    public void incrementSaturdayShifts() {
        this.saturdayShifts++;
    }

    public void incrementFridayShifts() {
        this.fridayShifts++;
    }

    public void incrementShiftsByLocation(String location) {
        this.shiftsByLocation.merge(location, 1, Integer::sum);
    }

    public int getTotalShifts() {
        return totalShifts;
    }

    public int getEveningShifts() {
        return eveningShifts;
    }

    public int getWeekendShifts() {
        return weekendShifts;
    }

    public int getPedsShifts() {
        return pedsShifts;
    }

    public int getNightShifts() {
        return nightShifts;
    }

    public int getDayShifts() {
        return dayShifts;
    }

    public int getIRShifts() {
        return irShifts;
    }

    public int getSundayShifts() {
        return sundayShifts;
    }

    public int getSaturdayShifts() {
        return saturdayShifts;
    }

    public int getFridayShifts() {
        return fridayShifts;
    }

    public Map<String, Integer> getShiftsByLocation() {
        return shiftsByLocation;
    }

    public String getEmployeeType() {
        return employeeType;
    }
}