package com.example;

import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.ValidationUtil;

@Path("/GetCompanyId")
public class CompanyResource {

    private static final Logger logger = Logger.getLogger(CompanyResource.class.getName());

    public static class CompanyRequest {
        public String companyCode;
    }

    public static class CompanyResponse {
        public boolean success;
        public String error;
        public String companyId;
        
        public CompanyResponse() {
            this.success = false;
        }
        
        public CompanyResponse(String companyId) {
            this.success = true;
            this.companyId = companyId;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCompanyId(CompanyRequest request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Received company ID request for company code: " + (request.companyCode != null ? request.companyCode : "null"));

            // Validate input parameters
            if (!ValidationUtil.isNotEmpty(request.companyCode)) {
                return ErrorHandler.badRequest("Missing required parameter: companyCode", 
                        "Missing required parameter: companyCode in getCompanyId request");
            }
            
            if (!ValidationUtil.isValidCompanyCode(request.companyCode)) {
                return ErrorHandler.badRequest("Invalid company code format", 
                        "Invalid company code format: " + request.companyCode);
            }

            con = DBConfig.getConnection();
            
            // Get company ID
            String query = "SELECT id FROM company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.companyCode);
            res = ps.executeQuery();
            
            if (res.next()) {
                String companyId = res.getString("id");
                logger.info("Found company ID: " + companyId + " for company code: " + request.companyCode);
                return Response.ok(new CompanyResponse(companyId)).build();
            } else {
                return ErrorHandler.notFound("Company not found", 
                        "No company found with code: " + request.companyCode);
            }

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("An unexpected error occurred", e);
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
