package com.example;

import com.example.util.DBConfig;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/gap-assessment")
public class GapAssessmentResource {
    private static final Logger logger = Logger.getLogger(GapAssessmentResource.class.getName());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGapAssessments(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        try (Connection con = DBConfig.getConnection()) {
            String moduleId = getModuleId(con, companyCode);

            if (!hasAccess(con, companyCode, userId, moduleId, "view_access")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Access denied"))
                        .build();
            }

            List<GapAssessment> assessments = getGapAssessments(con, companyCode);
            return Response.ok(assessments).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.serverError()
                    .entity(new ErrorResponse("Database error occurred"))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGapAssessmentDetails(
            @PathParam("id") String id,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        try (Connection con = DBConfig.getConnection()) {
            String moduleId = getModuleId(con, companyCode);

            if (!hasAccess(con, companyCode, userId, moduleId, "view_access")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Access denied"))
                        .build();
            }

            GapAssessmentDetails details = getGapAssessmentDetails(con, companyCode, id);
            return Response.ok(details).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.serverError()
                    .entity(new ErrorResponse("Database error occurred"))
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGapAssessment(
            GapAssessmentRequest request,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        try (Connection con = DBConfig.getConnection()) {
            String moduleId = getModuleId(con, companyCode);

            if (!hasAccess(con, companyCode, userId, moduleId, "add_access")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Access denied"))
                        .build();
            }

            String id = createGapAssessment(con, companyCode, request);
            return Response.ok(new SuccessResponse("Gap assessment created successfully", id)).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.serverError()
                    .entity(new ErrorResponse("Database error occurred"))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGapAssessment(
            @PathParam("id") String id,
            GapAssessmentRequest request,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        try (Connection con = DBConfig.getConnection()) {
            String moduleId = getModuleId(con, companyCode);

            if (!hasAccess(con, companyCode, userId, moduleId, "edit_access")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Access denied"))
                        .build();
            }

            updateGapAssessment(con, companyCode, id, request);
            return Response.ok(new SuccessResponse("Gap assessment updated successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.serverError()
                    .entity(new ErrorResponse("Database error occurred"))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteGapAssessment(
            @PathParam("id") String id,
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String userId) {

        try (Connection con = DBConfig.getConnection()) {
            String moduleId = getModuleId(con, companyCode);

            if (!hasAccess(con, companyCode, userId, moduleId, "delete_access")) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(new ErrorResponse("Access denied"))
                        .build();
            }

            deleteGapAssessment(con, companyCode, id);
            return Response.ok(new SuccessResponse("Gap assessment deleted successfully")).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.serverError()
                    .entity(new ErrorResponse("Database error occurred"))
                    .build();
        }
    }

    private String getModuleId(Connection con, String companyCode) throws SQLException {
        String query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'GAP ASSESSMENT'";
        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("module_id");
            }
            throw new SQLException("Module not found");
        }
    }

    private boolean hasAccess(Connection con, String companyCode, String userId, String moduleId, String accessType) throws SQLException {
        String query = "SELECT " + accessType + " FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && !rs.getString(accessType).equalsIgnoreCase("No");
            }
        }
    }

    private List<GapAssessment> getGapAssessments(Connection con, String companyCode) throws SQLException {
        List<GapAssessment> assessments = new ArrayList<>();
        String query = "SELECT * FROM " + companyCode + "_gap_assessment_header";

        try (PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                GapAssessment assessment = new GapAssessment();
                assessment.id = String.valueOf(rs.getInt("id"));
                assessment.std_id = rs.getInt("std_id");
                assessment.departmentName = rs.getString("department_name");
                assessment.meetingDate = rs.getString("meeting_date");
                assessment.meetingTime = rs.getString("meeting_time");
                assessment.contactPerson = rs.getString("contact_person");
                assessment.remarks = rs.getString("remarks");
                assessment.details = getGapAssessmentDetailsList(con, companyCode, assessment.id);
                assessments.add(assessment);
            }
        }
        return assessments;
    }

    private List<GapAssessmentDetail> getGapAssessmentDetailsList(Connection con, String companyCode, String headerId) throws SQLException {
        List<GapAssessmentDetail> details = new ArrayList<>();
        String query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, headerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GapAssessmentDetail detail = new GapAssessmentDetail();
                    detail.clauseNo = rs.getString("clause_no");
                    detail.description = rs.getString("description");
                    detail.areaRequireImprovement = rs.getString("area_require_improvement");
                    detail.status = rs.getString("status");
                    detail.possibleBarrierToCertification = rs.getString("possible_barrier_to_certification");
                    details.add(detail);
                }
            }
        }
        return details;
    }

    private GapAssessmentDetails getGapAssessmentDetails(Connection con, String companyCode, String id) throws SQLException {
        GapAssessmentDetails details = new GapAssessmentDetails();
        String query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE id = ?";

        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    details.id = String.valueOf(rs.getInt("id"));
                    details.std_id = rs.getInt("std_id");
                    details.departmentName = rs.getString("department_name");
                    details.meetingDate = rs.getString("meeting_date");
                    details.meetingTime = rs.getString("meeting_time");
                    details.contactPerson = rs.getString("contact_person");
                    details.remarks = rs.getString("remarks");
                    details.details = getGapAssessmentDetailsList(con, companyCode, id);
                }
            }
        }
        return details;
    }

    private String createGapAssessment(Connection con, String companyCode, GapAssessmentRequest request) throws SQLException {
        String headerId = null;

        // First, get the next available ID for header
        String getNextIdQuery = "SELECT MAX(id) + 1 as next_id FROM " + companyCode + "_gap_assessment_header";
        try (PreparedStatement ps = con.prepareStatement(getNextIdQuery);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                headerId = String.valueOf(rs.getInt("next_id"));
            }
        }

        // Now insert with the explicit ID
        String headerQuery = "INSERT INTO " + companyCode + "_gap_assessment_header " +
                "(id, std_id, department_name, meeting_date, meeting_time, contact_person, remarks) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(headerQuery)) {
            // Set the ID explicitly
            if (headerId != null) {
                ps.setInt(1, Integer.parseInt(headerId));
            } else {
                ps.setInt(1, 2); // Since we already have 0 and 1
            }

            // Handle null values for std_id
            if (request.std_id != 0) {
                ps.setInt(2, request.std_id);
            } else {
                ps.setNull(2, Types.INTEGER);
            }

            ps.setString(3, request.departmentName);
            ps.setString(4, request.meetingDate);
            ps.setString(5, request.meetingTime);
            ps.setString(6, request.contactPerson);
            ps.setString(7, request.remarks);

            int affectedRows = ps.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating gap assessment failed, no rows affected.");
            }
        }

        if (headerId != null && request.details != null && !request.details.isEmpty()) {
            // Get the next available ID for detail
            String getNextDetailIdQuery = "SELECT MAX(id) + 1 as next_id FROM " + companyCode + "_gap_assessment_detail";
            int nextDetailId = 1; // Default to 1 if no records exist

            try (PreparedStatement ps = con.prepareStatement(getNextDetailIdQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nextDetailId = rs.getInt("next_id");
                }
            }

            String detailQuery = "INSERT INTO " + companyCode + "_gap_assessment_detail " +
                    "(id, gap_assessment_header_id, clause_no, description, area_require_improvement, status, possible_barrier_to_certification) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(detailQuery)) {
                for (GapAssessmentDetail detail : request.details) {
                    ps.setInt(1, nextDetailId++);
                    ps.setInt(2, Integer.parseInt(headerId));
                    ps.setString(3, detail.clauseNo);
                    ps.setString(4, detail.description);
                    ps.setString(5, detail.areaRequireImprovement);
                    ps.setString(6, detail.status);
                    ps.setString(7, detail.possibleBarrierToCertification);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        return headerId;
    }

    private void updateGapAssessment(Connection con, String companyCode, String id, GapAssessmentRequest request) throws SQLException {
        // Update header
        String headerQuery = "UPDATE " + companyCode + "_gap_assessment_header SET " +
                "std_id = ?, department_name = ?, meeting_date = ?, meeting_time = ?, " +
                "contact_person = ?, remarks = ? WHERE id = ?";

        try (PreparedStatement ps = con.prepareStatement(headerQuery)) {
            // Handle null values for std_id
            if (request.std_id != 0) {
                ps.setInt(1, request.std_id);
            } else {
                ps.setNull(1, Types.INTEGER);
            }

            ps.setString(2, request.departmentName);
            ps.setString(3, request.meetingDate);
            ps.setString(4, request.meetingTime);
            ps.setString(5, request.contactPerson);
            ps.setString(6, request.remarks);
            ps.setInt(7, Integer.parseInt(id));
            ps.executeUpdate();
        }

        // Delete existing details
        String deleteDetailsQuery = "DELETE FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
        try (PreparedStatement ps = con.prepareStatement(deleteDetailsQuery)) {
            ps.setInt(1, Integer.parseInt(id));
            ps.executeUpdate();
        }

        // Insert new details
        if (request.details != null && !request.details.isEmpty()) {
            // Get the next available ID for detail
            String getNextDetailIdQuery = "SELECT MAX(id) + 1 as next_id FROM " + companyCode + "_gap_assessment_detail";
            int nextDetailId = 1; // Default to 1 if no records exist

            try (PreparedStatement ps = con.prepareStatement(getNextDetailIdQuery);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nextDetailId = rs.getInt("next_id");
                }
            }

            String detailQuery = "INSERT INTO " + companyCode + "_gap_assessment_detail " +
                    "(id, gap_assessment_header_id, clause_no, description, area_require_improvement, status, possible_barrier_to_certification) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(detailQuery)) {
                for (GapAssessmentDetail detail : request.details) {
                    ps.setInt(1, nextDetailId++);
                    ps.setInt(2, Integer.parseInt(id));
                    ps.setString(3, detail.clauseNo);
                    ps.setString(4, detail.description);
                    ps.setString(5, detail.areaRequireImprovement);
                    ps.setString(6, detail.status);
                    ps.setString(7, detail.possibleBarrierToCertification);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private void deleteGapAssessment(Connection con, String companyCode, String id) throws SQLException {
        String deleteDetailsQuery = "DELETE FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
        try (PreparedStatement ps = con.prepareStatement(deleteDetailsQuery)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }

        String deleteHeaderQuery = "DELETE FROM " + companyCode + "_gap_assessment_header WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(deleteHeaderQuery)) {
            ps.setInt(1, Integer.parseInt(id));
            ps.executeUpdate();
        }
    }

    // Data classes
    public static class GapAssessment {
        public String id;
        public int std_id;
        public String departmentName;
        public String meetingDate;
        public String meetingTime;
        public String contactPerson;
        public String remarks;
        public List<GapAssessmentDetail> details;
    }

    public static class GapAssessmentDetail {
        public String clauseNo;
        public String description;
        public String areaRequireImprovement;
        public String status;
        public String possibleBarrierToCertification;
    }

    public static class GapAssessmentRequest {
        public int std_id;
        public String departmentName;
        public String meetingDate;
        public String meetingTime;
        public String contactPerson;
        public String remarks;
        public List<GapAssessmentDetail> details;
    }

    public static class GapAssessmentDetails {
        public String id;
        public int std_id;
        public String departmentName;
        public String meetingDate;
        public String meetingTime;
        public String contactPerson;
        public String remarks;
        public List<GapAssessmentDetail> details;
    }

    public static class SuccessResponse {
        public boolean success = true;
        public String message;
        public String id;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public SuccessResponse(String message, String id) {
            this.message = message;
            this.id = id;
        }
    }

    public static class ErrorResponse {
        public boolean success = false;
        public String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}