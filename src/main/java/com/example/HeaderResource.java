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

@Path("/Header")
public class HeaderResource {

    private static final Logger logger = Logger.getLogger(HeaderResource.class.getName());

    public static class HeaderRequest {
        public String employeeName;
        public String employeeId;
        public String companyCode;
        public String actualCompanyCode;
        public String loginCompanyName;
        public String loginType;
        public String parentCompanyCode;
        public String configType;
        public String riskType;
    }

    public static class HeaderResponse {
        public boolean success;
        public String error;
        public HeaderData headerData;
        
        public HeaderResponse() {
            this.success = false;
        }
        
        public HeaderResponse(HeaderData headerData) {
            this.success = true;
            this.headerData = headerData;
        }
    }

    public static class ModuleAccess {
        public String moduleId;
        public String moduleName;
        public boolean viewAccess;
        
        public ModuleAccess(String moduleId, String moduleName, boolean viewAccess) {
            this.moduleId = moduleId;
            this.moduleName = moduleName;
            this.viewAccess = viewAccess;
        }
    }

    public static class HeaderData {
        public String userId;
        public String companyId;
        public List<ModuleAccess> moduleAccess;
        public List<String> modulesList;
        
        public HeaderData(String userId, String companyId, List<ModuleAccess> moduleAccess, List<String> modulesList) {
            this.userId = userId;
            this.companyId = companyId;
            this.moduleAccess = moduleAccess;
            this.modulesList = modulesList;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHeaderData(HeaderRequest request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;
        ResultSet res1 = null;

        try {
            logger.info("Received header data request for employee: " + (request.employeeName != null ? request.employeeName : "null"));

            // Validate input parameters
            if (!ValidationUtil.isNotEmpty(request.employeeName) || 
                !ValidationUtil.isNotEmpty(request.companyCode) || 
                !ValidationUtil.isNotEmpty(request.loginType)) {
                
                return ErrorHandler.badRequest("Missing required parameters", 
                        "Missing required parameters in header request");
            }
            
            // Validate company code format
            if (!ValidationUtil.isValidCompanyCode(request.companyCode)) {
                return ErrorHandler.badRequest("Invalid company code format", 
                        "Invalid company code format: " + request.companyCode);
            }

            con = DBConfig.getConnection();
            
            // Get user ID
            String userId = "";
            String query = "SELECT user_id FROM " + request.companyCode + "_user_master WHERE username = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.employeeName);
            res = ps.executeQuery();
            
            if (res.next()) {
                userId = res.getString("user_id");
                logger.info("Found user ID: " + userId + " for employee: " + request.employeeName);
            } else {
                return ErrorHandler.notFound("User not found", 
                        "No user found with name: " + request.employeeName);
            }

            // Get company ID based on login type
            String companyId = getCompanyId(con, ps, res, request);
            
            // Get module access information
            List<ModuleAccess> moduleAccess = getModuleAccess(con, ps, res, res1, request.companyCode, userId);

            // Get package modules list
            List<String> modulesList = getModulesList(con, ps, res, request.actualCompanyCode);

            // Create HeaderData object
            HeaderData headerData = new HeaderData(userId, companyId, moduleAccess, modulesList);
            
            logger.info("Successfully retrieved header data for employee: " + request.employeeName);
            return Response.ok(new HeaderResponse(headerData)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred: " + e.getMessage(), e);
        } catch (Exception e) {
            return ErrorHandler.serverError("An unexpected error occurred: " + e.getMessage(), e);
        } finally {
            closeResources(res, res1, ps, con);
        }
    }
    
    private String getCompanyId(Connection con, PreparedStatement ps, ResultSet res, HeaderRequest request) throws SQLException {
        String companyId = "";
        String query;
        
        if (request.loginType.equalsIgnoreCase("Certification body Client")) {
            query = "SELECT * FROM CB_" + request.parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                companyId = res.getString("id");
            }
        } else if (request.loginType.equalsIgnoreCase("Consultant Client")) {
            query = "SELECT * FROM CS_" + request.parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                companyId = res.getString("id");
            }
        } else if (request.loginType.equalsIgnoreCase("CPA Client")) {
            query = "SELECT * FROM CPA_" + request.parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                companyId = res.getString("id");
            }
        } else if (request.loginType.equalsIgnoreCase("Client Login")) {
            query = "SELECT * FROM company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, request.actualCompanyCode);
            res = ps.executeQuery();
            if (res.next()) {
                companyId = res.getString("id");
            }
        }
        
        return companyId;
    }
    
    private List<ModuleAccess> getModuleAccess(Connection con, PreparedStatement ps, ResultSet res, ResultSet res1, 
            String companyCode, String userId) throws SQLException {
        
        List<ModuleAccess> moduleAccess = new ArrayList<>();
        String moduleId;
        String query;
        
        // Get module access for Audit Plan
        query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'AUDIT PLAN'";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            moduleId = res.getString("module_id");
            
            query = "SELECT * FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                boolean viewAccess = "Yes".equalsIgnoreCase(res1.getString("view_access"));
                moduleAccess.add(new ModuleAccess(moduleId, "AUDIT PLAN", viewAccess));
            }
        }
        
        // Get module access for Internal Audit
        query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'INTERNAL AUDIT'";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            moduleId = res.getString("module_id");
            
            query = "SELECT * FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                boolean viewAccess = "Yes".equalsIgnoreCase(res1.getString("view_access"));
                moduleAccess.add(new ModuleAccess(moduleId, "INTERNAL AUDIT", viewAccess));
            }
        }
        
        // Get module access for External Audit Plan
        query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'EXTERNAL AUDIT PLAN'";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            moduleId = res.getString("module_id");
            
            query = "SELECT * FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                boolean viewAccess = "Yes".equalsIgnoreCase(res1.getString("view_access"));
                moduleAccess.add(new ModuleAccess(moduleId, "EXTERNAL AUDIT PLAN", viewAccess));
            }
        }
        
        // Get module access for External Audit
        query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'EXTERNAL AUDIT'";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            moduleId = res.getString("module_id");
            
            query = "SELECT * FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                boolean viewAccess = "Yes".equalsIgnoreCase(res1.getString("view_access"));
                moduleAccess.add(new ModuleAccess(moduleId, "EXTERNAL AUDIT", viewAccess));
            }
        }
        
        // Get module access for Gap Assessment
        query = "SELECT module_id FROM " + companyCode + "_module_master WHERE module_name = 'GAP ASSESSMENT'";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            moduleId = res.getString("module_id");
            
            query = "SELECT * FROM " + companyCode + "_user_module_access WHERE user_id = ? AND module_id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, userId);
            ps.setString(2, moduleId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                boolean viewAccess = "Yes".equalsIgnoreCase(res1.getString("view_access"));
                moduleAccess.add(new ModuleAccess(moduleId, "GAP ASSESSMENT", viewAccess));
            }
        }
        
        return moduleAccess;
    }
    
    private List<String> getModulesList(Connection con, PreparedStatement ps, ResultSet res, String actualCompanyCode) throws SQLException {
        List<String> modulesList = new ArrayList<>();
        int packageCategoryId = 0;
        int packageCategoryModuleId = 0;
        String query;
        
        /*
        query = "SELECT package_category_id FROM " + actualCompanyCode + "_package_validity";
        ps = con.prepareStatement(query);
        res = ps.executeQuery();
        if (res.next()) {
            packageCategoryId = res.getInt("package_category_id");
        }
        */
        
        query = "SELECT id FROM package_category_modules WHERE id = ?";
        ps = con.prepareStatement(query);
        ps.setInt(1, packageCategoryId);
        res = ps.executeQuery();
        if (res.next()) {
            packageCategoryModuleId = res.getInt("id");
        }
        
        query = "SELECT * FROM package_category_modules_detail WHERE package_category_modules_id = ?";
        ps = con.prepareStatement(query);
        ps.setInt(1, packageCategoryModuleId);
        res = ps.executeQuery();
        while (res.next()) {
            modulesList.add(res.getString("module_name"));
        }
        
        return modulesList;
    }

    private void closeResources(ResultSet res, ResultSet res1, PreparedStatement ps, Connection con) {
        try {
            if (res != null) res.close();
            if (res1 != null) res1.close();
            if (ps != null) ps.close();
            if (con != null) con.close();
        } catch (SQLException e) {
            logger.warning("Error closing resources: " + e.getMessage());
        }
    }
}
