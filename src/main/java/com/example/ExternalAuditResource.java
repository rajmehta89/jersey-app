package com.example;

import com.example.util.DBConfig;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.sql.*;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("/external-audit")
public class ExternalAuditResource {

    private static final Logger logger = Logger.getLogger(ExternalAuditResource.class.getName());
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // DTO classes
    public static class ExternalAuditRequest {
        public String internalAuditNo;
        public String auditPlanId;
        public String standardName;
        public String auditDate;
        public String status;
        public List<AuditDetail> details;
    }

    public static class AuditDetail {
        public String clauseNo;
        public String ncNo;
        public String descType;
        public String evidence;
        public String comment;
        public String status;
    }

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

    // DTO class for create request
    public static class CreateExternalAuditRequest {
        public String auditPlanId;
        public String auditNo;
        public String standardName;
        public String auditDate;
        public String scope;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createExternalAudit(
            CreateExternalAuditRequest request,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            // Get standard ID
            String stdId = null;
            String query = "SELECT id FROM standard_master WHERE std_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, request.standardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdId = rs.getString("id");
                    }
                }
            }

            // Check for duplicate
            query = "SELECT id FROM " + companyCode +
                    "_external_audit_master WHERE audit_no = ? AND std_id = ? AND audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, request.auditNo);
                ps.setString(2, stdId);
                ps.setString(3, request.auditPlanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Response.status(Status.CONFLICT)
                                .entity(new ExternalAuditResponse(false, "Audit already exists"))
                                .build();
                    }
                }
            }

            // Get next ID for external audit
            int externalAuditId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_external_audit_master";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    externalAuditId = rs.getInt("max_id") + 1;
                }
            }

            // Get next ID for log
            int logId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    logId = rs.getInt("max_id") + 1;
                }
            }

            // Insert audit
            query = "INSERT INTO " + companyCode + "_external_audit_master " +
                    "(id, audit_plan_id, audit_no, std_id, std_name, audit_date, status, scope) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'In Process', ?)";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, externalAuditId);
                ps.setString(2, request.auditPlanId);
                ps.setString(3, request.auditNo);
                ps.setString(4, stdId);
                ps.setString(5, request.standardName);
                ps.setString(6, request.auditDate);
                ps.setString(7, request.scope);
                ps.executeUpdate();
            }

            // Update plan standard status
            query = "UPDATE " + companyCode + "_external_audit_plan_standard " +
                    "SET status = 'In Process' " +
                    "WHERE audit_plan_id = ? AND standard_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, request.auditPlanId);
                ps.setString(2, request.standardName);
                ps.executeUpdate();
            }

            // Check remaining standards
            int stdCount = 0;
            query = "SELECT COUNT(id) as count FROM " + companyCode +
                    "_external_audit_plan_standard WHERE status = '' AND audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, request.auditPlanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdCount = rs.getInt("count");
                    }
                }
            }

            // Update plan status
            String planStatus = stdCount == 0 ? "In Process" : "Partial Process";
            query = "UPDATE " + companyCode + "_external_audit_plan " +
                    "SET status = ? WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, planStatus);
                ps.setString(2, request.auditPlanId);
                ps.executeUpdate();
            }

            // Log the action
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            query = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, logId);
                ps.setString(2, todayDate);
                ps.setString(3, employeeName);
                ps.setString(4, "Create");
                ps.setString(5, "External Audit");
                ps.setString(6, String.valueOf(externalAuditId));
                ps.executeUpdate();
            }

            con.commit();
            ExternalAuditResponse response = new ExternalAuditResponse(true, "External audit created successfully");
            response.data.put("auditId", externalAuditId);
            response.data.put("standardName", request.standardName);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



// Add these new endpoints to your ExternalAuditResource class

    @GET
    @Path("/audit-plans")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlans(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            ExternalAuditResponse response = new ExternalAuditResponse(true, "Audit plans retrieved successfully");
            List<Map<String, String>> plans = new ArrayList<>();

            String query = "SELECT id, audit_no FROM " + companyCode +
                    "_external_audit_plan WHERE status in ('approve','Partial Process','Partial Complet')";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    Map<String, String> plan = new HashMap<>();
                    plan.put("id", rs.getString("id"));
                    plan.put("auditNo", rs.getString("audit_no"));
                    plans.add(plan);
                }
            }

            response.data.put("plans", plans);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/audit-plans/{planId}/standards")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPlanStandards(
            @PathParam("planId") String planId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            ExternalAuditResponse response = new ExternalAuditResponse(true, "Standards retrieved successfully");
            List<String> standards = new ArrayList<>();

            String query = "SELECT standard_name FROM " + companyCode +
                    "_external_audit_plan_standard WHERE audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        standards.add(rs.getString("standard_name"));
                    }
                }
            }

            response.data.put("standards", standards);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAudits(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            ExternalAuditResponse response = new ExternalAuditResponse(true, "Audits retrieved successfully");
            List<Map<String, Object>> audits = new ArrayList<>();

            String query = "SELECT * FROM " + companyCode + "_external_audit_master ORDER BY id DESC";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> audit = new HashMap<>();
                    audit.put("id", rs.getString("id"));
                    audit.put("auditPlanId", rs.getString("audit_plan_id"));
                    audit.put("auditNo", rs.getString("audit_no"));
                    audit.put("standardId", rs.getString("std_id"));
                    audit.put("standardName", rs.getString("std_name"));
                    audit.put("audit_date", rs.getString("audit_date"));
                    audit.put("status", rs.getString("status"));
                    audit.put("scope", rs.getString("scope"));

                    audits.add(audit);
                }
            }

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
    public Response getAuditDetails(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            ExternalAuditResponse response = new ExternalAuditResponse(true, "Audit details retrieved successfully");
            List<Map<String, String>> details = new ArrayList<>();

            String query = "SELECT * FROM " + companyCode + "_external_audit_detail WHERE external_audit_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> detail = new HashMap<>();
                        detail.put("clauseNo", rs.getString("clause_no"));
                        detail.put("ncNo", rs.getString("nc_no"));
                        detail.put("type", rs.getString("desc_type"));
                        detail.put("evidence", rs.getString("evidence"));
                        detail.put("comment", rs.getString("comment"));
                        detail.put("status", rs.getString("status"));
                        details.add(detail);
                    }
                }
            }

            response.data.put("details", details);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAudit(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            // Delete details first
            String query = "DELETE FROM " + companyCode + "_external_audit_detail WHERE external_audit_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                ps.executeUpdate();
            }

            // Delete main record
            query = "DELETE FROM " + companyCode + "_external_audit_master WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                int rowsDeleted = ps.executeUpdate();

                if (rowsDeleted == 0) {
                    con.rollback();
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ExternalAuditResponse(false, "Audit not found"))
                            .build();
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
                ps.setString(4, "Delete");
                ps.setString(5, "External Audit");
                ps.setString(6, auditId);
                ps.executeUpdate();
            }

            con.commit();
            return Response.ok(new ExternalAuditResponse(true, "Audit deleted successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveAudit(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            String query = "UPDATE " + companyCode + "_external_audit_master SET status = 'Complet' WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                int rowsUpdated = ps.executeUpdate();

                if (rowsUpdated == 0) {
                    con.rollback();
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ExternalAuditResponse(false, "Audit not found"))
                            .build();
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
                ps.setString(4, "Approve");
                ps.setString(5, "External Audit");
                ps.setString(6, auditId);
                ps.executeUpdate();
            }

            con.commit();
            return Response.ok(new ExternalAuditResponse(true, "Audit approved successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }



    @PUT
    @Path("/{id}/back-status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response revertStatus(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            String query = "UPDATE " + companyCode + "_external_audit_master SET status = 'In Process' WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                int rowsUpdated = ps.executeUpdate();

                if (rowsUpdated == 0) {
                    con.rollback();
                    return Response.status(Status.NOT_FOUND)
                            .entity(new ExternalAuditResponse(false, "Audit not found"))
                            .build();
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
                ps.setString(4, "Back Status");
                ps.setString(5, "External Audit");
                ps.setString(6, auditId);
                ps.executeUpdate();
            }

            con.commit();
            return Response.ok(new ExternalAuditResponse(true, "Status reverted successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{id}/export")
    @Produces("application/msword")
    public Response exportAudit(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            // Get company details
            String companyName = null;
            String street1 = null;
            String street2 = null;
            String city = null;
            String pincode = null;
            String country = null;
            String personName = null;

            String query = "SELECT * FROM company_registration WHERE company_code = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, companyCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        companyName = rs.getString("company_name");
                        street1 = rs.getString("street1");
                        street2 = rs.getString("street2");
                        city = rs.getString("city");
                        pincode = rs.getString("pincode");
                        country = rs.getString("country");
                        personName = rs.getString("person_name");
                    }
                }
            }

            // Get audit details
            String auditNo = null;
            String auditDate = null;
            String scope = null;
            int auditPlanId = 0;
            String standardName = null;
            String stdId = null;

            query = "SELECT * FROM " + companyCode + "_external_audit_master WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        auditPlanId = rs.getInt("audit_plan_id");
                        auditNo = rs.getString("audit_no");
                        auditDate = rs.getString("audit_date");
                        scope = rs.getString("scope");
                        standardName = rs.getString("std_name");
                        stdId = rs.getString("std_id");
                    }
                }
            }

            // Generate Word document content
            StringBuilder content = new StringBuilder();
            content.append("<!DOCTYPE html><html><head>");
            content.append("<meta charset='UTF-8'>");
            content.append("<style>");
            content.append("table { width: 100%; border-collapse: collapse; }");
            content.append("th, td { border: 1px solid black; padding: 8px; }");
            content.append("th { background-color: #f2f2f2; }");
            content.append("</style></head><body>");

            // Organization details table
            content.append("<table>");
            content.append("<tr><th align='left' width='20%'>ORGANIZATION NAME</th>");
            content.append("<td colspan='3'>").append(companyName).append("</td></tr>");

            content.append("<tr><th align='left'>ADDRESS</th><td colspan='3'>");
            if (street1 != null && !street1.equals("-")) content.append(street1).append(", ");
            if (street2 != null && !street2.equals("-")) content.append(street2).append(", ");
            if (city != null && !city.equals("-")) content.append(city).append(", ");
            if (pincode != null && !pincode.equals("-")) content.append(pincode).append(" ");
            if (country != null && !country.equals("-")) content.append("(").append(country).append(")");
            content.append("</td></tr>");

            content.append("<tr><th align='left'>SCOPE</th>");
            content.append("<td colspan='3'>").append(scope != null ? scope : "").append("</td></tr>");

            content.append("<tr><th align='left'>AUDIT DATE</th>");
            content.append("<td>").append(auditDate != null ? auditDate : "").append("</td>");
            content.append("<th align='left' width='30%'>AUDIT MAN-DAYS</th>");
            content.append("<td>&nbsp;</td></tr>");

            // Get audit type details
            content.append("<tr><th align='left'>TYPE OF AUDIT</th><td colspan='3'>");
            query = "SELECT at.audit_type FROM " + companyCode + "_external_audit_type_detail eatd " +
                    "JOIN audit_type at ON eatd.audit_type_id = at.id " +
                    "WHERE eatd.external_audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, auditPlanId);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) content.append(", ");
                        content.append(rs.getString("audit_type"));
                        first = false;
                    }
                }
            }
            content.append("</td></tr>");

            // Audit details table
            content.append("<table><tr>");
            content.append("<th width='10%'>A</th>");
            content.append("<th width='35%'>Control Objectives & Controls</th>");
            content.append("<th width='45%'>Evidence</th>");
            content.append("<th width='10%'>Remarks</th></tr>");

            // Get clause details
            query = "SELECT cm.number, cm.name, ead.evidence, ead.comment " +
                    "FROM " + companyCode + "_external_audit_detail ead " +
                    "JOIN clause_master cm ON ead.clause_no = cm.number AND ead.std_id = cm.std_id " +
                    "WHERE ead.external_audit_id = ? ORDER BY cm.number + 0 ASC";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        content.append("<tr>");
                        content.append("<th>").append(rs.getString("number")).append("</th>");
                        content.append("<td>").append(rs.getString("name")).append("</td>");
                        content.append("<td>").append(rs.getString("evidence") != null ? rs.getString("evidence") : "&nbsp;").append("</td>");
                        content.append("<td>").append(rs.getString("comment") != null ? rs.getString("comment") : "&nbsp;").append("</td>");
                        content.append("</tr>");
                    }
                }
            }

            content.append("</table></body></html>");

            // Return the Word document
            return Response.ok(content.toString())
                    .header("Content-Type", "application/msword")
                    .header("Content-Disposition", "attachment; filename=External_Audit_" + auditNo + ".doc")
                    .build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new ExternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{id}/export-nc")
    @Produces("application/msword")
    public Response exportNCDoc(
            @PathParam("id") String auditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new ExternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            // Get audit details
            String auditNo = null;
            String auditPlanId = null;
            String stdName = null;
            String stdId = null;
            String auditStartDate = null;
            String auditEndDate = null;
            String scope = null;

            String query = "SELECT * FROM " + companyCode + "_external_audit_master WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        auditPlanId = rs.getString("audit_plan_id");
                        auditNo = rs.getString("audit_no");
                        stdName = rs.getString("std_name");
                        stdId = rs.getString("std_id");
                        scope = rs.getString("scope");
                    } else {
                        return Response.status(Status.NOT_FOUND)
                                .type(MediaType.TEXT_PLAIN)
                                .entity("Audit not found with ID: " + auditId)
                                .build();
                    }
                }
            }

            // Get audit plan dates
            if (auditPlanId != null) {
                query = "SELECT * FROM " + companyCode + "_external_audit_plan WHERE id = ?";
                try (PreparedStatement ps = con.prepareStatement(query)) {
                    ps.setString(1, auditPlanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            auditStartDate = rs.getString("audit_start_date");
                            auditEndDate = rs.getString("audit_end_date");
                        }
                    }
                }
            }

            // Get company details
            String companyName = null;
            String street1 = null;
            String street2 = null;
            String city = null;
            String pincode = null;
            String personName = null;
            String country = null;

            query = "SELECT * FROM company_registration WHERE company_code = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, companyCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        companyName = rs.getString("company_name");
                        street1 = rs.getString("street1");
                        street2 = rs.getString("street2");
                        city = rs.getString("city");
                        pincode = rs.getString("pincode");
                        personName = rs.getString("person_name");
                        country = rs.getString("country");
                    }
                }
            }

            // Calculate audit duration
            long days = 1;
            if (auditStartDate != null && auditEndDate != null) {
                try {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    java.util.Date start = format.parse(auditStartDate);
                    java.util.Date end = format.parse(auditEndDate);
                    long diff = end.getTime() - start.getTime();
                    days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) + 1;
                } catch (ParseException e) {
                    logger.warning("Error parsing dates: " + e.getMessage());
                }
            }

            // Generate Word document content
            StringBuilder content = new StringBuilder();
            content.append("<!DOCTYPE html>");
            content.append("<html>");
            content.append("<head>");
            content.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
            content.append("<style>");
            content.append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }");
            content.append("th, td { border: 1px solid black; padding: 8px; }");
            content.append("th { background-color: #D3D3D3; }");
            content.append(".header { background-color: gray; color: white; }");
            content.append("</style>");
            content.append("</head>");
            content.append("<body>");

            // Audit Content
            content.append("<h3 style=\"color: #DC143C;\">Audit Content : </h3>");
            content.append("<span style=\"color: #DC143C;\">(1)</span> A SMETA audit was conducted which included some or all of Labour Standards, Health & Safety, Environment and Business Ethics.");

            // Audit Details Table
            content.append("<table>");
            content.append("<tr><th colspan='5' class='header'>Audit Detail</th></tr>");

            // Company Details
            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Business name:</td>");
            content.append("<td colspan='4'>").append(companyName != null ? companyName : "").append("</td>");
            content.append("</tr>");

            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Site address:</td>");
            content.append("<td colspan='4'>").append(formatAddress(street1, street2, city, pincode)).append("</td>");
            content.append("</tr>");

            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Country:</td>");
            content.append("<td colspan='4'>").append(country != null ? country : "").append("</td>");
            content.append("</tr>");

            // Audit Dates
            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Date of Audit:</td>");
            content.append("<td colspan='4'>").append(formatDateRange(auditStartDate, auditEndDate)).append("</td>");
            content.append("</tr>");

            content.append("</table>");

            // Audit Company Name
            content.append("<table>");
            content.append("<tr><th class='header'>Audit Company Name</th></tr>");
            content.append("<tr><td>Niall Services Pvt Ltd</td></tr>");
            content.append("</table>");

            // Audit Parameters
            content.append("<table>");
            content.append("<tr><th colspan='2' class='header'>Audit Parameters</th></tr>");

            // Time in and out table
            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Time in and time out</td>");
            content.append("<td>");
            content.append("<table>");

            // Days headers
            content.append("<tr>");
            for (int i = 1; i <= days; i++) {
                content.append("<th colspan='2' class='header'>Day ").append(i).append("</th>");
            }
            content.append("</tr>");

            // Time in
            content.append("<tr>");
            for (int i = 1; i <= days; i++) {
                content.append("<td style=\"background:#D3D3D3;\">In</td>");
                content.append("<td>&nbsp;</td>");
            }
            content.append("</tr>");

            // Time out
            content.append("<tr>");
            for (int i = 1; i <= days; i++) {
                content.append("<td style=\"background:#D3D3D3;\">Out</td>");
                content.append("<td>&nbsp;</td>");
            }
            content.append("</tr>");
            content.append("</table>");
            content.append("</td></tr>");

            // Additional parameters
            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Audit type:</td>");
            content.append("<td>FULL INITIAL</td>");
            content.append("</tr>");

            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Was the audit announced?</td>");
            content.append("<td>ANNOUNCED</td>");
            content.append("</tr>");

            content.append("<tr>");
            content.append("<td style=\"background:#D3D3D3;\">Who signed and agreed CAPR</td>");
            content.append("<td>").append(personName != null ? personName : "").append("</td>");
            content.append("</tr>");

            content.append("</table>");

            // Generate NC details
            content.append("<table>");
            content.append("<tr><th colspan='6' class='header'>Non-Compliance Details</th></tr>");
            content.append("<tr style=\"background:#D3D3D3;\">");
            content.append("<th>Clause No</th>");
            content.append("<th>NC No</th>");
            content.append("<th>Type</th>");
            content.append("<th>Evidence</th>");
            content.append("<th>Comment</th>");
            content.append("<th>Status</th>");
            content.append("</tr>");

            // Get NC details
            query = "SELECT d.*, n.type FROM " + companyCode + "_external_audit_detail d " +
                    "LEFT JOIN " + companyCode + "_external_nonconformities n " +
                    "ON d.external_audit_id = n.external_audit_id AND d.nc_no = n.nc_no " +
                    "WHERE d.external_audit_id = ? AND d.desc_type = 'NC' " +
                    "ORDER BY d.clause_no, d.nc_no";

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        content.append("<tr>");
                        content.append("<td>").append(rs.getString("clause_no")).append("</td>");
                        content.append("<td>").append(rs.getString("nc_no")).append("</td>");
                        content.append("<td>").append(rs.getString("type")).append("</td>");
                        content.append("<td>").append(rs.getString("evidence")).append("</td>");
                        content.append("<td>").append(rs.getString("comment")).append("</td>");
                        content.append("<td>").append(rs.getString("status")).append("</td>");
                        content.append("</tr>");
                    }
                }
            }

            content.append("</table>");
            content.append("</body>");
            content.append("</html>");

            // Return the Word document with proper headers
            return Response.ok(content.toString())
                    .header("Content-Type", "application/msword")
                    .header("Content-Disposition", "attachment; filename=Audit_Document_" + (auditNo != null ? auditNo : "Unknown") + ".doc")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Database error occurred: " + e.getMessage())
                    .build();
        }
    }

    private String formatAddress(String street1, String street2, String city, String pincode) {
        StringBuilder address = new StringBuilder();
        if (street1 != null && !street1.isEmpty()) address.append(street1).append("<br>");
        if (street2 != null && !street2.isEmpty()) address.append(street2).append("<br>");
        if (city != null && !city.isEmpty()) address.append(city);
        if (pincode != null && !pincode.isEmpty()) address.append(" - ").append(pincode);
        return address.toString();
    }

    private String formatDateRange(String startDate, String endDate) {
        if (startDate == null && endDate == null) return "";
        if (startDate == null) return "To " + endDate;
        if (endDate == null) return "From " + startDate;
        return "From " + startDate + " To " + endDate;
    }

    private void generateNCDetails(StringBuilder content, Connection con, String companyCode, String auditId) throws SQLException {
        String query = "SELECT DISTINCT clause_no FROM " + companyCode +
                "_external_nonconformities WHERE external_audit_id = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, auditId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String clauseNo = rs.getString("clause_no");
                    generateClauseDetails(content, con, companyCode, auditId, clauseNo);
                }
            }
        }
    }

    private void generateClauseDetails(StringBuilder content, Connection con, String companyCode,
                                       String auditId, String clauseNo) throws SQLException {

        String query = "SELECT * FROM " + companyCode + "_external_nonconformities " +
                "WHERE external_audit_id = ? AND clause_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, auditId);
            ps.setString(2, clauseNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    generateNCDetail(content, con, companyCode, auditId, clauseNo, rs.getString("nc_no"));
                }
            }
        }
    }

    private void generateNCDetail(StringBuilder content, Connection con, String companyCode,
                                  String auditId, String clauseNo, String ncNo) throws SQLException {

        String query = "SELECT * FROM " + companyCode + "_external_audit_detail " +
                "WHERE external_audit_id = ? AND nc_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, auditId);
            ps.setString(2, ncNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    content.append("<table style=\"width:100%;border: 1px solid black;border-collapse: collapse;\" border=\"1\">");
                    content.append("<tr><th colspan='2'>Non-Compliance Details</th></tr>");

                    content.append("<tr>");
                    content.append("<td>Clause:</td>");
                    content.append("<td>").append(clauseNo).append("</td>");
                    content.append("</tr>");

                    content.append("<tr>");
                    content.append("<td>NC No:</td>");
                    content.append("<td>").append(ncNo).append("</td>");
                    content.append("</tr>");

                    content.append("<tr>");
                    content.append("<td>Evidence:</td>");
                    content.append("<td>").append(rs.getString("evidence")).append("</td>");
                    content.append("</tr>");

                    content.append("<tr>");
                    content.append("<td>Comment:</td>");
                    content.append("<td>").append(rs.getString("comment")).append("</td>");
                    content.append("</tr>");

                    content.append("</table>");
                }
            }
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

    private int getMaxId(Connection con, String table) throws SQLException {
        String query = "SELECT MAX(id) as max_id FROM " + table;
        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("max_id") : 0;
        }
    }


    private boolean isValidAuth(String employeeId, String companyCode) {
        return employeeId != null && !employeeId.isEmpty() && companyCode != null && !companyCode.isEmpty();
    }

    private boolean isValidCompanyCode(String companyCode) {
        return companyCode != null && COMPANY_CODE_PATTERN.matcher(companyCode).matches();
    }
}