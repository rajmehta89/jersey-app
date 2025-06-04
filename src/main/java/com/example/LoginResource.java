package com.example;

import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Date;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.example.util.DBConfig;
import com.example.util.ErrorHandler;
import com.example.util.SecurityUtil;
import com.example.util.ValidationUtil;

@Path("/ValidateLogin")
public class LoginResource {

    private static final Logger logger = Logger.getLogger(LoginResource.class.getName());

    public static class LoginRequest {
        public String username;
        public String password;
        public String companyCode;
        public String loginType;
        public String parentCompanyCode;
    }

    public static class LoginResponse {
        public boolean success;
        public String error;
        public String redirect;
        public UserData userData;
        
        public LoginResponse() {
            this.success = false;
        }
        
        public LoginResponse(String redirect, UserData userData) {
            this.success = true;
            this.redirect = redirect;
            this.userData = userData;
        }
    }

    public static class UserData {
        public String empId;
        public String empName;
        public String companyCode;
        public String actualCompanyCode;
        public String loginCompanyName;
        public String loginType;
        public String configType;
        public String riskType;
        public String parentCompanyCode;
        public String actualParentCompanyCode;
        public String companyId;
        public String useDesignationId;
        public String userDepartmentId;

        public UserData(String empId, String empName, String companyCode, String actualCompanyCode,
                        String loginCompanyName, String loginType, String configType, String riskType,
                        String parentCompanyCode, String actualParentCompanyCode, String companyId,
                        String useDesignationId, String userDepartmentId) {
            this.empId = empId;
            this.empName = empName;
            this.companyCode = companyCode;
            this.actualCompanyCode = actualCompanyCode;
            this.loginCompanyName = loginCompanyName;
            this.loginType = loginType;
            this.configType = configType;
            this.riskType = riskType;
            this.parentCompanyCode = parentCompanyCode;
            this.actualParentCompanyCode = actualParentCompanyCode;
            this.companyId = companyId;
            this.useDesignationId = useDesignationId;
            this.userDepartmentId = userDepartmentId;
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateLogin(LoginRequest request) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet res = null;
        ResultSet res1 = null;
        ResultSet res2 = null;

        try {
            logger.info("Received login request for username: " + (request.username != null ? request.username : "null"));

            // Validate input parameters
            if (!ValidationUtil.isNotEmpty(request.username) || 
                !ValidationUtil.isNotEmpty(request.password) ||
                !ValidationUtil.isNotEmpty(request.companyCode) || 
                !ValidationUtil.isNotEmpty(request.loginType)) {
                
                return ErrorHandler.badRequest("Missing required parameters", 
                        "Missing required parameters in login request");
            }

            // Validate parent company code for specific login types
            if (("Certification body Client".equalsIgnoreCase(request.loginType) ||
                    "Consultant Client".equalsIgnoreCase(request.loginType) ||
                    "CPA Client".equalsIgnoreCase(request.loginType)) &&
                    !ValidationUtil.isNotEmpty(request.parentCompanyCode)) {
                
                return ErrorHandler.badRequest("Parent company code is required for this login type", 
                        "Missing parent company code for login type: " + request.loginType);
            }
            
            // Validate company code format
            if (!ValidationUtil.isValidCompanyCode(request.companyCode)) {
                return ErrorHandler.badRequest("Invalid company code format", 
                        "Invalid company code format: " + request.companyCode);
            }
            
            // Validate username format
            if (!ValidationUtil.isValidUsername(request.username)) {
                return ErrorHandler.badRequest("Invalid username format", 
                        "Invalid username format: " + request.username);
            }

            con = DBConfig.getConnection();
            String actualCompanyCode = request.companyCode;
            String actualParentCompanyCode = request.parentCompanyCode != null ? request.parentCompanyCode : "";
            String companyCode = request.companyCode.replaceAll(" ", "_");
            String parentCompanyCode = request.parentCompanyCode != null ? request.parentCompanyCode.replaceAll(" ", "_") : "";

            // Format company code based on login type
            companyCode = formatCompanyCode(companyCode, parentCompanyCode, request.loginType);

            // Prepare login query
            String query;
            try {
                query = prepareLoginQuery(companyCode, request.loginType);
            } catch (IllegalArgumentException e) {
                return ErrorHandler.badRequest("Invalid company code", e.getMessage());
            }
            
            ps = con.prepareStatement(query);
            ps.setString(1, request.username);
            
            // In a real implementation, we would hash the password and compare with stored hash
            // For now, we'll just use the plain password for compatibility with existing data
            ps.setString(2, request.password);
            
            res = ps.executeQuery();

            String employeeId = null;
            String employeeName = null;
            String empId = null;

            if (res.next()) {
                employeeId = res.getString("user_id");
                employeeName = res.getString("username");
                empId = res.getString("emp_id");

                logger.info("Login successful for user: " + employeeName);
            }

            if (employeeId == null) {
                return ErrorHandler.unauthorized("Invalid username or password", 
                        "Invalid login attempt for username: " + request.username);
            }

            // Fetch company details and employee data
            CompanyData companyData = fetchCompanyData(con, ps, res1, res2, request.loginType, 
                    actualCompanyCode, actualParentCompanyCode, parentCompanyCode, companyCode, empId);

            // Create UserData object
            UserData userData = new UserData(employeeId, employeeName, companyCode, actualCompanyCode,
                    companyData.loginCompanyName, request.loginType, companyData.configType, companyData.riskType, 
                    parentCompanyCode, actualParentCompanyCode, companyData.companyId, 
                    companyData.useDesignationId, companyData.userDepartmentId);

            // Determine redirect path
            String redirectPath = getRedirectPath(request.loginType, companyData.packageStatus, companyData.companyStatus);
            
            logger.info("Redirecting to: " + redirectPath);
            return Response.ok(new LoginResponse(redirectPath, userData)).build();

        } catch (SQLException e) {
            return ErrorHandler.serverError("Database error occurred", e);
        } catch (Exception e) {
            return ErrorHandler.serverError("An unexpected error occurred", e);
        } finally {
            closeResources(res, res1, res2, ps, con);
        }
    }
    
    private String formatCompanyCode(String companyCode, String parentCompanyCode, String loginType) {
        if ("Certification body".equalsIgnoreCase(loginType)) {
            return "CB_" + companyCode;
        } else if ("Consultant".equalsIgnoreCase(loginType)) {
            return "CS_" + companyCode;
        } else if ("CPA".equalsIgnoreCase(loginType)) {
            return "CPA_" + companyCode;
        } else if ("Certification body Client".equalsIgnoreCase(loginType)) {
            return "CB_" + parentCompanyCode + "_" + companyCode;
        } else if ("Consultant Client".equalsIgnoreCase(loginType)) {
            return "CS_" + parentCompanyCode + "_" + companyCode;
        } else if ("CPA Client".equalsIgnoreCase(loginType)) {
            return "CPA_" + parentCompanyCode + "_" + companyCode;
        }
        return companyCode;
    }

    private String prepareLoginQuery(String companyCode, String loginType) {
        if (!ValidationUtil.isValidCompanyCode(companyCode)) {
            logger.warning("Invalid company code received: " + companyCode);
            throw new IllegalArgumentException("Invalid company code");
        }
        if ("Niall Login".equalsIgnoreCase(loginType)) {
            return "SELECT * FROM niall_user_master WHERE username = ? AND password = ?";
        } else {
            return "SELECT * FROM " + companyCode + "_user_master WHERE username = ? AND password = ?";
        }
    }

    private String getRedirectPath(String loginType, String packageStatus, String companyStatus) {
        if ("Niall Login".equalsIgnoreCase(loginType)) {
            return "/APITest/Home2";
        } else if ("Certification body".equalsIgnoreCase(loginType) ||
                "Consultant".equalsIgnoreCase(loginType) ||
                "CPA".equalsIgnoreCase(loginType)) {
            return "/APITest/Home3";
        } else {
            if ("Expired".equalsIgnoreCase(packageStatus) || "Inactive".equalsIgnoreCase(companyStatus)) {
                return  "/errorpage.jsp";
            }
            return "/Home";
        }
    }
    
    private static class CompanyData {
        String loginCompanyName = "";
        String configType = null;
        String riskType = null;
        String companyId = null;
        String useDesignationId = null;
        String userDepartmentId = null;
        String companyStatus = null;
        String packageStatus = "Expired";
    }
    
    private CompanyData fetchCompanyData(Connection con, PreparedStatement ps, ResultSet res1, ResultSet res2, 
            String loginType, String actualCompanyCode, String actualParentCompanyCode, 
            String parentCompanyCode, String companyCode, String empId) throws SQLException {
        
        CompanyData data = new CompanyData();
        String query;
        int companyIdInt = 0;
        Date validityTo = null;

        if ("Certification body".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM certification_body WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }
        } else if ("Consultant".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM consultant WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }
        } else if ("CPA".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM cpa WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }
        } else if ("Niall Login".equalsIgnoreCase(loginType)) {
            data.loginCompanyName = "Niall Services";
        } else if ("Client Login".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
                data.configType = res2.getString("config_type");
                data.riskType = res2.getString("risk_type");
                data.companyId = res2.getString("id");
                data.companyStatus = res2.getString("company_status");
                companyIdInt = res2.getInt("id");
            }

            query = "SELECT * FROM " + companyCode + "_employee_detail WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, empId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                data.useDesignationId = res1.getString("designation_id");
                data.userDepartmentId = res1.getString("department_id");
            }
        } else if ("Certification body Client".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM CB_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }

            query = "SELECT * FROM " + companyCode + "_employee_detail WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, empId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                data.useDesignationId = res1.getString("designation_id");
                data.userDepartmentId = res1.getString("department_id");
            }
        } else if ("Consultant Client".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM CS_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }

            query = "SELECT * FROM " + companyCode + "_employee_detail WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, empId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                data.useDesignationId = res1.getString("designation_id");
                data.userDepartmentId = res1.getString("department_id");
            }
        } else if ("CPA Client".equalsIgnoreCase(loginType)) {
            query = "SELECT * FROM CPA_" + parentCompanyCode + "_company_registration WHERE company_code = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, actualCompanyCode);
            res2 = ps.executeQuery();
            if (res2.next()) {
                data.loginCompanyName = res2.getString("company_name");
            }

            query = "SELECT * FROM " + companyCode + "_employee_detail WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, empId);
            res1 = ps.executeQuery();
            if (res1.next()) {
                data.useDesignationId = res1.getString("designation_id");
                data.userDepartmentId = res1.getString("department_id");
            }
        }

        // Check package validity
        if (companyIdInt > 0) {
            query = "SELECT * FROM package_validity_detail WHERE status = 'Active' AND company_id = ?";
            ps = con.prepareStatement(query);
            ps.setInt(1, companyIdInt);
            res1 = ps.executeQuery();
            if (res1.next()) {
                data.packageStatus = res1.getString("status");
                validityTo = res1.getDate("validity_to");
            }
        }

        // Check if package is expired based on validity_to date
        if (validityTo != null) {
            LocalDate todaysDate = LocalDate.now();
            LocalDate validityDate = new java.sql.Date(validityTo.getTime()).toLocalDate();
            if (validityDate.isBefore(todaysDate)) {
                data.packageStatus = "Expired";
            }
        }
        
        return data;
    }

    private void closeResources(ResultSet res, ResultSet res1, ResultSet res2, PreparedStatement ps, Connection con) {
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
