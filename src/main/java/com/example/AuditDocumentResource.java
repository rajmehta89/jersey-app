package com.example;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import java.io.*;

import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.ValidationUtil;

@Path("/audit-document")
public class AuditDocumentResource {

    private static final Logger logger = Logger.getLogger(AuditDocumentResource.class.getName());

    // Response class for clause requirement
    public static class ClauseRequirementResponse {

        public boolean success;

        public String error;

        public String requirement;
        
        public ClauseRequirementResponse() {
            
            this.success = false;

        }
        
        public ClauseRequirementResponse(String requirement) {

            this.success = true;

            this.requirement = requirement;

        }

    }

    @GET
    @Path("/requirement")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClauseRequirement(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @QueryParam("std-name") String stdName,
            @QueryParam("clause-no") String clauseNo) {
        
        if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(employeeId)) {
            return ErrorHandler.badRequest("Company code and employee ID are required in headers", 
                "Missing headers: company-code=" + companyCode + ", employee-id=" + employeeId);
        }
        
        if (!ValidationUtil.isNotEmpty(stdName) || !ValidationUtil.isNotEmpty(clauseNo)) {
            return ErrorHandler.badRequest("Standard name and clause number are required", 
                "Invalid parameters: stdName=" + stdName + ", clauseNo=" + clauseNo);
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            con = DBConfig.getConnection();
            
            // First get the standard ID
            String standardQuery = "SELECT id FROM standard_master WHERE std_name = ?";
            ps = con.prepareStatement(standardQuery);
            ps.setString(1, stdName);
            rs = ps.executeQuery();
            
            if (!rs.next()) {
                return ErrorHandler.notFound("Standard not found", 
                    "Standard not found for name: " + stdName);
            }
            
            String stdId = rs.getString("id");
            
            // Close previous result set and prepared statement
            rs.close();
            ps.close();
            
            // Now get the clause requirement
            String requirementQuery = "SELECT requirement FROM clause_master WHERE number = ? AND std_id = ?";
            ps = con.prepareStatement(requirementQuery);
            ps.setString(1, clauseNo);
            ps.setString(2, stdId);
            rs = ps.executeQuery();
            
            if (!rs.next()) {
                return ErrorHandler.notFound("Clause requirement not found", 
                    "Clause requirement not found for clause: " + clauseNo + " and standard: " + stdName);
            }
            
            String requirement = rs.getString("requirement");
            
            return Response.ok(new ClauseRequirementResponse(requirement)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } finally {
            closeResources(rs, ps, con);
        }
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadAuditDocument(
            @HeaderParam("company-code") String companyCode,
            @HeaderParam("employee-id") String employeeId,
            @FormDataParam("intr-audit-id") String intrAuditId,
            @FormDataParam("std-name") String stdName,
            @FormDataParam("clause-no") String clauseNo,
            @FormDataParam("file") FormDataContentDisposition fileDetails,
            @FormDataParam("file") InputStream fileInputStream) {
        
        if (!ValidationUtil.isNotEmpty(companyCode) || !ValidationUtil.isNotEmpty(employeeId)) {
            return ErrorHandler.badRequest("Company code and employee ID are required in headers", 
                "Missing headers: company-code=" + companyCode + ", employee-id=" + employeeId);
        }
        
        if (!ValidationUtil.isNotEmpty(intrAuditId) || 
            !ValidationUtil.isNotEmpty(stdName) || 
            !ValidationUtil.isNotEmpty(clauseNo)) {
            return ErrorHandler.badRequest("All fields are required", 
                "Invalid parameters: intrAuditId=" + intrAuditId + 
                ", stdName=" + stdName + ", clauseNo=" + clauseNo);
        }

        if (fileDetails == null || fileInputStream == null) {
            return ErrorHandler.badRequest("File is required", "No file was uploaded");
        }

        String fileName = fileDetails.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            return ErrorHandler.badRequest("Invalid file name", "File name cannot be empty");
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = DBConfig.getConnection();
            
            // Get standard ID
            String standardQuery = "SELECT id FROM standard_master WHERE std_name = ?";
            ps = con.prepareStatement(standardQuery);
            ps.setString(1, stdName);
            rs = ps.executeQuery();
            
            if (!rs.next()) {
                return ErrorHandler.notFound("Standard not found", 
                    "Standard not found for name: " + stdName);
            }
            
            String stdId = rs.getString("id");
            
            // Get company code from internal audit
            rs.close();
            ps.close();
            String companyQuery = "SELECT company_code FROM internal_audit WHERE id = ?";
            ps = con.prepareStatement(companyQuery);
            ps.setString(1, intrAuditId);
            rs = ps.executeQuery();
            
            if (!rs.next()) {
                return ErrorHandler.notFound("Internal audit not found", 
                    "Internal audit not found for id: " + intrAuditId);
            }
            
            String dbCompanyCode = rs.getString("company_code");
            if (!companyCode.equals(dbCompanyCode)) {
                return ErrorHandler.unauthorized("Company code mismatch", 
                    "Header company code does not match database company code");
            }
            
            // Create directory if it doesn't exist
            String basePath = "C:\\Installation\\ManagementERP";
            String relativePath = companyCode + "\\" + clauseNo + "\\Attachment";
            File directory = new File(basePath + File.separator + relativePath);
            if (!directory.exists() && !directory.mkdirs()) {
                return ErrorHandler.serverError("Failed to create directory", 
                    "Failed to create directory: " + directory.getAbsolutePath());
            }
            
            // Save file with unique name to prevent overwrites
            String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
            File outputFile = new File(directory, uniqueFileName);
            
            try (OutputStream out = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192]; // Increased buffer size for better performance
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            // Insert document record
            rs.close();
            ps.close();
            String maxIdQuery = "SELECT COALESCE(MAX(id), 0) as max_id FROM " + companyCode + "_internal_audit_document";
            ps = con.prepareStatement(maxIdQuery);
            rs = ps.executeQuery();
            
            int newId = 1;
            if (rs.next()) {
                newId = rs.getInt("max_id") + 1;
            }
            
            String insertQuery = "INSERT INTO " + companyCode + "_internal_audit_document (id, internal_audit_id, standard_id, clause_no, file_name) VALUES (?, ?, ?, ?, ?)";
            ps.close();
            ps = con.prepareStatement(insertQuery);
            ps.setInt(1, newId);
            ps.setString(2, intrAuditId);
            ps.setString(3, stdId);
            ps.setString(4, clauseNo);
            ps.setString(5, uniqueFileName);
            
            int result = ps.executeUpdate();
            
            if (result > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Document uploaded successfully");
                response.put("fileName", uniqueFileName);
                response.put("filePath", outputFile.getAbsolutePath());
                return Response.ok(response).build();
            } else {
                return ErrorHandler.serverError("Failed to save document record", 
                    "No rows affected when inserting document record");
            }

        } catch (SQLException | IOException e) {
            return ErrorHandler.serverError("Error processing document upload", e);
        } finally {
            closeResources(rs, ps, con);
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