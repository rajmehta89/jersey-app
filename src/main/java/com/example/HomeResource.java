package com.example;

import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.ValidationUtil;

@Path("/Home")
public class HomeResource {

    private static final Logger logger = Logger.getLogger(HomeResource.class.getName());

    public static class HomeRequest {
        public String companyCode;
    }

    public static class HomeResponse {
        public boolean success;
        public String error;
        public HomeData homeData;
        
        public HomeResponse() {
            this.success = false;
        }
        
        public HomeResponse(HomeData homeData) {
            this.success = true;
            this.homeData = homeData;
        }
    }

    public static class HomeData {
        public String companyId;
        public List<String> stdNameList;
        public List<String> stdCategory;

        public HomeData(String companyId, List<String> stdNameList, List<String> stdCategory) {
            this.companyId = companyId;
            this.stdNameList = stdNameList;
            this.stdCategory = stdCategory;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHomeData(HomeRequest request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;

        try {
            logger.info("Received home data request for company code: " + (request.companyCode != null ? request.companyCode : "null"));

            // Validate input parameters
            if (!ValidationUtil.isNotEmpty(request.companyCode)) {
                return ErrorHandler.badRequest("Missing required parameter: companyCode", 
                        "Missing required parameter: companyCode in getHomeData request");
            }
            
            if (!ValidationUtil.isValidCompanyCode(request.companyCode)) {
                return ErrorHandler.badRequest("Invalid company code format", 
                        "Invalid company code format: " + request.companyCode);
            }

            con = DBConfig.getConnection();
            
            // Get company ID
            String companyId = "";
            String query = "SELECT id FROM company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.companyCode);
            res = ps.executeQuery();
            
            if (res.next()) {
                companyId = res.getString("id");
                logger.info("Found company ID: " + companyId + " for company code: " + request.companyCode);
            } else {
                return ErrorHandler.notFound("Company not found", 
                        "No company found with code: " + request.companyCode);
            }

            // Initialize lists for standard names and categories
            List<String> stdNameList = new ArrayList<>();
            List<String> stdCategory = new ArrayList<>();
            
            // You can add additional queries here to populate these lists if needed
            // For example:
            /*
            query = "SELECT standard_name, category FROM standards WHERE company_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, companyId);
            res = ps.executeQuery();
            
            while (res.next()) {
                stdNameList.add(res.getString("standard_name"));
                stdCategory.add(res.getString("category"));
            }
            */

            // Create HomeData object
            HomeData homeData = new HomeData(companyId, stdNameList, stdCategory);
            
            logger.info("Successfully retrieved home data for company code: " + request.companyCode);
            return Response.ok(new HomeResponse(homeData)).build();

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
