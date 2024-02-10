package org.acme.employeescheduling.domain;

import java.util.Map;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyColumn;

@Entity
public class Rotation {
    @Id
    @GeneratedValue
    private Long id;

    private String residentType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rotation_blocks")
    @MapKeyColumn(name = "location")
    private Map<String, Integer> requiredBlocks; // Key: Location, Value: Minimum number of blocks

    public Rotation() {
    }

    public Rotation(String residentType, Map<String, Integer> requiredBlocks) {
        this.residentType = residentType;
        this.requiredBlocks = requiredBlocks;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResidentType() {
        return residentType;
    }

    public void setResidentType(String residentType) {
        this.residentType = residentType;
    }

    public Map<String, Integer> getRequiredBlocks() {
        return requiredBlocks;
    }

    public void setRequiredBlocks(Map<String, Integer> requiredBlocks) {
        this.requiredBlocks = requiredBlocks;
    }

    @Override
    public String toString() {
        return "Rotation{" +
               "id=" + id +
               ", residentType='" + residentType + '\'' +
               ", requiredBlocks=" + requiredBlocks +
               '}';
    }
}