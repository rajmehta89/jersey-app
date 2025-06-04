package com.example.Models;

import java.util.List;

public class AuditPlan {
    private String id;
    private String auditNo;
    private String auditDate;
    private String startDate;
    private String endDate;
    private String status;
    private List<AuditStandard> standards;
    private List<AuditDetail> details;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuditNo() {
        return auditNo;
    }

    public void setAuditNo(String auditNo) {
        this.auditNo = auditNo;
    }

    public String getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(String auditDate) {
        this.auditDate = auditDate;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<AuditStandard> getStandards() {
        return standards;
    }

    public void setStandards(List<AuditStandard> standards) {
        this.standards = standards;
    }

    public List<AuditDetail> getDetails() {
        return details;
    }

    public void setDetails(List<AuditDetail> details) {
        this.details = details;
    }
}

class AuditStandard {
    private String standardName;
    private String status;

    // Getters and Setters
    public String getStandardName() {
        return standardName;
    }

    public void setStandardName(String standardName) {
        this.standardName = standardName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

class AuditDetail {
    private String department;
    private String auditDate;

    // Getters and Setters
    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getAuditDate() {
        return auditDate;
    }

    public void setAuditDate(String auditDate) {
        this.auditDate = auditDate;
    }
}
