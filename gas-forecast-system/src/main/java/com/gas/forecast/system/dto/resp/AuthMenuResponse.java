package com.gas.forecast.system.dto.resp;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMenuResponse {
    private Long id;

    private Long parentId;

    private String name;

    private String path;

    private String component;

    private String icon;

    private Integer sortNo;

    private Integer hidden;

    private List<AuthMenuResponse> children;

    public Long id() {
        return id;
    }

    public Long parentId() {
        return parentId;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public String component() {
        return component;
    }

    public String icon() {
        return icon;
    }

    public Integer sortNo() {
        return sortNo;
    }

    public Integer hidden() {
        return hidden;
    }

    public List<AuthMenuResponse> children() {
        return children;
    }
}
