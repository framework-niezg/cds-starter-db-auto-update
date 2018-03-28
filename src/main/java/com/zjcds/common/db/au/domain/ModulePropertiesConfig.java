package com.zjcds.common.db.au.domain;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

/**
 * 单个模块的数据库自动升级配置对象
 * created date：2018-03-26
 * @author niezhegang
 */
@Getter
public class ModulePropertiesConfig {
    /**默认sql脚本目录名，放在模块目录下面*/
    public final static String  DefaultSqlScriptDir = "sql";
    /**对模块升级时默认初始化版本号 */
    public final static Integer DefaultInitVersion = 0;

    /**模块名称*/
    private String moduleName;
    /**自否自动执行*/
    private Boolean autoExec = true;
    /**脚本执行顺序,值越小执行顺序越优先*/
    private Integer order = Integer.MAX_VALUE;

    /**当前程序需要版本*/
    private Integer currentVersion;
    /**升级使用的数据源bean名*/
    private String dataSource = "dataSource";

    private ModulePropertiesConfig() {

    }

    private void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    private void setAutoExec(boolean autoExec) {
        this.autoExec = autoExec;
    }

    private void setOrder(Integer order) {
        this.order = order;
    }

    private void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    private void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public static Builder newBuilder(){
        return new Builder();
    }

    @Override
    public String toString() {
        return "（" +
                "升级模块='" + moduleName + '\'' +
                ", 模块对应数据库版本=" + currentVersion +
                ", 连接数据源='" + dataSource + '\'' +
                ", 排序码='" + order + '\'' +
                ", 自动执行='" + autoExec + '\'' +
                '）';
    }

    public static final class Builder {
        private String moduleName;
        private Boolean autoExec;
        private Integer order;
        private Integer currentVersion;
        private String dataSource;

        private Builder() {

        }

        public Builder moduleProperties(ModuleProperties moduleProperties) {
            if(moduleProperties != null){
                moduleName(moduleProperties.getModuleName());
                autoExec(moduleProperties.getAutoExec());
                order(moduleProperties.getOrder());
                currentVersion(moduleProperties.getCurrentVersion());
                dataSource(moduleProperties.getDataSource());
            }
            return this;
        }

        public Builder moduleName(String val) {
            moduleName = val;
            return this;
        }

        public Builder autoExec(Boolean val) {
            autoExec = val;
            return this;
        }

        public Builder order(Integer val) {
            order = val;
            return this;
        }


        public Builder currentVersion(Integer val) {
            currentVersion = val;
            return this;
        }

        public Builder dataSource(String val) {
            dataSource = val;
            return this;
        }

        public ModulePropertiesConfig build() {
            ModulePropertiesConfig modulePropertiesConfig = new ModulePropertiesConfig();
            Assert.hasText(moduleName,"数据库自动升级设置的模块名称不能为空！");
            Assert.notNull(currentVersion,moduleName+"当前数据库需升级到的版本号不能为空！");
            modulePropertiesConfig.setModuleName(StringUtils.trim(moduleName));
            modulePropertiesConfig.setCurrentVersion(currentVersion);
            if(autoExec != null)
                modulePropertiesConfig.setAutoExec(autoExec);
            if(order != null)
                modulePropertiesConfig.setOrder(order);
            else
                modulePropertiesConfig.setOrder(Integer.MAX_VALUE);
            if(StringUtils.isNoneBlank(dataSource))
                modulePropertiesConfig.setDataSource(StringUtils.trim(dataSource));
            return modulePropertiesConfig;
        }
    }
}
