package com.zjcds.common.db.au;

import com.zjcds.common.db.au.domain.ModulePropertiesConfig;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

import static com.zjcds.common.db.au.DBAutoUpdateProperties.DBAutoUpdatePropertiesPath;

/**
 * created date：2018-03-01
 * @author niezhegang
 */
@ConfigurationProperties(DBAutoUpdatePropertiesPath)
@Getter
@Setter
public class DBAutoUpdateProperties {

    public final static String DBAutoUpdatePropertiesPath = "com.zjcds.db";

    private static final String DefaultVersionTableName = "t_sys_db_version";

    private static final String LineSeq =  System.getProperty("line.separator");

    private final static String DefaultTableDataSource = "dataSource";

    private static final String DefaultVersionFieldName = "ver";

    private static final String DefaultModuleFieldName = "module";
    //版本表访问使用的数据源
    private String verTableDataSource = DefaultTableDataSource;
    //版本表名
    private String versionTableName = DefaultVersionTableName;
    //模块字段名
    private String moduleFieldName = DefaultModuleFieldName;
    //版本字段名
    private String versionFieldName = DefaultVersionFieldName;
    /**是否自动执行脚本升级*/
    private Boolean autoExec = true;
    /**所有模块的自动升级配置*/
    private List<ModulePropertiesConfig> modulePropertyConfigs = new ArrayList<>();

    public String printDbUpdateSummaryInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(LineSeq).append("数据库自动升级配置情况：");
        if(CollectionUtils.isNotEmpty(modulePropertyConfigs)) {
            int order = 1;
            for(ModulePropertiesConfig moduleDBAutoUpdateProperty : modulePropertyConfigs){
                sb.append(LineSeq).append(StringUtils.leftPad("", 10," ")).append("序列").append(order).append("：").append(moduleDBAutoUpdateProperty.toString());
                order++;
            }
        }
        return sb.toString();
    }

}
