package com.zjcds.common.db.au;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * created date：2018-03-01
 * @author niezhegang
 */
@ConfigurationProperties("com.zjcds.db")
@Getter
@Setter
public class DBAutoUpdateProperties {

    private static final String DefaultVersionTableName = "t_sys_db_version";

    private static final String DefaultVersionFieldName = "ver";

    private String versionTableName = DefaultVersionFieldName;

    //版本字段名
    private String versionFieldName = DefaultVersionFieldName;
    //初始化版本
    private Integer initVersion = 0;
    //sql目录
    private String sql;

    private Integer currentVersion;

}
