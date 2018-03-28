package com.zjcds.common.db.au.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * created date：2018-03-27
 * @author niezhegang
 */
@Getter
@Setter
public class ModuleProperties {
    /**模块名称*/
    private String moduleName;
    /**自否自动执行*/
    private Boolean autoExec = true;
    /**脚本执行顺序,值越小执行顺序越优先*/
    private Integer order;

    /**当前程序需要版本*/
    private Integer currentVersion;
    /**升级使用的数据源bean名*/
    private String dataSource = "dataSource";
}
