package com.gas.forecast.system.service;

import com.gas.forecast.common.core.PageInfoDTO;

import java.util.List;
import java.util.Map;

public interface SystemManagementService {
    PageInfoDTO<Map<String, Object>> listUsers(Map<String, Object> req);

    Map<String, Object> saveUser(Map<String, Object> req);

    void deleteUser(Long id);

    PageInfoDTO<Map<String, Object>> listRoles(Map<String, Object> req);

    Map<String, Object> saveRole(Map<String, Object> req);

    void deleteRole(Long id);

    List<Map<String, Object>> listDepartments(String keyword);

    Map<String, Object> saveDepartment(Map<String, Object> req);

    void deleteDepartment(Long id);

    List<Map<String, Object>> listPermissions(String keyword);

    Map<String, Object> savePermission(Map<String, Object> req);

    void deletePermission(Long id);

    Map<String, Object> options();
}
