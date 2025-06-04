package com.example;

import com.example.util.DBConfig;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("/internalaudit")
public class InternalAuditResource {

    private static final Logger logger = Logger.getLogger(InternalAuditResource.class.getName());
    private static final Pattern COMPANY_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public static class InternalAuditRequest {
        public String auditPlanId;
        public String standardName;
        public int stdId;
        public String auditDate;
        public String status;
        public String scope;
        public List<AuditDetail> details;

    }

    public static class AuditDetail {
        public String clauseNo;
        public String ncNo;
        public String descType;
        public String comment;
        public String status;
    }

    private static final String SMETA_VERSION = "6.1";
    private static final String SMETA_DATE = "March 2019";

    public static class InternalAuditResponse {
        public boolean success;
        public String message;
        public Map<String, Object> data;

        public InternalAuditResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.data = new HashMap<>();
        }
    }

    public static class StatusUpdateRequest {
        public String status;
    }

    public static class UserAccess {
        public boolean addAccess;
        public boolean deleteAccess;
        public boolean approveAccess;
    }
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInternalAudit(@PathParam("id") String internalAuditId,
                                     @HeaderParam("company-code") String companyCode,
                                     @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            InternalAuditResponse response = new InternalAuditResponse(true, "Data retrieved successfully");
            Map<String, Object> internalAuditData = new HashMap<>();

            // Fetch standards for dropdowns
            List<Map<String, String>> standards = new ArrayList<>();
            String query = "SELECT company_id, std_name FROM company_std_detail " +
                    "WHERE company_id = (SELECT company_id FROM " + companyCode + "_user_master WHERE emp_id = ?) " +
                    "AND status = 'Active'";
            ps = con.prepareStatement(query);
            ps.setString(1, employeeId);
            rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> standard = new HashMap<>();
                standard.put("id", rs.getString("company_id"));
                standard.put("name", rs.getString("std_name"));
                standards.add(standard);
            }
            internalAuditData.put("standards", standards);

            // Fetch audit plans for dropdowns
            List<Map<String, String>> auditPlans = new ArrayList<>();
            query = "SELECT id, audit_no, audit_date FROM " + companyCode + "_audit_plan " +
                    "WHERE status = 'Approve'";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> plan = new HashMap<>();
                plan.put("id", rs.getString("id"));
                plan.put("auditNo", rs.getString("audit_no"));
                plan.put("auditDate", rs.getString("audit_date"));
                auditPlans.add(plan);
            }
            internalAuditData.put("auditPlans", auditPlans);

            if (!"0".equals(internalAuditId)) {
                // Fetch specific internal audit with audit plan details
                query = "SELECT ia.*, ap.audit_no as plan_audit_no " +
                        "FROM " + companyCode + "_internal_audit_master ia " +
                        "LEFT JOIN " + companyCode + "_audit_plan ap ON ia.audit_plan_id = ap.id " +
                        "WHERE ia.id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, internalAuditId);
                rs = ps.executeQuery();

                if (rs.next()) {
                    internalAuditData.put("id", rs.getString("id"));
                    internalAuditData.put("internalAuditNo", rs.getString("audit_no"));
                    internalAuditData.put("auditPlanId", rs.getString("audit_plan_id"));
                    internalAuditData.put("standardName", rs.getString("std_name"));
                    internalAuditData.put("auditDate", rs.getString("audit_date"));
                    internalAuditData.put("status", rs.getString("status"));
                    internalAuditData.put("planAuditNo", rs.getString("scope"));
                } else {
                    return Response.status(Status.NOT_FOUND)
                            .entity(new InternalAuditResponse(false, "Internal audit not found"))
                            .build();
                }

                // Fetch audit details with standard information
                List<Map<String, String>> details = new ArrayList<>();
                query = "SELECT iad.*, csd.std_name " +
                        "FROM " + companyCode + "_internal_audit_detail iad " +
                        "LEFT JOIN company_std_detail csd ON iad.std_id = csd.company_id " +
                        "WHERE iad.internal_audit_id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, internalAuditId);
                rs = ps.executeQuery();

                while (rs.next()) {
                    Map<String, String> detail = new HashMap<>();
                    detail.put("id", rs.getString("id"));
                    detail.put("internalAuditId", rs.getString("internal_audit_id")); // Changed from std_id
                    detail.put("stdName", rs.getString("std_name"));
                    detail.put("ncNo", rs.getString("nc_no"));
                    detail.put("comment", rs.getString("comment"));
                    detail.put("status", rs.getString("status"));
                    detail.put("workplaceRequirement", rs.getString("workplace_requirment"));
                    detail.put("subCategory", rs.getString("sub_category"));
                    detail.put("issueTitle", rs.getString("issue_title"));
                    detail.put("requirement", rs.getString("requirement"));
                    detail.put("guidance", rs.getString("guidance"));
                    detail.put("action", rs.getString("action"));
                    detail.put("evidence", rs.getString("evidence"));
                    details.add(detail);
                }
                internalAuditData.put("details", details);
            }

            response.data = internalAuditData;
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createInternalAudit(Map<String, Object> request,
                                        @HeaderParam("company-code") String companyCode,
                                        @HeaderParam("employee-id") String employeeId,
                                        @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Invalid company code"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            con.setAutoCommit(false);

            String standardName = (String) request.get("standardName");
            String auditPlanId = (String) request.get("auditPlanId");
            String auditNo = (String) request.get("auditNo");
            String auditDate = (String) request.get("auditDate");
            String scope = (String) request.get("scope");

            // Get standard ID
            String stdId = "";
            String query = "SELECT id FROM standard_master WHERE std_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, standardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdId = rs.getString("id");
                    } else {
                        con.rollback();
                        return Response.status(Status.BAD_REQUEST)
                                .entity(new InternalAuditResponse(false, "Invalid standard name"))
                                .build();
                    }
                }
            }

            // Check for duplicate audit
            query = "SELECT * FROM " + companyCode + "_internal_audit_master WHERE audit_no = ? AND std_id = ? AND audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditNo);
                ps.setString(2, stdId);
                ps.setString(3, auditPlanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        con.rollback();
                        return Response.status(Status.BAD_REQUEST)
                                .entity(new InternalAuditResponse(false, "Duplicate audit found"))
                                .build();
                    }
                }
            }

            // Get next internal audit ID
            int maxInternalAuditId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_internal_audit_master";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxInternalAuditId = rs.getInt("max_id");
                }
            }
            maxInternalAuditId++;

            // Insert internal audit
            query = "INSERT INTO " + companyCode + "_internal_audit_master " +
                    "(id, audit_plan_id, audit_no, std_id, std_name, audit_date, status, scope) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, maxInternalAuditId);
                ps.setString(2, auditPlanId);
                ps.setString(3, auditNo);
                ps.setString(4, stdId);
                ps.setString(5, standardName);
                ps.setString(6, auditDate);
                ps.setString(7, "In Process");
                ps.setString(8, scope);
                ps.executeUpdate();
            }

            // Update audit plan standard status
            query = "UPDATE " + companyCode + "_audit_plan_standard SET status = 'In Process' " +
                    "WHERE audit_plan_id = ? AND standard_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditPlanId);
                ps.setString(2, standardName);
                ps.executeUpdate();
            }

            // Check remaining standards
            int stdCount = 0;
            query = "SELECT COUNT(id) as std_count FROM " + companyCode + "_audit_plan_standard " +
                    "WHERE status = '' AND audit_plan_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditPlanId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdCount = rs.getInt("std_count");
                    }
                }
            }

            // Update audit plan status
            if (stdCount == 0) {
                query = "UPDATE " + companyCode + "_audit_plan SET status = 'In Process' WHERE id = ?";
            } else {
                query = "UPDATE " + companyCode + "_audit_plan SET status = 'Partial Process' WHERE id = ?";
            }
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, auditPlanId);
                ps.executeUpdate();
            }

            // Log the action
            int maxLogId = 0;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayDate = LocalDateTime.now().format(dtf);

            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    maxLogId = rs.getInt("max_id");
                }
            }
            maxLogId++;

            query = "INSERT INTO " + companyCode + "_log_master " +
                    "(id, fired_date, fired_by, status, module_name, module_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement logStmt = con.prepareStatement(query)) {
                logStmt.setInt(1, maxLogId);
                logStmt.setString(2, todayDate);
                logStmt.setString(3, employeeName);
                logStmt.setString(4, "Create");
                logStmt.setString(5, "Internal Audit");
                logStmt.setString(6, String.valueOf(maxInternalAuditId));
                logStmt.executeUpdate();
            }

            con.commit();


            // Create response with redirect information
            InternalAuditResponse response = new InternalAuditResponse(true, "Internal audit created successfully");
            response.data.put("internalAuditId", maxInternalAuditId);
            response.data.put("standardName", standardName);
            response.data.put("redirectUrl", "/APITest/ViewInternalAuditSystem?intr_audit_id=" + maxInternalAuditId + "&standard_name=" + standardName);

            // Add additional response headers for redirect
            return Response.status(Status.CREATED)
                    .entity(response)
                    .header("X-Redirect-URL", "/APITest/ViewInternalAuditSystem?intr_audit_id=" + maxInternalAuditId + "&standard_name=" + standardName)
                    .build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        }
    }

    // 1. Get Clause Details
    @GET
    @Path("/clause-details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClauseDetails(
            @QueryParam("std_name") String standardName,
            @QueryParam("clause_no") String clauseNo,
            @HeaderParam("company-code") String companyCode) {

        try (Connection con = DBConfig.getConnection()) {
            // Get standard ID
            String stdId = "";
            String query = "SELECT id FROM standard_master WHERE std_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, standardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdId = rs.getString("id");
                    }
                }
            }

            // Get clause details
            List<Map<String, Object>> clauseDetailsList = new ArrayList<>();
            query = "SELECT * FROM clause_master WHERE std_id= ? ";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, stdId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> clauseDetails = new HashMap<>();
                        clauseDetails.put("name", rs.getString("name"));
                        clauseDetails.put("description", rs.getString("description"));
                        clauseDetails.put("requirement", rs.getString("requirement"));
                        clauseDetails.put("guidance", rs.getString("guidance"));
                        clauseDetails.put("clauseRequiredDocument", rs.getString("clause_required_document"));
                        clauseDetailsList.add(clauseDetails);
                    }
                }
            }

            return Response.ok(clauseDetailsList).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Database error occurred: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/{auditId}/details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditDetails(
            @PathParam("auditId") String auditId,
            @HeaderParam("company-code") String companyCode) {

        Map<String, Object> response = new HashMap<>();

        // Validate required parameters
        if (companyCode == null || companyCode.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Company code is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        if (auditId == null || auditId.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Audit ID is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        try (Connection conn = DBConfig.getConnection()) {
            // Get audit details - matching the exact query from JSP
            String sql = "SELECT * FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ?";

            List<Map<String, String>> details = new ArrayList<>();
            boolean isFirstRow = true;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, auditId);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, String> detail = new HashMap<>();
                    detail.put("clauseNo", rs.getString("clause_no"));
                    detail.put("ncNo", rs.getString("nc_no"));
                    detail.put("type", rs.getString("desc_type"));
                    detail.put("description", rs.getString("comment"));
                    detail.put("status", rs.getString("status"));
                    details.add(detail);
                }
            }

            response.put("success", true);
            response.put("message", "Audit details retrieved successfully");
            response.put("data", details);
            return Response.ok(response).build();

        } catch (SQLException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Database error occurred");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }

    @GET
    @Path("/documents")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDocuments(@HeaderParam("company-code") String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "message", "Company code is required"))
                    .build();
        }

        List<Map<String, Object>> documents = new ArrayList<>();

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT id, internal_audit_id, std_id, clause_no, file_name " +
                    "FROM " + companyCode + "_internal_audit_document " +
                    "ORDER BY id";

            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", rs.getInt("id"));
                    doc.put("internalAuditId", rs.getObject("internal_audit_id"));
                    doc.put("stdId", rs.getObject("std_id"));
                    doc.put("clauseNo", rs.getString("clause_no"));
                    doc.put("fileName", rs.getString("file_name"));
                    documents.add(doc);
                }
            }

            return Response.ok()
                    .entity(Map.of(
                            "success", true,
                            "data", documents
                    ))
                    .build();

        } catch (SQLException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "success", false,
                            "message", "Database error occurred",
                            "error", e.getMessage()
                    ))
                    .build();
        }
    }

    @GET
    @Path("/standard-documents")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStandardDocuments(@HeaderParam("company-code") String companyCode) {
        // Validate company code
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "success", false,
                            "message", "Company code is required"
                    ))
                    .build();
        }

        List<Map<String, Object>> documents = new ArrayList<>();

        try (Connection con = DBConfig.getConnection()) {
            String query = "SELECT id, intr_aud_id, std_id, clause_no, file_name " +
                    "FROM " + companyCode + "_internal_audit_std_doc " +
                    "ORDER BY id";

            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("id", rs.getInt("id"));
                    doc.put("intrAudId", rs.getObject("intr_aud_id"));
                    doc.put("stdId", rs.getObject("std_id"));
                    doc.put("clauseNo", rs.getString("clause_no"));
                    doc.put("fileName", rs.getString("file_name"));
                    documents.add(doc);
                }
            }

            // Successful response
            return Response.ok()
                    .entity(Map.of(
                            "success", true,
                            "data", documents
                    ))
                    .build();

        } catch (SQLException e) {
            // Error response
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "success", false,
                            "message", "Failed to retrieve documents",
                            "error", e.getMessage()
                    ))
                    .build();
        }
    }

    @POST
    @Path("/add-info")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addAuditInfo(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("actual-company-code") String actualCompanyCode,
            Map<String, Object> request) {

        if (employeeId == null || employeeId.trim().isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("success", false, "message", "Unauthorized access"))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            // Validate required fields
            if (request.get("internalAuditId") == null || request.get("clauseNo") == null
                    || request.get("standardName") == null || request.get("descType") == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "message", "Missing required fields"))
                        .build();
            }

            String internalAuditId = (String) request.get("internalAuditId");
            String clauseNo = (String) request.get("clauseNo");
            String standardName = (String) request.get("standardName");
            String descType = (String) request.get("descType");

            // 1. Get audit number
            String auditNo = "";
            String query = "SELECT audit_no FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, internalAuditId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        auditNo = rs.getString("audit_no");
                    }
                }
            }

            // 2. Get standard ID with null check
            String stdId = "";
            query = "SELECT id FROM standard_master WHERE std_name = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, standardName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stdId = rs.getString("id");
                    }
                }
            }

            if (stdId.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("success", false, "message", "Invalid standard name"))
                        .build();
            }

            // 3. Get NC count
            int ncCount = 0;
            query = "SELECT COUNT(id) FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ? AND clause_no = ? AND std_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, internalAuditId);
                ps.setString(2, clauseNo);
                ps.setInt(3, Integer.parseInt(stdId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ncCount = rs.getInt(1);
                    }
                }
            }
            ncCount++;

            // 4. Determine status and NC number
            String status;
            String ncNo;

            if ("MJ NC".equalsIgnoreCase(descType) || "MN NC".equalsIgnoreCase(descType)) {
                status = "Draft";
                ncNo = actualCompanyCode + "-" + auditNo + "-" + clauseNo + "-" + ncCount;
            } else {
                status = "Done";
                ncNo = "";
            }

            // 5. Get next ID
            int nextId = 1;
            query = "SELECT MAX(id) FROM " + companyCode + "_internal_audit_detail";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        nextId = rs.getInt(1) + 1;
                    }
                }
            }

            // 6. Insert audit info
            query = "INSERT INTO " + companyCode + "_internal_audit_detail " +
                    "(id, internal_audit_id, std_id, clause_no, comment, desc_type, status, " +
                    "workplace_requirment, sub_category, issue_title, requirement, guidance, nc_no, " +
                    "action, evidence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, nextId);
                ps.setString(2, internalAuditId);
                ps.setInt(3, Integer.parseInt(stdId));
                ps.setString(4, clauseNo);
                ps.setString(5, (String) request.get("comment"));
                ps.setString(6, descType);
                ps.setString(7, status);
                ps.setString(8, (String) request.get("workplaceReq"));
                ps.setString(9, (String) request.get("subCategory"));
                ps.setString(10, (String) request.get("issueTitle"));
                ps.setString(11, (String) request.get("requirement"));
                ps.setString(12, (String) request.get("guidance"));
                ps.setString(13, ncNo);
                ps.setString(14, (String) request.get("action"));
                ps.setString(15, (String) request.get("evidence"));

                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating audit info failed, no rows affected.");
                }

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("auditInfoId", nextId);
                responseData.put("status", status);
                responseData.put("ncNo", ncNo);

                return Response.ok(Map.of(
                        "success", true,
                        "message", "Audit info added successfully",
                        "data", responseData
                )).build();
            }

        } catch (SQLException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "message", "Database error occurred: " + e.getMessage()))
                    .build();
        } catch (NumberFormatException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "message", "Invalid numeric value in request"))
                    .build();
        }
    }

    @GET
    @Path("/sub-categories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubCategories(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("workplace") String workplace) {

        Map<String, Object> response = new HashMap<>();
        if (companyCode == null || companyCode.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Company code is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        if (workplace == null || workplace.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Workplace requirement is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        try (Connection conn = DBConfig.getConnection()) {
            String sql = "SELECT DISTINCT Subcategory AS label, Subcategory AS value " +
                    "FROM sedex_nonconformities " +
                    "WHERE workplace_requirement = ? " +
                    "ORDER BY Subcategory";

            List<Map<String, String>> subCategories = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, workplace);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, String> subCategory = new HashMap<>();
                    subCategory.put("label", rs.getString("label"));
                    subCategory.put("value", rs.getString("value"));
                    subCategories.add(subCategory);
                }
            }

            response.put("success", true);
            response.put("message", "Sub categories retrieved successfully");
            response.put("data", subCategories);
            return Response.ok(response).build();

        } catch (SQLException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Database error occurred");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }

    @GET
    @Path("/issue-titles")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIssueTitles(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("workplace") String workplace,
            @QueryParam("sub_category") String subCategory) {

        Map<String, Object> response = new HashMap<>();
        if (companyCode == null || companyCode.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Company code is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }
        if (workplace == null || workplace.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Workplace requirement is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }
        if (subCategory == null || subCategory.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Sub category is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        try (Connection conn = DBConfig.getConnection()) {
            String sql = "SELECT DISTINCT New_Issue_Title AS label, New_Issue_Title AS value " +
                    "FROM sedex_nonconformities " +
                    "WHERE workplace_requirement = ? AND Subcategory = ? " +
                    "ORDER BY New_Issue_Title";

            List<Map<String, String>> issueTitles = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, workplace);
                pstmt.setString(2, subCategory);
                ResultSet rs = pstmt.executeQuery();

                while (rs.next()) {
                    Map<String, String> title = new HashMap<>();
                    title.put("label", rs.getString("label"));
                    title.put("value", rs.getString("value"));
                    issueTitles.add(title);
                }
            }

            response.put("success", true);
            response.put("message", "Issue titles retrieved successfully");
            response.put("data", issueTitles);
            return Response.ok(response).build();

        } catch (SQLException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Database error occurred");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }

    @GET
    @Path("/issue-details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIssueDetails(
            @HeaderParam("company-code") String companyCode,
            @QueryParam("workplace") String workplace,
            @QueryParam("sub_category") String subCategory,
            @QueryParam("issue_title") String issueTitle) {

        Map<String, Object> response = new HashMap<>();
        if (companyCode == null || companyCode.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Company code is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }
        if (workplace == null || workplace.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Workplace requirement is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }
        if (subCategory == null || subCategory.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Sub category is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }
        if (issueTitle == null || issueTitle.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Issue title is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
        }

        try (Connection conn = DBConfig.getConnection()) {
            String sql = "SELECT verification_method, Recomended_Completion " +
                    "FROM sedex_nonconformities " +
                    "WHERE  workplace_requirement = ? " +
                    "AND Subcategory = ? AND New_Issue_Title = ? LIMIT 1";

            Map<String, String> details = new HashMap<>();

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, workplace);
                pstmt.setString(2, subCategory);
                pstmt.setString(3, issueTitle);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    details.put("verificationMethod", rs.getString("verification_method"));
                    details.put("timeScale", rs.getString("time_scale"));
                }
            }

            response.put("success", true);
            response.put("message", "Issue details retrieved successfully");
            response.put("data", details);
            return Response.ok(response).build();

        } catch (SQLException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Database error occurred");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
        }
    }


    @GET
    @Path("/workplace-requirements")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkplaceRequirements(
            @HeaderParam("company-code") String companyCode) {

        try (Connection con = DBConfig.getConnection()) {
            List<Map<String, String>> requirements = new ArrayList<>();
            String query = "SELECT DISTINCT Workplace_Requirement FROM sedex_nonconformities WHERE id > 0";

            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> requirement = new HashMap<>();
                    String value = rs.getString("Workplace_Requirement");
                    requirement.put("value", value);
                    requirement.put("label", value);
                    requirements.add(requirement);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Workplace requirements retrieved successfully");
            response.put("data", requirements);

            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Database error occurred: " + e.getMessage());

            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
        }
    }



    @GET
    @Path("/view")
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewInternalAudit(
            @QueryParam("intr_audit_id") String internalAuditId,
            @QueryParam("standard_name") String standardName,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName,
            @HeaderParam("use-designation-id") String useDesignationId,
            @HeaderParam("user-department-id") String userDepartmentId) {

        if (!isValidAuth(employeeId, companyCode)) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Authentication required");
            return Response.status(Status.UNAUTHORIZED).entity(error).build();
        }

        try (Connection con = DBConfig.getConnection()) {
            // Get rights ID
            String rightsId = "";
            String query = "SELECT id FROM " + companyCode + "_customer_rights " +
                    "WHERE dept_id = ? AND desig_id = ?";
            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, userDepartmentId);
                ps.setString(2, useDesignationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        rightsId = rs.getString("id");
                    }
                }
            }

            // Get audit data
            List<Map<String, Object>> auditData = new ArrayList<>();
            query = "SELECT crd.*, sm.id as std_id, cm.name as clause_name, " +
                    "mcm.name as main_clause_name " +
                    "FROM " + companyCode + "_customer_rights_detail crd " +
                    "JOIN standard_master sm ON crd.standard_name = sm.std_name " +
                    "LEFT JOIN clause_master cm ON cm.number = crd.clause_no AND cm.std_id = sm.id " +
                    "LEFT JOIN clause_master mcm ON mcm.id = cm.main_clause_id AND mcm.std_id = sm.id " +
                    "WHERE crd.standard_name = ? AND crd.rights_id = ? " +
                    "ORDER BY CAST(crd.clause_no AS UNSIGNED) ASC";

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setString(1, standardName);
                ps.setString(2, rightsId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> audit = new HashMap<>();
                        audit.put("id", rs.getString("id"));
                        audit.put("std_id", rs.getString("std_id"));
                        audit.put("clause_no", rs.getString("clause_no"));
                        audit.put("clause_name", rs.getString("clause_name"));
                        audit.put("main_clause", rs.getString("main_clause_name"));

                        // Get audit details
                        List<Map<String, Object>> details = getAuditDetails(con, companyCode, internalAuditId,
                                rs.getString("std_id"), rs.getString("clause_no"));
                        audit.put("details", details);

                        // Get audit documents
                        List<Map<String, Object>> documents = getAuditDocuments(con, companyCode,
                                rs.getString("std_id"), rs.getString("clause_no"));
                        audit.put("documents", documents);

                        // Get standard documents
                        List<Map<String, Object>> standardDocuments = getStandardDocuments(con, companyCode,
                                rs.getString("std_id"), rs.getString("clause_no"));
                        audit.put("standardDocuments", standardDocuments);

                        auditData.add(audit);
                    }
                }
            }

            // Get user module access rights
            Map<String, String> userRights = getUserModuleAccess(con, companyCode, employeeName);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Internal audit details retrieved successfully");
            responseData.put("auditData", auditData);
            responseData.put("userRights", userRights);
            responseData.put("standardName", standardName);
            responseData.put("internalAuditId", internalAuditId);

            return Response.ok().entity(responseData).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Database error occurred: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }


    private Map<String, String> getUserModuleAccess(Connection con, String companyCode, String employeeName) throws SQLException {
        Map<String, String> rights = new HashMap<>();
        String query = "SELECT uma.add_access, uma.edit_access, uma.delete_access " +
                "FROM " + companyCode + "_user_master um " +
                "JOIN " + companyCode + "_user_module_access uma ON um.user_id = uma.user_id " +
                "JOIN " + companyCode + "_module_master mm ON uma.module_id = mm.module_id " +
                "WHERE um.username = ? AND mm.module_name = 'INTERNAL AUDIT'";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, employeeName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rights.put("add_access", rs.getString("add_access"));
                    rights.put("edit_access", rs.getString("edit_access"));
                    rights.put("delete_access", rs.getString("delete_access"));
                }
            }
        }
        return rights;
    }

    private List<Map<String, Object>> getAuditDetails(Connection con, String companyCode, String internalAuditId, String stdId, String clauseNo) throws SQLException {
        List<Map<String, Object>> details = new ArrayList<>();
        String query = "SELECT * FROM " + companyCode + "_internal_audit_detail " +
                "WHERE internal_audit_id = ? AND std_id = ? AND clause_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, internalAuditId);
            ps.setString(2, stdId);
            ps.setString(3, clauseNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("id", rs.getString("id"));
                    detail.put("descType", rs.getString("desc_type"));
                    detail.put("comment", rs.getString("comment"));
                    details.add(detail);
                }
            }
        }
        return details;
    }

    private List<Map<String, Object>> getAuditDocuments(Connection con, String companyCode, String stdId, String clauseNo) throws SQLException {
        List<Map<String, Object>> documents = new ArrayList<>();
        String query = "SELECT * FROM " + companyCode + "_internal_audit_document " +
                "WHERE std_id = ? AND clause_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, stdId);
            ps.setString(2, clauseNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", rs.getString("id"));
                    doc.put("fileName", rs.getString("file_name"));
                    doc.put("fileUrl", "http://124.123.122.108:8089/APITest/assets/Documents/Internal Audit/" + companyCode + "/" + rs.getString("file_name"));
                    documents.add(doc);
                }
            }
        }
        return documents;
    }

    private List<Map<String, Object>> getStandardDocuments(Connection con, String companyCode, String stdId, String clauseNo) throws SQLException {
        List<Map<String, Object>> documents = new ArrayList<>();
        String query = "SELECT * FROM " + companyCode + "_internal_audit_std_doc " +
                "WHERE std_id = ? AND clause_no = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, stdId);
            ps.setString(2, clauseNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", rs.getString("id"));
                    doc.put("fileName", rs.getString("file_name"));
                    doc.put("fileUrl", "http://124.123.122.108:8089/APITest/assets/Documents/Internal Audit/" + companyCode + "/" + rs.getString("file_name"));
                    documents.add(doc);
                }
            }
        }
        return documents;
    }




    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateInternalAudit(@PathParam("id") String internalAuditId,
                                        InternalAuditRequest request,
                                        @HeaderParam("company-code") String companyCode,
                                        @HeaderParam("employee-id") String employeeId,
                                        @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Invalid company code"))
                    .build();
        }

        if (!validateInternalAuditRequest(request)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Invalid internal audit data"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Get audit_no from audit plan
            String auditNo = "";
            String query = "SELECT audit_no FROM " + companyCode + "_audit_plan WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.auditPlanId);
            rs = ps.executeQuery();
            if (rs.next()) {
                auditNo = rs.getString("audit_no");
            } else {
                con.rollback();
                return Response.status(Status.BAD_REQUEST)
                        .entity(new InternalAuditResponse(false, "Invalid audit plan ID"))
                        .build();
            }

            query = "UPDATE " + companyCode + "_internal_audit_master SET " +
                    "audit_plan_id = ?, audit_no = ?, std_name = ?, audit_date = ?, status = ? " +
                    "WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.auditPlanId);
            ps.setString(2, auditNo);
            ps.setString(3, request.standardName);
            ps.setString(4, request.auditDate);
            ps.setString(5, request.status);
            ps.setString(6, internalAuditId);
            int rowsUpdated = ps.executeUpdate();

            if (rowsUpdated == 0) {
                con.rollback();
                return Response.status(Status.NOT_FOUND)
                        .entity(new InternalAuditResponse(false, "Internal audit not found"))
                        .build();
            }

            query = "DELETE FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.executeUpdate();

            int maxDetailId = 0;
            query = "SELECT MAX(id) as max_id FROM " + companyCode + "_internal_audit_detail";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                maxDetailId = rs.getInt("max_id");
            }

            if (request.details != null && !request.details.isEmpty()) {
                query = "INSERT INTO " + companyCode + "_internal_audit_detail " +
                        "(id, internal_audit_id, clause_no, nc_no, desc_type, comment, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";
                ps = con.prepareStatement(query);
                for (AuditDetail detail : request.details) {
                    maxDetailId++;
                    ps.setInt(1, maxDetailId);
                    ps.setString(2, internalAuditId);
                    ps.setString(3, detail.clauseNo);
                    ps.setString(4, detail.ncNo);
                    ps.setString(5, detail.descType);
                    ps.setString(6, detail.comment);
                    ps.setString(7, detail.status);
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
            ps.setString(5, "Internal Audit");
            ps.setString(6, internalAuditId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new InternalAuditResponse(true, "Internal audit updated successfully")).build();

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
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllInternalAudits(@HeaderParam("company-code") String companyCode,
                                         @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        if (!isValidCompanyCode(companyCode)) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Invalid company code"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            InternalAuditResponse response = new InternalAuditResponse(true, "Internal audits retrieved successfully");
            List<Map<String, Object>> internalAudits = new ArrayList<>();

            String query = "SELECT * FROM " + companyCode + "_internal_audit_master ORDER BY id DESC";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> audit = new HashMap<>();
                audit.put("id", rs.getString("id"));
                audit.put("audit_plan_id", rs.getString("audit_plan_id"));
                audit.put("audit_no", rs.getString("audit_no"));
                audit.put("std_id", rs.getString("std_id"));
                audit.put("std_name", rs.getString("std_name"));
                audit.put("audit_date", rs.getString("audit_date"));
                audit.put("status", rs.getString("status"));
                audit.put("scope", rs.getString("scope"));

                // Fetch audit details
                List<Map<String, String>> details = new ArrayList<>();
                String detailQuery = "SELECT * FROM " + companyCode + "_internal_audit_detail WHERE internal_audit_id = ?";
                PreparedStatement detailPs = con.prepareStatement(detailQuery);
                detailPs.setString(1, rs.getString("id"));
                ResultSet detailRs = detailPs.executeQuery();
                while (detailRs.next()) {
                    Map<String, String> detail = new HashMap<>();
                    detail.put("id", detailRs.getString("id"));
                    detail.put("internal_audit_id", detailRs.getString("internal_audit_id"));
                    detail.put("std_id", detailRs.getString("std_id"));
                    detail.put("clause_no", detailRs.getString("clause_no"));
                    detail.put("comment", detailRs.getString("comment"));
                    detail.put("desc_type", detailRs.getString("desc_type"));
                    detail.put("status", detailRs.getString("status"));
                    detail.put("workplace_requirment", detailRs.getString("workplace_requirment"));
                    detail.put("sub_category", detailRs.getString("sub_category"));
                    detail.put("issue_title", detailRs.getString("issue_title"));
                    detail.put("requirement", detailRs.getString("requirement"));
                    detail.put("guidance", detailRs.getString("guidance"));
                    detail.put("nc_no", detailRs.getString("nc_no"));
                    detail.put("action", detailRs.getString("action"));
                    detail.put("evidence", detailRs.getString("evidence"));
                    details.add(detail);
                }

                audit.put("details", details);

                detailRs.close();
                detailPs.close();

                internalAudits.add(audit);
            }

            response.data.put("data", internalAudits);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteInternalAudit(
            @PathParam("id") String internalAuditId,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @HeaderParam("employee-name") String employeeName) {

        // Validate inputs
        if (employeeId == null || employeeId.trim().isEmpty() ||
                companyCode == null || companyCode.trim().isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("success", false, "message", "Authentication required"))
                    .build();
        }

        if (internalAuditId == null || internalAuditId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("success", false, "message", "Internal audit ID is required"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false); // Start transaction

            // 1. Get audit details
            String auditPlanId = null;
            String stdName = null;
            String query = "SELECT audit_plan_id, std_name FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("success", false, "message", "Internal audit not found"))
                        .build();
            }
            auditPlanId = rs.getString("audit_plan_id");
            stdName = rs.getString("std_name");
            closeResources(rs, ps,con);

            // 2. Update audit plan standard status
            query = "UPDATE " + companyCode + "_audit_plan_standard SET status = '' WHERE audit_plan_id = ? AND standard_name = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.setString(2, stdName);
            ps.executeUpdate();
            closeResources(null, ps,con);

            // 3. Check remaining standards and update audit plan status
            int stdCount = 0;
            query = "SELECT COUNT(id) FROM " + companyCode + "_audit_plan_standard WHERE status = 'In Process' AND audit_plan_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            rs = ps.executeQuery();
            if (rs.next()) {
                stdCount = rs.getInt(1);
            }
            closeResources(rs, ps,con);

            query = stdCount > 0
                    ? "UPDATE " + companyCode + "_audit_plan SET status = 'Partial Process' WHERE id = ?"
                    : "UPDATE " + companyCode + "_audit_plan SET status = 'Approve' WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, auditPlanId);
            ps.executeUpdate();
            closeResources(null, ps,con);

            // 4. Delete internal audit records
            query = "DELETE FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.executeUpdate();
            closeResources(null, ps,con);

            query = "DELETE FROM " + companyCode + "_log_master WHERE module_name = 'Internal Audit' AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.executeUpdate();
            closeResources(null, ps,con);

            // 5. Log the deletion
            int logId = 1;
            query = "SELECT MAX(id) FROM " + companyCode + "_log_master";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();
            if (rs.next()) {
                logId = rs.getInt(1) + 1;
            }
            closeResources(rs, ps,con);

            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, logId);
            ps.setString(2, employeeName);
            ps.setString(3, "Delete");
            ps.setString(4, "Internal Audit");
            ps.setString(5, internalAuditId);
            ps.executeUpdate();

            con.commit(); // Commit transaction

            return Response.ok()
                    .entity(Map.of("success", true, "message", "Internal audit deleted successfully"))
                    .build();

        } catch (SQLException e) {
            try {
                if (con != null) con.rollback();
            } catch (SQLException ex) {
                e.addSuppressed(ex);
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "message", "Database error: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    private void closeResources(ResultSet rs, Statement stmt, Connection con) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (con != null) con.close();
        } catch (SQLException e) {
            // Log error if needed
        }
    }

    @GET
    @Path("/user-access")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserAccess(@HeaderParam("company-code") String companyCode,
                                  @HeaderParam("employee-id") String employeeId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            InternalAuditResponse response = new InternalAuditResponse(true, "User access retrieved successfully");
            UserAccess access = new UserAccess();

            // Get module ID for Internal Audit
            String query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'INTERNAL AUDIT'";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return Response.status(Status.NOT_FOUND)
                        .entity(new InternalAuditResponse(false, "Module not found"))
                        .build();
            }

            String moduleId = rs.getString("module_id");

            // Get user access rights
            query = "SELECT add_access, delete_access, approve_access FROM " + companyCode +
                    "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, employeeId);
            ps.setString(2, moduleId);
            rs = ps.executeQuery();

            if (rs.next()) {
                access.addAccess = !"No".equalsIgnoreCase(rs.getString("add_access"));
                access.deleteAccess = !"No".equalsIgnoreCase(rs.getString("delete_access"));
                access.approveAccess = !"No".equalsIgnoreCase(rs.getString("approve_access"));
            }

            response.data.put("access", access);
            return Response.ok(response).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity(new InternalAuditResponse(false, "Database error occurred"))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }


    @GET
    @Path("/export")
    @Produces(MediaType.TEXT_HTML)
    public Response exportInternalAuditHtml(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @QueryParam("intr_audit_id") String internalAuditId,
            @QueryParam("standard_name") String standardName) {

        // Validate inputs
        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("<html><body><h1>Error: Authentication required</h1></body></html>")
                    .build();
        }

        if (internalAuditId == null || internalAuditId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("<html><body><h1>Error: Internal audit ID is required</h1></body></html>")
                    .build();
        }

        Connection con = null;
        try {
            con = DBConfig.getConnection();

            // 1. Get all report data
            Map<String, Object> reportData = gatherReportData(con, companyCode, internalAuditId, standardName);
            if (reportData == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("<html><body><h1>Error: Audit data not found</h1></body></html>")
                        .build();
            }

            // 2. Generate HTML content
            String htmlContent = generateHtmlReport(reportData);

            // 3. Return HTML response with download headers
            return Response.ok(htmlContent)
                    .header("Content-Disposition",
                            "attachment; filename=\"Audit_Report_" + reportData.get("auditNo") + ".html\"")
                    .build();

        } catch (SQLException e) {
//            logger.error("Database error during export: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error: Database error occurred</h1></body></html>")
                    .build();
        } finally {
            DBConfig.closeConnection(con);
        }
    }

    private Map<String, Object> gatherReportData(Connection con, String companyCode,
                                                 String internalAuditId, String standardName) throws SQLException {
        Map<String, Object> reportData = new HashMap<>();

        // 1. Company details
        String companyQuery = "SELECT company_name, street1, street2, city, pincode, country, person_name, state " +
                "FROM company_registration WHERE company_code = ?";
        try (PreparedStatement ps = con.prepareStatement(companyQuery)) {
            ps.setString(1, companyCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> companyDetails = new HashMap<>();
                    companyDetails.put("name", rs.getString("company_name"));
                    companyDetails.put("street1", rs.getString("street1"));
                    companyDetails.put("street2", rs.getString("street2"));
                    companyDetails.put("city", rs.getString("city"));
                    companyDetails.put("pincode", rs.getString("pincode"));
                    companyDetails.put("country", rs.getString("country"));
                    companyDetails.put("personName", rs.getString("person_name"));
                    companyDetails.put("state", rs.getString("state"));
                    reportData.put("company", companyDetails);
                }
            }
        }

        // 2. Audit master details
        String auditQuery = "SELECT audit_plan_id, std_name, std_id, audit_no " +
                "FROM " + companyCode + "_internal_audit_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(auditQuery)) {
            ps.setString(1, internalAuditId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reportData.put("auditPlanId", rs.getString("audit_plan_id"));
                    reportData.put("stdName", rs.getString("std_name"));
                    reportData.put("stdId", rs.getString("std_id"));
                    reportData.put("auditNo", rs.getString("audit_no"));
                }
            }
        }

        // 3. Audit plan dates
        String planQuery = "SELECT audit_start_date, audit_end_date " +
                "FROM " + companyCode + "_audit_plan WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(planQuery)) {
            ps.setString(1, (String) reportData.get("auditPlanId"));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    reportData.put("startDate", rs.getString("audit_start_date"));
                    reportData.put("endDate", rs.getString("audit_end_date"));
                }
            }
        }

        // 4. Auditors
        List<String> auditors = new ArrayList<>();
        String auditorQuery = "SELECT auditor_name FROM " + companyCode + "_audit_plan_auditors WHERE audit_plan_id = ?";
        try (PreparedStatement ps = con.prepareStatement(auditorQuery)) {
            ps.setString(1, (String) reportData.get("auditPlanId"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditors.add(rs.getString("auditor_name"));
                }
            }
        }
        reportData.put("auditors", auditors);

        // 5. Findings
        List<Map<String, String>> findings = new ArrayList<>();
        String findingsQuery = "SELECT d.std_id, d.clause_no, d.desc_type, d.evidence, s.std_name " +
                "FROM " + companyCode + "_internal_audit_detail d " +
                "JOIN standard_master s ON d.std_id = s.id " +
                "WHERE d.internal_audit_id = ?";
        try (PreparedStatement ps = con.prepareStatement(findingsQuery)) {
            ps.setString(1, internalAuditId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> finding = new HashMap<>();
                    finding.put("stdId", rs.getString("std_id"));
                    finding.put("stdName", rs.getString("std_name"));
                    finding.put("clauseNo", rs.getString("clause_no"));

                    // Get clause name
                    String clauseName = getClauseName(con, rs.getString("std_id"), rs.getString("clause_no"));
                    finding.put("clauseName", clauseName);

                    // Format grading
                    String grading = rs.getString("desc_type");
                    if ("MJ NC".equals(grading)) grading = "Major Nonconformity";
                    else if ("MN NC".equals(grading)) grading = "Minor Nonconformity";
                    finding.put("grading", grading);

                    finding.put("evidence", rs.getString("evidence"));
                    findings.add(finding);
                }
            }
        }
        reportData.put("findings", findings);
        reportData.put("standardName", standardName);

        return reportData;
    }

    private String generateHtmlReport(Map<String, Object> reportData) {
        StringBuilder html = new StringBuilder();

        // HTML Header
        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Internal Audit Report</title>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }")
                .append("h1, h2, h3 { color: #2c3e50; text-align: center; }")
                .append("h1 { margin-bottom: 5px; }")
                .append("h2 { margin-top: 5px; margin-bottom: 10px; }")
                .append("h3 { margin-top: 5px; margin-bottom: 20px; color: #7f8c8d; }")
                .append(".section { margin-bottom: 30px; }")
                .append(".section-title { background-color: #3498db; color: white; padding: 10px; margin-bottom: 15px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }")
                .append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
                .append("th { background-color: #f2f2f2; }")
                .append(".finding { margin-bottom: 20px; padding: 15px; background-color: #f8f9fa; border-radius: 5px; }")
                .append("@media print { body { margin: 0; } .section { page-break-inside: avoid; } }")
                .append("</style>")
                .append("</head>")
                .append("<body>");

        // Report Header
        Map<String, String> company = (Map<String, String>) reportData.get("company");
        html.append("<h1>").append(escapeHtml(company.get("name"))).append("</h1>")
                .append("<h2>").append(escapeHtml((String) reportData.get("auditNo"))).append("</h2>")
                .append("<h3>").append(escapeHtml((String) reportData.get("standardName"))).append("</h3>");

        // 1. Organization Profile
        html.append("<div class='section'>")
                .append("<div class='section-title'>1. Organisation Profile</div>")
                .append("<table>")
                .append("<tr><th>Field</th><th>Details</th></tr>")
                .append("<tr><td>Organisation Name</td><td>").append(escapeHtml(company.get("name"))).append("</td></tr>")
                .append("<tr><td>Address</td><td>").append(formatAddress(company)).append("</td></tr>")
                .append("<tr><td>City</td><td>").append(escapeHtml(company.get("city"))).append("</td></tr>")
                .append("<tr><td>State</td><td>").append(escapeHtml(company.get("state"))).append("</td></tr>")
                .append("<tr><td>Country</td><td>").append(escapeHtml(company.get("country"))).append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // 2. Dates & Organization
        html.append("<div class='section'>")
                .append("<div class='section-title'>2. Dates & Organisation</div>")
                .append("<table>")
                .append("<tr><th>Field</th><th>Details</th></tr>")
                .append("<tr><td>Audit Location</td><td>").append(escapeHtml(company.get("name"))).append("</td></tr>")
                .append("<tr><td>Audit Dates</td><td>")
                .append(formatDate((String) reportData.get("startDate")))
                .append(" to ")
                .append(formatDate((String) reportData.get("endDate")))
                .append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // 3. Audit Team
        html.append("<div class='section'>")
                .append("<div class='section-title'>3. Audit Team</div>")
                .append("<table>")
                .append("<tr><th>Role</th><th>Name</th></tr>")
                .append("<tr><td>Lead Auditor</td><td>")
                .append(String.join(", ", (List<String>) reportData.get("auditors")))
                .append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // 4. Audit Findings
        html.append("<div class='section'>")
                .append("<div class='section-title'>4. Audit Findings</div>");

        int findingNumber = 0;
        for (Map<String, String> finding : (List<Map<String, String>>) reportData.get("findings")) {
            findingNumber++;
            html.append("<div class='finding'>")
                    .append("<h4>Finding ").append(findingNumber).append("</h4>")
                    .append("<table>")
                    .append("<tr><td>Standard</td><td>").append(escapeHtml(finding.get("stdName"))).append("</td></tr>")
                    .append("<tr><td>Grading</td><td>").append(escapeHtml(finding.get("grading"))).append("</td></tr>")
                    .append("<tr><td>Clause</td><td>").append(escapeHtml(finding.get("clauseNo")))
                    .append(" - ").append(escapeHtml(finding.get("clauseName"))).append("</td></tr>")
                    .append("<tr><td>Objective Evidence</td><td>").append(escapeHtml(finding.get("evidence"))).append("</td></tr>")
                    .append("</table>")
                    .append("</div>");
        }

        html.append("</div>") // Close findings section
                .append("</body>")
                .append("</html>");

        return html.toString();
    }

    // Helper methods
    private String formatAddress(Map<String, String> company) {
        StringBuilder address = new StringBuilder();
        if (company.get("street1") != null) address.append(escapeHtml(company.get("street1"))).append(", ");
        if (company.get("street2") != null) address.append(escapeHtml(company.get("street2"))).append(", ");
        if (company.get("city") != null) address.append(escapeHtml(company.get("city")));
        return address.toString();
    }

    private String formatDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) return "";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");
            return outputFormat.format(inputFormat.parse(dateString));
        } catch (Exception e) {
            return dateString;
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }



    @GET
    @Path("/export-nc-doc")
    @Produces(MediaType.TEXT_HTML)
    public Response exportNCDocument(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @QueryParam("id") String internalAuditId) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Authentication required")
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            Map<String, String> companyDetails = fetchCompanyDetails(con, companyCode);
            Map<String, String> auditDetails = fetchAuditDetails(con, companyCode, internalAuditId);

            String htmlContent = generateNCDocument(companyDetails, auditDetails);

            return Response.ok(htmlContent)
                    .header("Content-Type", "text/html")
                    .build();
        } catch (Exception e) {
            logger.severe("Error exporting NC document: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to generate document: " + e.getMessage())
                    .build();
        }
    }

    private String generateNCDocument(Map<String, String> company, Map<String, String> audit) {
        StringBuilder html = new StringBuilder();

        // HTML Header
        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<title>NC Document</title>")
                .append("<meta charset='UTF-8'>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; margin: 20px; line-height: 1.6; }")
                .append("h1 { color: #2c3e50; margin-bottom: 10px; }")
                .append("h2 { color: #34495e; margin-bottom: 10px; }")
                .append("h3 { color: #7f8c8d; margin-bottom: 20px; }")
                .append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }")
                .append("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }")
                .append("th { background-color: #f2f2f2; color: #2c3e50; }")
                .append("tr:nth-child(even) { background-color: #f9f9f9; }")
                .append(".section { margin-bottom: 30px; }")
                .append(".section-title { background-color: #3498db; color: white; padding: 10px; margin-bottom: 15px; }")
                .append("ul { list-style-type: disc; margin-left: 20px; }")
                .append("li { margin-bottom: 5px; }")
                .append("</style>")
                .append("</head>")
                .append("<body>");

        // Header Section
        html.append("<div class='section'>")
                .append("<h1>Audit Content</h1>")
                .append("</div>");

        // SMETA Content
        html.append("<div class='section'>")
                .append("<p>(1) A SMETA audit was conducted which included some or all of Labour Standards, ")
                .append("Health & Safety, Environment and Business Ethics. The SMETA Best Practice Version ")
                .append(SMETA_VERSION).append(" (").append(SMETA_DATE).append(") was applied. ")
                .append("The scope of workers included all types at the site e.g. direct employees, agency workers, ")
                .append("workers employed by service providers and workers provided by other contractors. ")
                .append("Any deviations from the SMETA Methodology are stated (with reasons for deviation) ")
                .append("in the SMETA Declaration.</p>")
                .append("</div>");

        // Audit Scope
        html.append("<div class='section'>")
                .append("<p>(2) The audit scope was against the following reference document</p>")
                .append("<h3>2-Pillar SMETA Audit</h3>")
                .append("<ul>")
                .append("<li>ETI Base Code</li>")
                .append("<li>SMETA Additions</li>")
                .append("<ul>")
                .append("<li>Universal rights covering UNGP</li>")
                .append("<li>Management systems and code implementation</li>")
                .append("<li>Responsible Recruitment</li>")
                .append("<li>Entitlement to Work & Immigration</li>")
                .append("<li>Sub-Contracting and Home working</li>")
                .append("</ul>")
                .append("</ul>")
                .append("</div>");

        // 4-Pillar SMETA
        html.append("<div class='section'>")
                .append("<h3>4-Pillar SMETA</h3>")
                .append("<ul>")
                .append("<li>2-Pillar requirements plus</li>")
                .append("<li>Additional Pillar assessment of Environment</li>")
                .append("<li>Additional Pillar assessment of Environment</li>")
                .append("<li>The Customer's Supplier Code (Appendix 1)</li>")
                .append("</ul>")
                .append("</div>");

        // Non-Compliance Info
        html.append("<div class='section'>")
                .append("<p>(3) Where appropriate non-compliances were raised against the ETI code / ")
                .append("SMETA Additions & local law and recorded as non-compliances on both the audit report, ")
                .append("CAPR and on Sedex.</p>")
                .append("<p>(4) Any Non-Compliance against customer code shall not be uploaded to Sedex. ")
                .append("However, in the CAPR these 'Variances in compliance between ETI code / SMETA Additions/ ")
                .append("local law and customer code' shall be noted in the observations section of the CAPR.</p>")
                .append("</div>");

        // Guidance Section
        html.append("<div class='section'>")
                .append("<h2>Guidance</h2>")
                .append("<p>The Corrective Action Plan Report summarises the site audit findings ")
                .append("and a corrective, and preventative action plan that both the auditor and the site manager ")
                .append("believe is reasonable to ensure conformity with the ETI Base Code, Local Laws and additional ")
                .append("audited requirements. After the initial audit, the form is used to rerecord actions taken ")
                .append("and to categorise the status of the non-compliances.</p>")
                .append("</div>");

        // Company Details Table
        html.append("<div class='section'>")
                .append("<h2>Audit Details</h2>")
                .append("<table>")
                .append("<tr><th colspan='5'>Audit Detail</th></tr>")
                .append("<tr><td>Business name (Company name):</td><td colspan='4'>").append(company.get("name")).append("</td></tr>")
                .append("<tr><td>Site name:</td><td colspan='4'>").append(company.get("name")).append("</td></tr>")
                .append("<tr><td>Site address:</td><td colspan='4'>")
                .append(company.get("street1")).append("<br>")
                .append(company.get("street2")).append("<br>")
                .append(company.get("city")).append("-").append(company.get("pincode"))
                .append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // Close HTML
        html.append("</body>")
                .append("</html>");

        return html.toString();
    }

    private Map<String, String> fetchCompanyDetails(Connection con, String companyCode) throws SQLException {
        Map<String, String> companyDetails = new HashMap<>();
        String query = "SELECT * FROM company_registration WHERE company_code = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, companyCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    companyDetails.put("name", getSafeString(rs.getString("company_name")));
                    companyDetails.put("street1", getSafeString(rs.getString("street1")));
                    companyDetails.put("street2", getSafeString(rs.getString("street2")));
                    companyDetails.put("city", getSafeString(rs.getString("city")));
                    companyDetails.put("pincode", getSafeString(rs.getString("pincode")));
                    companyDetails.put("country", getSafeString(rs.getString("country")));
                    companyDetails.put("personName", getSafeString(rs.getString("person_name")));
                    companyDetails.put("state", getSafeString(rs.getString("state")));
                }
            }
        }
        return companyDetails;
    }

    private Map<String, String> fetchAuditDetails(Connection con, String companyCode, String internalAuditId) throws SQLException {
        Map<String, String> auditDetails = new HashMap<>();
        String query = "SELECT * FROM " + companyCode + "_internal_audit_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, internalAuditId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String auditPlanId = getSafeString(rs.getString("audit_plan_id"));
                    auditDetails.put("planId", auditPlanId);
                    auditDetails.put("stdName", getSafeString(rs.getString("std_name")));
                    auditDetails.put("stdId", getSafeString(rs.getString("std_id")));
                    auditDetails.put("auditNo", getSafeString(rs.getString("audit_no")));

                    String[] dates = fetchAuditPlanDates(con, companyCode, auditPlanId);
                    auditDetails.put("startDate", dates[0]);
                    auditDetails.put("endDate", dates[1]);
                }
            }
        }
        return auditDetails;
    }

    private String[] fetchAuditPlanDates(Connection con, String companyCode, String auditPlanId) throws SQLException {
        String query = "SELECT audit_start_date, audit_end_date FROM " + companyCode + "_audit_plan WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, auditPlanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                            getSafeString(rs.getString("audit_start_date")),
                            getSafeString(rs.getString("audit_end_date"))
                    };
                }
            }
        }
        return new String[]{"", ""};
    }




    private String getSafeString(String text) {
        return text != null ? text : "";
    }

    private String getStandardName(Connection con, String stdId) throws SQLException {
        if (stdId == null || stdId.isEmpty()) {
            return "";
        }
        String query = "SELECT std_name FROM standard_master WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, stdId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? getSafeString(rs.getString("std_name")) : "";
            }
        }
    }

    private String getClauseName(Connection con, String stdId, String clauseNo) throws SQLException {
        if (stdId == null || stdId.isEmpty() || clauseNo == null || clauseNo.isEmpty()) {
            return "";
        }
        String query = "SELECT name FROM clause_master WHERE std_id = ? AND number = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, stdId);
            ps.setString(2, clauseNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? getSafeString(rs.getString("name")) : "";
            }
        }
    }

    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAuditStatus(@PathParam("id") String internalAuditId,
                                      StatusUpdateRequest request,
                                      @HeaderParam("company-code") String companyCode,
                                      @HeaderParam("employee-id") String employeeId,
                                      @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        if (request == null || request.status == null || request.status.isEmpty()) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new InternalAuditResponse(false, "Status is required"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Check current status
            String query = "SELECT status FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return Response.status(Status.NOT_FOUND)
                        .entity(new InternalAuditResponse(false, "Internal audit not found"))
                        .build();
            }

            String currentStatus = rs.getString("status");

            // Validate status transition
            if ("Approve".equals(request.status)) {
                if (!"In Process".equals(currentStatus)) {
                    return Response.status(Status.BAD_REQUEST)
                            .entity(new InternalAuditResponse(false, "Can only approve audits in 'In Process' status"))
                            .build();
                }
            } else if ("Draft".equals(request.status)) {
                if (!"Complet".equals(currentStatus)) {
                    return Response.status(Status.BAD_REQUEST)
                            .entity(new InternalAuditResponse(false, "Can only revert completed audits to draft"))
                            .build();
                }
            }

            // Update status
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String currentDate = LocalDateTime.now().format(dtf);

            if ("Approve".equals(request.status)) {
                query = "UPDATE " + companyCode + "_internal_audit_master SET status = 'Complet' WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, internalAuditId);
            } else {
                query = "UPDATE " + companyCode + "_internal_audit_master SET status = ? WHERE id = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, request.status);
                ps.setString(2, internalAuditId);
            }
            ps.executeUpdate();

            // Log the action
            int maxLogId = getMaxLogId(con, companyCode);
            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId + 1);
            ps.setString(2, currentDate);
            ps.setString(3, employeeName);
            ps.setString(4, request.status);
            ps.setString(5, "Internal Audit");
            ps.setString(6, internalAuditId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new InternalAuditResponse(true, "Audit status updated successfully")).build();

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
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @PUT
    @Path("/{id}/close")
    @Produces(MediaType.APPLICATION_JSON)
    public Response closeAudit(@PathParam("id") String internalAuditId,
                               @HeaderParam("company-code") String companyCode,
                               @HeaderParam("employee-id") String employeeId,
                               @HeaderParam("employee-name") String employeeName) {

        if (!isValidAuth(employeeId, companyCode)) {
            return Response.status(Status.UNAUTHORIZED)
                    .entity(new InternalAuditResponse(false, "Authentication required"))
                    .build();
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            con.setAutoCommit(false);

            // Check if audit can be closed
            String query = "SELECT status FROM " + companyCode + "_internal_audit_master WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return Response.status(Status.NOT_FOUND)
                        .entity(new InternalAuditResponse(false, "Internal audit not found"))
                        .build();
            }

            String currentStatus = rs.getString("status");

            // Only completed audits can be closed
            if (!"Complet".equals(currentStatus)) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(new InternalAuditResponse(false, "Only completed audits can be closed"))
                        .build();
            }

            // Update status to closed
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String currentDate = LocalDateTime.now().format(dtf);

            query = "UPDATE " + companyCode + "_internal_audit_master SET status = 'Closed' WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, internalAuditId);
            ps.executeUpdate();

            // Log the action
            int maxLogId = getMaxLogId(con, companyCode);
            query = "INSERT INTO " + companyCode + "_log_master (id, fired_date, fired_by, status, module_name, module_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            ps = con.prepareStatement(query);
            ps.setInt(1, maxLogId + 1);
            ps.setString(2, currentDate);
            ps.setString(3, employeeName);
            ps.setString(4, "Close");
            ps.setString(5, "Internal Audit");
            ps.setString(6, internalAuditId);
            ps.executeUpdate();

            con.commit();

            return Response.ok(new InternalAuditResponse(true, "Audit closed successfully")).build();

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
                    .entity(new InternalAuditResponse(false, "Database error occurred: " + e.getMessage()))
                    .build();
        } finally {
            closeResources(rs, ps, con);
        }
    }


    private boolean isValidAuth(String employeeId, String companyCode) {
        return employeeId != null && !employeeId.isEmpty() && companyCode != null && !companyCode.isEmpty();
    }

    private boolean isValidCompanyCode(String companyCode) {
        return companyCode != null && COMPANY_CODE_PATTERN.matcher(companyCode).matches();
    }

    // Update the validation method
    private boolean validateInternalAuditRequest(InternalAuditRequest request) {
        if (request == null) return false;

        // Required fields
        if (request.auditPlanId == null || request.auditPlanId.isEmpty()) return false;
        if (request.standardName == null || request.standardName.isEmpty()) return false;
        if (request.auditDate == null || request.auditDate.isEmpty()) return false;
        if (request.scope == null || request.scope.isEmpty()) return false;

        // Optional fields with default values
        if (request.status == null || request.status.isEmpty()) {
            request.status = "In Process";
        }

        // Validate details if present
        if (request.details != null) {
            for (AuditDetail detail : request.details) {
                if (detail.clauseNo == null || detail.clauseNo.isEmpty()) return false;
                if (detail.ncNo == null || detail.ncNo.isEmpty()) return false;
                if (detail.descType == null || detail.descType.isEmpty()) return false;
                if (detail.comment == null || detail.comment.isEmpty()) return false;
                if (detail.status == null || detail.status.isEmpty()) return false;
            }
        }

        return true;
    }

    private int getMaxLogId(Connection con, String companyCode) throws SQLException {
        String query = "SELECT MAX(id) as max_id FROM " + companyCode + "_log_master";
        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("max_id");
            }
            return 0;
        }
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