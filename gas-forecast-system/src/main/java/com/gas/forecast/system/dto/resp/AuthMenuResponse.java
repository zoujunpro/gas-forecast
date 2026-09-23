package com.gas.forecast.system.dto.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 授权菜单节点应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMenuResponse {
    /**
     * 菜单ID。
     */
    private Long id;

    /**
     * 上级菜单ID。
     */
    private Long parentId;

    /**
     * 菜单名称。
     */
    private String name;

    /**
     * 路由路径。
     */
    private String path;

    /**
     * 前端组件路径。
     */
    private String component;

    /**
     * 菜单图标名称。
     */
    private String icon;

    /**
     * 排序号。
     */
    private Integer sortNo;

    /**
     * 是否隐藏。
     */
    private Integer hidden;

    /**
     * 子菜单列表。
     */
    private List<AuthMenuResponse> children;
}
