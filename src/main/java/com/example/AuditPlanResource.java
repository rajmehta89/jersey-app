package com.example;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import com.example.util.DBConfig;
import com.example.util.ErrorHandler;

@Path("/auditplan")
public class AuditPlanResource {
    private static final Logger logger = Logger.getLogger(AuditPlanResource.class.getName());

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditPlans(
            @HeaderParam("company_code") String companyCode,
            @HeaderParam("user_id") String userId) {

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            // Validate input parameters
            if (companyCode == null || companyCode.isEmpty() ||
                    userId == null || userId.isEmpty()) {
                return ErrorHandler.badRequest("Missing required headers",
                        "company_code and user_id headers are required");
            }

            con = DBConfig.getConnection();

            String moduleId = getModuleId(con, companyCode);

            System.out.println("Module ID: " + moduleId);

            if (moduleId == null) {
                return ErrorHandler.notFound("Module not found",
                        "AUDIT PLAN module not configured for this company");
            }

            // Check user access
            if (!hasAccess(con, companyCode, userId, moduleId, "view_access")) {
                return ErrorHandler.unauthorized("Access denied",
                        "User doesn't have view access to AUDIT PLAN module");
            }

            // Get audit plans list
            List<Map<String, Object>> auditPlans = getAuditPlanList(con, companyCode);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", auditPlans);

            return Response.ok(response).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("An unexpected error occurred", e);
        } finally {
            closeResources(res, ps, con);
        }
    }

    @POST
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAuditPlan(
            @HeaderParam("company_code") String companyCode,
            @HeaderParam("user_id") String userId,
            Map<String, Object> requestData) {

        Connection con = null;
        PreparedStatement ps = null;

        try {
            // Validate input parameters
            if (companyCode == null || companyCode.isEmpty() ||
                    userId == null || userId.isEmpty()) {
                return ErrorHandler.badRequest("Missing required headers",
                        "company_code and user_id headers are required");
            }

            if (requestData == null || !requestData.containsKey("id")) {
                return ErrorHandler.badRequest("Missing audit plan ID",
                        "id parameter is required in request body");
            }

            String auditPlanId = requestData.get("id").toString();
            con = DBConfig.getConnection();

            // Get module ID for AUDIT PLAN
            String moduleId = getModuleId(con, companyCode);
            if (moduleId == null) {
                return ErrorHandler.notFound("Module not found",
                        "AUDIT PLAN module not configured for this company");
            }

            // Check user delete access
            if (!hasAccess(con, companyCode, userId, moduleId, "delete_access")) {
                return ErrorHandler.unauthorized("Access denied",
                        "User doesn't have delete access to AUDIT PLAN module");
            }

            // Check if audit plan exists and is in Draft status
            if (!isDeletable(con, companyCode, auditPlanId)) {
                return ErrorHandler.badRequest("Cannot delete",
                        "Audit plan is not in Draft status or doesn't exist");
            }

            // Delete audit plan (using transaction)
            con.setAutoCommit(false);
            try {
                // Delete standards first
                String deleteStandards = "DELETE FROM " + companyCode +
                        "_audit_plan_standard WHERE audit_plan_id = ?";
                ps = con.prepareStatement(deleteStandards);
                ps.setString(1, auditPlanId);
                ps.executeUpdate();
                ps.close();

                // Delete details
                String deleteDetails = "DELETE FROM " + companyCode +
                        "_audit_plan_detail WHERE audit_plan_id = ?";
                ps = con.prepareStatement(deleteDetails);
                ps.setString(1, auditPlanId);
                ps.executeUpdate();
                ps.close();

                // Delete main record
                String deletePlan = "DELETE FROM " + companyCode +
                        "_audit_plan WHERE id = ?";
                ps = con.prepareStatement(deletePlan);
                ps.setString(1, auditPlanId);
                int rowsDeleted = ps.executeUpdate();

                if (rowsDeleted > 0) {
                    con.commit();
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Audit plan deleted successfully");
                    return Response.ok(response).build();
                } else {
                    con.rollback();
                    return ErrorHandler.notFound("Audit plan not found",
                            "No audit plan found with ID: " + auditPlanId);
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("An unexpected error occurred", e);
        } finally {
            closeResources(null, ps, con);
        }
    }

    // Helper methods
    private String getModuleId(Connection con, String companyCode) throws SQLException {
        String query = "SELECT module_id FROM " + companyCode +
                "_module_master WHERE module_name = 'AUDIT PLAN'";
        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString("module_id") : null;
        }
    }

    private boolean hasAccess(Connection con, String companyCode, String userId,
                              String moduleId, String accessType) throws SQLException {
        String query = "SELECT " + accessType + " FROM " + companyCode +
                "_user_module_access WHERE user_id = ? AND module_id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(accessType).equalsIgnoreCase("Yes");
            }
        }
    }

    private List<Map<String, Object>> getAuditPlanList(Connection con, String companyCode)
            throws SQLException {
        List<Map<String, Object>> auditPlans = new ArrayList<>();

        String query = "SELECT id, audit_no, audit_date, audit_start_date, audit_end_date, status " +
                "FROM " + companyCode + "_audit_plan ORDER BY audit_date DESC";

        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> plan = new HashMap<>();
                plan.put("id", rs.getString("id"));
                plan.put("audit_no", rs.getString("audit_no"));
                plan.put("audit_date", rs.getString("audit_date"));
                plan.put("audit_start_date", rs.getString("audit_start_date"));
                plan.put("audit_end_date", rs.getString("audit_end_date"));
                plan.put("status", rs.getString("status"));

                // Get standards for this audit plan
                List<Map<String, String>> standards = new ArrayList<>();
                String standardsQuery = "SELECT standard_name, status FROM " + companyCode +
                        "_audit_plan_standard WHERE audit_plan_id = ?";
                try (PreparedStatement ps2 = con.prepareStatement(standardsQuery)) {
                    ps2.setString(1, rs.getString("id"));
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        while (rs2.next()) {
                            Map<String, String> standard = new HashMap<>();
                            standard.put("standard_name", rs2.getString("standard_name"));
                            standard.put("status", rs2.getString("status"));
                            standards.add(standard);
                        }
                    }
                }
                plan.put("standards", standards);

                // Get details for this audit plan
                List<Map<String, String>> details = new ArrayList<>();
                String detailsQuery = "SELECT department, audit_date FROM " + companyCode +
                        "_audit_plan_detail WHERE audit_plan_id = ?";
                try (PreparedStatement ps3 = con.prepareStatement(detailsQuery)) {
                    ps3.setString(1, rs.getString("id"));
                    try (ResultSet rs3 = ps3.executeQuery()) {
                        while (rs3.next()) {
                            Map<String, String> detail = new HashMap<>();
                            detail.put("department", rs3.getString("department"));
                            detail.put("audit_date", rs3.getString("audit_date"));
                            details.add(detail);
                        }
                    }
                }
                plan.put("details", details);

                auditPlans.add(plan);
            }
        }

        return auditPlans;
    }

    private boolean isDeletable(Connection con, String companyCode, String auditPlanId)
            throws SQLException {
        String query = "SELECT status FROM " + companyCode +
                "_audit_plan WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, auditPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("status").equalsIgnoreCase("Draft");
            }
        }
    }

    private void closeResources(ResultSet rs, PreparedStatement ps, Connection con) {
        try {
            if (rs != null) rs.close();
            if (ps != null) ps.close();
            if (con != null) con.close();
        } catch (SQLException e) {
            logger.warning("Error closing resources: " + e.getMessage());
        }
    }
}