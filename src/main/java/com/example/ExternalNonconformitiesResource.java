package com.example;

import com.example.util.DBConfig;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("/external-nonconformities")
public class ExternalNonconformitiesResource {

    private static final Logger logger = Logger.getLogger(ExternalNonconformitiesResource.class.getName());
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final String MODULE_NAME = "EXTERNAL AUDIT";

    // Response classes
    public static class NonconformityResponse<T> {
        public boolean success;
        public String message;
        public T data;

        public NonconformityResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public NonconformityResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    // DTO classes
    public static class Nonconformity {
        public String id;
        public String externalAuditId;
        public String ncNo;
        public String site;
        public String clauseNo;
        public String type;
        public String processArea;
        public String auditor;
        public String correction;
        public String correctionWhen;
        public String correctionWhom;
        public String rootCause;
        public String corrective;
        public String correctiveWhen;
        public String correctiveWhom;
        public String status;
        public String stdName;
    }

    public static class UserAccess {
        public String addAccess;
        public String editAccess;
        public String deleteAccess;
        public String approveAccess;
    }

    public static class ClauseDetails {
        public String standardName;
        public String auditNo;
        public String processArea;
        public String auditor;
        public String clauseNo;
        public String type;
        public String auditFindings;
        public String auditEvidence;
        public String site;
    }

    // GET all nonconformities
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNonconformities(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!validateHeaders(companyCode, employeeId, employeeName)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid headers");
        }

        if (!isValidCompanyCode(companyCode)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid company code format");
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();

            // Get user access
            UserAccess access = getUserAccess(con, companyCode, employeeId, MODULE_NAME);
            if (access == null) {
                return buildErrorResponse(Status.FORBIDDEN, "User does not have access");
            }

            // Get nonconformities
            List<Nonconformity> nonconformities = getNonconformitiesList(con, companyCode);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("access", access);
            responseData.put("nonconformities", nonconformities);

            return Response.ok(new NonconformityResponse<>(true, "Nonconformities retrieved successfully", responseData))
                    .build();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving nonconformities", e);
            return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error occurred");
        } finally {
            closeQuietly(con);
        }
    }


    @GET
    @Path("/audit-plans")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCompletedAuditPlans(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId) {

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT id, audit_no FROM " + companyCode + "_external_audit_master WHERE status='Complet'";
            List<Map<String, String>> auditPlans = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> plan = new HashMap<>();
                    plan.put("id", rs.getString("id"));
                    plan.put("auditNo", rs.getString("audit_no"));
                    auditPlans.add(plan);
                }
            }

            return Response.ok(auditPlans).build();  // returns pure list of JSON objects

        } catch (SQLException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(error)
                    .build();
        }
    }


    @GET
    @Path("/clause-details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClauseDetails(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @QueryParam("external_audit_id") String auditId,
            @QueryParam("external_nc_no") String ncNo) {

        Map<String, Object> details = new HashMap<>();

        try (Connection con = DBConfig.getConnection()) {

            // Get the entire row from external audit detail table
            String clauseQuery = "SELECT * FROM " + companyCode + "_external_audit_detail " +
                    "WHERE external_audit_id = ? AND nc_no = ?";

            try (PreparedStatement ps = con.prepareStatement(clauseQuery)) {
                ps.setString(1, auditId);
                ps.setString(2, ncNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            Object value = rs.getObject(i);
                            details.put(columnName, value);
                        }
                    }
                }
            }

            String query = "SELECT * FROM company_registration WHERE company_code = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, companyCode); // assuming 'companyCode' is set properly

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i);
                            String value = rs.getString(i);
                            details.put(columnName, value);
                        }

                        // Optional: Add custom 'site' field
                        String site = String.format("%s%s-%s-%s",
                                rs.getString("street1"),
                                rs.getString("street2"),
                                rs.getString("village"),
                                rs.getString("pincode"));
                        details.put("site", site);
                    }
                }
            }

            return Response.ok(details).build();

        } catch (SQLException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }


    // POST create nonconformity
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName,
            NonconformityRequest request) {

        // Validate headers
        if (!validateHeaders(companyCode, employeeId, employeeName)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid headers");
        }

        // Validate required fields
        if (request.externalAuditId == null || request.ncNo == null || request.site == null ||
                request.clauseNo == null || request.type == null || request.processArea == null ||
                request.auditor == null) {
            return buildErrorResponse(Status.BAD_REQUEST, "Missing required fields");
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Validate user access
            UserAccess access = getUserAccess(con, companyCode, employeeId, MODULE_NAME);
            if (access == null || !"Y".equals(access.addAccess)) {
                return buildErrorResponse(Status.FORBIDDEN, "User does not have add access");
            }

            // Get next ID
            int ncId = getNextId(con, companyCode + "_external_nonconformities");

            // Insert nonconformity
            String insertQuery = "INSERT INTO " + companyCode + "_external_nonconformities " +
                    "(id, external_audit_id, nc_no, site, clause_no, type, process_area, " +
                    "auditor, correction, correction_when, correction_whom, root_cause, " +
                    "corrective, corrective_when, corrective_whom, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Draft')";

            try (PreparedStatement ps = con.prepareStatement(insertQuery)) {
                ps.setInt(1, ncId);
                ps.setString(2, request.externalAuditId);
                ps.setString(3, request.ncNo);
                ps.setString(4, request.site);
                ps.setString(5, request.clauseNo);
                ps.setString(6, request.type);
                ps.setString(7, request.processArea);
                ps.setString(8, request.auditor);
                ps.setString(9, request.correction);
                ps.setString(10, request.correctionWhen);
                ps.setString(11, request.correctionWhom);
                ps.setString(12, request.rootCause);
                ps.setString(13, request.corrective);
                ps.setString(14, request.correctiveWhen);
                ps.setString(15, request.correctiveWhom);
                ps.executeUpdate();
            }

            // Log action
            logAction(con, companyCode, employeeName, "Create", MODULE_NAME, String.valueOf(ncId));

            con.commit();
            return Response.ok(new NonconformityResponse<>(true, "Nonconformity created successfully"))
                    .build();

        } catch (SQLException e) {
            rollbackQuietly(con);
            logger.log(Level.SEVERE, "Error creating nonconformity", e);
            return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error occurred");
        } finally {
            closeQuietly(con);
        }
    }

    // Request DTO class
    public static class NonconformityRequest {
        public String externalAuditId;
        public String ncNo;
        public String site;
        public String clauseNo;
        public String type;
        public String processArea;
        public String auditor;
        public String correction;
        public String correctionWhen;
        public String correctionWhom;
        public String rootCause;
        public String corrective;
        public String correctiveWhen;
        public String correctiveWhom;
        public String status;
    }

    // ... existing code ...

    // DELETE nonconformity
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteNonconformity(
            @PathParam("id") String ncId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!validateHeaders(companyCode, employeeId, employeeName)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid headers");
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Validate user access
            UserAccess access = getUserAccess(con, companyCode, employeeId, MODULE_NAME);
            if (access == null || !"Y".equals(access.deleteAccess)) {
                return buildErrorResponse(Status.FORBIDDEN, "User does not have delete access");
            }

            // Check if NC exists and is in draft status
            if (!canDeleteNonconformity(con, companyCode, ncId)) {
                return buildErrorResponse(Status.BAD_REQUEST, "Cannot delete this nonconformity");
            }

            // Delete NC
            deleteNonconformityFromDB(con, companyCode, ncId);

            // Log action
            logAction(con, companyCode, employeeName, "Delete", MODULE_NAME, ncId);

            con.commit();
            return Response.ok(new NonconformityResponse<>(true, "Nonconformity deleted successfully"))
                    .build();

        } catch (SQLException e) {
            rollbackQuietly(con);
            logger.log(Level.SEVERE, "Error deleting nonconformity", e);
            return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error occurred");
        } finally {
            closeQuietly(con);
        }
    }

    // PUT approve nonconformity
    @PUT
    @Path("/{id}/approve")
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveNonconformity(
            @PathParam("id") String ncId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!validateHeaders(companyCode, employeeId, employeeName)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid headers");
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Validate user access
            UserAccess access = getUserAccess(con, companyCode, employeeId, MODULE_NAME);
            if (access == null || !"Y".equals(access.approveAccess)) {
                return buildErrorResponse(Status.FORBIDDEN, "User does not have approve access");
            }

            // Update status
            if (!updateNonconformityStatus(con, companyCode, ncId, "Done")) {
                return buildErrorResponse(Status.NOT_FOUND, "Nonconformity not found");
            }

            // Log action
            logAction(con, companyCode, employeeName, "Approve", MODULE_NAME, ncId);

            con.commit();
            return Response.ok(new NonconformityResponse<>(true, "Nonconformity approved successfully"))
                    .build();

        } catch (SQLException e) {
            rollbackQuietly(con);
            logger.log(Level.SEVERE, "Error approving nonconformity", e);
            return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error occurred");
        } finally {
            closeQuietly(con);
        }
    }

    // PUT revert status
    @PUT
    @Path("/{id}/back-status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response revertStatus(
            @PathParam("id") String ncId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        if (!validateHeaders(companyCode, employeeId, employeeName)) {
            return buildErrorResponse(Status.BAD_REQUEST, "Invalid headers");
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Validate user access
            UserAccess access = getUserAccess(con, companyCode, employeeId, MODULE_NAME);
            if (access == null || !"Y".equals(access.approveAccess)) {
                return buildErrorResponse(Status.FORBIDDEN, "User does not have approve access");
            }

            // Update status
            if (!updateNonconformityStatus(con, companyCode, ncId, "Draft")) {
                return buildErrorResponse(Status.NOT_FOUND, "Nonconformity not found");
            }

            // Log action
            logAction(con, companyCode, employeeName, "Revert Status", MODULE_NAME, ncId);

            con.commit();
            return Response.ok(new NonconformityResponse<>(true, "Status reverted successfully"))
                    .build();

        } catch (SQLException e) {
            rollbackQuietly(con);
            logger.log(Level.SEVERE, "Error reverting status", e);
            return buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error occurred");
        } finally {
            closeQuietly(con);
        }
    }

    // Helper methods
    private boolean validateHeaders(String companyCode, String employeeId, String employeeName) {
        return companyCode != null && !companyCode.isEmpty() &&
                employeeId != null && !employeeId.isEmpty() &&
                employeeName != null && !employeeName.isEmpty();
    }

    private boolean isValidCompanyCode(String companyCode) {
        return companyCode != null && COMPANY_CODE_PATTERN.matcher(companyCode).matches();
    }

    private UserAccess getUserAccess(Connection con, String companyCode, String employeeId, String moduleName)
            throws SQLException {
        String query = "SELECT add_access, edit_access, delete_access, approve_access " +
                "FROM " + companyCode + "_user_module_access " +
                "WHERE user_id = ? AND module_id = (SELECT module_id FROM " +
                companyCode + "_module_master WHERE module_name = ?)";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, employeeId);
            ps.setString(2, moduleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UserAccess access = new UserAccess();
                    access.addAccess = rs.getString("add_access");
                    access.editAccess = rs.getString("edit_access");
                    access.deleteAccess = rs.getString("delete_access");
                    access.approveAccess = rs.getString("approve_access");
                    return access;
                }
                return null;
            }
        }
    }

    private List<Nonconformity> getNonconformitiesList(Connection con, String companyCode) throws SQLException {
        String query = "SELECT n.*, m.std_name " +
                "FROM " + companyCode + "_external_nonconformities n " +
                "LEFT JOIN " + companyCode + "_external_audit_master m ON n.external_audit_id = m.id " +
                "ORDER BY n.id DESC";

        List<Nonconformity> nonconformities = new ArrayList<>();
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Nonconformity nc = new Nonconformity();
                nc.id = rs.getString("id");
                nc.externalAuditId = rs.getString("external_audit_id");
                nc.ncNo = rs.getString("nc_no");
                nc.site = rs.getString("site");
                nc.clauseNo = rs.getString("clause_no");
                nc.type = rs.getString("type");
                nc.processArea = rs.getString("process_area");
                nc.auditor = rs.getString("auditor");
                nc.correction = rs.getString("correction");
                nc.correctionWhen = rs.getString("correction_when");
                nc.correctionWhom = rs.getString("correction_whom");
                nc.rootCause = rs.getString("root_cause");
                nc.corrective = rs.getString("corrective");
                nc.correctiveWhen = rs.getString("corrective_when");
                nc.correctiveWhom = rs.getString("corrective_whom");
                nc.status = rs.getString("status");
                nc.stdName = rs.getString("std_name");
                nonconformities.add(nc);
            }
        }
        return nonconformities;
    }

    private ClauseDetails getClauseDetailsFromDB(Connection con, String companyCode,
                                                 String externalAuditId, String externalNcNo) throws SQLException {
        String query = "SELECT m.std_name, m.audit_no, p.process_area, a.auditor_name, " +
                "d.clause_no, d.type, d.audit_findings, d.audit_evidence, " +
                "c.street1, c.street2, c.village, c.pincode " +
                "FROM " + companyCode + "_external_audit_master m " +
                "LEFT JOIN " + companyCode + "_external_audit_plan_detail p ON m.audit_plan_id = p.audit_plan_id " +
                "LEFT JOIN " + companyCode + "_external_audit_plan_auditors a ON m.audit_plan_id = a.audit_plan_id " +
                "LEFT JOIN " + companyCode + "_external_audit_detail d ON m.id = d.external_audit_id " +
                "LEFT JOIN company_registration c ON c.company_code = ? " +
                "WHERE m.id = ? AND d.nc_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, companyCode);
            ps.setString(2, externalAuditId);
            ps.setString(3, externalNcNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClauseDetails details = new ClauseDetails();
                    details.standardName = rs.getString("std_name");
                    details.auditNo = rs.getString("audit_no");
                    details.processArea = rs.getString("process_area");
                    details.auditor = rs.getString("auditor_name");
                    details.clauseNo = rs.getString("clause_no");
                    details.type = rs.getString("type");
                    details.auditFindings = rs.getString("audit_findings");
                    details.auditEvidence = rs.getString("audit_evidence");
                    details.site = String.format("%s %s - %s - %s",
                            rs.getString("street1"),
                            rs.getString("street2"),
                            rs.getString("village"),
                            rs.getString("pincode"));
                    return details;
                }
                return null;
            }
        }
    }

    private int insertNonconformity(Connection con, String companyCode, Map<String, String> formData)
            throws SQLException {
        int ncId = getNextId(con, companyCode + "_external_nonconformities");

        String sql = "INSERT INTO " + companyCode + "_external_nonconformities " +
                "(id, external_audit_id, nc_no, site, clause_no, type, process_area, auditor, " +
                "correction, correction_when, correction_whom, root_cause, corrective, " +
                "corrective_when, corrective_whom, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Draft')";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ncId);
            ps.setString(2, formData.get("external_audit_id"));
            ps.setString(3, formData.get("nc_no"));
            ps.setString(4, formData.get("site"));
            ps.setString(5, formData.get("clause_no"));
            ps.setString(6, formData.get("type"));
            ps.setString(7, formData.get("process_area"));
            ps.setString(8, formData.get("auditor"));
            ps.setString(9, formData.get("correction"));
            ps.setString(10, formData.get("correction_when"));
            ps.setString(11, formData.get("correction_whom"));
            ps.setString(12, formData.get("root_cause"));
            ps.setString(13, formData.get("corrective"));
            ps.setString(14, formData.get("corrective_when"));
            ps.setString(15, formData.get("corrective_whom"));
            ps.executeUpdate();
        }
        return ncId;
    }

    private boolean canDeleteNonconformity(Connection con, String companyCode, String ncId) throws SQLException {
        String query = "SELECT status FROM " + companyCode + "_external_nonconformities WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, ncId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "Draft".equalsIgnoreCase(rs.getString("status"));
            }
        }
    }

    private void deleteNonconformityFromDB(Connection con, String companyCode, String ncId) throws SQLException {
        String query = "DELETE FROM " + companyCode + "_external_nonconformities WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, ncId);
            ps.executeUpdate();
        }
    }

    private boolean updateNonconformityStatus(Connection con, String companyCode, String ncId, String status)
            throws SQLException {
        String query = "UPDATE " + companyCode + "_external_nonconformities SET status = ? WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, status);
            ps.setString(2, ncId);
            return ps.executeUpdate() > 0;
        }
    }

    private void logAction(Connection con, String companyCode, String employeeName,
                           String action, String moduleName, String moduleId) throws SQLException {
        String query = "INSERT INTO " + companyCode + "_log_master " +
                "(id, fired_date, fired_by, status, module_name, module_id) " +
                "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setInt(1, getNextId(con, companyCode + "_log_master"));
            ps.setString(2, employeeName);
            ps.setString(3, action);
            ps.setString(4, moduleName);
            ps.setString(5, moduleId);
            ps.executeUpdate();
        }
    }

    private int getNextId(Connection con, String table) throws SQLException {
        String query = "SELECT COALESCE(MAX(id), 0) + 1 FROM " + table;
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    private Response buildErrorResponse(Status status, String message) {
        return Response.status(status)
                .entity(new NonconformityResponse<>(false, message))
                .build();
    }

    private void rollbackQuietly(Connection con) {
        try {
            if (con != null) {
                con.rollback();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error rolling back transaction", e);
        }
    }

    private void closeQuietly(Connection con) {
        try {
            if (con != null) {
                con.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error closing connection", e);
        }
    }
}