package com.example;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.ValidationUtil;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/nonconformities")
public class InternalNonconformitiesResource {

    private static final Logger logger = Logger.getLogger(InternalNonconformitiesResource.class.getName());

    // Request and Response classes
    public static class Nonconformity {
        public String id;
        public String ncNo;
        public String internalAuditNo;
        public String standardName;
        public String clauseNo;
        public String site;
        public String processArea;
        public String auditor;
        public String type;
        public String status;
        public String correction;
        public String correctionWhen;
        public String correctionWhom;
        public String rootCause;
        public String corrective;
        public String correctiveWhen;
        public String correctiveWhom;

        public Nonconformity() {}
    }

    public static class NonconformitiesResponse {
        public boolean success;
        public String error;
        public List<Nonconformity> data;

        public NonconformitiesResponse(boolean success, List<Nonconformity> data) {
            this.success = success;
            this.data = data;
        }

        public NonconformitiesResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class Audit {
        public String id;
        public String auditNo;

        public Audit() {}
    }

    public static class AuditsResponse {
        public boolean success;
        public String error;
        public List<Audit> data;

        public AuditsResponse(boolean success, List<Audit> data) {
            this.success = success;
            this.data = data;
        }

        public AuditsResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class ActionResponse {
        public boolean success;
        public String error;
        public String message;

        public ActionResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public ActionResponse(boolean success, String error, String message) {
            this.success = success;
            this.error = error;
            this.message = message;
        }
    }

    public static class NonconformityRequest {
        public String ncNo;
        public String internalAuditNo;
        public String standardName;
        public String clauseNo;
        public String site;
        public String processArea;
        public String auditor;
        public String type;
        public String status;
        public String correction;
        public String correctionWhen;
        public String correctionWhom;
        public String rootCause;
        public String corrective;
        public String correctiveWhen;
        public String correctiveWhom;
        public int fieldCount;

        public NonconformityRequest() {}
    }

    public static class NcOption {
        public String ncNo;

        public NcOption() {}
    }

    public static class NcOptionsResponse {
        public boolean success;
        public String error;
        public List<NcOption> data;

        public NcOptionsResponse(boolean success, List<NcOption> data) {
            this.success = success;
            this.data = data;
        }

        public NcOptionsResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class ProcessAreasResponse {
        public boolean success;
        public String error;
        public List<Map<String,String>> data;

        public ProcessAreasResponse(boolean success, List<Map<String,String>> data) {
            this.success = success;
            this.data = data;
        }

        public ProcessAreasResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class AuditorsResponse {
        public boolean success;
        public String error;
        public List<String> data;

        public AuditorsResponse(boolean success, List<String> data) {
            this.success = success;
            this.data = data;
        }

        public AuditorsResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class SiteDetails {
        public String site;

        public SiteDetails() {}
    }

    public static class SiteResponse {
        public boolean success;
        public String error;
        public SiteDetails data;

        public SiteResponse(boolean success, SiteDetails data) {
            this.success = success;
            this.data = data;
        }

        public SiteResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    @PUT
    @Path("/{id}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String ncId) {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            logger.info("Approving NC ID: " + ncId + " | Company: " + companyCode + " | User: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return ErrorHandler.badRequest("Missing headers", "company-code and user-id are required");
            }


            con = DBConfig.getConnection();
            String approvedBy = userId;
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String query = "SELECT MAX(id) FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            int logId = rs.next() ? rs.getInt(1) + 1 : 1;
            closeResources(rs, ps, null);

            if (ValidationUtil.isNotEmpty(ncId) && ncId != null) {
                // Approve
                query = "SELECT status, nc_no, internal_audit_id FROM " + companyCode + "_nonconformities WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, ncId);
                rs = ps.executeQuery();

                if (!rs.next()) {
                    return ErrorHandler.badRequest("Nonconformity not found", "ID: " + ncId);
                }

                String status = rs.getString("status");
                String ncNo = rs.getString("nc_no");
                String internalAuditId = rs.getString("internal_audit_id");
                closeResources(rs, ps, null);

                if (!"Draft".equalsIgnoreCase(status)) {
                    return ErrorHandler.badRequest("Invalid status for approval", "Expected: Draft, Found: " + status);
                }

                // Update to Done
                ps = con.prepareStatement("UPDATE " + companyCode + "_nonconformities SET status = 'Done' WHERE id = ?");
                ps.setString(1, ncId);
                ps.executeUpdate();
                closeResources(null, ps, null);

                ps = con.prepareStatement("UPDATE " + companyCode + "_internal_audit_detail SET status = 'Done' WHERE internal_audit_id = ? AND nc_no = ?");
                ps.setString(1, internalAuditId);
                ps.setString(2, ncNo);
                ps.executeUpdate();
                closeResources(null, ps, null);

                ps = con.prepareStatement("INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) VALUES (?, ?, ?, ?, ?, ?)");
                ps.setInt(1, logId);
                ps.setString(2, today);
                ps.setString(3, approvedBy);
                ps.setString(4, "Done");
                ps.setString(5, "Nonconformities");
                ps.setString(6, ncId);
                ps.executeUpdate();
                closeResources(null, ps, null);

                return Response.ok(new ActionResponse(true, "Nonconformity approved successfully")).build();

            } else if (ValidationUtil.isNotEmpty(ncId)) {
                // Revert
                query = "SELECT status, nc_no, internal_audit_id FROM " + companyCode + "_nonconformities WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, ncId);
                rs = ps.executeQuery();

                if (!rs.next()) {

                    return ErrorHandler.badRequest("Nonconformity not found", "Back ID: " + ncId);

                }

                String status = rs.getString("status");
                String ncNo = rs.getString("nc_no");
                String internalAuditId = rs.getString("internal_audit_id");
                closeResources(rs, ps, null);

                if (!"Done".equalsIgnoreCase(status)) {
                    return ErrorHandler.badRequest("Invalid status for revert", "Expected: Done, Found: " + status);
                }

                ps = con.prepareStatement("UPDATE " + companyCode + "_nonconformities SET status = 'Draft' WHERE id = ?");
                ps.setString(1, ncId);
                ps.executeUpdate();
                closeResources(null, ps, null);

                ps = con.prepareStatement("UPDATE " + companyCode + "_internal_audit_detail SET status = 'Draft' WHERE internal_audit_id = ? AND nc_no = ?");
                ps.setString(1, internalAuditId);
                ps.setString(2, ncNo);
                ps.executeUpdate();
                closeResources(null, ps, null);

                ps = con.prepareStatement("INSERT INTO " + companyCode + "_log_master (id, log_date, approved_by, status, module, module_id) VALUES (?, ?, ?, ?, ?, ?)");
                ps.setInt(1, logId);
                ps.setString(2, today);
                ps.setString(3, approvedBy);
                ps.setString(4, "Draft");
                ps.setString(5, "Nonconformities");
                ps.setString(6, ncId);
                ps.executeUpdate();
                closeResources(null, ps, null);

                return Response.ok(new ActionResponse(true, "Nonconformity reverted to Draft successfully")).build();
            }

            return ErrorHandler.badRequest("Invalid request", "Provide either ncId or backId");

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @PUT
    @Path("/{id}/revert")
    @Produces(MediaType.APPLICATION_JSON)
    public Response revertNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String backId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            logger.info("Reverting nonconformity id: " + backId + ", company_code: " + companyCode + ", user_id: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId) || !ValidationUtil.isNotEmpty(backId)) {
                return ErrorHandler.badRequest("Company code, user ID, and nonconformity ID are required", "Missing required headers or parameters");
            }

            con = DBConfig.getConnection();
            String approvedBy = userId; // Assume userId maps to emp_name; adjust if lookup needed
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Get max log ID
            String query = "SELECT MAX(id) FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            int logId = rs.next() ? rs.getInt(1) + 1 : 1;
            closeResources(rs, ps, null);

            // Check nonconformity status
            query = "SELECT status, nc_no, internal_audit_id FROM " + companyCode + "_nonconformities WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, backId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return ErrorHandler.badRequest("Nonconformity not found", "ID: " + backId);
            }

            String status = rs.getString("status");
            String ncNo = rs.getString("nc_no");
            String internalAuditId = rs.getString("internal_audit_id");
            closeResources(rs, ps, null);

            if (!"Done".equalsIgnoreCase(status)) {
                return ErrorHandler.badRequest("Nonconformity is not in Done status", "Status: " + status);
            }

            // Update nonconformities to Draft
            query = "UPDATE " + companyCode + "_nonconformities SET status = 'Draft' WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, backId);
            int rowsAffected = ps.executeUpdate();
            closeResources(null, ps, null);

            // Update internal audit detail to Draft
            query = "UPDATE " + companyCode + "_internal_audit_detail SET status = 'Draft' WHERE internal_audit_id = ? AND nc_no = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.setString(2, ncNo);
            ps.executeUpdate();
            closeResources(null, ps, null);

            // Insert log
            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, logId);
            ps.setString(2, today);
            ps.setString(3, approvedBy);
            ps.setString(4, "Draft");
            ps.setString(5, "Nonconformities");
            ps.setString(6, backId);
            ps.executeUpdate();
            closeResources(null, ps, null);

            return Response.ok(new ActionResponse(true, "Nonconformity reverted to Draft successfully")).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred during reversion", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }

    // Export nonconformity as HTML table
    @GET
    @Path("/{id}/export")
    @Produces(MediaType.TEXT_HTML)
    public Response exportNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String ncId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            logger.info("Exporting nonconformity id: " + ncId + ", company_code: " + companyCode + ", user_id: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId) || !ValidationUtil.isNotEmpty(ncId)) {
                return ErrorHandler.badRequest("Company code, user ID, and nonconformity ID are required", "Missing required headers or parameters");
            }

            con = DBConfig.getConnection();

            // Fetch nonconformity data
            String query = "SELECT * FROM " + companyCode + "_nonconformities WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, ncId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return ErrorHandler.badRequest("Nonconformity not found", "ID: " + ncId);
            }

            String internalAuditId = rs.getString("internal_audit_id");
            String site = rs.getString("site");
            String type = rs.getString("type");
            String ncNo = rs.getString("nc_no");
            String clauseNo = rs.getString("clause_no");
            String processArea = rs.getString("process_area");
            String auditor = rs.getString("auditor");
            String correction = rs.getString("correction");
            String correctionWhen = rs.getString("correction_when");
            String correctionWhom = rs.getString("correction_whom");
            String rootCause = rs.getString("root_cause");
            String corrective = rs.getString("corrective");
            String correctiveWhen = rs.getString("corrective_when");
            String correctiveWhom = rs.getString("corrective_whom");
            closeResources(rs, ps, null);

            // Fetch audit master data
            String stdName = "";
            query = "SELECT std_name FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            rs = ps.executeQuery();
            if (rs.next()) {
                stdName = rs.getString("std_name");
            }
            closeResources(rs, ps, null);

            // Fetch audit detail data
            String auditEvidence = "";
            String auditFindings = "";
            query = "SELECT evidence, comment FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ? AND nc_no = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.setString(2, ncNo);
            rs = ps.executeQuery();
            if (rs.next()) {
                auditEvidence = rs.getString("evidence");
                auditFindings = rs.getString("comment");
            }
            closeResources(rs, ps, null);

            // Fetch nonconformity detail data
            String correctionDate = "";
            String correctionEA = "";
            String correctionEvidence = "";
            String correctiveDate = "";
            String correctiveEA = "";
            String correctiveEvidence = "";
            query = "SELECT * FROM " + companyCode + "_nonconformities_detail WHERE nc_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, ncId);
            rs = ps.executeQuery();
            if (rs.next()) {
                correctionDate = rs.getString("correction_date");
                correctionEA = rs.getString("correction_e_a");
                correctionEvidence = rs.getString("correction_evidence");
                correctiveDate = rs.getString("corrective_date");
                correctiveEA = rs.getString("corrective_e_a");
                correctiveEvidence = rs.getString("corrective_evidence");
            }
            closeResources(rs, ps, null);

            // Generate HTML table
            StringBuilder html = new StringBuilder();
            html.append("<table cellpadding=\"1\" cellspacing=\"1\" border=\"1\">");

            html.append("<tr><th colspan='9'><h3>Nonconformities</h3></th></tr>");
            html.append("<tr><td colspan='9'>All audit results gatheres by the audit team during the audit (certification audit,special audit,change audit,recertification audit,re-audit,surveillance audit) shall be listed in the table below.</td></tr>");

            html.append("<tr>");
            html.append("<td>No.</td><td>").append(safeString(ncNo)).append("</td>");
            html.append("<td>Standard</td><td colspan='4'>").append(safeString(stdName)).append("</td>");
            html.append("<td>Type</td><td>").append(safeString(type)).append("</td>");
            html.append("</tr>");

            html.append("<tr><td>Site</td><td colspan='8'>").append(safeString(site)).append("</td></tr>");

            html.append("<tr>");
            html.append("<td>Clause No</td><td colspan='2'>").append(safeString(clauseNo)).append("</td>");
            html.append("<td colspan='2'>Process / Area</td><td colspan='4'>").append(safeString(processArea)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td rowspan='2'>Audit results<br> (filled out by auditor)</td>");
            html.append("<td>Findings</td><td colspan='7'>").append(safeString(auditFindings)).append("</td>");
            html.append("</tr>");

            html.append("<tr><td>Evidence</td><td colspan='7'>").append(safeString(auditEvidence)).append("</td></tr>");

            html.append("<tr><td colspan='9'>Action : (Filled Out by organization)</td></tr>");

            html.append("<tr>");
            html.append("<td rowspan='2'>Correction : <br> (immediate)</td>");
            html.append("<td colspan='8'>").append(safeString(correction)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td>When :</td><td>").append(safeString(correctionWhen)).append("</td>");
            html.append("<td>Whom :</td><td colspan='5'>").append(safeString(correctionWhom)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td>Root Cause : <br> (why did the nonconformity occur; no repetition of the finding)</td>");
            html.append("<td colspan='8'>").append(safeString(rootCause)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td rowspan='2'>Corrective : <br> (Action to avoid repetition of root cause)</td>");
            html.append("<td colspan='8'>").append(safeString(corrective)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td>When :</td><td>").append(safeString(correctiveWhen)).append("</td>");
            html.append("<td>Whom :</td><td colspan='5'>").append(safeString(correctiveWhom)).append("</td>");
            html.append("</tr>");

            html.append("<tr><td colspan='9'>Auditor's decision of correction and corrective action : (filled out by auditor)</td></tr>");

            html.append("<tr>");
            html.append("<td>Correction:</td>");
            html.append("<td>Date</td><td>").append(safeString(correctionDate)).append("</td>");
            html.append("<td>Effective (E)/ Accepted (A):</td><td>").append(safeString(correctionEA)).append("</td>");
            html.append("<td>Evidence of implementation:</td><td colspan='3'>").append(safeString(correctionEvidence)).append("</td>");
            html.append("</tr>");

            html.append("<tr>");
            html.append("<td>Corrective:</td>");
            html.append("<td>Date</td><td>").append(safeString(correctiveDate)).append("</td>");
            html.append("<td>Effective (E)/ Accepted (A):</td><td>").append(safeString(correctiveEA)).append("</td>");
            html.append("<td>Evidence of implementation:</td><td colspan='3'>").append(safeString(correctiveEvidence)).append("</td>");
            html.append("</tr>");

            html.append("</table>");

            return Response.ok(html.toString()).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred during export", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }

    private String safeString(String input) {
        return input != null ? input.replace("<", "&lt;").replace(">", "&gt;") : "";
    }


    // DELETE nonconformity
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String id) {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            logger.info("Deleting nonconformity id: " + id + " for company_code: " + companyCode + ", user_id: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId) || !ValidationUtil.isNotEmpty(id)) {
                return ErrorHandler.badRequest("Company code, user ID, and nonconformity ID are required", "Missing required headers or parameters");
            }

            con = DBConfig.getConnection();
            String query = "DELETE FROM " + companyCode + "_nonconformities WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, id);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                return Response.ok(new ActionResponse(true, "Nonconformity deleted successfully")).build();
            } else {
                return ErrorHandler.badRequest("Nonconformity not found", "ID: " + id);
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred during deletion", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                logger.warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    @POST
    @Path("/{id}/auditor-decision")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addAuditorDecision(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String ncId,
            Map<String, Object> requestBody) {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            logger.info("Adding auditor's decision for nc_id: " + ncId + ", company_code: " + companyCode + ", user_id: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId) || !ValidationUtil.isNotEmpty(ncId)) {
                return ErrorHandler.badRequest("Company code, user ID, and nonconformity ID are required", "Missing required headers or parameters");
            }

            // Extract values from requestBody map
            String correctionDate = (String) requestBody.get("correctionDate");
            String correctionEA = (String) requestBody.get("correctionEA");
            String correctionEvidence = (String) requestBody.get("correctionEvidence");
            String correctiveDate = (String) requestBody.get("correctiveDate");
            String correctiveEA = (String) requestBody.get("correctiveEA");
            String correctiveEvidence = (String) requestBody.get("correctiveEvidence");


            con = DBConfig.getConnection();

            // Verify nonconformity exists
            String query = "SELECT id FROM " + companyCode + "_nonconformities WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, ncId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return ErrorHandler.badRequest("Nonconformity not found", "ID: " + ncId);
            }
            closeResources(rs, ps, null);

            // Get max ID from nonconformities_detail
            query = "SELECT MAX(id) FROM " + companyCode + "_nonconformities_detail";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            int ncDetailId = rs.next() ? rs.getInt(1) + 1 : 1;
            closeResources(rs, ps, null);

            // Insert auditor's decision
            query = "INSERT INTO " + companyCode + "_nonconformities_detail (id, nc_id, correction_date, correction_e_a, correction_evidence, corrective_date, corrective_e_a, corrective_evidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, ncDetailId);
            ps.setString(2, ncId);
            ps.setString(3, correctionDate);
            ps.setString(4, correctionEA);
            ps.setString(5, correctionEvidence);
            ps.setString(6, correctiveDate);
            ps.setString(7, correctiveEA);
            ps.setString(8, correctiveEvidence);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Auditor's decision added successfully");
                return Response.ok(response).build();
            } else {
                return ErrorHandler.serverError("Failed to add auditor's decision", (Throwable) null);
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred during insertion", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }


    @GET
    @Path("/view")
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewNonconformities(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        Connection con = null;
        PreparedStatement ps = null;
        PreparedStatement ps2 = null;
        ResultSet res = null;
        ResultSet res2 = null;
        List<Map<String, Object>> nonconformities = new ArrayList<>();

        try {
            logger.info("Fetching nonconformities view for company_code: " + companyCode + " and user_id: " + userId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return ErrorHandler.badRequest("Company code and user ID are required", "Missing required headers");
            }

            con = DBConfig.getConnection();

            String query = "SELECT * FROM " + companyCode + "_nonconformities WHERE id > 0";
            ps = con.prepareStatement(query);
            res = ps.executeQuery();

            while (res.next()) {
                Map<String, Object> nc = new HashMap<>();
                nc.put("id", res.getString("id"));
                nc.put("nc_no", res.getString("nc_no"));
                nc.put("clause_no", res.getString("clause_no"));
                nc.put("site", res.getString("site"));
                nc.put("process_area", res.getString("process_area"));
                nc.put("status", res.getString("status"));

                // Fetch audit info
                String auditQuery = "SELECT audit_no, std_name, status FROM " + companyCode + "_internal_audit_master WHERE id = ?";
                ps2 = con.prepareStatement(auditQuery);
                ps2.setString(1, res.getString("internal_audit_id"));
                res2 = ps2.executeQuery();

                if (res2.next()) {
                    nc.put("audit_no", res2.getString("audit_no"));
                    nc.put("standard_name", res2.getString("std_name"));
                    nc.put("audit_status", res2.getString("status"));
                } else {
                    nc.put("audit_no", "");
                    nc.put("standard_name", "");
                    nc.put("audit_status", "");
                }

                nonconformities.add(nc);
                closeResources(res2, ps2, null);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", nonconformities);
            return Response.ok(response).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }


    // Get all nonconformities
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNonconformities(@HeaderParam("company-code") String companyCode) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;
        List<Nonconformity> nonconformities = new ArrayList<>();

        try {
            logger.info("Fetching nonconformities for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode)) {
                return ErrorHandler.badRequest("Company code is required", "Missing company_code header");
            }

            con = DBConfig.getConnection();
            String query = "SELECT * FROM " + companyCode + "_nonconformities";
            ps = con.prepareStatement(query);
            res = ps.executeQuery();

            while (res.next()) {
                Nonconformity nc = new Nonconformity();
                nc.id = res.getString("id");
                nc.ncNo = res.getString("nc_no");
                nc.internalAuditNo = res.getString("internal_audit_id");
                nc.clauseNo = res.getString("clause_no");
                nc.site = res.getString("site");
                nc.processArea = res.getString("process_area");
                nc.auditor = res.getString("auditor");
                nc.type = res.getString("type");
                nc.status = res.getString("status");
                nc.correction = res.getString("correction");
                nc.correctionWhen = res.getString("correction_when");
                nc.correctionWhom = res.getString("correction_whom");
                nc.rootCause = res.getString("root_cause");
                nc.corrective = res.getString("corrective");
                nc.correctiveWhen = res.getString("corrective_when");
                nc.correctiveWhom = res.getString("corrective_whom");
                nonconformities.add(nc);
            }

            return Response.ok(new NonconformitiesResponse(true, nonconformities)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }



    // Create a new nonconformity
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            NonconformityRequest request) {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            logger.info("Creating nonconformity for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return ErrorHandler.badRequest("Company code and user ID are required", "Missing required headers");
            }

            if (!ValidationUtil.isNotEmpty(request.ncNo) || !ValidationUtil.isNotEmpty(request.internalAuditNo)) {
                return ErrorHandler.badRequest("NC No and Internal Audit No are required", "Missing required fields");
            }

            con = DBConfig.getConnection();
            // Generate new ID
            String query = "SELECT MAX(id) FROM " + companyCode + "_nonconformities";
            ps = con.prepareStatement(query);
            ResultSet rs = ps.executeQuery();
            int ncId = rs.next() ? rs.getInt(1) + 1 : 1;
            closeResources(rs, ps, null);

            query = "INSERT INTO " + companyCode + "_nonconformities (id, nc_no, internal_audit_id, clause_no, site, process_area, auditor, type, status, correction, correction_when, correction_whom, root_cause, corrective, corrective_when, corrective_whom) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, ncId);
            ps.setString(2, request.ncNo);
            ps.setString(3, request.internalAuditNo);
            ps.setString(4, request.clauseNo);
            ps.setString(5, request.site);
            ps.setString(6, request.processArea);
            ps.setString(7, request.auditor);
            ps.setString(8, request.type);
            ps.setString(9, request.status != null ? request.status : "Draft");
            ps.setString(10, request.correction);
            ps.setString(11, request.correctionWhen);
            ps.setString(12, request.correctionWhom);
            ps.setString(13, request.rootCause);
            ps.setString(14, request.corrective);
            ps.setString(15, request.correctiveWhen);
            ps.setString(16, request.correctiveWhom);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                return Response.ok(new ActionResponse(true, "Nonconformity created successfully")).build();
            } else {
                return ErrorHandler.serverError("Failed to create nonconformity", (Throwable) null);
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(null, ps, con);
        }
    }

    // Get audits
    @GET
    @Path("/audits")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAudits(@HeaderParam("company-code") String companyCode) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching audits for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode)) {
                return ErrorHandler.badRequest("Company code is required", "Missing company_code header");
            }

            con = DBConfig.getConnection();
            String query = "SELECT id, audit_no FROM " + companyCode + "_internal_audit_master WHERE status IN ('Complet', 'In Process')";
            ps = con.prepareStatement(query);
            res = ps.executeQuery();

            List<Audit> audits = new ArrayList<>();
            while (res.next()) {
                Audit audit = new Audit();
                audit.id = res.getString("id");
                audit.auditNo = res.getString("audit_no");
                audits.add(audit);
            }

            return Response.ok(new AuditsResponse(true, audits)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    // Get audit details
    @GET
    @Path("/audit-details")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getAuditDetails(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("internal-audit-id") String internalAuditId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching audit details for internal_audit_id: " + internalAuditId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(internalAuditId)) {
                return ErrorHandler.badRequest("Company code and internal audit ID are required", "Missing parameters");
            }

            con = DBConfig.getConnection();
            String query = "SELECT standard_name, nc_no, process_area, auditor FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ? LIMIT 1";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            res = ps.executeQuery();

            if (res.next()) {
                String response = (res.getString("standard_name") != null ? res.getString("standard_name") : "") + "$" +
                        (res.getString("nc_no") != null ? res.getString("nc_no") : "") + "$" +
                        (res.getString("process_area") != null ? res.getString("process_area") : "") + "$" +
                        (res.getString("auditor") != null ? res.getString("auditor") : "");
                return Response.ok(response).build();
            } else {
                return Response.ok("").build();
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    // Get NC options
    @GET
    @Path("/nc-options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNcOptions(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("internal-audit-id") String internalAuditId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching NC options for internal_audit_id: " + internalAuditId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(internalAuditId)) {
                return ErrorHandler.badRequest("Company code and internal audit ID are required", "Missing parameters");
            }

            con = DBConfig.getConnection();
            String query = "SELECT nc_no FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            res = ps.executeQuery();

            List<NcOption> options = new ArrayList<>();
            while (res.next()) {
                NcOption option = new NcOption();
                option.ncNo = res.getString("nc_no");
                options.add(option);
            }

            return Response.ok(new NcOptionsResponse(true, options)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    // Get clause details
    @GET
    @Path("/clause-details")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getClauseDetails(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("internal-audit-id") String internalAuditId,
            @QueryParam("nc-no") String ncNo) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching clause details for nc_no: " + ncNo + " and internal_audit_id: " + internalAuditId);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(internalAuditId) || !ValidationUtil.isNotEmpty(ncNo)) {
                return ErrorHandler.badRequest("Company code, internal audit ID, and NC No are required", "Missing parameters");
            }

            con = DBConfig.getConnection();
            String query = "SELECT clause_no, desc_type, evidence, comment FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ? AND nc_no = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.setString(2, ncNo);
            res = ps.executeQuery();

            if (res.next()) {
                String response = (res.getString("clause_no") != null ? res.getString("clause_no") : "") + "$" +
                        (res.getString("desc_type") != null ? res.getString("desc_type") : "") + "$" +
                        (res.getString("evidence") != null ? res.getString("evidence") : "") + "$" +
                        (res.getString("comment") != null ? res.getString("comment") : "");
                return Response.ok(response).build();
            } else {
                return Response.ok("").build();
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    // Get process areas
    @GET
    @Path("/process-areas")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProcessAreas(@HeaderParam("company-code") String companyCode,@QueryParam("internal-audit-id") String internalAuditId) {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching process areas for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode)) {
                return ErrorHandler.badRequest("Company code is required", "Missing company_code header");
            }

            con = DBConfig.getConnection();
            String query = "SELECT process_area FROM " + companyCode + "_nonconformities WHERE internal_audit_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            res = ps.executeQuery();

            List<Map<String,String>> options = new ArrayList<>();

            while (res.next()) {
                Map<String, String> option = new HashMap<>();
                option.put("ProcessArea", res.getString("process_area"));
                options.add(option);
            }

            return Response.ok(new ProcessAreasResponse(true, options)).build();

        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateNonconformity(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId,
            @PathParam("id") String id,
            Map<String, String> payload) {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId) || !ValidationUtil.isNotEmpty(id)) {
                return ErrorHandler.badRequest("Missing required headers or parameters", "company-code, user-id, and id are required");
            }

            con = DBConfig.getConnection();

            // Check if ID exists
            String query = "SELECT id FROM " + companyCode + "_nonconformities WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, id);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return ErrorHandler.badRequest("Nonconformity not found", "ID: " + id);
            }
            closeResources(rs, ps, null);

            // Update statement
            query = "UPDATE " + companyCode + "_nonconformities SET nc_no = ?, internal_audit_id = ?, clause_no = ?, site = ?, process_area = ?, auditor = ?, type = ?, status = ?, correction = ?, correction_when = ?, correction_whom = ?, root_cause = ?, corrective = ?, corrective_when = ?, corrective_whom = ? WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, payload.get("nc_no"));
            ps.setString(2, payload.get("internal_audit_id"));
            ps.setString(3, payload.get("clause_no"));
            ps.setString(4, payload.get("site"));
            ps.setString(5, payload.get("process_area"));
            ps.setString(6, payload.get("auditor"));
            ps.setString(7, payload.get("type"));
            ps.setString(8, "Draft");
            ps.setString(9, payload.get("correction"));
            ps.setString(10, payload.get("correction_when"));
            ps.setString(11, payload.get("correction_whom"));
            ps.setString(12, payload.get("root_cause"));
            ps.setString(13, payload.get("corrective"));
            ps.setString(14, payload.get("corrective_when"));
            ps.setString(15, payload.get("corrective_whom"));
            ps.setString(16, id);
            ps.executeUpdate();

            return Response.ok(new ActionResponse(true, "Nonconformity updated")).build();

        } catch (Exception e) {
            return ErrorHandler.serverError("Server error", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }


    // Get auditors
    @GET
    @Path("/auditors")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditors(@HeaderParam("company-code") String companyCode) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching auditors for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode)) {
                return ErrorHandler.badRequest("Company code is required", "Missing company_code header");
            }

            con = DBConfig.getConnection();
            String query = "SELECT DISTINCT auditor_name FROM " + companyCode + "_audit_plan_auditors";
            ps = con.prepareStatement(query);
            res = ps.executeQuery();

            List<String> auditors = new ArrayList<>();
            while (res.next()) {
                String auditor = res.getString("auditor_name");
                if (auditor != null) {
                    auditors.add(auditor);
                }
            }

            return Response.ok(new AuditorsResponse(true, auditors)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    // Get site details
    @GET
    @Path("/site")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSite(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("actual-company-code") String actualCompanyCode,
            @QueryParam("login-type") String loginType,
            @QueryParam("parent-company-code") String parentCompanyCode) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Fetching site details for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(actualCompanyCode) || !ValidationUtil.isNotEmpty(loginType)) {
                return ErrorHandler.badRequest("Company code, actual company code, and login type are required", "Missing parameters");
            }

            con = DBConfig.getConnection();
            String tablePrefix = "";
            if (loginType.equalsIgnoreCase("Certification body Client")) {
                tablePrefix = "CB_" + parentCompanyCode + "_";
            } else if (loginType.equalsIgnoreCase("Consultant Client")) {
                tablePrefix = "CS_" + parentCompanyCode + "_";
            } else if (loginType.equalsIgnoreCase("CPA Client")) {
                tablePrefix = "CPA_" + parentCompanyCode + "_";
            } else if (loginType.equalsIgnoreCase("Client Login")) {
                tablePrefix = "";
            } else {
                return ErrorHandler.badRequest("Invalid login type", "Login type: " + loginType);
            }

            String query = "SELECT street1, street2, village, pincode FROM " + tablePrefix + "company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res = ps.executeQuery();

            if (res.next()) {
                SiteDetails details = new SiteDetails();
                StringBuilder site = new StringBuilder();
                if (res.getString("street1") != null) site.append(res.getString("street1"));
                if (res.getString("street2") != null) site.append(res.getString("street2"));
                if (res.getString("village") != null) site.append("-").append(res.getString("village"));
                if (res.getString("pincode") != null) site.append("-").append(res.getString("pincode"));
                details.site = site.toString();
                return Response.ok(new SiteResponse(true, details)).build();
            } else {
                return Response.ok(new SiteResponse(true, new SiteDetails())).build();
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("Unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    private void closeResources(ResultSet res, PreparedStatement ps, Connection con) {
        try {
            if (res != null) res.close();
            if (ps != null) ps.close();
            if (con != null) con.close();
        } catch (SQLException e) {
            logger.warning("Error closing resources: " + e.getMessage());
        }
    }
}