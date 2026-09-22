package com.gas.forecast.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.dao.domain.SysDepartmentTb;
import com.gas.forecast.dao.domain.SysPermissionTb;
import com.gas.forecast.dao.domain.SysRolePermissionRef;
import com.gas.forecast.dao.domain.SysRoleTb;
import com.gas.forecast.dao.domain.SysUserDepartmentRef;
import com.gas.forecast.dao.domain.SysUserRoleRef;
import com.gas.forecast.dao.domain.SysUserTb;
import com.gas.forecast.dao.mapper.SysDepartmentTbMapper;
import com.gas.forecast.dao.mapper.SysPermissionTbMapper;
import com.gas.forecast.dao.mapper.SysRolePermissionRefMapper;
import com.gas.forecast.dao.mapper.SysRoleTbMapper;
import com.gas.forecast.dao.mapper.SysUserDepartmentRefMapper;
import com.gas.forecast.dao.mapper.SysUserRoleRefMapper;
import com.gas.forecast.dao.mapper.SysUserTbMapper;
import com.gas.forecast.system.service.PasswordHashService;
import com.gas.forecast.system.service.SystemManagementService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemManagementServiceImpl implements SystemManagementService {
    private final SysUserTbMapper userMapper;
    private final SysRoleTbMapper roleMapper;
    private final SysDepartmentTbMapper departmentMapper;
    private final SysPermissionTbMapper permissionMapper;
    private final SysUserRoleRefMapper userRoleRefMapper;
    private final SysUserDepartmentRefMapper userDepartmentRefMapper;
    private final SysRolePermissionRefMapper rolePermissionRefMapper;
    private final PasswordHashService passwordHashService;

    public SystemManagementServiceImpl(
            SysUserTbMapper userMapper,
            SysRoleTbMapper roleMapper,
            SysDepartmentTbMapper departmentMapper,
            SysPermissionTbMapper permissionMapper,
            SysUserRoleRefMapper userRoleRefMapper,
            SysUserDepartmentRefMapper userDepartmentRefMapper,
            SysRolePermissionRefMapper rolePermissionRefMapper,
            PasswordHashService passwordHashService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.departmentMapper = departmentMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleRefMapper = userRoleRefMapper;
        this.userDepartmentRefMapper = userDepartmentRefMapper;
        this.rolePermissionRefMapper = rolePermissionRefMapper;
        this.passwordHashService = passwordHashService;
    }

    public PageInfoDTO<Map<String, Object>> listUsers(Map<String, Object> req) {
        long page = longValue(req.get("page"), 1);
        long size = longValue(req.get("size"), 10);
        String keyword = stringValue(req.get("keyword"));
        List<SysUserTb> all = userMapper.selectList(
                Wrappers.<SysUserTb>lambdaQuery().eq(SysUserTb::getDeleted, 0).orderByAsc(SysUserTb::getId));
        List<SysRoleTb> roles =
                roleMapper.selectList(Wrappers.<SysRoleTb>lambdaQuery().orderByAsc(SysRoleTb::getId));
        List<SysDepartmentTb> departments = departmentMapper.selectList(
                Wrappers.<SysDepartmentTb>lambdaQuery().orderByAsc(SysDepartmentTb::getId));
        List<SysUserRoleRef> userRoles = userRoleRefMapper.selectList(Wrappers.emptyWrapper());
        List<SysUserDepartmentRef> userDepartments = userDepartmentRefMapper.selectList(Wrappers.emptyWrapper());
        List<Map<String, Object>> rows = all.stream()
                .filter(user -> matches(
                        keyword,
                        user.getUsername(),
                        user.getRealName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getOrgCode()))
                .map(user -> userRow(user, roles, departments, userRoles, userDepartments))
                .toList();
        return page(rows, page, size);
    }

    @Transactional
    public Map<String, Object> saveUser(Map<String, Object> req) {
        Long id = longOrNull(req.get("id"));
        String username = required(req, "username");
        SysUserTb user = id == null ? new SysUserTb() : userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "用户不存在");
        }
        if (id == null
                && userMapper.selectCount(Wrappers.<SysUserTb>lambdaQuery().eq(SysUserTb::getUsername, username)) > 0) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "用户名已存在");
        }
        user.setUsername(username);
        user.setRealName(required(req, "realName"));
        user.setEmail(stringValue(req.get("email")));
        user.setPhone(stringValue(req.get("phone")));
        user.setOrgCode(stringValue(req.get("orgCode")));
        user.setAvatar(stringValue(req.get("avatar")));
        user.setStatus(intValue(req.get("status"), 1));
        user.setDeleted(0);
        String password = stringValue(req.get("password"));
        if (id == null || !password.isBlank()) {
            String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            user.setPasswordSalt(salt);
            user.setPasswordHash(passwordHashService.hash(username, password.isBlank() ? "admin123" : password, salt));
        }
        if (id == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        replaceUserRefs(user.getId(), longList(req.get("roleIds")), longList(req.get("departmentIds")));
        return Map.of("id", user.getId());
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUserTb user = userMapper.selectById(id);
        if (user == null) {
            return;
        }
        user.setDeleted(1);
        userMapper.updateById(user);
    }

    public PageInfoDTO<Map<String, Object>> listRoles(Map<String, Object> req) {
        long page = longValue(req.get("page"), 1);
        long size = longValue(req.get("size"), 10);
        String keyword = stringValue(req.get("keyword"));
        List<SysRolePermissionRef> refs = rolePermissionRefMapper.selectList(Wrappers.emptyWrapper());
        List<Map<String, Object>> rows =
                roleMapper.selectList(Wrappers.<SysRoleTb>lambdaQuery().orderByAsc(SysRoleTb::getId)).stream()
                        .filter(role -> matches(keyword, role.getRoleCode(), role.getRoleName(), role.getDescription()))
                        .map(role -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", role.getId());
                            row.put("roleCode", role.getRoleCode());
                            row.put("roleName", role.getRoleName());
                            row.put("description", role.getDescription());
                            row.put(
                                    "permissionIds",
                                    refs.stream()
                                            .filter(ref -> ref.getRoleId().equals(role.getId()))
                                            .map(SysRolePermissionRef::getPermissionId)
                                            .toList());
                            row.put("createdAt", role.getCreatedAt());
                            row.put("updatedAt", role.getUpdatedAt());
                            return row;
                        })
                        .toList();
        return page(rows, page, size);
    }

    @Transactional
    public Map<String, Object> saveRole(Map<String, Object> req) {
        Long id = longOrNull(req.get("id"));
        SysRoleTb role = id == null ? new SysRoleTb() : roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "角色不存在");
        }
        role.setRoleCode(required(req, "roleCode"));
        role.setRoleName(required(req, "roleName"));
        role.setDescription(stringValue(req.get("description")));
        if (id == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.updateById(role);
        }
        replaceRolePermissions(role.getId(), longList(req.get("permissionIds")));
        return Map.of("id", role.getId());
    }

    @Transactional
    public void deleteRole(Long id) {
        roleMapper.deleteById(id);
        rolePermissionRefMapper.delete(
                Wrappers.<SysRolePermissionRef>lambdaQuery().eq(SysRolePermissionRef::getRoleId, id));
        userRoleRefMapper.delete(Wrappers.<SysUserRoleRef>lambdaQuery().eq(SysUserRoleRef::getRoleId, id));
    }

    public List<Map<String, Object>> listDepartments(String keyword) {
        List<SysDepartmentTb> departments = departmentMapper.selectList(Wrappers.<SysDepartmentTb>lambdaQuery()
                .orderByAsc(SysDepartmentTb::getSortNo)
                .orderByAsc(SysDepartmentTb::getId));
        return departments.stream()
                .filter(item -> matches(keyword, item.getDepartmentName(), item.getOrgCode()))
                .map(this::departmentRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> saveDepartment(Map<String, Object> req) {
        Long id = longOrNull(req.get("id"));
        SysDepartmentTb department = id == null ? new SysDepartmentTb() : departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "部门不存在");
        }
        department.setParentId(longOrNull(req.get("parentId")));
        department.setDepartmentName(required(req, "departmentName"));
        department.setOrgCode(required(req, "orgCode"));
        department.setSortNo(intValue(req.get("sortNo"), 0));
        department.setStatus(intValue(req.get("status"), 1));
        if (id == null) {
            departmentMapper.insert(department);
        } else {
            departmentMapper.updateById(department);
        }
        return Map.of("id", department.getId());
    }

    public void deleteDepartment(Long id) {
        departmentMapper.deleteById(id);
    }

    public List<Map<String, Object>> listPermissions(String keyword) {
        List<SysPermissionTb> permissions = permissionMapper.selectList(Wrappers.<SysPermissionTb>lambdaQuery()
                .orderByAsc(SysPermissionTb::getSortNo)
                .orderByAsc(SysPermissionTb::getId));
        return permissions.stream()
                .filter(item -> matches(
                        keyword,
                        item.getPermissionName(),
                        item.getPath(),
                        item.getPerms(),
                        item.getButtonCode(),
                        item.getPermissionType()))
                .map(this::permissionRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> savePermission(Map<String, Object> req) {
        Long id = longOrNull(req.get("id"));
        SysPermissionTb permission = id == null ? new SysPermissionTb() : permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "权限不存在");
        }
        permission.setParentId(longOrNull(req.get("parentId")));
        permission.setPermissionName(required(req, "permissionName"));
        permission.setPath(nullableString(req.get("path")));
        permission.setComponent(nullableString(req.get("component")));
        permission.setPermissionType(required(req, "permissionType"));
        permission.setPerms(nullableString(req.get("perms")));
        permission.setButtonCode(nullableString(req.get("buttonCode")));
        permission.setIcon(nullableString(req.get("icon")));
        permission.setSortNo(intValue(req.get("sortNo"), 0));
        permission.setHidden(intValue(req.get("hidden"), 0));
        permission.setStatus(intValue(req.get("status"), 1));
        if (id == null) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
        return Map.of("id", permission.getId());
    }

    public void deletePermission(Long id) {
        if (permissionMapper.selectCount(Wrappers.<SysPermissionTb>lambdaQuery().eq(SysPermissionTb::getParentId, id))
                > 0) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "请先删除子菜单或按钮");
        }
        permissionMapper.deleteById(id);
        rolePermissionRefMapper.delete(
                Wrappers.<SysRolePermissionRef>lambdaQuery().eq(SysRolePermissionRef::getPermissionId, id));
    }

    public Map<String, Object> options() {
        Map<String, Object> data = new HashMap<>();
        data.put(
                "roles",
                roleMapper.selectList(Wrappers.<SysRoleTb>lambdaQuery().orderByAsc(SysRoleTb::getId)).stream()
                        .map(role -> option(role.getId(), role.getRoleName() + " (" + role.getRoleCode() + ")"))
                        .toList());
        data.put(
                "departments",
                departmentMapper
                        .selectList(Wrappers.<SysDepartmentTb>lambdaQuery()
                                .orderByAsc(SysDepartmentTb::getSortNo)
                                .orderByAsc(SysDepartmentTb::getId))
                        .stream()
                        .map(department -> option(department.getId(), department.getDepartmentName()))
                        .toList());
        data.put("permissions", listPermissions(""));
        return data;
    }

    private Map<String, Object> userRow(
            SysUserTb user,
            List<SysRoleTb> roles,
            List<SysDepartmentTb> departments,
            List<SysUserRoleRef> userRoles,
            List<SysUserDepartmentRef> userDepartments) {
        Set<Long> roleIds = userRoles.stream()
                .filter(ref -> ref.getUserId().equals(user.getId()))
                .map(SysUserRoleRef::getRoleId)
                .collect(Collectors.toSet());
        Set<Long> departmentIds = userDepartments.stream()
                .filter(ref -> ref.getUserId().equals(user.getId()))
                .map(SysUserDepartmentRef::getDepartmentId)
                .collect(Collectors.toSet());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("realName", user.getRealName());
        row.put("email", user.getEmail());
        row.put("phone", user.getPhone());
        row.put("orgCode", user.getOrgCode());
        row.put("status", user.getStatus());
        row.put("roleIds", new ArrayList<>(roleIds));
        row.put(
                "roleNames",
                roles.stream()
                        .filter(role -> roleIds.contains(role.getId()))
                        .map(SysRoleTb::getRoleName)
                        .toList());
        row.put("departmentIds", new ArrayList<>(departmentIds));
        row.put(
                "departmentNames",
                departments.stream()
                        .filter(department -> departmentIds.contains(department.getId()))
                        .map(SysDepartmentTb::getDepartmentName)
                        .toList());
        row.put("createdAt", user.getCreatedAt());
        row.put("updatedAt", user.getUpdatedAt());
        return row;
    }

    private Map<String, Object> departmentRow(SysDepartmentTb item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("parentId", item.getParentId());
        row.put("departmentName", item.getDepartmentName());
        row.put("orgCode", item.getOrgCode());
        row.put("sortNo", item.getSortNo());
        row.put("status", item.getStatus());
        row.put("createdAt", item.getCreatedAt());
        row.put("updatedAt", item.getUpdatedAt());
        return row;
    }

    private Map<String, Object> permissionRow(SysPermissionTb item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("parentId", item.getParentId());
        row.put("permissionName", item.getPermissionName());
        row.put("path", item.getPath());
        row.put("component", item.getComponent());
        row.put("permissionType", item.getPermissionType());
        row.put("perms", item.getPerms());
        row.put("buttonCode", item.getButtonCode());
        row.put("icon", item.getIcon());
        row.put("sortNo", item.getSortNo());
        row.put("hidden", item.getHidden());
        row.put("status", item.getStatus());
        row.put("createdAt", item.getCreatedAt());
        row.put("updatedAt", item.getUpdatedAt());
        return row;
    }

    private void replaceUserRefs(Long userId, List<Long> roleIds, List<Long> departmentIds) {
        userRoleRefMapper.delete(Wrappers.<SysUserRoleRef>lambdaQuery().eq(SysUserRoleRef::getUserId, userId));
        roleIds.forEach(roleId -> {
            SysUserRoleRef ref = new SysUserRoleRef();
            ref.setUserId(userId);
            ref.setRoleId(roleId);
            userRoleRefMapper.insert(ref);
        });
        userDepartmentRefMapper.delete(
                Wrappers.<SysUserDepartmentRef>lambdaQuery().eq(SysUserDepartmentRef::getUserId, userId));
        departmentIds.forEach(departmentId -> {
            SysUserDepartmentRef ref = new SysUserDepartmentRef();
            ref.setUserId(userId);
            ref.setDepartmentId(departmentId);
            userDepartmentRefMapper.insert(ref);
        });
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionRefMapper.delete(
                Wrappers.<SysRolePermissionRef>lambdaQuery().eq(SysRolePermissionRef::getRoleId, roleId));
        Set<Long> distinct = new HashSet<>(permissionIds);
        distinct.forEach(permissionId -> {
            SysRolePermissionRef ref = new SysRolePermissionRef();
            ref.setRoleId(roleId);
            ref.setPermissionId(permissionId);
            rolePermissionRefMapper.insert(ref);
        });
    }

    private PageInfoDTO<Map<String, Object>> page(List<Map<String, Object>> rows, long page, long size) {
        int from = (int) Math.min(rows.size(), Math.max(0, (page - 1) * size));
        int to = (int) Math.min(rows.size(), from + size);
        return PageInfoDTO.of(rows.subList(from, to), rows.size(), page, size);
    }

    private boolean matches(String keyword, String... values) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = keyword.toLowerCase();
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> option(Long value, String label) {
        return Map.of("value", value, "label", label);
    }

    private String required(Map<String, Object> req, String key) {
        String value = stringValue(req.get(key));
        if (value.isBlank()) {
            throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, key + "不能为空");
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableString(Object value) {
        String text = stringValue(value);
        return text.isBlank() ? null : text;
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return ((Number) value).intValue();
    }

    private long longValue(Object value, long defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return ((Number) value).longValue();
    }

    private Long longOrNull(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> ((Number) item).longValue())
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
