package com.example;

import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Base64;

@Path("/audit-plan")
public class AuditPlanEdit {

    private static final Logger logger = Logger.getLogger(AuditPlanResource.class.getName());
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // DTO classes
    public static class AuditPlanRequest {
        public String auditNo;
        public String auditDate;
        public String auditStartDate;
        public String auditEndDate;
        public String reportSubmitDate;
        public List<Auditor> auditors;
        public List<Standard> standards;
        public List<AuditDetail> details;
    }

    public static class Auditor {
        public String name;
        public String designation;
    }

    public static class Standard {
        public String name;
    }

    public static class AuditDetail {
        public String department;
        public String auditDate;
        public String startTime;
        public String endTime;
        public String auditorName;
        public String auditCriteria;
    }

    public static class AuditPlanResponse {
        public boolean success;
        public String message;
        public Map<String, Object> data;

        public AuditPlanResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.data = new HashMap<>();
        }
    }

    public static class FileUploadRequest {
        public String intrAuditId;
        public String clauseNo;
        public String stdName;
        public List<byte[]> files;
        public List<String> fileNames;
    }

    // Request DTO for file upload
    public static class AuditDocumentRequest {
        public String intrAuditId;
        public String clauseNo;
        public String stdName;
        public List<FileData> files;
    }

    public static class FileData {
        public String fileName;
        public String fileContent;  // Base64 encoded file content
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlan(@PathParam("id") String auditPlanId,
                                 @HeaderParam("company-code") String companyCode,
                                 @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            AuditPlanResponse response = new AuditPlanResponse(true, "Data retrieved successfully");
            Map<String, Object> auditPlanData = new HashMap<>();

            // Fetch employees and standards for dropdowns
            List<String> employees = new ArrayList<>();
            String query = "SELECT name FROM " + companyCode + "_employee_detail WHERE id > 0";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            while (rs.next()) {
                employees.add(rs.getString("name"));
            }
            auditPlanData.put("employees", employees);

            List<String> allStandards = new ArrayList<>();
            query = "SELECT std_name FROM company_std_detail WHERE company_id = (SELECT company_id FROM " +
                    companyCode + "_user_master WHERE emp_id = ?) AND status = 'Active'";
            ps = con.prepareStatement(query);
            ps.setString(1, employeeId);
            rs = ps.executeQuery();
            while (rs.next()) {
                allStandards.add(rs.getString("std_name"));
            }
            auditPlanData.put("allStandards", allStandards);

            if (!"0".equals(auditPlanId)) {
                // Fetch specific audit plan
                query = "SELECT * FROM " + companyCode + "_audit_plan WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, auditPlanId);
                rs = ps.executeQuery();

                if (rs.next()) {
                    auditPlanData.put("id", rs.getString("id"));
                    auditPlanData.put("auditNo", rs.getString("audit_no"));
                    auditPlanData.put("auditDate", rs.getString("audit_date"));
                    auditPlanData.put("auditStartDate", rs.getString("audit_start_date"));
                    auditPlanData.put("auditEndDate", rs.getString("audit_end_date"));
                    auditPlanData.put("reportSubmitDate", rs.getString("report_submit_date"));
                } else {
                    return Response.status(Status.NOT_FOUND)
                            .entity(new AuditPlanResponse(false, "Audit plan not found"))
                            .build();
                }

                // Fetch auditors
                List<Map<String, String>> auditors = new ArrayList<>();
                query = "SELECT * FROM " + companyCode + "_audit_plan_auditors WHERE audit_plan_id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, auditPlanId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> auditor = new HashMap<>();
                    auditor.put("id", rs.getString("id"));
                    auditor.put("name", rs.getString("auditor_name"));
                    auditor.put("designation", rs.getString("auditor_desig"));
                    auditors.add(auditor);
                }
                auditPlanData.put("auditors", auditors);

                // Fetch standards
                List<Map<String, String>> standards = new ArrayList<>();
                query = "SELECT * FROM " + companyCode + "_audit_plan_standard WHERE audit_plan_id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, auditPlanId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> standard = new HashMap<>();
                    standard.put("id", rs.getString("id"));
                    standard.put("name", rs.getString("standard_name"));
                    standards.add(standard);
                }
                auditPlanData.put("standards", standards);

                // Fetch details
                List<Map<String, String>> details = new ArrayList<>();
                query = "SELECT * FROM " + companyCode + "_audit_plan_detail WHERE audit_plan_id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, auditPlanId);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, String> detail = new HashMap<>();
                    detail.put("id", rs.getString("id"));
                    detail.put("department", rs.getString("department"));
                    detail.put("auditDate", rs.getString("audit_date"));
                    detail.put("startTime", rs.getString("audit_start_time"));
                    detail.put("endTime", rs.getString("audit_end_time"));
                    detail.put("auditorName", rs.getString("auditor_name"));
                    detail.put("auditCriteria", rs.getString("audit_criteria"));
                    details.add(detail);
                }
                auditPlanData.put("details", details);
            }

            response.data = auditPlanData;
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    private boolean isDuplicateAudit(Connection con, String companyCode, AuditPlanRequest request) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM " + companyCode + "_audit_plan_detail d " +
                      "WHERE d.audit_date = ? AND d.auditor_name = ?";
        
        try (PreparedStatement ps = con.prepareStatement(query)) {
            // Check for each detail if there's already an audit on that day with same auditor
            for (AuditDetail detail : request.details) {
                ps.setString(1, detail.auditDate);
                ps.setString(2, detail.auditorName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("count") > 0) {
                        return true; // Found a duplicate
                    }
                }
            }
        }
        return false;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAuditPlan(AuditPlanRequest request,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        if (!validateAuditPlanRequest(request)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid audit plan data"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Check for duplicate audits
            if (isDuplicateAudit(con, companyCode, request)) {
                return Response.status(Status.CONFLICT)
                        .entity(new AuditPlanResponse(false, "An audit already exists for the given date with the same auditor"))
                        .build();
            }

            int maxAuditPlanId = 0;
            String query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxAuditPlanId = rs.getInt("max_id");
            }
            maxAuditPlanId++;

            query = "INSERT INTO " + companyCode + "_audit_plan " +
                    "(id, audit_no, audit_date, audit_start_date, audit_end_date, report_submit_date,status) " +
                    "VALUES (?, ?, ?, ?, ?, ?,?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxAuditPlanId);
            ps.setString(2, request.auditNo);
            ps.setString(3, request.auditDate);
            ps.setString(4, request.auditStartDate);
            ps.setString(5, request.auditEndDate);
            ps.setString(6, request.reportSubmitDate);
            ps.setString(7, "Draft");
            ps.executeUpdate();

            int maxAuditorId = 0;
            int maxStandardId = 0;
            int maxDetailId = 0;

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_auditors";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxAuditorId = rs.getInt("max_id");
            }

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_standard";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxStandardId = rs.getInt("max_id");
            }

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_detail";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxDetailId = rs.getInt("max_id");
            }

            if (request.auditors != null && !request.auditors.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_auditors (id, audit_plan_id, auditor_name, auditor_desig) VALUES (?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                for (Auditor auditor : request.auditors) {
                    maxAuditorId++;
                    ps.setInt(1, maxAuditorId);
                    ps.setInt(2, maxAuditPlanId);
                    ps.setString(3, auditor.name);
                    ps.setString(4, auditor.designation);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            if (request.standards != null && !request.standards.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_standard (id, audit_plan_id, standard_name) VALUES (?, ?, ?)";
                ps = con.prepareStatement(query);
                for (Standard standard : request.standards) {
                    maxStandardId++;
                    ps.setInt(1, maxStandardId);
                    ps.setInt(2, maxAuditPlanId);
                    ps.setString(3, standard.name);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            if (request.details != null && !request.details.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_detail " +
                        "(id, audit_plan_id, department, audit_date, audit_start_time, audit_end_time, auditor_name, audit_criteria) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                for (AuditDetail detail : request.details) {
                    maxDetailId++;
                    ps.setInt(1, maxDetailId);
                    ps.setInt(2, maxAuditPlanId);
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

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxLogId = rs.getInt("max_id");
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId);
            ps.setString(2, todayDate);
            ps.setString(3, employeeName);
            ps.setString(4, "Add");
            ps.setString(5, "Audit Plan");
            ps.setString(6, String.valueOf(maxAuditPlanId));
            ps.executeUpdate();

            con.commit();

            AuditPlanResponse response = new AuditPlanResponse(true, "Audit plan created successfully");
            response.data.put("auditPlanId", maxAuditPlanId);
            return Response.status(Status.CREATED).entity(response).build();

        } catch (SQLException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAuditPlan(@PathParam("id") String auditPlanId,
                                    AuditPlanRequest request,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        if (!validateAuditPlanRequest(request)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid audit plan data"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            String query = "UPDATE " + companyCode + "_audit_plan SET " +
                    "audit_no = ?, audit_date = ?, audit_start_date = ?, " +
                    "audit_end_date = ?, report_submit_date = ? " +
                    "WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.auditNo);
            ps.setString(2, request.auditDate);
            ps.setString(3, request.auditStartDate);
            ps.setString(4, request.auditEndDate);
            ps.setString(5, request.reportSubmitDate);
            ps.setString(6, auditPlanId);
            int rowsUpdated = ps.executeUpdate();

            if (rowsUpdated == 0) {
                con.rollback();
                return Response.status(Status.NOT_FOUND)
                        .entity(new AuditPlanResponse(false, "Audit plan not found"))
                        .build();
            }

            query = "DELETE FROM " + companyCode + "_audit_plan_auditors WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            query = "DELETE FROM " + companyCode + "_audit_plan_standard WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            query = "DELETE FROM " + companyCode + "_audit_plan_detail WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            int maxAuditorId = 0;
            int maxStandardId = 0;
            int maxDetailId = 0;

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_auditors";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxAuditorId = rs.getInt("max_id");
            }

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_standard";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxStandardId = rs.getInt("max_id");
            }

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_audit_plan_detail";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxDetailId = rs.getInt("max_id");
            }

            if (request.auditors != null && !request.auditors.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_auditors (id, audit_plan_id, auditor_name, auditor_desig) VALUES (?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                for (Auditor auditor : request.auditors) {
                    maxAuditorId++;
                    ps.setInt(1, maxAuditorId);
                    ps.setInt(2, auditPlanId);
                    ps.setString(3, auditor.name);
                    ps.setString(4, auditor.designation);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            if (request.standards != null && !request.standards.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_standard (id, audit_plan_id, standard_name) VALUES (?, ?, ?)";
                ps = con.prepareStatement(query);
                for (Standard standard : request.standards) {
                    maxStandardId++;
                    ps.setInt(1, maxStandardId);
                    ps.setString(2, auditPlanId);
                    ps.setString(3, standard.name);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            if (request.details != null && !request.details.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_audit_plan_detail " +
                        "(id, audit_plan_id, department, audit_date, audit_start_time, audit_end_time, auditor_name, audit_criteria) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                for (AuditDetail detail : request.details) {
                    maxDetailId++;
                    ps.setInt(1, maxDetailId);
                    ps.setInt(2, auditPlanId);
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

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxLogId = rs.getInt("max_id");
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId);
            ps.setString(2, todayDate);
            ps.setString(3, employeeName);
            ps.setString(4, "Edit");
            ps.setString(5, "Audit Plan");
            ps.setString(6, auditPlanId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new AuditPlanResponse(true, "Audit plan updated successfully")).build();

        } catch (SQLException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAuditPlans(@HeaderParam("company-code") String companyCode,
                                     @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            AuditPlanResponse response = new AuditPlanResponse(true, "Audit plans retrieved successfully");
            List<Map<String, Object>> auditPlans = new ArrayList<>();

            String query = "SELECT * FROM " + companyCode + "_audit_plan ORDER BY id DESC";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> plan = new HashMap<>();
                plan.put("id", rs.getString("id"));
                plan.put("reportSubmitDate",rs.getString("report_submit_date"));
                plan.put("status", rs.getString("status"));
                plan.put("approvedDate",rs.getString("approved_date"));
                plan.put("auditNo", rs.getString("audit_no"));
                plan.put("auditDate", rs.getString("audit_date"));
                plan.put("auditStartDate", rs.getString("audit_start_date"));
                plan.put("auditEndDate", rs.getString("audit_end_date"));
                plan.put("reportSubmitDate", rs.getString("report_submit_date"));
                auditPlans.add(plan);
            }

            response.data.put("auditPlans", auditPlans);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAuditPlan(@PathParam("id") String auditPlanId,
                                    @HeaderParam("company-code") String companyCode,
                                    @HeaderParam("employee-id") String employeeId,
                                    @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            String query = "DELETE FROM " + companyCode + "_audit_plan_detail WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            query = "DELETE FROM " + companyCode + "_audit_plan_standard WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            query = "DELETE FROM " + companyCode + "_audit_plan_auditors WHERE audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();

            query = "DELETE FROM " + companyCode + "_audit_plan WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            int rowsDeleted = ps.executeUpdate();

            if (rowsDeleted == 0) {
                con.rollback();
                return Response.status(Status.NOT_FOUND)
                        .entity(new AuditPlanResponse(false, "Audit plan not found"))
                        .build();
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            int maxLogId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxLogId = rs.getInt("max_id");
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId);
            ps.setString(2, todayDate);
            ps.setString(3, employeeName);
            ps.setString(4, "Delete");
            ps.setString(5, "Audit Plan");
            ps.setString(6, auditPlanId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new AuditPlanResponse(true, "Audit plan deleted successfully")).build();

        } catch (SQLException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @GET
    @Path("/standards/by-name")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStandardNoByName(@HeaderParam("company-code") String companyCode, @HeaderParam("employee-id") String userId) {
        Logger logger = Logger.getLogger("StandardMasterHandler");

        if (companyCode == null || companyCode.isEmpty() || userId == null || userId.isEmpty()) {
            return createErrorResponse("Company code and user ID are required", "Missing required headers", Response.Status.BAD_REQUEST);
        }

        try (Connection con = DBConfig.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT std_name FROM standard_master WHERE std_no = ?")) {
            logger.info("Fetching std_name for std_id 'SEDEX' for company_code: " + companyCode);

            ps.setString(1, "SEDEX");
            try (ResultSet res = ps.executeQuery()) {
                if (res.next()) {
                    Map<String, Object> response = new HashMap<>();
                    Map<String, String> data = new HashMap<>();
                    data.put("stdName", res.getString("std_name"));
                    response.put("success", true);
                    response.put("error", null);
                    response.put("data", data);
                    return Response.ok(response).build();
                } else {
                    return createErrorResponse("Standard not found", "No standard with name 'SEDEX 4 Pillar'", Response.Status.NOT_FOUND);
                }
            }

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return createErrorResponse("Database error occurred", e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.severe("Unexpected error: " + e.getMessage());
            return createErrorResponse("Unexpected error occurred", e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response createErrorResponse(String errorMessage, String details, Response.Status status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", errorMessage);
        errorResponse.put("details", details);
        return Response.status(status).entity(errorResponse).build();
    }

    @GET
    @Path("/audit-plan-auditors")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlanAuditors(@HeaderParam("company-code") String companyCode, @HeaderParam("employee-id") String userId) {
        Logger logger = Logger.getLogger("AuditPlanAuditorsHandler");
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;
        List<Map<String, Object>> auditors = new ArrayList<>();

        try {
            logger.info("Fetching audit plan auditors for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return ErrorHandler.badRequest("Company code and user ID are required", "Missing required headers");
            }

            // Establish connection to the database
            con = DBConfig.getConnection();

            // Query to fetch auditors' details
            String query = "SELECT id,designation,description FROM niall_designation_master";
            ps = con.prepareStatement(query);
            res = ps.executeQuery();

            // Processing the result set
            while (res.next()) {
                Map<String, Object> auditor = new HashMap<>();
                auditor.put("id", res.getInt("id"));
                auditor.put("designation", res.getString("designation"));
                auditor.put("description", res.getString("description"));
                auditors.add(auditor);
            }

            // Preparing the response data
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("error", null);
            response.put("data", auditors);

            return Response.ok(response).build();

        } catch (SQLException e) {
            // Handle SQL exceptions and log error
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            // Handle other unexpected exceptions
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            // Close database resources
            try {
                if (res != null) res.close();
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                logger.warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAuditPlanStatus(@PathParam("id") String auditPlanId,
                                          StatusUpdateRequest request,
                                          @HeaderParam("company-code") String companyCode,
                                          @HeaderParam("employee-id") String employeeId,
                                          @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        if (request == null || request.status == null ||
                (!request.status.equals("Approve") && !request.status.equals("Draft"))) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid status value. Must be 'Approve' or 'Draft'"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Get current status
            String query = "SELECT status FROM " + companyCode + "_audit_plan WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return Response.status(Status.NOT_FOUND)
                        .entity(new AuditPlanResponse(false, "Audit plan not found"))
                        .build();
            }

            String currentStatus = rs.getString("status");
            logger.info("Current status: " + currentStatus + ", Requested status: " + request.status);

            // Update status based on current status
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String currentDate = LocalDateTime.now().format(dtf);

            if (request.status.equals("Approve")) {
                if (!currentStatus.equals("Draft")) {
                    return Response.status(Status.BAD_REQUEST)
                            .entity(new AuditPlanResponse(false, "Can only approve draft audit plans"))
                            .build();
                }

                query = "UPDATE " + companyCode + "_audit_plan SET status = ?, approved_by = ?, approved_date = ? WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, "Approve");
                ps.setString(2, employeeName);
                ps.setString(3, currentDate);
                ps.setString(4, auditPlanId);
            } else if (request.status.equals("Draft")) {
                if (!currentStatus.equals("Approve")) {
                    return Response.status(Status.BAD_REQUEST)
                            .entity(new AuditPlanResponse(false, "Can only revert approved audit plans to draft"))
                            .build();
                }

                query = "UPDATE " + companyCode + "_audit_plan SET status = ?, approved_by = NULL, approved_date = NULL WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, "Draft");
                ps.setString(2, auditPlanId);
            }

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated == 0) {
                con.rollback();
                return Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity(new AuditPlanResponse(false, "Failed to update audit plan status"))
                        .build();
            }

            // Log the action
            int maxLogId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxLogId = rs.getInt("max_id");
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId);
            ps.setString(2, currentDate);
            ps.setString(3, employeeName);
            ps.setString(4, request.status);
            ps.setString(5, "Audit Plan");
            ps.setString(6, auditPlanId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new AuditPlanResponse(true, "Audit plan status updated successfully")).build();

        } catch (SQLException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    // Add this class inside your AuditPlanEdit class
    public static class StatusUpdateRequest {
        public String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @GET
    @Path("/departments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDepartments(@HeaderParam("company-code") String companyCode,
                                   @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            AuditPlanResponse response = new AuditPlanResponse(true, "Departments retrieved successfully");
            List<Map<String, String>> departments = new ArrayList<>();

            String query = "SELECT department FROM " + companyCode + "_department_master WHERE id > 0";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, String> dept = new HashMap<>();
                dept.put("name", rs.getString("department"));
                departments.add(dept);
            }

            response.data.put("departments", departments);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @POST
    @Path("/attach-document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response attachAuditDocument(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDetail,
            @FormDataParam("intr_audit_id") FormDataBodyPart intrAuditIdPart,
            @FormDataParam("clause_no") FormDataBodyPart clauseNoPart,
            @FormDataParam("std_name") FormDataBodyPart stdNamePart,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new AuditPlanResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid company code"))
                    .build();
        }

        // Extract values from FormDataBodyPart
        String intrAuditId = intrAuditIdPart != null ? intrAuditIdPart.getValue() : null;
        String clauseNo = clauseNoPart != null ? clauseNoPart.getValue() : null;
        String stdName = stdNamePart != null ? stdNamePart.getValue() : null;

        // Validate required parameters
        if (fileInputStream == null || fileDetail == null) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "No file was uploaded"))
                    .build();
        }

        if (intrAuditId == null || clauseNo == null || stdName == null) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Missing required parameters"))
                    .build();
        }

        String fileName = fileDetail.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new AuditPlanResponse(false, "Invalid file name"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Get standard ID
            String standardId = null;
            String query = "SELECT id FROM standard_master WHERE std_name = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, stdName);
            rs = ps.executeQuery();
            if (rs.next()) {
                standardId = rs.getString("id");
            } else {
                return Response.status(Status.BAD_REQUEST)
                        .entity(new AuditPlanResponse(false, "Standard not found"))
                        .build();
            }

            // Get max document ID
            int maxDocId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_internal_audit_document";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxDocId = rs.getInt("max_id");
            }
            maxDocId++;

            // Create directory structure
            String basePath = "C:\\Installation\\ManagementERP";
            String relativePath = companyCode + "\\" + clauseNo + "\\Attachment\\n";
            String fullPath = basePath + File.separator + relativePath;

            File directory = new File(fullPath);
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity(new AuditPlanResponse(false, "Failed to create directory structure"))
                            .build();
                }
            }

            // Save file metadata to database
            query = "INSERT INTO " + companyCode + "_internal_audit_document (id, intr_audit_id, std_id, clause_no, file_name) VALUES (?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxDocId);
            ps.setString(2, intrAuditId);
            ps.setString(3, standardId);
            ps.setString(4, clauseNo);
            ps.setString(5, fileName);
            ps.executeUpdate();

            // Save file to filesystem
            File outputFile = new File(directory, fileName);
            try (OutputStream out = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                logger.info("File saved successfully: " + outputFile.getAbsolutePath());
            }

            con.commit();

            AuditPlanResponse response = new AuditPlanResponse(true, "File uploaded successfully");
            response.data.put("fileName", fileName);
            response.data.put("documentId", maxDocId);
            response.data.put("filePath", outputFile.getAbsolutePath());
            response.data.put("intrAuditId", intrAuditId);
            response.data.put("stdName", stdName);
            response.data.put("clauseNo", clauseNo);
            return Response.status(Status.CREATED).entity(response).build();

        } catch (SQLException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } catch (IOException e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException ex) {
                logger.severe("Rollback error: " + ex.getMessage());
            }
            logger.severe("File processing error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new AuditPlanResponse(false, "File processing error: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                logger.warning("Error closing file input stream: " + e.getMessage());
            }
        }
    }

    private String extractFileName(String contentDisposition) {
        if (contentDisposition != null) {
            for (String content : contentDisposition.split(";")) {
                if (content.trim().startsWith("filename")) {
                    return content.substring(content.indexOf("=") + 2, content.length() - 1);
                }
            }
        }
        return null;
    }

    private boolean isValidAuth(String employeeId, String companyCode) {
        return employeeId != null && !employeeId.isEmpty() && companyCode != null && !companyCode.isEmpty();
    }

    private boolean isValidCompanyCode(String companyCode) {
        return companyCode != null && COMPANY_CODE_PATTERN.matcher(companyCode).matches();
    }

    private boolean validateAuditPlanRequest(AuditPlanRequest request) {
        if (request == null) return false;
        if (request.auditNo == null || request.auditNo.isEmpty()) return false;
        if (request.auditDate == null || request.auditDate.isEmpty()) return false;
        if (request.auditEndDate == null || request.auditEndDate.isEmpty()) return false;
        // Add more validation as needed (e.g., date format, time format)
        return true;
    }

    private void closeResources(ResultSet rs, PreparedStatement ps, Connection con) {
        try {
            if (rs != null) {
                rs.close();
                logger.fine("ResultSet closed");
            }
            if (ps != null) {
                ps.close();
                logger.fine("PreparedStatement closed");
            }
            if (con != null) {
                con.close();
                logger.fine("Connection closed");
            }
        } catch (SQLException e) {
            logger.warning("Error closing resources: " + e.getMessage());
        }
    }
}
