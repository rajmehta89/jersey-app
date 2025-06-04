package com.example;

import com.example.util.ValidationUtil;
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

@Path("/gap-assessment-report")
public class GapAssessmentReportResource {

    private static final Logger logger = Logger.getLogger(GapAssessmentResource.class.getName());

    // Response class
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public T getData() { return data; }
    }

    // Meeting Details Response class
    public static class MeetingDetailsResponse {
        private List<String> meetingDates;
        private List<String> labels;

        public MeetingDetailsResponse(List<String> meetingDates, List<String> labels) {
            this.meetingDates = meetingDates;
            this.labels = labels;
        }

        public List<String> getMeetingDates() { return meetingDates; }
        public List<String> getLabels() { return labels; }
    }

    // Request class for report generation
    public static class ReportRequest {
        private String companyCode;
        private String standardName;
        private String department;
        private List<String> meetingDates;
        private List<String> labels;

        public String getCompanyCode() { return companyCode; }
        public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }
        public String getStandardName() { return standardName; }
        public void setStandardName(String standardName) { this.standardName = standardName; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public List<String> getMeetingDates() { return meetingDates; }
        public void setMeetingDates(List<String> meetingDates) { this.meetingDates = meetingDates; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
    }

    @GET
    @Path("/standards")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStandards(@HeaderParam("company-code") String companyCode, @HeaderParam("employee-id") String userId) {

        Connection con = null;

        PreparedStatement ps = null;

        ResultSet res = null;

        List<Map<String, String>> standards = new ArrayList<>();

        try {

            logger.info("Fetching standards for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {

                Map<String, Object> errorResponse = new HashMap<>();

                errorResponse.put("success", false);

                errorResponse.put("error", "Company code and user ID are required");

                errorResponse.put("details", "Missing required headers");

                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(errorResponse).build();

            }

            con = DBConfig.getConnection();

            String query = "SELECT DISTINCT pcrd.standard_name " +
                    "FROM " + companyCode + "_customer_rights_detail pcrd " +
                    "LEFT JOIN company_std_detail csd ON csd.std_name = pcrd.standard_name " +
                    "WHERE csd.company_id = (SELECT company_id FROM " + companyCode + "_customer_rights WHERE id = ?) " +
                    "AND csd.status = 'Active' AND pcrd.rights_id = (SELECT id FROM " + companyCode + "_customer_rights WHERE id = ?)";

            ps = con.prepareStatement(query);

            ps.setString(1, userId);

            ps.setString(2, userId);

            res = ps.executeQuery();

            while (res.next()) {

                Map<String, String> standard = new HashMap<>();

                standard.put("stdName", res.getString("standard_name"));

                standards.add(standard);

            }

            Map<String, Object> response = new HashMap<>();

            response.put("success", true);
            response.put("error", null);
            response.put("data", standards);
            return Response.ok(response).build();

        } catch (SQLException e) {

            logger.severe("Database error: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Database error occurred");
            errorResponse.put("details", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();

        } finally {
            try {
                if (res != null) res.close();
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                logger.warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    @GET
    @Path("/departments/{std_name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDepartments(
            @HeaderParam("company-code") String companyCode,
            @PathParam("std_name") String standardName) {  // <-- Should be @PathParam, not @QueryParam

        if (companyCode == null || companyCode.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse<>(false, "Missing company_code header", null))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {

            String stdId = getStandardId(con, standardName);

            String query = "SELECT DISTINCT department_name " +
                    "FROM " + companyCode + "_gap_assessment_header " +
                    "WHERE std_id = ?";

            try (PreparedStatement pstmt = con.prepareStatement(query)) {
                pstmt.setString(1, stdId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    List<String> departments = new ArrayList<>();
                    while (rs.next()) {
                        departments.add(rs.getString("department_name"));
                    }

                    return Response.ok(new ApiResponse<>(true, "Departments retrieved successfully", departments)).build();
                }
            }
        } catch (SQLException e) {
            return Response.serverError()
                    .entity(new ApiResponse<>(false, "Error fetching departments: " + e.getMessage(), null))
                    .build();
        }
    }


    @GET
    @Path("/meeting-details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMeetingDetails(
            @HeaderParam("company_code") String companyCode,
            @HeaderParam("standard_name") String standardName,
            @HeaderParam("department") String department) {

        if (companyCode == null || companyCode.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse<>(false, "Missing company_code header", null))
                    .build();
        }

        try (Connection con = DBConfig.getConnection()) {
            String stdId = getStandardId(con, standardName);

            String meetingQuery = "SELECT meeting_date " +
                    "FROM " + companyCode + "_gap_assessment_header " +
                    "WHERE std_id = ? AND department_name = ?";

            List<String> meetingDates = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            try (PreparedStatement meetingStmt = con.prepareStatement(meetingQuery)) {
                meetingStmt.setString(1, stdId);
                meetingStmt.setString(2, department);

                try (ResultSet meetingRs = meetingStmt.executeQuery()) {
                    while (meetingRs.next()) {
                        meetingDates.add(meetingRs.getString("meeting_date"));
                    }
                }
            }

            // Now fetch labels
            String labelQuery = "SELECT label FROM gap_assessment_template WHERE std_name = ?";
            try (PreparedStatement labelStmt = con.prepareStatement(labelQuery)) {
                labelStmt.setString(1, standardName);

                try (ResultSet labelRs = labelStmt.executeQuery()) {
                    while (labelRs.next()) {
                        labels.add(labelRs.getString("label"));
                    }
                }
            }

            MeetingDetailsResponse response = new MeetingDetailsResponse(meetingDates, labels);
            return Response.ok(new ApiResponse<>(true, "Meeting details retrieved successfully", response)).build();

        } catch (SQLException e) {
            return Response.serverError()
                    .entity(new ApiResponse<>(false, "Error fetching meeting details: " + e.getMessage(), null))
                    .build();
        }
    }


    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    public Response generateReport(@HeaderParam("company-code") String companyCode, @HeaderParam("employee-id") String userId, Map<String, Object> request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null, res1 = null, res2 = null;

        String actualCompanyCode = companyCode; // Assume same as company_code
        String loginType = "Client Login"; // Default, can be dynamic
        String parentCompanyCode = companyCode; // Assume same as company_code
        String actualCompanyName = "";
        String stdId = "";
        String clauseName, description, guidance, templateText, newTemplateText;
        String mergeMeetingDate = "", mergeMeetingTime = "", mergeContactPerson = "";
        int nonexistentCount = 0, initialCount = 0, limitedCount = 0, definedCount = 0, managedCount = 0, optimizedCount = 0, notApplicableCount = 0;
        int nonexistentPossible = 0, initialPossible = 0, limitedPossible = 0, definedPossible = 0, managedPossible = 0, optimizedPossible = 0, notApplicablePossible = 0;
        List<String> clauseData = new ArrayList<>();
        List<String> clauseData2 = new ArrayList<>();
        List<String> labelData = new ArrayList<>();

        try {
            logger.info("Generating gap assessment report for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Company code and user ID are required</h1></body></html>")
                        .build();
            }

            String stdName = (String) request.get("std_name");
            String department = (String) request.get("department");
            List<String> meetingDates = (List<String>) request.get("meeting_dates");
            List<String> labels = (List<String>) request.get("labels");

            int meetingCount = (meetingDates == null) ? 0 : meetingDates.size();
            int labelCount = (labels == null) ? 0 : labels.size();

            if (!ValidationUtil.isNotEmpty(stdName) || !ValidationUtil.isNotEmpty(department)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Standard name and department are required</h1></body></html>")
                        .build();
            }

            con = DBConfig.getConnection();

            // Fetch company name based on login_type
            String query;
            if (loginType.equalsIgnoreCase("Certification body Client")) {
                query = "SELECT company_name FROM CB_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else if (loginType.equalsIgnoreCase("Consultant Client")) {
                query = "SELECT company_name FROM CS_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else if (loginType.equalsIgnoreCase("CPA Client")) {
                query = "SELECT company_name FROM CPA_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else {
                query = "SELECT company_name FROM company_registration WHERE company_code = ?";
            }
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                actualCompanyName = res.getString("company_name");
            } else {
                actualCompanyName = "Unknown Company";
            }
            res.close();
            ps.close();

            // Fetch std_id
            query = "SELECT id FROM standard_master WHERE std_name = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, stdName);
            res = ps.executeQuery();
            if (res.next()) {
                stdId = res.getString("id");
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Standard not found</h1></body></html>")
                        .build();
            }
            res.close();
            ps.close();

            // Process gap assessment headers and details
            for (int j = 0; j < meetingCount; j++) {
                query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE std_id = ? AND department_name = ? AND meeting_date = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdId);
                ps.setString(2, department);
                ps.setString(3, meetingDates.get(j));
                res = ps.executeQuery();
                if (res.next()) {
                    mergeMeetingDate += res.getString("meeting_date") + ",";
                    mergeMeetingTime += res.getString("meeting_time") + ",";
                    mergeContactPerson += res.getString("contact_person") + ",";

                    String headerId = res.getString("id");
                    query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
                    ps = con.prepareStatement(query);
                    ps.setString(1, headerId);
                    res1 = ps.executeQuery();
                    while (res1.next()) {
                        query = "SELECT name FROM clause_master WHERE std_id = ? AND number = ?";
                        ps = con.prepareStatement(query);
                        ps.setString(1, stdId);
                        ps.setString(2, res1.getString("clause_no").replaceAll(" ", ""));
                        res2 = ps.executeQuery();
                        clauseName = res2.next() ? res2.getString("name") : "";

                        clauseData.add(res1.getString("clause_no"));
                        clauseData.add(clauseName);
                        clauseData.add(res1.getString("description"));
                        clauseData.add(res1.getString("area_require_improvement"));
                        clauseData.add(res1.getString("status"));
                        clauseData.add(res1.getString("possible_barrier_to_certification"));
                        clauseData.add(res1.getString("remarks"));

                        String status = res1.getString("status");
                        boolean isPossibleBarrier = res1.getString("possible_barrier_to_certification").equalsIgnoreCase("Yes");
                        switch (status.toLowerCase()) {
                            case "nonexistent":
                                nonexistentCount++;
                                if (isPossibleBarrier) nonexistentPossible++;
                                break;
                            case "initial":
                                initialCount++;
                                if (isPossibleBarrier) initialPossible++;
                                break;
                            case "limited":
                                limitedCount++;
                                if (isPossibleBarrier) limitedPossible++;
                                break;
                            case "defined":
                                definedCount++;
                                if (isPossibleBarrier) definedPossible++;
                                break;
                            case "managed":
                                managedCount++;
                                if (isPossibleBarrier) managedPossible++;
                                break;
                            case "optimized":
                                optimizedCount++;
                                if (isPossibleBarrier) optimizedPossible++;
                                break;
                            case "not applicable":
                                notApplicableCount++;
                                if (isPossibleBarrier) notApplicablePossible++;
                                break;
                        }
                    }
                    res1.close();
                }
                res.close();
                ps.close();
            }

            // Process labels
            for (int j = 0; j < labelCount; j++) {
                query = "SELECT label, template_text FROM gap_assessment_template WHERE std_name = ? AND label = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdName);
                ps.setString(2, labels.get(j));
                res1 = ps.executeQuery();
                while (res1.next()) {
                    labelData.add(res1.getString("label"));
                    templateText = res1.getString("template_text");
                    newTemplateText = templateText.replaceAll("-CLIENT-", actualCompanyName)
                            .replaceAll("-STANDARD-", stdName)
                            .replaceAll("-UnderDefense-", "Niall Services Pvt. Ltd.");
                    if (labels.get(j).equalsIgnoreCase("Key stakeholders interviewed")) {
                        newTemplateText = newTemplateText.replaceAll("-DEPT-", department)
                                .replaceAll("-M_DATE-", mergeMeetingDate)
                                .replaceAll("-M_TIME-", mergeMeetingTime)
                                .replaceAll("-C_PERSON-", mergeContactPerson);
                    }
                    labelData.add(newTemplateText);
                }
                res1.close();
                ps.close();
            }

            // Process clause data with master details
            for (int j = 0; j < meetingCount; j++) {
                query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE std_id = ? AND department_name = ? AND meeting_date = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdId);
                ps.setString(2, department);
                ps.setString(3, meetingDates.get(j));
                res = ps.executeQuery();
                if (res.next()) {
                    String headerId = res.getString("id");
                    query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
                    ps = con.prepareStatement(query);
                    ps.setString(1, headerId);
                    res1 = ps.executeQuery();
                    while (res1.next()) {
                        query = "SELECT name, description, guidance FROM clause_master WHERE std_id = ? AND number = ?";
                        ps = con.prepareStatement(query);
                        ps.setString(1, stdId);
                        ps.setString(2, res1.getString("clause_no").replaceAll(" ", ""));
                        res2 = ps.executeQuery();
                        if (res2.next()) {
                            clauseName = res2.getString("name");
                            description = res2.getString("description");
                            guidance = res2.getString("guidance");
                        } else {
                            clauseName = "";
                            description = "";
                            guidance = "";
                        }
                        res2.close();

                        clauseData2.add(res1.getString("clause_no"));
                        clauseData2.add(clauseName);
                        clauseData2.add(description);
                        clauseData2.add(guidance);
                        clauseData2.add(res1.getString("description"));
                        clauseData2.add(res1.getString("area_require_improvement"));
                        clauseData2.add(res1.getString("status"));
                        clauseData2.add(res1.getString("possible_barrier_to_certification"));
                        clauseData2.add(res1.getString("remarks"));
                    }
                    res1.close();
                }
                res.close();
                ps.close();
            }

            // Build HTML response
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><title>Gap Assessment Report</title>")
                    .append("<style>")
                    .append("body { font-family: Arial, sans-serif; margin: 20px; }")
                    .append("h1, h2 { color: #333; }")
                    .append("table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }")
                    .append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
                    .append("th { background-color: #f2f2f2; }")
                    .append("</style></head><body>");

            html.append("<h1>Gap Assessment Report</h1>")
                    .append("<p><strong>Company:</strong> ").append(actualCompanyName).append("</p>")
                    .append("<p><strong>Standard:</strong> ").append(stdName).append("</p>")
                    .append("<p><strong>Department:</strong> ").append(department).append("</p>");

            // Clause Data Table
            html.append("<h2>Clause Data</h2><table>")
                    .append("<tr><th>Clause No</th><th>Clause Name</th><th>Description</th><th>Area Requiring Improvement</th><th>Status</th><th>Possible Barrier</th><th>Remarks</th></tr>");
            for (int i = 0; i < clauseData.size(); i += 7) {
                html.append("<tr>")
                        .append("<td>").append(clauseData.get(i)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 1)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 2)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 3)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 4)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 5)).append("</td>")
                        .append("<td>").append(clauseData.get(i + 6)).append("</td>")
                        .append("</tr>");
            }
            html.append("</table>");

            // Label Data Table
            html.append("<h2>Label Data</h2><table>")
                    .append("<tr><th>Label</th><th>Template Text</th></tr>");
            for (int i = 0; i < labelData.size(); i += 2) {
                html.append("<tr>")
                        .append("<td>").append(labelData.get(i)).append("</td>")
                        .append("<td>").append(labelData.get(i + 1)).append("</td>")
                        .append("</tr>");
            }
            html.append("</table>");

            // Clause Data with Master Details
            html.append("<h2>Clause Data with Master Details</h2><table>")
                    .append("<tr><th>Clause No</th><th>Clause Name</th><th>Master Description</th><th>Guidance</th><th>Description</th><th>Area Requiring Improvement</th><th>Status</th><th>Possible Barrier</th><th>Remarks</th></tr>");
            for (int i = 0; i < clauseData2.size(); i += 9) {
                html.append("<tr>")
                        .append("<td>").append(clauseData2.get(i)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 1)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 2)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 3)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 4)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 5)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 6)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 7)).append("</td>")
                        .append("<td>").append(clauseData2.get(i + 8)).append("</td>")
                        .append("</tr>");
            }
            html.append("</table>");

            // Status Counts
            html.append("<h2>Status Summary</h2><table>")
                    .append("<tr><th>Status</th><th>Count</th><th>Possible Barriers</th></tr>")
                    .append("<tr><td>Nonexistent</td><td>").append(nonexistentCount).append("</td><td>").append(nonexistentPossible).append("</td></tr>")
                    .append("<tr><td>Initial</td><td>").append(initialCount).append("</td><td>").append(initialPossible).append("</td></tr>")
                    .append("<tr><td>Limited</td><td>").append(limitedCount).append("</td><td>").append(limitedPossible).append("</td></tr>")
                    .append("<tr><td>Defined</td><td>").append(definedCount).append("</td><td>").append(definedPossible).append("</td></tr>")
                    .append("<tr><td>Managed</td><td>").append(managedCount).append("</td><td>").append(managedPossible).append("</td></tr>")
                    .append("<tr><td>Optimized</td><td>").append(optimizedCount).append("</td><td>").append(optimizedPossible).append("</td></tr>")
                    .append("<tr><td>Not Applicable</td><td>").append(notApplicableCount).append("</td><td>").append(notApplicablePossible).append("</td></tr>")
                    .append("</table>");

            html.append("</body></html>");

            return Response.ok(html.toString()).build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error: Database error occurred - " + e.getMessage() + "</h1></body></html>")
                    .build();
        } finally {
            try {
                clauseData.clear();
                clauseData2.clear();
                labelData.clear();
                if (res != null) res.close();
                if (res1 != null) res1.close();
                if (res2 != null) res2.close();
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                logger.warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    // POST Generate DOC Report
    @POST
    @Path("/generate-doc")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/msword")
    public Response generateDocReport(@HeaderParam("company-code") String companyCode, @HeaderParam("employee-id") String userId, Map<String, Object> request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null, res1 = null, res2 = null;

        String actualCompanyCode = companyCode;
        String loginType = "Client Login";
        String parentCompanyCode = companyCode;
        String actualCompanyName = "";
        String stdId = "";
        String clauseName, description, guidance, templateText, newTemplateText;
        String mergeMeetingDate = "", mergeMeetingTime = "", mergeContactPerson = "";
        int nonexistentCount = 0, initialCount = 0, limitedCount = 0, definedCount = 0, managedCount = 0, optimizedCount = 0, notApplicableCount = 0;
        int nonexistentPossible = 0, initialPossible = 0, limitedPossible = 0, definedPossible = 0, managedPossible = 0, optimizedPossible = 0, notApplicablePossible = 0;

        try {
            logger.info("Generating gap assessment DOC report for company_code: " + companyCode);

            if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(userId)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Company code and user ID are required</h1></body></html>")
                        .type(MediaType.TEXT_HTML)
                        .build();
            }

            String stdName = (String) request.get("std_name");
            String department = (String) request.get("department");
            List<String> meetingDates = (List<String>) request.get("meeting_dates");
            List<String> labels = (List<String>) request.get("labels");

            int meetingCount = (meetingDates == null) ? 0 : meetingDates.size();
            int labelCount = (labels == null) ? 0 : labels.size();

            if (!ValidationUtil.isNotEmpty(stdName) || !ValidationUtil.isNotEmpty(department)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Standard name and department are required</h1></body></html>")
                        .type(MediaType.TEXT_HTML)
                        .build();
            }

            con = DBConfig.getConnection();

            // Fetch company name
            String query;
            if (loginType.equalsIgnoreCase("Certification body Client")) {
                query = "SELECT company_name FROM CB_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else if (loginType.equalsIgnoreCase("Consultant Client")) {
                query = "SELECT company_name FROM CS_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else if (loginType.equalsIgnoreCase("CPA Client")) {
                query = "SELECT company_name FROM CPA_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            } else {
                query = "SELECT company_name FROM company_registration WHERE company_code = ?";
            }
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                actualCompanyName = res.getString("company_name");
            } else {
                actualCompanyName = "Unknown Company";
            }
            res.close();
            ps.close();

            // Fetch std_id
            query = "SELECT id FROM standard_master WHERE std_name = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, stdName);
            res = ps.executeQuery();
            if (res.next()) {
                stdId = res.getString("id");
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("<html><body><h1>Error: Standard not found</h1></body></html>")
                        .type(MediaType.TEXT_HTML)
                        .build();
            }
            res.close();
            ps.close();

            // Process gap assessment headers and details for counts
            for (int j = 0; j < meetingCount; j++) {
                query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE std_id = ? AND department_name = ? AND meeting_date = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdId);
                ps.setString(2, department);
                ps.setString(3, meetingDates.get(j));
                res = ps.executeQuery();
                if (res.next()) {
                    mergeMeetingDate += res.getString("meeting_date") + ",";
                    mergeMeetingTime += res.getString("meeting_time") + ",";
                    mergeContactPerson += res.getString("contact_person") + ",";

                    String headerId = res.getString("id");
                    query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
                    ps = con.prepareStatement(query);
                    ps.setString(1, headerId);
                    res1 = ps.executeQuery();
                    while (res1.next()) {
                        String status = res1.getString("status");
                        boolean isPossibleBarrier = res1.getString("possible_barrier_to_certification").equalsIgnoreCase("Yes");
                        switch (status.toLowerCase()) {
                            case "nonexistent":
                                nonexistentCount++;
                                if (isPossibleBarrier) nonexistentPossible++;
                                break;
                            case "initial":
                                initialCount++;
                                if (isPossibleBarrier) initialPossible++;
                                break;
                            case "limited":
                                limitedCount++;
                                if (isPossibleBarrier) limitedPossible++;
                                break;
                            case "defined":
                                definedCount++;
                                if (isPossibleBarrier) definedPossible++;
                                break;
                            case "managed":
                                managedCount++;
                                if (isPossibleBarrier) managedPossible++;
                                break;
                            case "optimized":
                                optimizedCount++;
                                if (isPossibleBarrier) optimizedPossible++;
                                break;
                            case "not applicable":
                                notApplicableCount++;
                                if (isPossibleBarrier) notApplicablePossible++;
                                break;
                        }
                    }
                    res1.close();
                }
                res.close();
                ps.close();
            }

            // Build DOC-compatible HTML
            StringBuilder html = new StringBuilder();
            html.append("<html><body style=\"font-family: Calibri; min-height: 100px; min-width: 200px\">");

            // Title
            html.append("<table class=\"table1\" style=\"width:100%;border-width: 1px solid black;border-collapse: collapse\" border=\"1\">")
                    .append("<tr><td><h3 style=\"text-align: center;font-family: Calibri;\">Gap Assessment Report of ").append(stdName).append("</h3></td></tr>")
                    .append("</table><br><br><br>");

            // Labels
            for (int j = 0; j < labelCount; j++) {
                query = "SELECT label, template_text FROM gap_assessment_template WHERE std_name = ? AND label = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdName);
                ps.setString(2, labels.get(j));
                res1 = ps.executeQuery();
                while (res1.next()) {
                    templateText = res1.getString("template_text");
                    newTemplateText = templateText.replaceAll("-CLIENT-", actualCompanyName)
                            .replaceAll("-STANDARD-", stdName)
                            .replaceAll("-UnderDefense-", "Niall Services Pvt. Ltd.");
                    if (labels.get(j).equalsIgnoreCase("Key stakeholders interviewed")) {
                        newTemplateText = newTemplateText.replaceAll("-DEPT-", department)
                                .replaceAll("-M_DATE-", mergeMeetingDate)
                                .replaceAll("-M_TIME-", mergeMeetingTime)
                                .replaceAll("-C_PERSON-", mergeContactPerson);
                    }
                    html.append("<h2>").append(res1.getString("label")).append("</h2>")
                            .append("<hr style=\"border: 1px solid black;\"><br>")
                            .append("<span style=\"font-family: Calibri;\">").append(newTemplateText).append("</span><br><br>");
                }
                res1.close();
                ps.close();
            }

            // Summary
            html.append("<h2>Summary</h2><hr style=\"border: 1px solid black;\"><br>")
                    .append("<table class=\"table1\" style=\"width:100%;border-width: 1px solid black;border-collapse: collapse\" border=\"1\">")
                    .append("<tbody>")
                    .append("<tr><td><b>Status</b></td><td><b>No of findings</b></td><td><b>Possible barriers to certification</b></td></tr>")
                    .append("<tr><td>Nonexistent</td><td>").append(nonexistentCount).append("</td><td>").append(nonexistentPossible).append("</td></tr>")
                    .append("<tr><td>Initial</td><td>").append(initialCount).append("</td><td>").append(initialPossible).append("</td></tr>")
                    .append("<tr><td>Limited</td><td>").append(limitedCount).append("</td><td>").append(limitedPossible).append("</td></tr>")
                    .append("<tr><td>Defined</td><td>").append(definedCount).append("</td><td>").append(definedPossible).append("</td></tr>")
                    .append("<tr><td>Managed</td><td>").append(managedCount).append("</td><td>").append(managedPossible).append("</td></tr>")
                    .append("<tr><td>Optimized</td><td>").append(optimizedCount).append("</td><td>").append(optimizedPossible).append("</td></tr>")
                    .append("<tr><td>Not applicable</td><td>").append(notApplicableCount).append("</td><td>").append(notApplicablePossible).append("</td></tr>")
                    .append("</tbody></table>");

            // Roadmap
            html.append("<br><br><h2>RoadMap</h2><hr style=\"border: 1px solid black;\"><br>")
                    .append(actualCompanyName).append(" needs to assign roles and responsibilities, to handle all actions related to the analysis of the ")
                    .append("non-conformity, execution of improvements and controls implementation to achieve the acceptable state ")
                    .append("for certification. ")
                    .append("The table below shows ").append(stdName).append(" controls ordered and prioritized by severity of Maturity Levels. ")
                    .append("The table represents step by step guide to start executing improvements on minor non-conformity ")
                    .append("clauses and proceed with major non-conformity. It is highly recommended to follow the order, controls, ")
                    .append("which marked as Conforms, represent what's already in place and working well, minor non-conformities ")
                    .append("can be resolved by one-time activities(e.g. waterfall methodology), major non-conformities requires ")
                    .append("iterative, team-based approach, in order complete all activities, resolve issues effectively and in time.");

            html.append("<table class=\"table1\" style=\"width:100%;border-width: 1px solid black;border-collapse: collapse\" border=\"1\">")
                    .append("<tbody>")
                    .append("<tr><td><b>Clause</b></td><td><b>What's already in place/working well</b></td>")
                    .append("<td><b>Areas requiring improvement</b></td><td><b>Status/level of work required</b></td>")
                    .append("<td><b>Possible barrier to certification?</b></td><td><b>Remarks</b></td></tr>");

            for (int j = 0; j < meetingCount; j++) {
                query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE std_id = ? AND department_name = ? AND meeting_date = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdId);
                ps.setString(2, department);
                ps.setString(3, meetingDates.get(j));
                res = ps.executeQuery();
                if (res.next()) {
                    String headerId = res.getString("id");
                    query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
                    ps = con.prepareStatement(query);
                    ps.setString(1, headerId);
                    res1 = ps.executeQuery();
                    while (res1.next()) {
                        query = "SELECT name FROM clause_master WHERE std_id = ? AND number = ?";
                        ps = con.prepareStatement(query);
                        ps.setString(1, stdId);
                        ps.setString(2, res1.getString("clause_no").replaceAll(" ", ""));
                        res2 = ps.executeQuery();
                        clauseName = res2.next() ? res2.getString("name") : "";
                        res2.close();

                        html.append("<tr>")
                                .append("<td>").append(res1.getString("clause_no")).append(" ").append(clauseName).append("</td>")
                                .append("<td>").append(res1.getString("description")).append("</td>")
                                .append("<td>").append(res1.getString("area_require_improvement")).append("</td>")
                                .append("<td>").append(res1.getString("status")).append("</td>")
                                .append("<td>").append(res1.getString("possible_barrier_to_certification")).append("</td>")
                                .append("<td>").append(res1.getString("remarks")).append("</td>")
                                .append("</tr>");
                    }
                    res1.close();
                }
                res.close();
                ps.close();
            }
            html.append("</tbody></table>");

            // Clause Detail Report
            html.append("<br><br><h2>Clause Detail Report</h2><hr style=\"border: 1px solid black;\"><br>");

            for (int j = 0; j < meetingCount; j++) {
                query = "SELECT * FROM " + companyCode + "_gap_assessment_header WHERE std_id = ? AND department_name = ? AND meeting_date = ?";
                ps = con.prepareStatement(query);
                ps.setString(1, stdId);
                ps.setString(2, department);
                ps.setString(3, meetingDates.get(j));
                res = ps.executeQuery();
                if (res.next()) {
                    String headerId = res.getString("id");
                    query = "SELECT * FROM " + companyCode + "_gap_assessment_detail WHERE gap_assessment_header_id = ?";
                    ps = con.prepareStatement(query);
                    ps.setString(1, headerId);
                    res1 = ps.executeQuery();
                    while (res1.next()) {
                        query = "SELECT name, description, guidance FROM clause_master WHERE std_id = ? AND number = ?";
                        ps = con.prepareStatement(query);
                        ps.setString(1, stdId);
                        ps.setString(2, res1.getString("clause_no").replaceAll(" ", ""));
                        res2 = ps.executeQuery();
                        if (res2.next()) {
                            clauseName = res2.getString("name");
                            description = res2.getString("description");
                            guidance = res2.getString("guidance");
                        } else {
                            clauseName = "";
                            description = "";
                            guidance = "";
                        }
                        res2.close();

                        html.append("<br><br>")
                                .append("<table class=\"table1\" style=\"width:100%;border-width: 1px solid black;border-collapse: collapse\" border=\"1\">")
                                .append("<tbody>")
                                .append("<tr><td style=\"width: 15%\"><b>Clause</b></td><td>").append(res1.getString("clause_no")).append(" ").append(clauseName).append("</td></tr>")
                                .append("<tr><td><b>Clause Description</b></td><td>").append(description).append("</td></tr>")
                                .append("<tr><td><b>Clause Guidance</b></td><td>").append(guidance).append("</td></tr>")
                                .append("<tr><td><b>What's already in place/working well</b></td><td>").append(res1.getString("description")).append("</td></tr>")
                                .append("<tr><td><b>Areas requiring improvement</b></td><td>").append(res1.getString("area_require_improvement")).append("</td></tr>")
                                .append("<tr><td><b>Status/level of work required</b></td><td>").append(res1.getString("status")).append("</td></tr>")
                                .append("<tr><td><b>Possible barrier to certification?</b></td><td>").append(res1.getString("possible_barrier_to_certification")).append("</td></tr>")
                                .append("<tr><td><b>Remarks</b></td><td>").append(res1.getString("remarks")).append("</td></tr>")
                                .append("</tbody></table>");
                    }
                    res1.close();
                }
                res.close();
                ps.close();
            }

            html.append("</body></html>");

            // Set response headers
            Response.ResponseBuilder responseBuilder = Response.ok(html.toString());
            responseBuilder.header("Content-Disposition", "inline; filename=\"Gap Assessment Report of " + department + ".doc\"");
            responseBuilder.type("application/msword");
            return responseBuilder.build();

        } catch (SQLException e) {
            logger.severe("Database error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error: Database error occurred - " + e.getMessage() + "</h1></body></html>")
                    .type(MediaType.TEXT_HTML)
                    .build();
        } finally {
            try {
                if (res != null) res.close();
                if (res1 != null) res1.close();
                if (res2 != null) res2.close();
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                logger.warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    // Helper method to get rights ID
    private String getRightsId(Connection con, String companyCode, String deptId, String desigId) throws SQLException {
        String query = "SELECT id FROM " + companyCode + "_customer_rights WHERE dept_id = ? AND desig_id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, deptId);
            pstmt.setString(2, desigId);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
            throw new SQLException("No rights found for the given department and designation");
        }
    }

    // Helper method to get standard ID
    private String getStandardId(Connection con, String standardName) throws SQLException {
        String query = "SELECT id FROM standard_master WHERE std_name = ?";
        try (PreparedStatement pstmt = con.prepareStatement(query)) {
            pstmt.setString(1, standardName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
            throw new SQLException("Standard not found: " + standardName);
        }
    }

    // Helper method to generate PDF report
    private String generatePDFReport(ReportRequest request) throws Exception {
        // Implement PDF generation logic here
        // This is a placeholder - implement actual PDF generation
        return "http://example.com/reports/" + request.getCompanyCode() + "/" + request.getStandardName() + ".pdf";
    }

    // Helper method to generate DOC report
    private String generateDOCReport(ReportRequest request) throws Exception {
        // Implement DOC generation logic here
        // This is a placeholder - implement actual DOC generation
        return "http://example.com/reports/" + request.getCompanyCode() + "/" + request.getStandardName() + ".doc";
    }
}