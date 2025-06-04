package com.example;

import com.example.util.DBConfig;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("/external-audit-plan")
public class ExternalAuditPlanResource {

    private static final Logger logger = Logger.getLogger(ExternalAuditPlanResource.class.getName());
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // DTO classes`
    public static class ExternalAuditPlanRequest {
        public String auditNo;
        public String auditType;
        public String auditDate;
        public String auditStartDate;
        public String auditEndDate;
        public String reportSubmitDate;
        public String status;
        public List<AuditorDetail> auditors;
        public List<StandardDetail> standards;
        public List<AuditDetail> details;
        public List<String> auditTypes;
    }

    public static class AuditorDetail {
        public String name;
        public String designation;
    }

    public static class StandardDetail {
        public String standardName;
        public String status;
    }

    public static class AuditDetail {
        public String department;
        public String auditDate;
        public String startTime;
        public String endTime;
        public String auditorName;
        public String auditCriteria;
    }

    // Add these class definitions after the existing DTO classes
    public static class ExternalAuditResponse {
        public boolean success;
        public String message;
        public Map<String, Object> data;

        public ExternalAuditResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.data = new HashMap<>();
        }
    }

    public static class ExternalAudit {
        public String id;
        public String auditPlanId;
        public String internalAuditNo;
        public String auditNo;
        public String standardName;
        public String auditDate;
        public String status;
        public String draftCount;
        public String allClauseDone;
    }

    public static class ExternalAuditDetail {
        public String clauseNo;
        public String ncNo;
        public String type;
        public String evidence;
        public String comment;
        public String status;
    }


    public static class ExternalAuditPlanResponse {
        public boolean success;
        public String message;
        public Map<String, Object> data;

        public ExternalAuditPlanResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.data = new HashMap<>();
        }
    }

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchExternalAudits(@QueryParam("query") String searchQuery,
                                         @HeaderParam("company-code") String companyCode,
                                         @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM " + companyCode + "_external_audit_plan " +
                    "WHERE audit_no LIKE ? OR standard_name LIKE ? OR audit_date LIKE ? " +
                    "ORDER BY id DESC";

            List<ExternalAudit> audits = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(query)) {
                String searchPattern = "%" + searchQuery + "%";
                ps.setString(1, searchPattern);
                ps.setString(2, searchPattern);
                ps.setString(3, searchPattern);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ExternalAudit audit = new ExternalAudit();
                        audit.id = rs.getString("id");
                        audit.auditPlanId = rs.getString("audit_plan_id");
                        audit.internalAuditNo = rs.getString("intr_audit_no");
                        audit.auditNo = rs.getString("audit_no");
                        audit.standardName = rs.getString("standard_name");
                        audit.auditDate = rs.getString("audit_date");
                        audit.status = rs.getString("status");
                        audit.draftCount = rs.getString("Draft_count");
                        audit.allClauseDone = rs.getString("all_clause_done");
                        audits.add(audit);
                    }
                }
            }

            ExternalAuditResponse response = new ExternalAuditResponse(true, "Search results retrieved successfully");
            response.data.put("audits", audits);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{id}/details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditDetails(@PathParam("id") int id,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            List<ExternalAuditDetail> details = new ArrayList<>();
            String query = "SELECT * FROM " + companyCode + "_external_audit_detail " +
                    "WHERE external_audit_id = ?";

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ExternalAuditDetail detail = new ExternalAuditDetail();
                        detail.clauseNo = rs.getString("clause_no");
                        detail.ncNo = rs.getString("nc_no");
                        detail.type = rs.getString("desc_type");
                        detail.evidence = rs.getString("evidence");
                        detail.comment = rs.getString("comment");
                        detail.status = rs.getString("status");
                        details.add(detail);
                    }
                }
            }

            ExternalAuditResponse response = new ExternalAuditResponse(true, "Audit details retrieved successfully");
            response.data.put("details", details);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }


    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAuditPlan(@PathParam("id") int id,
                                    ExternalAuditPlanRequest request,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            // Update main record
            String query = "UPDATE " + companyCode + "_external_audit_plan SET " +
                    "audit_no = ?, audit_type = ?, audit_date = ?, " +
                    "audit_start_date = ?, audit_end_date = ?, report_submit_date = ?, " +
                    "status = ? WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, request.auditNo);
                ps.setString(2, request.auditType);
                ps.setString(3, request.auditDate);
                ps.setString(4, request.auditStartDate);
                ps.setString(5, request.auditEndDate);
                ps.setString(6, request.reportSubmitDate);
                ps.setString(7, request.status);
                ps.setInt(8, id);
                ps.executeUpdate();
            }

            // Delete existing related records
            String[] tables = {
                    companyCode + "_external_audit_plan_auditors",
                    companyCode + "_external_audit_plan_standard",
                    companyCode + "_external_audit_plan_detail",
                    companyCode + "_external_audit_type_detail"
            };

            // Try different possible column names
            String[] possibleIdColumns = {
                    "audit_plan_id", "external_audit_plan_id", "id", "plan_id"
            };

            for (String table : tables) {
                boolean deleted = false;
                for (String idColumn : possibleIdColumns) {
                    try {
                        String deleteQuery = "DELETE FROM " + table + " WHERE " + idColumn + " = ?";
                        try (PreparedStatement ps = con.prepareStatement(deleteQuery)) {
                            ps.setInt(1, id);
                            ps.executeUpdate();
                            deleted = true;
                            break; // If successful, break the loop
                        }
                    } catch (SQLException e) {
                        // Try next column name
                        continue;
                    }
                }
                if (!deleted) {
                    throw new SQLException("Could not delete from table " + table +
                            " with any of the possible ID columns");
                }
            }

            // Insert new auditors
            if (request.auditors != null && !request.auditors.isEmpty()) {
                int maxAuditorId = getMaxId(con, companyCode + "_external_audit_plan_auditors");
                query = "INSERT INTO " + companyCode + "_external_audit_plan_auditors " +
                        "(id, audit_plan_id, auditor_name, auditor_desig) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (AuditorDetail auditor : request.auditors) {
                        ps.setInt(1, ++maxAuditorId);
                        ps.setInt(2, id);
                        ps.setString(3, auditor.name);
                        ps.setString(4, auditor.designation);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert new standards
            if (request.standards != null && !request.standards.isEmpty()) {
                int maxStandardId = getMaxId(con, companyCode + "_external_audit_plan_standard");
                query = "INSERT INTO " + companyCode + "_external_audit_plan_standard " +
                        "(id, audit_plan_id, standard_name, status) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (StandardDetail standard : request.standards) {
                        ps.setInt(1, ++maxStandardId);
                        ps.setInt(2, id);
                        ps.setString(3, standard.standardName);
                        ps.setString(4, standard.status);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert new details
            if (request.details != null && !request.details.isEmpty()) {
                int maxDetailId = getMaxId(con, companyCode + "_external_audit_plan_detail");
                query = "INSERT INTO " + companyCode + "_external_audit_plan_detail " +
                        "(id, audit_plan_id, department, audit_date, audit_start_time, " +
                        "audit_end_time, auditor_name, audit_criteria) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (AuditDetail detail : request.details) {
                        ps.setInt(1, ++maxDetailId);
                        ps.setInt(2, id);
                        ps.setString(3, detail.department);
                        ps.setString(4, detail.auditDate);
                        ps.setString(5, detail.startTime);
                        ps.setString(6, detail.endTime);
                        ps.setString(7, detail.auditorName);
                        ps.setString(8, detail.auditCriteria);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert new audit types
            if (request.auditTypes != null && !request.auditTypes.isEmpty()) {
                int maxAuditTypeId = getMaxId(con, companyCode + "_external_audit_type_detail");
                query = "INSERT INTO " + companyCode + "_external_audit_type_detail " +
                        "(id, external_audit_plan_id, audit_type_id) VALUES (?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (String auditTypeId : request.auditTypes) {
                        ps.setInt(1, ++maxAuditTypeId);
                        ps.setInt(2, id);
                        ps.setString(3, auditTypeId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Log the action
            logAction(con, companyCode, employeeName, "Update", "External Audit Plan", String.valueOf(id));

            con.commit();
            return Response.ok(new ExternalAuditPlanResponse(true, "Audit plan updated successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



    private int getMaxId(Connection con, String table) throws SQLException {
        String query = "SELECT MAX(id) as max_id FROM " + table;
        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("max_id") : 0;
        }
    }

    private void logAction(Connection con, String companyCode, String employeeName,
                           String action, String moduleName, String moduleId) throws SQLException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String todayDate = LocalDateTime.now().format(dtf);

        int maxLogId = getMaxId(con, companyCode + "_log_master");
        String query = "INSERT INTO " + companyCode + "_log_master " +
                "(id, fired_date, fired_by, status, module_name, module_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, ++maxLogId);
            ps.setString(2, todayDate);
            ps.setString(3, employeeName);
            ps.setString(4, action);
            ps.setString(5, moduleName);
            ps.setString(6, moduleId);
            ps.executeUpdate();
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlan(@PathParam("id") int id,
                                 @HeaderParam("company-code") String companyCode,
                                 @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            Map<String, Object> auditPlan = new HashMap<>();

            // Get main audit plan data
            String query = "SELECT * FROM " + companyCode + "_external_audit_plan WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        auditPlan.put("id", rs.getInt("id"));
                        auditPlan.put("auditNo", rs.getString("audit_no"));
                        auditPlan.put("auditType", rs.getString("audit_type"));
                        auditPlan.put("auditDate", rs.getString("audit_date"));
                        auditPlan.put("auditStartDate", rs.getString("audit_start_date"));
                        auditPlan.put("auditEndDate", rs.getString("audit_end_date"));
                        auditPlan.put("reportSubmitDate", rs.getString("report_submit_date"));
                        auditPlan.put("status", rs.getString("status"));
                    }
                }
            }

            // Get auditors
            List<Map<String, String>> auditors = new ArrayList<>();
            query = "SELECT * FROM " + companyCode + "_external_audit_plan_auditors WHERE audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> auditor = new HashMap<>();
                        auditor.put("name", rs.getString("auditor_name"));
                        auditor.put("designation", rs.getString("auditor_desig"));
                        auditors.add(auditor);
                    }
                }
            }
            auditPlan.put("auditors", auditors);

            // Get standards
            List<Map<String, String>> standards = new ArrayList<>();
            query = "SELECT * FROM " + companyCode + "_external_audit_plan_standard WHERE audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> standard = new HashMap<>();
                        standard.put("standardName", rs.getString("standard_name"));
                        standard.put("status", rs.getString("status"));
                        standards.add(standard);
                    }
                }
            }
            auditPlan.put("standards", standards);

            // Get details
            List<Map<String, String>> details = new ArrayList<>();
            query = "SELECT * FROM " + companyCode + "_external_audit_plan_detail WHERE audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> detail = new HashMap<>();
                        detail.put("department", rs.getString("department"));
                        detail.put("auditDate", rs.getString("audit_date"));
                        detail.put("startTime", rs.getString("audit_start_time"));
                        detail.put("endTime", rs.getString("audit_end_time"));
                        detail.put("auditorName", rs.getString("auditor_name"));
                        detail.put("auditCriteria", rs.getString("audit_criteria"));
                        details.add(detail);
                    }
                }
            }
            auditPlan.put("details", details);

            // Get audit types
            List<String> auditTypes = new ArrayList<>();
            query = "SELECT audit_type_id FROM " + companyCode + "_external_audit_type_detail WHERE external_audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auditTypes.add(rs.getString("audit_type_id"));
                    }
                }
            }
            auditPlan.put("auditTypes", auditTypes);

            ExternalAuditPlanResponse response = new ExternalAuditPlanResponse(true, "Audit plan retrieved successfully");
            response.data = auditPlan;
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlans(@HeaderParam("company-code") String companyCode,
                                  @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM " + companyCode + "_external_audit_plan ORDER BY id DESC";
            List<Map<String, Object>> auditPlans = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> plan = new HashMap<>();
                    plan.put("id", rs.getInt("id"));
                    plan.put("auditNo", rs.getString("audit_no"));
                    plan.put("auditDate", rs.getString("audit_date"));
                    plan.put("startDate", rs.getString("audit_start_date"));
                    plan.put("endDate", rs.getString("audit_end_date"));
                    plan.put("status", rs.getString("status"));

                    // Get standards
                    List<Map<String, String>> standards = new ArrayList<>();
                    String standardsQuery = "SELECT * FROM " + companyCode + "_external_audit_plan_standard WHERE audit_plan_id = ?";
                    try (PreparedStatement standardsPs = con.prepareStatement(standardsQuery)) {
                        standardsPs.setInt(1, rs.getInt("id"));
                        try (ResultSet standardsRs = standardsPs.executeQuery()) {
                            while (standardsRs.next()) {
                                Map<String, String> standard = new HashMap<>();
                                standard.put("standardName", standardsRs.getString("standard_name"));
                                standard.put("status", standardsRs.getString("status"));
                                standards.add(standard);
                            }
                        }
                    }
                    plan.put("standards", standards);

                    // Get details
                    List<Map<String, String>> details = new ArrayList<>();
                    String detailsQuery = "SELECT * FROM " + companyCode + "_external_audit_plan_detail WHERE audit_plan_id = ?";
                    try (PreparedStatement detailsPs = con.prepareStatement(detailsQuery)) {
                        detailsPs.setInt(1, rs.getInt("id"));
                        try (ResultSet detailsRs = detailsPs.executeQuery()) {
                            while (detailsRs.next()) {
                                Map<String, String> detail = new HashMap<>();
                                detail.put("department", detailsRs.getString("department"));
                                detail.put("auditDate", detailsRs.getString("audit_date"));
                                details.add(detail);
                            }
                        }
                    }
                    plan.put("details", details);

                    auditPlans.add(plan);
                }
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("data", auditPlans);
            ExternalAuditPlanResponse response = new ExternalAuditPlanResponse(true, "Audit plans retrieved successfully");
            response.data = responseData;
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAuditPlan(@PathParam("id") int id,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            // Delete related records first
            String[] tables = {
                    companyCode + "_external_audit_plan_auditors",
                    companyCode + "_external_audit_plan_standard",
                    companyCode + "_external_audit_plan_detail",
                    companyCode + "_external_audit_type_detail"
            };

            for (String table : tables) {

                String deleteQuery;


                if(!table.equals(companyCode + "_external_audit_type_detail")) {
                    deleteQuery = "DELETE FROM " + table + " WHERE audit_plan_id = ?";
                }

                else{
                    deleteQuery = "DELETE FROM " + table + " WHERE external_audit_plan_id = ?";
                }

                try (PreparedStatement ps = con.prepareStatement(deleteQuery)) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }

            // Delete main record
            String deleteMainQuery = "DELETE FROM " + companyCode + "_external_audit_plan WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(deleteMainQuery)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // Log the action
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            String maxLogIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(maxLogIdQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxLogId = rs.getInt("max_id");
                }
            }
            maxLogId++;

            String logQuery = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(logQuery)) {
                ps.setInt(1, maxLogId);
                ps.setString(2, todayDate);
                ps.setString(3, employeeName);
                ps.setString(4, "Delete");
                ps.setString(5, "External Audit Plan");
                ps.setString(6, String.valueOf(id));
                ps.executeUpdate();
            }

            con.commit();
            return Response.ok(new ExternalAuditPlanResponse(true, "Audit plan deleted successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveAuditPlan(@PathParam("id") int id,
                                     @HeaderParam("company-code") String companyCode,
                                     @HeaderParam("employee-id") String employeeId,
                                     @HeaderParam("employee-name") String employeeName) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "UPDATE " + companyCode + "_external_audit_plan SET status = 'Approve' WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // Log the action
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            String maxLogIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(maxLogIdQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxLogId = rs.getInt("max_id");
                }
            }
            maxLogId++;

            String logQuery = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(logQuery)) {
                ps.setInt(1, maxLogId);
                ps.setString(2, todayDate);
                ps.setString(3, employeeName);
                ps.setString(4, "Approve");
                ps.setString(5, "External Audit Plan");
                ps.setString(6, String.valueOf(id));
                ps.executeUpdate();
            }

            return Response.ok(new ExternalAuditPlanResponse(true, "Audit plan approved successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}/back-status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response backStatus(@PathParam("id") int id,
                               @HeaderParam("company-code") String companyCode,
                               @HeaderParam("employee-id") String employeeId,
                               @HeaderParam("employee-name") String employeeName) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "UPDATE " + companyCode + "_external_audit_plan SET status = 'Draft' WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            // Log the action
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            String maxLogIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(maxLogIdQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxLogId = rs.getInt("max_id");
                }
            }
            maxLogId++;

            String logQuery = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(logQuery)) {
                ps.setInt(1, maxLogId);
                ps.setString(2, todayDate);
                ps.setString(3, employeeName);
                ps.setString(4, "Back Status");
                ps.setString(5, "External Audit Plan");
                ps.setString(6, String.valueOf(id));
                ps.executeUpdate();
            }

            return Response.ok(new ExternalAuditPlanResponse(true, "Status reverted successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }


    @GET
    @Path("/certification-bodies")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertificationBodies(@HeaderParam("company-code") String companyCode,
                                           @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM certification_body WHERE id > 0";
            List<Map<String, String>> bodies = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> body = new HashMap<>();
                    body.put("id", rs.getString("id"));
                    body.put("companyName", rs.getString("company_name"));
                    bodies.add(body);
                }
            }

            return Response.ok(bodies).build();
        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/employees")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployees(@HeaderParam("company-code") String companyCode,
                                 @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM " + companyCode + "_employee_detail WHERE id > 0";
            List<Map<String, String>> employees = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> employee = new HashMap<>();
                    employee.put("id", rs.getString("id"));
                    employee.put("name", rs.getString("name"));
                    employees.add(employee);
                }
            }

            return Response.ok(employees).build();
        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/standards")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStandards(@HeaderParam("company-code") String companyCode,
                                 @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM company_std_detail WHERE company_id = ? AND status = 'Active'";
            List<Map<String, String>> standards = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, companyCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> standard = new HashMap<>();
                        standard.put("id", rs.getString("id"));
                        standard.put("stdName", rs.getString("std_name"));
                        standards.add(standard);
                    }
                }
            }

            return Response.ok(standards).build();
        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/departments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDepartments(@HeaderParam("company-code") String companyCode,
                                   @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM " + companyCode + "_department_master WHERE id > 0";
            List<Map<String, String>> departments = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> department = new HashMap<>();
                    department.put("id", rs.getString("id"));
                    department.put("department", rs.getString("department"));
                    departments.add(department);
                }
            }

            return Response.ok(departments).build();
        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/audit-types")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditTypes(@HeaderParam("company-code") String companyCode,
                                  @HeaderParam("employee-id") String employeeId) {
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT * FROM audit_type WHERE id > 0";
            List<Map<String, String>> auditTypes = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> auditType = new HashMap<>();
                    auditType.put("id", rs.getString("id"));
                    auditType.put("auditType", rs.getString("audit_type"));
                    auditTypes.add(auditType);
                }
            }

            return Response.ok(auditTypes).build();
        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAuditPlan(ExternalAuditPlanRequest request,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            // Get next ID for main table
            int maxId = 0;
            String query = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_plan";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxId = rs.getInt("max_id");
                }
            }
            maxId++;

            // Insert main record
            query = "INSERT INTO " + companyCode + "_external_audit_plan " +
                    "(id, audit_no, audit_type, audit_date, audit_start_date, audit_end_date, report_submit_date, status, approved_by, approved_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, maxId);
                ps.setString(2, request.auditNo);
                ps.setString(3, request.auditType);
                ps.setString(4, request.auditDate);
                ps.setString(5, request.auditStartDate);
                ps.setString(6, request.auditEndDate);
                ps.setString(7, request.reportSubmitDate);
                ps.setString(8, request.status);
                ps.setString(9, employeeName);
                ps.setString(10, request.auditDate);
                ps.executeUpdate();
            }

            // Insert auditors
            if (request.auditors != null && !request.auditors.isEmpty()) {
                // Get next ID for auditors
                int maxAuditorId = 0;
                String maxAuditorIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_plan_auditors";
                try (PreparedStatement ps = con.prepareStatement(maxAuditorIdQuery);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxAuditorId = rs.getInt("max_id");
                    }
                }
                maxAuditorId++;

                query = "INSERT INTO " + companyCode + "_external_audit_plan_auditors " +
                        "(id, audit_plan_id, auditor_name, auditor_desig) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (AuditorDetail auditor : request.auditors) {
                        ps.setInt(1, maxAuditorId++);
                        ps.setInt(2, maxId);
                        ps.setString(3, auditor.name);
                        ps.setString(4, auditor.designation);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert standards
            if (request.standards != null && !request.standards.isEmpty()) {
                // Get next ID for standards
                int maxStandardId = 0;
                String maxStandardIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_plan_standard";
                try (PreparedStatement ps = con.prepareStatement(maxStandardIdQuery);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxStandardId = rs.getInt("max_id");
                    }
                }
                maxStandardId++;

                query = "INSERT INTO " + companyCode + "_external_audit_plan_standard " +
                        "(id, audit_plan_id, standard_name, status) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (StandardDetail standard : request.standards) {
                        ps.setInt(1, maxStandardId++);
                        ps.setInt(2, maxId);
                        ps.setString(3, standard.standardName);
                        ps.setString(4, standard.status);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert details
            if (request.details != null && !request.details.isEmpty()) {
                // Get next ID for details
                int maxDetailId = 0;
                String maxDetailIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_plan_detail";
                try (PreparedStatement ps = con.prepareStatement(maxDetailIdQuery);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxDetailId = rs.getInt("max_id");
                    }
                }
                maxDetailId++;

                query = "INSERT INTO " + companyCode + "_external_audit_plan_detail " +
                        "(id, audit_plan_id, department, audit_date, audit_start_time, audit_end_time, auditor_name, audit_criteria) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (AuditDetail detail : request.details) {
                        ps.setInt(1, maxDetailId++);
                        ps.setInt(2, maxId);
                        ps.setString(3, detail.department);
                        ps.setString(4, detail.auditDate);
                        ps.setString(5, detail.startTime);
                        ps.setString(6, detail.endTime);
                        ps.setString(7, detail.auditorName);
                        ps.setString(8, detail.auditCriteria);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Insert audit types
            if (request.auditTypes != null && !request.auditTypes.isEmpty()) {
                // Get next ID for audit types
                int maxAuditTypeId = 0;
                String maxAuditTypeIdQuery = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_type_detail";
                try (PreparedStatement ps = con.prepareStatement(maxAuditTypeIdQuery);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxAuditTypeId = rs.getInt("max_id");
                    }
                }
                maxAuditTypeId++;

                query = "INSERT INTO " + companyCode + "_external_audit_type_detail " +
                        "(id, external_audit_plan_id, audit_type_id) VALUES (?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    for (String auditTypeId : request.auditTypes) {
                        ps.setInt(1, maxAuditTypeId++);
                        ps.setInt(2, maxId);
                        ps.setString(3, auditTypeId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            // Log the action
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxLogId = rs.getInt("max_id");
                }
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, maxLogId);
                ps.setString(2, todayDate);
                ps.setString(3, employeeName);
                ps.setString(4, "Add");
                ps.setString(5, "External Audit Plan");
                ps.setString(6, String.valueOf(maxId));
                ps.executeUpdate();
            }

            con.commit();

            ExternalAuditPlanResponse response = new ExternalAuditPlanResponse(true, "Audit plan created successfully");
            response.data.put("auditPlanId", maxId);
            return Response.status(Status.CREATED).entity(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    private boolean isValidAuth(String employeeId, String companyCode) {
        return employeeId != null && !employeeId.isEmpty() && companyCode != null && !companyCode.isEmpty();
    }
}